package app.lifetime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.ZoneId

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
    fun clearIncomingRemovesHalfWrittenFiles() {
        val storage = storage()
        val leftover = tempWith(storage, "mp4", "partial")
        storage.clearIncoming()
        assertFalse(leftover.exists())
    }
}
