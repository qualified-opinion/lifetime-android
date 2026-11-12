package app.lifetime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class MediaStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun storage() = MediaStorage(
        root = File(tmp.root, "media"),
        incoming = File(tmp.root, "incoming"),
        zone = { ZoneId.of("Europe/Berlin") },
    )

    private fun tempWith(storage: MediaStorage, ext: String, content: String) =
        storage.newTempFile(ext).apply { writeText(content) }

    @Test
    fun commitFilesByLocalYearAndMonth() {
        val storage = storage()
        // 23:30 UTC on 31 Oct is already 1 Nov in Berlin.
        val at = Instant.parse("2026-10-31T23:30:00Z").toEpochMilli()
        val temp = tempWith(storage, "jpg", "photo")

        val path = storage.commit(temp, MediaType.PHOTO, at, "jpg")

        assertEquals("2026/11/IMG_20261101_003000_000.jpg", path)
        assertFalse(temp.exists())
        assertEquals("photo", File(storage.root, path).readText())
    }

    @Test
    fun commitNeverOverwritesAnExistingFile() {
        val storage = storage()
        val at = Instant.parse("2026-10-16T12:00:00Z").toEpochMilli()

        val first = storage.commit(tempWith(storage, "mp4", "a"), MediaType.VIDEO, at, "mp4")
        val second = storage.commit(tempWith(storage, "mp4", "b"), MediaType.VIDEO, at, "mp4")

        assertNotEquals(first, second)
        assertTrue(second.startsWith("2026/10/VID_"))
        assertEquals("a", File(storage.root, first).readText())
        assertEquals("b", File(storage.root, second).readText())
    }

    @Test
    fun concurrentCommitsNeverShareAPathOrClobberContent() {
        val storage = storage()
        val at = Instant.parse("2026-10-16T12:00:00Z").toEpochMilli()
        val threads = 8
        val barrier = CyclicBarrier(threads)
        val latch = CountDownLatch(threads)
        val paths = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val failure = AtomicReference<Throwable>()
        val pool = Executors.newFixedThreadPool(threads)
        try {
            repeat(threads) { i ->
                pool.execute {
                    try {
                        val temp = tempWith(storage, "jpg", "photo-$i")
                        barrier.await(5, TimeUnit.SECONDS)
                        val path = storage.commit(temp, MediaType.PHOTO, at, "jpg")
                        assertTrue(paths.add(path))
                        assertEquals("photo-$i", File(storage.root, path).readText())
                    } catch (t: Throwable) {
                        failure.compareAndSet(null, t)
                    } finally {
                        latch.countDown()
                    }
                }
            }
            assertTrue(latch.await(15, TimeUnit.SECONDS))
            failure.get()?.let { throw it }
            assertEquals(threads, paths.size)
            assertEquals(threads, storage.root.walkTopDown().count { it.isFile })
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun clearIncomingRemovesHalfWrittenFiles() {
        val storage = storage()
        val leftover = tempWith(storage, "mp4", "partial")
        leftover.setLastModified(System.currentTimeMillis() - MediaStorage.DEFAULT_INCOMING_MIN_AGE_MS - 1_000)
        storage.clearIncoming()
        assertFalse(leftover.exists())
    }

    @Test
    fun clearIncomingSkipsFreshTempsStillLikelyInUse() {
        val storage = storage()
        val fresh = tempWith(storage, "jpg", "in-flight")
        val now = System.currentTimeMillis()
        fresh.setLastModified(now)
        storage.clearIncoming(nowMs = now, minAgeMs = MediaStorage.DEFAULT_INCOMING_MIN_AGE_MS)
        assertTrue(fresh.exists())
        assertEquals("in-flight", fresh.readText())
    }

    @Test
    fun fileForRejectsPathsOutsideTheMediaRoot() {
        val storage = storage()
        val moment = Moment(
            type = MediaType.PHOTO,
            source = MomentSource.CAMERA,
            relativePath = "../secret.txt",
            mimeType = "image/jpeg",
            capturedAt = 0,
            addedAt = 0,
        )
        try {
            storage.fileFor(moment)
            fail("Expected path escape to be rejected")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("escapes media root"))
        }
    }

    @Test
    fun fileForResolvesCanonicalPathsUnderTheRoot() {
        val storage = storage()
        val at = Instant.parse("2026-10-16T12:00:00Z").toEpochMilli()
        val path = storage.commit(tempWith(storage, "jpg", "ok"), MediaType.PHOTO, at, "jpg")
        val moment = Moment(
            type = MediaType.PHOTO,
            source = MomentSource.CAMERA,
            relativePath = path,
            mimeType = "image/jpeg",
            capturedAt = at,
            addedAt = at,
        )
        val file = storage.fileFor(moment)
        assertTrue(file.canonicalPath.startsWith(storage.root.canonicalPath + File.separator))
        assertEquals("ok", file.readText())
    }
}
