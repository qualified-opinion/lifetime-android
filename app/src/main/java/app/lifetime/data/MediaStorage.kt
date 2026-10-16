package app.lifetime.data

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * App-private file store, organised by year and month:
 *
 *   files/media/2026/10/IMG_20261016_143215_120.jpg
 *   files/media/2026/10/VID_20261016_150002_004.mp4
 *
 * App-private storage needs no storage permission, is removed with the app, and is never
 * scanned by the system gallery, which keeps the timeline private. "Export" copies it out.
 */
class MediaStorage(context: Context) {

    val root: File = File(context.filesDir, "media").apply { mkdirs() }
    private val incoming: File = File(context.filesDir, "incoming").apply { mkdirs() }

    fun fileFor(moment: Moment): File = File(root, moment.relativePath)

    /** A scratch file for a capture or import that is still being written. */
    fun newTempFile(extension: String): File =
        File(incoming, "${UUID.randomUUID()}.$extension")

    /**
     * Moves a finished temp file into its year/month folder and returns its relative path.
     */
    fun commit(temp: File, type: MediaType, capturedAt: Long, extension: String): String {
        val time = Instant.ofEpochMilli(capturedAt).atZone(ZoneId.systemDefault())
        val folder = "%04d/%02d".format(time.year, time.monthValue)
        val prefix = if (type == MediaType.VIDEO) "VID" else "IMG"
        val stamp = NAME_FORMAT.format(time)

        val dir = File(root, folder).apply { mkdirs() }
        var name = "${prefix}_$stamp.$extension"
        var n = 1
        while (File(dir, name).exists()) {
            name = "${prefix}_${stamp}_${n++}.$extension"
        }
        val target = File(dir, name)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = false)
            temp.delete()
        }
        return "$folder/$name"
    }

    /** Leftovers from captures that never finished (app killed mid-recording, etc). */
    fun clearIncoming() {
        incoming.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        val NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
    }
}
