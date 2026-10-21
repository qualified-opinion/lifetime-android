package app.lifetime.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class RestoreDuplicatesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val key = RestoreDuplicates.Key(1_000, MediaType.PHOTO, 100)

    private fun media(name: String, value: Byte): File = tmp.newFile(name).apply {
        writeBytes(ByteArray(100) { value })
    }

    @Test fun unrelatedMetadataDoesNotReadFiles() = runBlocking {
        val index = RestoreDuplicates(openFile = { error("Unrelated candidates must not be read") })
        index.add(key, media("existing", 1))
        assertFalse(index.contains(key.copy(capturedAt = 2_000), media("incoming", 1)))
    }

    @Test fun findsContentMatchAmongCollisionsInEitherOrder() = runBlocking {
        val different = media("different", 1)
        val same = media("same", 2)
        val incoming = media("incoming", 2)
        for (files in listOf(listOf(different, same), listOf(same, different))) {
            val index = RestoreDuplicates()
            files.forEach { index.add(key, it) }
            assertTrue(index.contains(key, incoming))
            assertFalse(index.contains(key, media("third-${files.first().name}", 3)))
        }
    }

    @Test fun candidateDigestsAreReusedWithinARestore() = runBlocking {
        val candidate = media("existing", 1)
        val incoming = media("incoming", 1)
        var candidateReads = 0
        val index = RestoreDuplicates(openFile = {
            if (it == candidate) candidateReads++
            it.inputStream()
        })
        index.add(key, candidate)
        assertTrue(index.contains(key, incoming))
        assertTrue(index.contains(key, incoming))
        assertEquals(1, candidateReads)
    }

    @Test fun unreadableCandidateDoesNotHideReadableMatchOrIncomingCopy() = runBlocking {
        val unreadable = media("unreadable", 1)
        val readable = media("readable", 2)
        var warnings = 0
        val index = RestoreDuplicates(
            openFile = { if (it == unreadable) throw IOException("cannot read") else it.inputStream() },
            onUnreadable = { file, _ -> assertEquals(unreadable, file); warnings++ },
        )
        index.add(key, unreadable)
        index.add(key, readable)
        assertTrue(index.contains(key, media("matching", 2)))
        assertFalse(index.contains(key, media("distinct", 3)))
        assertEquals(1, warnings)
    }
}
