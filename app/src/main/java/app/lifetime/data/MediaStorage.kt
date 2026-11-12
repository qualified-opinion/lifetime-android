package app.lifetime.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * App-private file store, organised by year and month:
 *
 *   files/media/2026/10/IMG_20261016_143215_120.jpg
 *   files/media/2026/10/VID_20261016_150002_004.mp4
 *
 * App-private storage needs no storage permission, is removed with the app, and is never
 * scanned by the system gallery, which keeps the timeline private. "Export" copies it out.
 */
class MediaStorage(
    val root: File,
    private val incoming: File,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    constructor(context: Context) : this(File(context.filesDir, "media"), File(context.filesDir, "incoming"))

    private val commitLock = Any()

    init {
        root.mkdirs()
        incoming.mkdirs()
    }

    /**
     * Resolves [relativePath] under the media root. Rejects anything that escapes the root
     * (e.g. `../` or an absolute path), so a tampered row cannot touch other app-private files.
     */
    fun fileFor(moment: Moment): File = resolve(moment.relativePath)

    /** Like [fileFor], but null for a path that escapes the root or can't be resolved; treat it as missing. */
    fun fileOrNull(moment: Moment): File? = try {
        fileFor(moment)
    } catch (_: IllegalStateException) {
        null
    } catch (_: IOException) {
        null
    }

    fun resolve(relativePath: String): File {
        val rootCanonical = root.canonicalFile
        val resolved = File(root, relativePath).canonicalFile
        val rootPath = rootCanonical.path
        check(
            resolved == rootCanonical ||
                resolved.path.startsWith(rootPath + File.separator),
        ) { "Path escapes media root: $relativePath" }
        return resolved
    }

    /** A scratch file for a capture or import that is still being written. */
    fun newTempFile(extension: String): File {
        incoming.mkdirs()
        return File(incoming, "${UUID.randomUUID()}.$extension")
    }

    /**
     * Moves a finished temp file into its year/month folder and returns its relative path.
     * Reserved paths protect existing database references even when their files are missing.
     * Commits are serialized and the target is created without replacing an existing file.
     */
    fun commit(
        temp: File,
        type: MediaType,
        capturedAt: Long,
        extension: String,
        reservedPaths: Set<String> = emptySet(),
    ): String = synchronized(commitLock) {
        val time = Instant.ofEpochMilli(capturedAt).atZone(zone())
        val folder = String.format(Locale.ROOT, "%04d/%02d", time.year, time.monthValue)
        val prefix = if (type == MediaType.VIDEO) "VID" else "IMG"
        val stamp = NAME_FORMAT.format(time)

        val dir = File(root, folder).apply { mkdirs() }
        var n = 0
        while (true) {
            val name = if (n == 0) "${prefix}_$stamp.$extension" else "${prefix}_${stamp}_${n}.$extension"
            n++
            val relativePath = "$folder/$name"
            if (relativePath in reservedPaths) continue
            val target = File(dir, name)
            if (target.exists()) continue
            try {
                moveIntoPlace(temp, target)
                return relativePath
            } catch (_: FileAlreadyExistsException) {
                // Another writer won the name; try the next suffix.
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    /**
     * Leftovers from captures that never finished (app killed mid-recording, etc).
     * Skips temps newer than [minAgeMs] so an in-flight capture or import is not deleted.
     */
    fun clearIncoming(
        nowMs: Long = System.currentTimeMillis(),
        minAgeMs: Long = DEFAULT_INCOMING_MIN_AGE_MS,
    ) {
        incoming.listFiles()?.forEach { file ->
            val age = nowMs - file.lastModified()
            if (age >= minAgeMs) file.delete()
        }
    }

    private fun moveIntoPlace(temp: File, target: File) {
        // Files.move without REPLACE_EXISTING refuses to clobber an existing target.
        try {
            Files.move(temp.toPath(), target.toPath())
            return
        } catch (exists: FileAlreadyExistsException) {
            throw exists
        } catch (_: IOException) {
            // Cross-device or other rename failure: copy without overwriting, then remove temp.
        }
        try {
            Files.copy(temp.toPath(), target.toPath())
            check(temp.delete()) { "Couldn't remove incoming file after filing" }
        } catch (failure: Throwable) {
            if (failure !is FileAlreadyExistsException) target.delete()
            throw failure
        }
    }

    companion object {
        val DEFAULT_INCOMING_MIN_AGE_MS: Long = TimeUnit.MINUTES.toMillis(10)

        private val NAME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.ROOT)
    }
}
