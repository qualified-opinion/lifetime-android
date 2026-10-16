package app.lifetime.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
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
    private val writePhotoLocation: (File, Location) -> Unit = { file, location ->
        ExifInterface(file).apply { setGpsInfo(location); saveAttributes() }
    },
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
            committed?.let { if (!it.delete()) Log.w(TAG, "Couldn't delete committed capture $it", failure) }
            throw failure
        } finally {
            temp.delete()
        }
    }

    suspend fun updateLocation(id: Long, location: Location) = withContext(Dispatchers.IO) {
        val coordinates = MetadataParsing.validLocation(location.latitude, location.longitude) ?: return@withContext
        if (dao.updateLocation(id, coordinates.first, coordinates.second) == 0) return@withContext
        val moment = dao.get(id)
        if (moment?.type == MediaType.PHOTO) {
            // Keep GPS in exported photos too; EXIF failure must not affect the saved row.
            runCatching { writePhotoLocation(storage.fileFor(moment), location) }
        }
    }

    data class ImportResult(val imported: Int, val undated: Int)

    /**
     * Copies picked gallery items into the app. The original stays where it was.
     * [onProgress] gets (done, total) after each item.
     */
    suspend fun import(
        uris: List<Uri>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult = importItems(uris, { importOne(it) }, onProgress)

    internal suspend fun <T> importItems(
        items: List<T>,
        importItem: suspend (T) -> Boolean,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): ImportResult = withContext(Dispatchers.IO) {
        var imported = 0
        var undated = 0
        items.forEachIndexed { i, item ->
            ensureActive() // stop between items if the import is cancelled
            try {
                if (importItem(item)) undated++
                imported++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Import failed for $item", failure)
            }
            onProgress(i + 1, items.size)
        }
        ImportResult(imported, undated)
    }

    private suspend fun importOne(uri: Uri): Boolean {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val type = if (mime.startsWith("video")) MediaType.VIDEO else MediaType.PHOTO
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: if (type == MediaType.VIDEO) "mp4" else "jpg"

        return importOne(
            mime, type, extension,
            openSource = {
                openOriginalOrPlain(
                    useOriginal = Build.VERSION.SDK_INT >= 29 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED,
                    openOriginal = {
                        val mediaUri = when {
                            uri.authority == MediaStore.AUTHORITY -> uri
                            DocumentsContract.isDocumentUri(context, uri) -> MediaStore.getMediaUri(context, uri)
                            else -> null
                        }
                        mediaUri?.let { resolver.openInputStream(MediaStore.setRequireOriginal(it)) }
                    },
                    openPlain = { resolver.openInputStream(uri) },
                )
            },
            readDate = { meta ->
                val provider = providerMetadata(uri)
                MetadataParsing.importDate(meta.takenAt, provider.taken, provider.name, provider.lastModified)
            },
        )
    }

    /** Callbacks keep provider I/O and metadata stubs out of JVM import tests. Returns whether the date was guessed. */
    internal suspend fun importOne(
        mime: String,
        type: MediaType,
        extension: String,
        openSource: () -> InputStream?,
        readDate: (MediaMeta) -> MetadataParsing.ImportDate,
        readMetadata: (File, MediaType) -> MediaMeta = metadataReader::read,
    ): Boolean {
        val temp = storage.newTempFile(extension)
        try {
            val input = openSource() ?: error("Cannot open media")
            input.use { src ->
                temp.outputStream().use { dst ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = src.read(buffer)
                        if (count < 0) break
                        dst.write(buffer, 0, count)
                    }
                }
            }

            val meta = readMetadata(temp, type)
            val date = readDate(meta)
            val location = MetadataParsing.validLocation(meta.latitude, meta.longitude)
            val size = temp.length()
            currentCoroutineContext().ensureActive()
            // Complete the file/row pair together even if cancellation arrives during the insert.
            withContext(NonCancellable) {
                var committed: File? = null
                try {
                    val relativePath = storage.commit(temp, type, date.capturedAt, extension)
                    committed = File(storage.root, relativePath)
                    insert(
                        Moment(
                            type = type,
                            source = MomentSource.IMPORT,
                            relativePath = relativePath,
                            mimeType = mime,
                            capturedAt = date.capturedAt,
                            addedAt = System.currentTimeMillis(),
                            latitude = location?.first,
                            longitude = location?.second,
                            width = meta.width,
                            height = meta.height,
                            durationMs = meta.durationMs,
                            sizeBytes = size,
                        ),
                    )
                } catch (failure: Throwable) {
                    committed?.let { if (!it.delete()) Log.w(TAG, "Couldn't delete committed import $it", failure) }
                    throw failure
                }
            }
            return date.usedNow
        } finally {
            temp.delete() // no-op once committed
        }
    }

    /**
     * Falls back to what the content provider knows when the file has no embedded date.
     */
    private data class ProviderMetadata(val taken: List<Long> = emptyList(), val name: String? = null, val lastModified: Long? = null)

    private fun providerMetadata(uri: Uri): ProviderMetadata = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            fun number(column: String): Long? = runCatching {
                val index = cursor.getColumnIndex(column)
                if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
            }.getOrNull()
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            ProviderMetadata(
                taken = DATE_COLUMNS.mapNotNull(::number),
                name = if (nameIndex < 0 || cursor.isNull(nameIndex)) null else cursor.getString(nameIndex),
                lastModified = number("last_modified"),
            )
        }
    }.getOrNull() ?: ProviderMetadata()

    private suspend fun insert(moment: Moment): Moment = moment.copy(id = dao.insert(moment))

    suspend fun delete(moment: Moment) = withContext(Dispatchers.IO) {
        storage.fileFor(moment).delete()
        dao.delete(moment)
    }

    data class ExportResult(val exported: Int, val missing: Int)

    /**
     * Writes every file plus a moments.json index into a zip at [target] (a file the user
     * picked with the system "save as" dialog). Returns exported and missing file counts.
     */
    suspend fun exportZip(target: Uri): ExportResult = exportZip(
        openTarget = { context.contentResolver.openOutputStream(target) },
        deleteTarget = { DocumentsContract.deleteDocument(context.contentResolver, target) },
    )

    /** Callbacks let JVM tests exercise document providers without Android's JSON stubs. */
    internal suspend fun exportZip(
        openTarget: () -> OutputStream?,
        deleteTarget: () -> Boolean,
        encodeIndex: (List<Moment>) -> ByteArray = { moments ->
            val index = JSONArray()
            moments.forEach { index.put(it.toJson()) }
            JSONObject().put("version", 1).put("moments", index).toString(2).toByteArray()
        },
    ): ExportResult = try {
        withContext(Dispatchers.IO) {
            ensureActive()
            val all = dao.getAll()
            val index = mutableListOf<Moment>()
            var missing = 0
            val output = openTarget() ?: error("Cannot open export document")
            // ZipOutputStream.close() can throw while finishing, before closing its output.
            output.use {
                ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                    // Photos and videos are already compressed; storing them is much faster.
                    zip.setLevel(Deflater.NO_COMPRESSION)
                    for (moment in all) {
                        ensureActive()
                        val file = storage.fileFor(moment)
                        if (!file.exists()) {
                            missing++
                            continue
                        }
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
                        index.add(moment)
                    }
                    zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                    zip.putNextEntry(ZipEntry("moments.json"))
                    zip.write(encodeIndex(index))
                    zip.closeEntry()
                }
            }
            ensureActive() // Includes errors/cancellation while finishing and closing the zip.
            ExportResult(index.size, missing)
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
        val DATE_COLUMNS = listOf("datetaken", "date_taken")
    }
}

/** Original access is optional: any non-cancellation failure falls back to the plain URI. */
internal fun <T : Any> openOriginalOrPlain(useOriginal: Boolean, openOriginal: () -> T?, openPlain: () -> T?): T? {
    if (useOriginal) {
        try {
            openOriginal()?.let { return it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Conversion and original access can fail even when the plain URI is readable.
        }
    }
    return openPlain()
}
