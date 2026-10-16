package app.lifetime.data

import android.content.ContextWrapper
import android.location.Location
import app.lifetime.ui.camera.CameraViewModel
import app.lifetime.ui.timeline.TimelineViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipFile

class CaptureExportTest {
    @get:Rule val tmp = TemporaryFolder()

    private class FakeDao : MomentDao {
        val rows = CopyOnWriteArrayList<Moment>()
        var insertFailure: Exception? = null
        var locationUpdates = 0

        override fun observeAll() = flowOf(emptyList<Moment>())
        override suspend fun getAll() = rows.toList()
        override suspend fun get(id: Long) = rows.find { it.id == id }
        override suspend fun insert(moment: Moment): Long {
            insertFailure?.let { throw it }
            val id = rows.size + 1L
            rows.add(moment.copy(id = id))
            return id
        }
        override suspend fun delete(moment: Moment) { rows.remove(moment) }
        override suspend fun updateLocation(id: Long, latitude: Double, longitude: Double): Int {
            locationUpdates++
            val row = get(id) ?: return 0
            if (row.latitude == null && row.longitude == null) {
                rows[rows.indexOf(row)] = row.copy(latitude = latitude, longitude = longitude)
                return 1
            }
            return 0
        }
    }

    private inner class Fixture(
        failFiling: Boolean = false,
        writePhotoLocation: (File, Location) -> Unit = { _, _ -> },
    ) {
        val dao = FakeDao()
        val incoming = File(tmp.root, "incoming")
        var filingThread: Thread? = null
        val storage = MediaStorage(File(tmp.root, "media"), incoming) {
            filingThread = Thread.currentThread()
            if (failFiling) throw IOException("Forced filing failure")
            ZoneOffset.UTC
        }
        val repository = MomentRepository(ContextWrapper(null), dao, storage, MediaMetadataReader(), writePhotoLocation)
        val unhandled = CopyOnWriteArrayList<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> unhandled.add(e) })

        fun camera(location: suspend () -> Location? = { null }, tagging: Boolean = true) = CameraViewModel(
            repository, scope, MutableStateFlow(tagging), {}, location,
        )

        fun captureFile(type: MediaType = MediaType.VIDEO) = repository.newCaptureFile(type).apply {
            // Large enough to flush the buffered zip during media copying.
            writeBytes(ByteArray(256 * 1024) { (it % 251).toByte() })
        }

        suspend fun savedCapture(): Moment = repository.addCaptured(captureFile(), MediaType.VIDEO, 0)
        fun assertNoFiles() {
            assertTrue(incoming.listFiles().orEmpty().isEmpty())
            assertTrue(storage.root.walkTopDown().none { it.isFile })
            assertTrue(dao.rows.isEmpty())
            assertTrue(unhandled.isEmpty())
        }
    }

    private fun location(lat: Double, lon: Double) = object : Location("test") {
        override fun getLatitude() = lat
        override fun getLongitude() = lon
    }

    @Test fun captureIsSafeBeforeLocationResolvesAndSurvivesAnUnfinishedWait() = runBlocking {
        val f = Fixture()
        try {
            val requested = CompletableDeferred<Unit>()
            val never = CompletableDeferred<Location?>()
            val vm = f.camera({ requested.complete(Unit); never.await() })
            val file = f.captureFile()
            val callbackThread = Thread.currentThread()
            val job = vm.onCaptured(file, MediaType.VIDEO, 0)
            withTimeout(5_000) { requested.await() }

            val row = f.dao.rows.single()
            assertNull(row.latitude)
            assertNull(row.longitude)
            assertEquals(row, vm.lastCapture.value)
            assertFalse(file.exists())
            assertNotEquals(callbackThread, f.filingThread)
            f.repository.cleanUpIncoming()
            assertEquals(256 * 1024L, f.repository.fileFor(row).length())

            // Simulates losing the location task: the saved row/file remain intact.
            job.cancel()
            withTimeout(5_000) { job.join() }
            assertEquals(row, f.dao.rows.single())
            assertTrue(f.repository.fileFor(row).exists())
            assertEquals(0, f.dao.locationUpdates)
            assertTrue(f.unhandled.isEmpty())
        } finally { f.scope.cancel() }
    }

    @Test fun lateLocationUpdatesTheSavedRow() = runBlocking {
        var exifWrites = 0
        val f = Fixture(writePhotoLocation = { _, _ -> exifWrites++ })
        try {
            val vm = f.camera({
                assertEquals(1, f.dao.rows.size)
                location(12.0, 34.0)
            })
            withTimeout(5_000) { vm.onCaptured(f.captureFile(), MediaType.VIDEO, 0).join() }
            assertEquals(12.0, f.dao.rows.single().latitude!!, 0.0)
            assertEquals(34.0, f.dao.rows.single().longitude!!, 0.0)
            assertEquals(1, f.dao.locationUpdates)
            assertEquals(0, exifWrites)
        } finally { f.scope.cancel() }
    }

    @Test fun lateLocationDoesNotReplaceALaterEdit() = runBlocking {
        val f = Fixture()
        try {
            val vm = f.camera({
                val row = f.dao.rows.single()
                f.dao.rows[0] = row.copy(latitude = 56.0, longitude = 78.0)
                location(12.0, 34.0)
            })
            withTimeout(5_000) { vm.onCaptured(f.captureFile(), MediaType.VIDEO, 0).join() }
            assertEquals(56.0, f.dao.rows.single().latitude!!, 0.0)
            assertEquals(78.0, f.dao.rows.single().longitude!!, 0.0)
        } finally { f.scope.cancel() }
    }

    @Test fun photoLocationReachesTheDatabaseBeforeExifIsWritten() = runBlocking {
        lateinit var f: Fixture
        var writes = 0
        f = Fixture(writePhotoLocation = { file, location ->
            val row = f.dao.rows.single()
            assertEquals(12.0, row.latitude!!, 0.0)
            assertEquals(34.0, row.longitude!!, 0.0)
            assertEquals(f.repository.fileFor(row), file)
            assertTrue(file.exists())
            assertEquals(12.0, location.latitude, 0.0)
            assertEquals(34.0, location.longitude, 0.0)
            writes++
        })
        try {
            val vm = f.camera({ location(12.0, 34.0) })
            withTimeout(5_000) { vm.onCaptured(f.captureFile(MediaType.PHOTO), MediaType.PHOTO, 0).join() }
            assertEquals(1, writes)
            assertEquals(1, f.dao.locationUpdates)
            assertTrue(f.unhandled.isEmpty())
        } finally { f.scope.cancel() }
    }

    @Test fun failingExifWriteKeepsTheSavedPhotoAndLocationRow() = runBlocking {
        lateinit var f: Fixture
        var rowBeforeExif: Moment? = null
        f = Fixture(writePhotoLocation = { _, _ ->
            rowBeforeExif = f.dao.rows.single()
            throw IOException("EXIF write failed")
        })
        try {
            val temp = f.captureFile(MediaType.PHOTO)
            val bytes = temp.readBytes()
            val saved = f.repository.addCaptured(temp, MediaType.PHOTO, 0)
            f.repository.updateLocation(saved.id, location(12.0, 34.0))
            val row = f.dao.rows.single()
            assertNotNull(rowBeforeExif)
            assertEquals(rowBeforeExif, row)
            assertEquals(saved.copy(latitude = 12.0, longitude = 34.0), row)
            assertEquals(12.0, row.latitude!!, 0.0)
            assertEquals(34.0, row.longitude!!, 0.0)
            assertArrayEquals(bytes, f.repository.fileFor(row).readBytes())
            assertFalse(temp.exists())
            assertTrue(f.unhandled.isEmpty())
        } finally { f.scope.cancel() }
    }

    @Test fun guardedLocationUpdateDoesNotWriteExifForAnAlreadyLocatedPhoto() = runBlocking {
        var writes = 0
        val f = Fixture(writePhotoLocation = { _, _ -> writes++ })
        try {
            val saved = f.repository.addCaptured(f.captureFile(MediaType.PHOTO), MediaType.PHOTO, 0)
            f.dao.rows[0] = saved.copy(latitude = 56.0, longitude = 78.0)
            f.repository.updateLocation(saved.id, location(12.0, 34.0))
            assertEquals(0, writes)
            assertEquals(saved.copy(latitude = 56.0, longitude = 78.0), f.dao.rows.single())
        } finally { f.scope.cancel() }
    }

    @Test fun guardedLocationUpdateDoesNotWriteExifForAPartiallyLocatedOrMissingPhoto() = runBlocking {
        var writes = 0
        val f = Fixture(writePhotoLocation = { _, _ -> writes++ })
        try {
            val saved = f.repository.addCaptured(f.captureFile(MediaType.PHOTO), MediaType.PHOTO, 0)
            f.dao.rows[0] = saved.copy(latitude = 56.0)
            f.repository.updateLocation(saved.id, location(12.0, 34.0))
            f.repository.updateLocation(999, location(12.0, 34.0))
            assertEquals(0, writes)
            assertEquals(saved.copy(latitude = 56.0), f.dao.rows.single())
        } finally { f.scope.cancel() }
    }

    @Test fun filingFailureDeletesIncomingAndReportsSaveError() = runBlocking {
        val f = Fixture(failFiling = true)
        try {
            val vm = f.camera({ error("Must not request location after a failed save") })
            withTimeout(5_000) { vm.onCaptured(f.captureFile(), MediaType.VIDEO, 0).join() }
            assertEquals("Couldn't save that capture", withTimeout(5_000) { vm.messages.first() })
            assertNull(vm.lastCapture.value)
            f.assertNoFiles()
        } finally { f.scope.cancel() }
    }

    @Test fun insertFailureRollsBackFiledCaptureAndReportsSaveError() = runBlocking {
        val f = Fixture()
        try {
            f.dao.insertFailure = IOException("Database unavailable")
            val vm = f.camera(tagging = false)
            withTimeout(5_000) { vm.onCaptured(f.captureFile(), MediaType.VIDEO, 0).join() }
            assertEquals("Couldn't save that capture", withTimeout(5_000) { vm.messages.first() })
            f.assertNoFiles()
        } finally { f.scope.cancel() }
    }

    @Test fun locationFailureKeepsTheSavedCapture() = runBlocking {
        val f = Fixture()
        try {
            val vm = f.camera({ throw IOException("Location unavailable") })
            withTimeout(5_000) { vm.onCaptured(f.captureFile(), MediaType.VIDEO, 0).join() }
            assertTrue(f.repository.fileFor(f.dao.rows.single()).exists())
            assertNotNull(vm.lastCapture.value)
            assertTrue(f.unhandled.isEmpty())
        } finally { f.scope.cancel() }
    }

    // A provider writes some bytes, then fails or cancels the actual export job.
    private suspend fun checkExportFailure(cancel: Boolean, refuseDeletion: Boolean = false, throwOnDelete: Boolean = false) {
        val f = Fixture()
        try {
            f.savedCapture()
            val target = File(tmp.root, "export.zip").apply { writeText("") }
            val vm = TimelineViewModel(f.repository, f.scope)
            var closed = false
            var deletionCalls = 0
            var bytesWritten = 0
            val job = vm.export {
                val exportJob = currentCoroutineContext().job
                f.repository.exportZip(
                    openTarget = {
                        object : FilterOutputStream(target.outputStream()) {
                            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                                out.write(bytes, offset, length)
                                bytesWritten += length
                                if (cancel) exportJob.cancel(CancellationException("Export cancelled"))
                                else throw IOException("Provider write failed")
                            }
                            override fun close() { try { super.close() } finally { closed = true } }
                        }
                    },
                    deleteTarget = {
                        deletionCalls++
                        assertTrue("Close before deleting", closed)
                        if (throwOnDelete) throw SecurityException("Provider denied deletion")
                        !refuseDeletion && target.delete()
                    },
                )
            }
            withTimeout(5_000) { job.join() }
            val message = withTimeout(5_000) { vm.messages.first() }
            assertTrue(bytesWritten > 0)
            assertTrue(closed)
            assertEquals(1, deletionCalls)
            assertEquals(cancel, job.isCancelled)
            assertNull(vm.busy.value)
            assertTrue(message, message.startsWith("Export failed:"))
            if (refuseDeletion || throwOnDelete) {
                assertTrue(message, message.contains("Couldn't delete the partial export document"))
                assertTrue(message, message.contains("Delete it manually"))
                assertTrue(target.exists())
            } else {
                assertFalse(target.exists())
                if (cancel) assertTrue(message, message.contains("Export cancelled"))
                else assertTrue(message, message.contains("Provider write failed"))
            }
            assertTrue(f.unhandled.isEmpty())
        } finally { f.scope.cancel() }
    }

    @Test fun failedExportDeletesPartialTargetAndReportsFailure() = runBlocking { checkExportFailure(cancel = false) }
    @Test fun cancelledExportDeletesPartialTargetReportsFailureAndRethrows() = runBlocking { checkExportFailure(cancel = true) }
    @Test fun failedExportReportsProviderRefusingDeletion() = runBlocking { checkExportFailure(cancel = false, refuseDeletion = true) }
    @Test fun cancelledExportReportsProviderRefusingDeletionAndRethrows() = runBlocking { checkExportFailure(cancel = true, refuseDeletion = true) }
    @Test fun failedExportReportsProviderThrowingOnDeletion() = runBlocking { checkExportFailure(cancel = false, throwOnDelete = true) }

    @Test fun timelineRethrowsCancellationEvenWhenTheCallerIsStillActive() = runBlocking {
        val f = Fixture()
        try {
            val vm = TimelineViewModel(f.repository, f.scope)
            val job = vm.export { throw CancellationException("Export cancelled") }
            withTimeout(5_000) { job.join() }
            assertTrue(job.isCancelled)
            assertNull(vm.busy.value)
            assertEquals("Export failed: Export cancelled", withTimeout(5_000) { vm.messages.first() })
        } finally { f.scope.cancel() }
    }

    @Test fun missingSourcesAreSkippedAndReportedInAValidExport() = runBlocking {
        val f = Fixture()
        try {
            val row = f.savedCapture()
            val secondMissing = f.savedCapture()
            val kept = f.savedCapture()
            assertTrue(f.repository.fileFor(row).delete())
            assertTrue(f.repository.fileFor(secondMissing).delete())
            val target = File(tmp.root, "export.zip")
            val vm = TimelineViewModel(f.repository, f.scope)
            var deletionCalls = 0
            val manifest = """{"version":1,"moments":[{"file":"media/${kept.relativePath}"}]}"""
            withTimeout(5_000) {
                vm.export {
                    f.repository.exportZip(
                        openTarget = { target.outputStream() },
                        deleteTarget = { deletionCalls++; target.delete() },
                        encodeIndex = { moments ->
                            assertEquals(listOf(kept), moments)
                            manifest.toByteArray()
                        },
                    ).also { assertEquals(MomentRepository.ExportResult(1, 2), it) }
                }.join()
            }
            assertEquals("Exported 1 moments (2 files were missing)", withTimeout(5_000) { vm.messages.first() })
            assertEquals(0, deletionCalls)
            assertTrue(target.exists())
            ZipFile(target).use { zip ->
                assertEquals(setOf("media/${kept.relativePath}", "moments.json"), zip.entries().asSequence().map { it.name }.toSet())
                assertArrayEquals(f.repository.fileFor(kept).readBytes(), zip.getInputStream(zip.getEntry("media/${kept.relativePath}")).use { it.readBytes() })
                assertEquals(manifest, zip.getInputStream(zip.getEntry("moments.json")).bufferedReader().use { it.readText() })
            }
            assertNull(vm.busy.value)
            assertTrue(f.unhandled.isEmpty())
        } finally { f.scope.cancel() }
    }

    @Test fun exportSuccessOmitsMissingSuffixWhenNoFilesAreMissing() = runBlocking {
        val f = Fixture()
        try {
            val vm = TimelineViewModel(f.repository, f.scope)
            withTimeout(5_000) { vm.export { MomentRepository.ExportResult(41, 0) }.join() }
            assertEquals("Exported 41 moments", withTimeout(5_000) { vm.messages.first() })
            assertNull(vm.busy.value)
        } finally { f.scope.cancel() }
    }

    @Test fun failureToOpenExportStillDeletesCreatedDocument() = runBlocking {
        val f = Fixture()
        try {
            val target = File(tmp.root, "export.zip").apply { writeText("") }
            val vm = TimelineViewModel(f.repository, f.scope)
            withTimeout(5_000) {
                vm.export { f.repository.exportZip({ null }, { target.delete() }) }.join()
            }
            assertTrue(withTimeout(5_000) { vm.messages.first() }.startsWith("Export failed:"))
            assertFalse(target.exists())
        } finally { f.scope.cancel() }
    }
}
