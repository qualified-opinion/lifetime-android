package app.lifetime.data

import android.content.Context
import android.location.Location
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The single entry point for reading and changing moments. It keeps the database row
 * and the file on disk in step.
 */
class MomentRepository(
    private val context: Context,
    private val dao: MomentDao,
    private val storage: MediaStorage,
    private val metadataReader: MediaMetadataReader,
) {

    val moments: Flow<List<Moment>> = dao.observeAll()

    suspend fun get(id: Long): Moment? = dao.get(id)

    fun fileFor(moment: Moment): File = storage.fileFor(moment)

    /** Where the camera should write a new capture before it is committed. */
    fun newCaptureFile(type: MediaType): File =
        storage.newTempFile(if (type == MediaType.VIDEO) "mp4" else "jpg")

    /** Files a capture that CameraX has finished writing to [temp]. */
    suspend fun addCaptured(
        temp: File,
        type: MediaType,
        capturedAt: Long,
    ): Moment = withContext(NonCancellable + Dispatchers.IO) {
        // Finish the file/row pair even if the caller is cancelled during filing.
        var committed: File? = null
        try {
            val meta = metadataReader.read(temp, type)
            val extension = temp.extension
            val size = temp.length()
            val relativePath = storage.commit(temp, type, capturedAt, extension)
            committed = File(storage.root, relativePath)
            insert(
                Moment(
                    type = type,
                    source = MomentSource.CAMERA,
                    relativePath = relativePath,
                    mimeType = if (type == MediaType.VIDEO) "video/mp4" else "image/jpeg",
                    capturedAt = capturedAt,
                    addedAt = System.currentTimeMillis(),
                    width = meta.width,
                    height = meta.height,
                    durationMs = meta.durationMs,
                    sizeBytes = size,
                ),
            )
        } catch (failure: Throwable) {
            committed?.delete()
            throw failure
        } finally {
            temp.delete()
        }
    }

    suspend fun updateLocation(id: Long, location: Location) =
        dao.updateLocation(id, location.latitude, location.longitude)

    /**
     * Copies picked gallery items into the app. The original stays where it was.
     * Returns how many were imported successfully. [onProgress] gets (done, total) after each item.
     */
    suspend fun import(
        uris: List<Uri>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        var imported = 0
        uris.forEachIndexed { i, uri ->
            ensureActive() // stop between items if the import is cancelled
            runCatching { importOne(uri) }
                .onSuccess { imported++ }
                .onFailure { Log.w(TAG, "Import failed for $uri", it) }
            onProgress(i + 1, uris.size)
        }
        imported
    }

    private suspend fun importOne(uri: Uri): Moment {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val type = if (mime.startsWith("video")) MediaType.VIDEO else MediaType.PHOTO
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: if (type == MediaType.VIDEO) "mp4" else "jpg"

        val temp = storage.newTempFile(extension)
        try {
            val input = resolver.openInputStream(uri) ?: error("Cannot open $uri")
            input.use { src -> temp.outputStream().use { dst -> src.copyTo(dst) } }

            val meta = metadataReader.read(temp, type)
            val capturedAt = meta.takenAt ?: providerDate(uri) ?: System.currentTimeMillis()
            val size = temp.length()
            val relativePath = storage.commit(temp, type, capturedAt, extension)
            return insert(
                Moment(
                    type = type,
                    source = MomentSource.IMPORT,
                    relativePath = relativePath,
                    mimeType = mime,
                    capturedAt = capturedAt,
                    addedAt = System.currentTimeMillis(),
                    latitude = meta.latitude,
                    longitude = meta.longitude,
                    width = meta.width,
                    height = meta.height,
                    durationMs = meta.durationMs,
                    sizeBytes = size,
                ),
            )
        } finally {
            temp.delete() // no-op once committed
        }
    }

    /**
     * Falls back to what the content provider knows when the file has no embedded date.
     */
    private fun providerDate(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            DATE_COLUMNS.firstNotNullOfOrNull { column ->
                val index = cursor.getColumnIndex(column)
                if (index < 0 || cursor.isNull(index)) return@firstNotNullOfOrNull null
                MetadataParsing.normaliseProviderDate(cursor.getLong(index))
            }
        }
    }.getOrNull()

    private suspend fun insert(moment: Moment): Moment = moment.copy(id = dao.insert(moment))

    suspend fun delete(moment: Moment) = withContext(Dispatchers.IO) {
        storage.fileFor(moment).delete()
        dao.delete(moment)
    }

    /**
     * Writes every file plus a moments.json index into a zip at [target] (a file the user
     * picked with the system "save as" dialog). Returns the number of moments exported.
     */
    suspend fun exportZip(target: Uri): Int = exportZip(
        openTarget = { context.contentResolver.openOutputStream(target) },
        deleteTarget = { DocumentsContract.deleteDocument(context.contentResolver, target) },
    )

    /** The callbacks also let JVM tests exercise failures from document providers. */
    internal suspend fun exportZip(
        openTarget: () -> OutputStream?,
        deleteTarget: () -> Boolean,
    ): Int = try {
        withContext(Dispatchers.IO) {
            ensureActive()
            val all = dao.getAll()
            val index = JSONArray()
            val output = openTarget() ?: error("Cannot open export document")
            // ZipOutputStream.close() can throw while finishing, before closing its output.
            output.use {
                ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                    // Photos and videos are already compressed; storing them is much faster.
                    zip.setLevel(Deflater.NO_COMPRESSION)
                    for (moment in all) {
                        ensureActive()
                        val file = storage.fileFor(moment)
                        zip.putNextEntry(ZipEntry("media/${moment.relativePath}").apply { time = moment.capturedAt })
                        file.inputStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                zip.write(buffer, 0, count)
                            }
                        }
                        zip.closeEntry()
                        index.put(moment.toJson())
                    }
                    zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                    zip.putNextEntry(ZipEntry("moments.json"))
                    zip.write(JSONObject().put("version", 1).put("moments", index).toString(2).toByteArray())
                    zip.closeEntry()
                }
            }
            ensureActive() // Includes errors/cancellation while finishing and closing the zip.
            index.length()
        }
    } catch (failure: Throwable) {
        // Also catches cancellation on the dispatch back to the caller after closing.
        withContext(NonCancellable) {
            // Keep the return from IO non-cancellable too, so cleanup errors aren't lost.
            val cleanupFailure = withContext(Dispatchers.IO) {
                runCatching { check(deleteTarget()) { "Provider refused to delete the document" } }.exceptionOrNull()
            }
            if (cleanupFailure != null) {
                val message = "Couldn't delete the partial export document. Delete it manually before retrying."
                val error = if (failure is CancellationException) {
                    CancellationException(message).apply { initCause(failure) }
                } else {
                    IOException(message, failure)
                }
                error.addSuppressed(cleanupFailure)
                throw error
            }
            throw failure
        }
    }

    private fun Moment.toJson() = JSONObject().apply {
        put("file", "media/$relativePath")
        put("type", type.name.lowercase())
        put("source", source.name.lowercase())
        put("capturedAt", Instant.ofEpochMilli(capturedAt).toString())
        put("mimeType", mimeType)
        latitude?.let { put("latitude", it) }
        longitude?.let { put("longitude", it) }
        if (width > 0) put("width", width)
        if (height > 0) put("height", height)
        if (durationMs > 0) put("durationMs", durationMs)
    }

    /** Called on start-up to drop half-written captures. */
    fun cleanUpIncoming() = storage.clearIncoming()

    private companion object {
        const val TAG = "MomentRepository"
        val DATE_COLUMNS = listOf("datetaken", "date_taken", "date_modified", "last_modified")
    }
}
