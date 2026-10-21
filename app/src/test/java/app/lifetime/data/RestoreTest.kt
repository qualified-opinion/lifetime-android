package app.lifetime.data

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Real Room, real zip and real JSON under Robolectric: an export reads back into the timeline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RestoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context get() = RuntimeEnvironment.getApplication()
    private val databases = mutableListOf<LifetimeDatabase>()

    @After fun closeDatabases() = databases.forEach { it.close() }

    private inner class Phone(name: String) {
        val database = Room.inMemoryDatabaseBuilder(context, LifetimeDatabase::class.java)
            .allowMainThreadQueries().build().also { databases.add(it) }
        val incoming = File(tmp.root, "$name/incoming")
        val storage = MediaStorage(File(tmp.root, "$name/media"), incoming) { ZoneOffset.UTC }
        val repository = MomentRepository(context, database.momentDao(), storage, MediaMetadataReader())

        fun capture(seed: Int) = repository.newCaptureFile(MediaType.PHOTO).apply {
            writeBytes(ByteArray(4_096) { (it * seed).toByte() })
        }

        suspend fun moments() = database.momentDao().getAll()

        /** Every file on disk has a row and every row has a file. */
        suspend fun assertConsistent() {
            val onDisk = storage.root.walk().filter { it.isFile }.map { it.relativeTo(storage.root).path }.toSet()
            assertEquals(onDisk, moments().map { it.relativePath }.toSet())
            assertTrue(incoming.listFiles().orEmpty().isEmpty())
        }

        suspend fun export(): File {
            val target = File(tmp.root, "export-${System.nanoTime()}.zip")
            repository.exportZip(openTarget = { target.outputStream() }, deleteTarget = { target.delete() })
            return target
        }
    }

    @Test fun exportThenRestoreGivesTheSameMoments() = runBlocking {
        val old = Phone("old")
        val photo = old.repository.addCaptured(old.capture(3), MediaType.PHOTO, 1_000)
        old.repository.addCaptured(old.capture(5), MediaType.PHOTO, 2_000)
        assertTrue(old.repository.setCaption(photo.id, "Sunday market"))
        old.database.momentDao().updateLocation(photo.id, 52.37, 4.89)
        val zip = old.export()

        val new = Phone("new")
        val result = new.repository.restore({ zip.inputStream() })

        assertEquals(MomentRepository.RestoreResult(restored = 2, alreadyHere = 0, failed = 0), result)
        fun List<Moment>.comparable() = map { it.copy(id = 0, addedAt = 0) }.sortedBy { it.capturedAt }
        assertEquals(old.moments().comparable(), new.moments().comparable())
        new.moments().forEach { assertArrayEquals(old.storage.fileFor(it).readBytes(), new.storage.fileFor(it).readBytes()) }
        new.assertConsistent()
    }

    @Test fun restoringTwiceAddsNothing() = runBlocking {
        val phone = Phone("one")
        phone.repository.addCaptured(phone.capture(3), MediaType.PHOTO, 1_000)
        phone.repository.addCaptured(phone.capture(7), MediaType.PHOTO, 2_000)
        val zip = phone.export()

        val first = phone.repository.restore({ zip.inputStream() })
        val second = phone.repository.restore({ zip.inputStream() })

        assertEquals(MomentRepository.RestoreResult(0, 2, 0), first)
        assertEquals(MomentRepository.RestoreResult(0, 2, 0), second)
        assertEquals(2, phone.moments().size)
        phone.assertConsistent()
    }

    @Test fun distinctCollidingFilesRestoreAndRepeatedZipAddsNothing() = runBlocking {
        val files = linkedMapOf(
            "media/a.jpg" to ByteArray(100) { 1 },
            "media/b.jpg" to ByteArray(100) { 2 },
            "media/a-again.jpg" to ByteArray(100) { 1 },
        )
        val zip = handMadeZip(files, files.keys.map { entry(it) })
        val phone = Phone("one")

        assertEquals(MomentRepository.RestoreResult(2, 1, 0), phone.repository.restore({ zip.inputStream() }))
        assertEquals(MomentRepository.RestoreResult(0, 3, 0), phone.repository.restore({ zip.inputStream() }))
        assertEquals(setOf(1.toByte(), 2.toByte()), phone.moments().map { phone.storage.fileFor(it).readBytes().first() }.toSet())
        phone.assertConsistent()
    }

    @Test fun existingCollisionsPreserveMediaAndCaptions() = runBlocking {
        val phone = Phone("one")
        val first = phone.repository.addCaptured(phone.capture(3), MediaType.PHOTO, 1_000)
        val second = phone.repository.addCaptured(phone.capture(7), MediaType.PHOTO, 1_000)
        phone.repository.setCaption(first.id, "keep first")
        phone.repository.setCaption(second.id, "keep second")
        val before = phone.moments()
        val originalBytes = before.associate { it.id to phone.storage.fileFor(it).readBytes() }
        val files = linkedMapOf(
            "media/match.jpg" to phone.storage.fileFor(first).readBytes(),
            "media/new.jpg" to ByteArray(4_096) { 42 },
        )
        val zip = handMadeZip(files, files.keys.map {
            entry(it, capturedAt = Instant.ofEpochMilli(1_000).toString(), caption = "from backup")
        })

        assertEquals(MomentRepository.RestoreResult(1, 1, 0), phone.repository.restore({ zip.inputStream() }))
        before.forEach { old ->
            assertEquals(old, phone.repository.get(old.id))
            assertArrayEquals(originalBytes.getValue(old.id), phone.storage.fileFor(old).readBytes())
        }
        assertEquals("from backup", phone.moments().single { it.id !in before.map { old -> old.id } }.caption)
        phone.assertConsistent()
    }

    @Test fun missingExistingFileDoesNotSuppressBackedUpCopy() = runBlocking {
        val phone = Phone("one")
        val old = phone.repository.addCaptured(phone.capture(3), MediaType.PHOTO, 1_000)
        val bytes = phone.storage.fileFor(old).readBytes()
        assertTrue(phone.storage.fileFor(old).delete())
        val zip = handMadeZip(mapOf("media/a.jpg" to bytes), listOf(entry("media/a.jpg", Instant.ofEpochMilli(1_000).toString())))

        assertEquals(MomentRepository.RestoreResult(1, 0, 0), phone.repository.restore({ zip.inputStream() }))
        assertEquals(old, phone.repository.get(old.id)) // Existing broken row is never rewritten.
        val restored = phone.moments().single { it.id != old.id }
        assertNotEquals(old.relativePath, restored.relativePath)
        assertFalse(phone.storage.fileFor(old).exists())
        assertArrayEquals(bytes, phone.storage.fileFor(restored).readBytes())
        assertTrue(phone.incoming.listFiles().orEmpty().isEmpty())
    }

    @Test fun unreadableExistingFileDoesNotSuppressBackedUpCopy() = runBlocking {
        val phone = Phone("one")
        val old = phone.repository.addCaptured(phone.capture(3), MediaType.PHOTO, 1_000)
        val existingFile = phone.storage.fileFor(old)
        val zip = handMadeZip(mapOf("media/a.jpg" to existingFile.readBytes()), listOf(entry("media/a.jpg", Instant.ofEpochMilli(1_000).toString())))

        val result = phone.repository.restore(
            openZip = { zip.inputStream() },
            openDuplicateFile = { if (it == existingFile) throw IOException("unreadable") else it.inputStream() },
        )
        assertEquals(MomentRepository.RestoreResult(1, 0, 0), result)
        assertEquals(old, phone.repository.get(old.id))
        phone.assertConsistent()
    }

    @Test fun cancellingWhileHashingIncomingCleansUp() = assertHashCancellation(existingFile = false)

    @Test fun cancellingWhileHashingExistingCleansUp() = assertHashCancellation(existingFile = true)

    private fun assertHashCancellation(existingFile: Boolean) = runBlocking {
        val phone = Phone("one")
        val old = phone.repository.addCaptured(phone.capture(3), MediaType.PHOTO, 1_000)
        val oldFile = phone.storage.fileFor(old)
        val original = oldFile.readBytes()
        val zip = handMadeZip(mapOf("media/a.jpg" to original), listOf(entry("media/a.jpg", Instant.ofEpochMilli(1_000).toString())))
        var closed = false
        val restore = async(Dispatchers.IO) {
            val job = currentCoroutineContext()[Job]!!
            phone.repository.restore(
                openZip = { zip.inputStream() },
                openDuplicateFile = { file ->
                    if ((file == oldFile) == existingFile) object : FilterInputStream(file.inputStream()) {
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            val count = `in`.read(buffer, offset, length)
                            job.cancel()
                            return count
                        }
                        override fun close() { super.close(); closed = true }
                    } else file.inputStream()
                },
            )
        }
        try {
            restore.await()
            fail("Expected hashing to stop on cancellation")
        } catch (expected: CancellationException) {
        }
        assertTrue("Hash stream must close", closed)
        assertEquals(listOf(old), phone.moments())
        assertArrayEquals(original, oldFile.readBytes())
        phone.assertConsistent()
    }

    @Test fun badEntriesAreRefusedAndGoodOnesRestored() = runBlocking {
        val zip = handMadeZip(
            files = mapOf("media/2026/10/good.jpg" to ByteArray(100) { 1 }, "media/2026/10/extra.jpg" to ByteArray(10)),
            entries = listOf(
                entry("media/2026/10/good.jpg", caption = "  kept  ", latitude = 0.0, longitude = 0.0),
                entry("media/2026/10/missing.jpg"), // listed, not in the zip
                entry("../../evil.jpg"), // not under media/
                entry("media/2026/10/bad-date.jpg", capturedAt = "yesterday"),
                entry("media/2026/10/good.jpg"), // listed twice
                JSONObject().put("file", "media/x.gif").put("type", "gif").put("capturedAt", "2026-10-16T14:32:15Z"),
            ),
        )
        val phone = Phone("one")
        val result = phone.repository.restore({ zip.inputStream() })

        assertEquals(MomentRepository.RestoreResult(restored = 1, alreadyHere = 0, failed = 5), result)
        val restored = phone.moments().single()
        assertEquals("kept", restored.caption)
        assertNull(restored.latitude) // 0,0 is rejected
        assertEquals(100L, restored.sizeBytes)
        assertEquals(MomentSource.CAMERA, restored.source)
        phone.assertConsistent()
    }

    @Test fun newerOrMissingIndexIsRefusedWithoutTouchingAnything() = runBlocking {
        val phone = Phone("one")
        val newer = handMadeZip(mapOf("media/a.jpg" to ByteArray(10)), listOf(entry("media/a.jpg")), version = 2)
        val noIndex = File(tmp.root, "plain.zip").apply {
            ZipOutputStream(outputStream()).use { it.putNextEntry(ZipEntry("photo.jpg")); it.write(ByteArray(10)) }
        }
        for (zip in listOf(newer, noIndex)) {
            try {
                phone.repository.restore({ zip.inputStream() })
                fail("Expected the restore to refuse $zip")
            } catch (expected: ExportIndex.NotAnExport) {
            }
        }
        assertTrue(phone.moments().isEmpty())
        phone.assertConsistent()
    }

    @Test fun cancellingMidRestoreLeavesNoFileWithoutARow() = runBlocking {
        val files = (1..5).associate { "media/2026/10/$it.jpg" to ByteArray(200_000) { b -> (b * it).toByte() } }
        val zip = handMadeZip(files, files.keys.mapIndexed { i, name -> entry(name, capturedAt = Instant.ofEpochMilli(i * 1_000L).toString()) })
        val phone = Phone("one")
        val secondItemStarted = CompletableDeferred<Unit>()
        var opened = 0
        val job = async(Dispatchers.IO) {
            phone.repository.restore(
                openZip = {
                    val stream = zip.inputStream()
                    if (++opened == 1) stream else SlowStream(stream)
                },
                onProgress = { done, _ -> if (done == 1) secondItemStarted.complete(Unit) },
            )
        }
        withTimeout(10_000) { secondItemStarted.await() }
        job.cancelAndJoin()

        assertTrue(phone.moments().size in 1..4)
        phone.assertConsistent()
    }

    @Test fun cancellingWhileScanningForIndexStopsReading() = assertScanCancellation(cancelPass = 1)

    @Test fun cancellingWhileSkippingUnlistedMediaStopsReading() = assertScanCancellation(cancelPass = 2)

    private fun assertScanCancellation(cancelPass: Int) = runBlocking {
        // Incompressible media forces multiple underlying reads while nextEntry drains it.
        val filler = kotlin.random.Random(42).nextBytes(200_000)
        val zip = handMadeZip(
            linkedMapOf("media/unlisted.jpg" to filler, "media/wanted.jpg" to ByteArray(100)),
            listOf(entry("media/wanted.jpg")),
        )
        val phone = Phone("one")
        var opened = 0
        var bytesAfterCancellation = 0
        val restore = async(Dispatchers.IO) {
            val restoreJob = currentCoroutineContext()[Job]!!
            phone.repository.restore(openZip = {
                val input = zip.inputStream()
                if (++opened != cancelPass) input else object : FilterInputStream(input) {
                    private var bytes = 0
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        val count = `in`.read(buffer, offset, length)
                        if (restoreJob.isCancelled && count > 0) bytesAfterCancellation += count
                        if (count > 0) bytes += count
                        if (bytes >= 16_384) restoreJob.cancel()
                        return count
                    }
                }
            })
        }
        try {
            restore.await()
            fail("Expected cancellation")
        } catch (expected: CancellationException) {
        }
        assertEquals(cancelPass, opened)
        assertEquals("Must stop reading as soon as cancellation is observed", 0, bytesAfterCancellation)
        assertTrue(phone.moments().isEmpty())
        phone.assertConsistent()
    }

    /** Slows the media pass so the cancel lands while an item is being copied. */
    private class SlowStream(input: InputStream) : FilterInputStream(input) {
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            Thread.sleep(1)
            return super.read(b, off, minOf(len, 1_024))
        }
    }

    private fun entry(
        file: String,
        capturedAt: String = "2026-10-16T14:32:15.120Z",
        caption: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ) = JSONObject().apply {
        put("file", file)
        put("type", "photo")
        put("source", "camera")
        put("capturedAt", capturedAt)
        put("mimeType", "image/jpeg")
        caption?.let { put("caption", it) }
        latitude?.let { put("latitude", it) }
        longitude?.let { put("longitude", it) }
    }

    private fun handMadeZip(files: Map<String, ByteArray>, entries: List<JSONObject>, version: Int = 1): File =
        File(tmp.root, "hand-${System.nanoTime()}.zip").apply {
            ZipOutputStream(outputStream()).use { zip ->
                files.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
                zip.putNextEntry(ZipEntry(ExportIndex.FILE_NAME))
                zip.write(JSONObject().put("version", version).put("moments", JSONArray(entries)).toString().toByteArray())
                zip.closeEntry()
            }
        }
}
