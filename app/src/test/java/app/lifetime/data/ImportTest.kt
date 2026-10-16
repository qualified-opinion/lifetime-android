package app.lifetime.data

import android.content.ContextWrapper
import app.lifetime.ui.timeline.TimelineViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList

class ImportTest {
    @get:Rule val tmp = TemporaryFolder()
    private val takenAt = Instant.parse("2026-10-16T14:32:15Z").toEpochMilli()

    private class FakeDao : MomentDao {
        val rows = CopyOnWriteArrayList<Moment>()
        var beforeInsert: suspend (Moment) -> Unit = {}
        override fun observeAll() = flowOf(emptyList<Moment>())
        override suspend fun getAll() = rows.toList()
        override suspend fun get(id: Long) = rows.find { it.id == id }
        override suspend fun insert(moment: Moment): Long {
            beforeInsert(moment)
            val id = rows.size + 1L
            rows.add(moment.copy(id = id))
            return id
        }
        override suspend fun updateLocation(id: Long, latitude: Double, longitude: Double) = 0
        override suspend fun delete(moment: Moment) { rows.remove(moment) }
    }

    private inner class Fixture {
        val dao = FakeDao()
        val incoming = File(tmp.root, "incoming")
        val storage = MediaStorage(File(tmp.root, "media"), incoming) { ZoneOffset.UTC }
        val repository = MomentRepository(ContextWrapper(null), dao, storage, MediaMetadataReader())
        val bytes = ByteArray(16_384) { (it % 251).toByte() }

        suspend fun import(
            meta: MediaMeta = MediaMeta(),
            name: String? = "PXL_20261016_143215.jpg",
            openSource: () -> java.io.InputStream? = { ByteArrayInputStream(bytes) },
        ) = repository.importOne(
            "image/jpeg", MediaType.PHOTO, "jpg", openSource,
            readDate = { MetadataParsing.importDate(it.takenAt, emptyList(), name, null, { takenAt }, ZoneOffset.UTC) },
            readMetadata = { file, type ->
                assertTrue(file.exists())
                assertEquals(MediaType.PHOTO, type)
                meta
            },
        )

        fun assertNoFiles() {
            assertTrue(incoming.listFiles().orEmpty().isEmpty())
            assertTrue(storage.root.walkTopDown().none { it.isFile })
            assertTrue(dao.rows.isEmpty())
        }
    }

    @Test fun originalAccessUsesOriginalAndDoesNotOpenPlain() {
        assertEquals("original", openOriginalOrPlain(true, { "original" }, { error("Must not open plain") }))
    }

    @Test fun deniedOrUnsupportedOriginalAccessFallsBackToPlain() {
        listOf(SecurityException("Denied"), UnsupportedOperationException("Unsupported")).forEach { failure ->
            var originalCalls = 0
            var plainCalls = 0
            assertEquals("plain", openOriginalOrPlain(true, { originalCalls++; throw failure }, { plainCalls++; "plain" }))
            assertEquals(1, originalCalls)
            assertEquals(1, plainCalls)
        }
    }

    @Test fun ineligibleOriginalAccessOpensOnlyPlainAndNullOriginalFallsBack() {
        assertEquals("plain", openOriginalOrPlain(false, { error("Must not request original") }, { "plain" }))
        assertEquals("plain", openOriginalOrPlain(true, { null }, { "plain" }))
        assertNull(openOriginalOrPlain<String>(false, { error("Must not request original") }, { null }))
    }

    @Test fun unrelatedOriginalFailuresFallBackAndPlainOpenerFailuresAreNotSwallowed() {
        val failure = IOException("Read failed")
        listOf(failure, IllegalArgumentException("Invalid original URI"), IllegalStateException("Provider unavailable"), Exception("Provider failed")).forEach { originalFailure ->
            assertEquals("plain", openOriginalOrPlain(true, { throw originalFailure }, { "plain" }))
        }
        listOf<() -> String?>(
            { openOriginalOrPlain(true, { throw SecurityException() }, { throw failure }) },
            { openOriginalOrPlain(true, { null }, { throw failure }) },
            { openOriginalOrPlain(false, { error("Must not request original") }, { throw failure }) },
        ).forEach { open ->
            try {
                open()
                fail("Expected plain failure")
            } catch (actual: IOException) { assertSame(failure, actual) }
        }
    }

    @Test fun originalCancellationPropagatesWithoutOpeningPlain() {
        val cancelled = CancellationException("Original open cancelled")
        try {
            openOriginalOrPlain(true, { throw cancelled }, { error("Must not open plain after cancellation") })
            fail("Expected cancellation")
        } catch (actual: CancellationException) { assertSame(cancelled, actual) }
    }

    @Test fun undatedPixelFilenameIsFiledOnItsDateWithExifLocationAndClosesSource() = runBlocking {
        val f = Fixture()
        var closed = false
        val guessed = f.import(MediaMeta(latitude = 12.0, longitude = 34.0), openSource = {
            object : ByteArrayInputStream(f.bytes) {
                override fun close() { closed = true; super.close() }
            }
        })
        assertFalse(guessed)
        assertTrue(closed)
        val row = f.dao.rows.single()
        assertEquals(takenAt, row.capturedAt)
        assertEquals("2026/10/IMG_20261016_143215_000.jpg", row.relativePath)
        assertEquals(MomentSource.IMPORT, row.source)
        assertEquals(12.0, row.latitude!!, 0.0)
        assertEquals(34.0, row.longitude!!, 0.0)
        assertArrayEquals(f.bytes, f.repository.fileFor(row).readBytes())
        assertTrue(f.incoming.listFiles().orEmpty().isEmpty())
    }

    @Test fun exifNullIslandIsFilteredBeforeInsertionAndMissingDateIsCounted() = runBlocking {
        val f = Fixture()
        assertTrue(f.import(MediaMeta(latitude = 0.0, longitude = -0.0), name = "renamed.jpg"))
        val row = f.dao.rows.single()
        assertEquals(takenAt, row.capturedAt)
        assertNull(row.latitude)
        assertNull(row.longitude)
    }

    @Test fun failedInsertDeletesCommittedImportAndBatchCountsItAsFailed() = runBlocking {
        val f = Fixture()
        val failure = IOException("Database unavailable")
        f.dao.beforeInsert = { row ->
            assertTrue(f.repository.fileFor(row).exists())
            throw failure
        }
        // The per-item path rethrows the insertion failure after cleanup.
        try {
            f.import(name = "renamed.jpg")
            fail("Expected insertion failure")
        } catch (actual: IOException) { assertEquals(failure.message, actual.message) }
        f.assertNoFiles()
        val progress = mutableListOf<Pair<Int, Int>>()
        val result = f.repository.importItems(listOf(Unit), { f.import(name = "renamed.jpg") }, { done, total -> progress.add(done to total) })
        assertEquals(MomentRepository.ImportResult(0, 0), result)
        assertEquals(listOf(1 to 1), progress)
        f.assertNoFiles()
    }

    @Test fun cancelledInsertDeletesCommittedImportAndRethrowsCancellation() = runBlocking {
        val f = Fixture()
        val failure = CancellationException("Insert cancelled")
        f.dao.beforeInsert = { row ->
            assertTrue(f.repository.fileFor(row).exists())
            throw failure
        }
        try {
            f.repository.importItems(listOf(Unit), { f.import() })
            fail("Expected cancellation")
        } catch (actual: CancellationException) { assertEquals(failure.message, actual.message) }
        f.assertNoFiles()
    }

    @Test fun cancellationDuringSuspendingInsertFinishesFileAndRowTogether() = runBlocking {
        val f = Fixture()
        val inserting = CompletableDeferred<Unit>()
        val finishInsert = CompletableDeferred<Unit>()
        f.dao.beforeInsert = { row ->
            assertTrue(f.repository.fileFor(row).exists())
            inserting.complete(Unit)
            finishInsert.await()
        }
        val job = launch(Dispatchers.Default) { f.repository.importItems(listOf(Unit), { f.import() }) }
        try {
            withTimeout(5_000) { inserting.await() }
            job.cancel()
            finishInsert.complete(Unit)
            withTimeout(5_000) { job.join() }
            assertTrue(job.isCancelled)
            val row = f.dao.rows.single()
            assertTrue(f.repository.fileFor(row).exists())
            assertTrue(f.incoming.listFiles().orEmpty().isEmpty())
        } finally { finishInsert.complete(Unit); job.cancel() }
    }

    @Test fun cancellationWhileCopyingClosesSourceAndDeletesIncoming() = runBlocking {
        val f = Fixture()
        var closed = false
        val job = launch(Dispatchers.Default) {
            val importJob = currentCoroutineContext().job
            f.repository.importItems(listOf(Unit), {
                f.import(openSource = {
                    object : ByteArrayInputStream(f.bytes) {
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            importJob.cancel()
                            return super.read(buffer, offset, length)
                        }
                        override fun close() { closed = true; super.close() }
                    }
                })
            })
        }
        withTimeout(5_000) { job.join() }
        assertTrue(job.isCancelled)
        assertTrue(closed)
        f.assertNoFiles()
    }

    @Test fun unreadableSourceCleansIncomingAndBatchContinuesWithAccurateCounts() = runBlocking {
        val f = Fixture()
        val progress = mutableListOf<Pair<Int, Int>>()
        val result = f.repository.importItems(listOf(0, 1, 2), { item ->
            when (item) {
                0 -> f.import(name = "renamed.jpg")
                1 -> f.import(openSource = { null })
                else -> f.import()
            }
        }, { done, total -> progress.add(done to total) })
        assertEquals(MomentRepository.ImportResult(2, 1), result)
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
        assertEquals(2, f.dao.rows.size)
        assertEquals(2, f.storage.root.walkTopDown().count { it.isFile })
        assertTrue(f.incoming.listFiles().orEmpty().isEmpty())
    }

    @Test fun importMessageReportsOnlySuccessfulUndatedItemsAndKeepsUnreadableSuffix() = runBlocking {
        val f = Fixture()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val vm = TimelineViewModel(f.repository, scope)
            withTimeout(5_000) { vm.import(15) { MomentRepository.ImportResult(12, 3) }.join() }
            assertEquals("Added 12 moments (3 had no date, filed under today) (3 couldn't be read)", withTimeout(5_000) { vm.messages.first() })
            assertNull(vm.busy.value)
            withTimeout(5_000) { vm.import(1) { MomentRepository.ImportResult(1, 0) }.join() }
            assertEquals("Added 1 moment", withTimeout(5_000) { vm.messages.first() })
            assertNull(vm.busy.value)
        } finally { scope.cancel() }
    }

    @Test fun cancelledImportClearsBusyAndPropagatesCancellation() = runBlocking {
        val f = Fixture()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val vm = TimelineViewModel(f.repository, scope)
            val job = vm.import(1) { throw CancellationException("Import cancelled") }
            withTimeout(5_000) { job.join() }
            assertTrue(job.isCancelled)
            assertNull(vm.busy.value)
        } finally { scope.cancel() }
    }
}
