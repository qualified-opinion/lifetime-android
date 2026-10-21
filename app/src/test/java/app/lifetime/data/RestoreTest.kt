package app.lifetime.data

import android.app.Application
import androidx.room.Room
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
