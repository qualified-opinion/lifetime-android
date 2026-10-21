package app.lifetime.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Per-restore candidate index. Metadata narrows the search; only matching content is skipped. */
internal class RestoreDuplicates(
    private val openFile: (File) -> InputStream = { it.inputStream() },
    private val onUnreadable: (File, IOException) -> Unit = { _, _ -> },
) {
    data class Key(val capturedAt: Long, val type: MediaType, val size: Long)

    private val candidates = mutableMapOf<Key, MutableList<File>>()
    // Candidates are hashed lazily, once per restore; null means unreadable, never a match.
    private val digests = mutableMapOf<File, ByteArray?>()

    fun add(key: Key, file: File) {
        candidates.getOrPut(key) { mutableListOf() }.add(file)
    }

    suspend fun contains(key: Key, incoming: File): Boolean {
        val matches = candidates[key] ?: return false
        val incomingDigest = digest(incoming)
        for (file in matches) {
            currentCoroutineContext().ensureActive()
            val candidate = if (digests.containsKey(file)) digests[file] else {
                val value = try {
                    digest(file)
                } catch (failure: IOException) {
                    // A broken existing file must not suppress the backed-up copy.
                    onUnreadable(file, failure)
                    null
                }
                digests[file] = value
                value
            }
            if (candidate != null && candidate.contentEquals(incomingDigest)) return true
        }
        return false
    }

    private suspend fun digest(file: File): ByteArray {
        val context = currentCoroutineContext()
        context.ensureActive()
        val hash = MessageDigest.getInstance("SHA-256")
        openFile(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                context.ensureActive()
                val count = input.read(buffer)
                context.ensureActive()
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest()
    }
}
