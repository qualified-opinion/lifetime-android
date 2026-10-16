package app.lifetime.data

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What we can learn from a media file itself. Every field is best-effort. */
data class MediaMeta(
    val takenAt: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
)

class MediaMetadataReader {

    fun read(file: File, type: MediaType): MediaMeta =
        runCatching { if (type == MediaType.VIDEO) readVideo(file) else readPhoto(file) }
            .getOrDefault(MediaMeta())

    private fun readPhoto(file: File): MediaMeta {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        val exif = runCatching { ExifInterface(file) }.getOrNull()
            ?: return MediaMeta(width = bounds.outWidth, height = bounds.outHeight)

        val rotated = exif.rotationDegrees == 90 || exif.rotationDegrees == 270
        val takenAt = parseExifDate(
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
            exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME),
        )
        val latLong = exif.latLong
        return MediaMeta(
            takenAt = takenAt,
            latitude = latLong?.getOrNull(0),
            longitude = latLong?.getOrNull(1),
            width = if (rotated) bounds.outHeight else bounds.outWidth,
            height = if (rotated) bounds.outWidth else bounds.outHeight,
        )
    }

    private fun readVideo(file: File): MediaMeta {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            fun key(k: Int) = retriever.extractMetadata(k)

            val rotation = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val w = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotated = rotation == 90 || rotation == 270
            val location = parseIso6709(key(MediaMetadataRetriever.METADATA_KEY_LOCATION))

            return MediaMeta(
                takenAt = parseVideoDate(key(MediaMetadataRetriever.METADATA_KEY_DATE)),
                latitude = location?.first,
                longitude = location?.second,
                width = if (rotated) h else w,
                height = if (rotated) w else h,
                durationMs = key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** EXIF dates look like "2026:10:16 14:32:15" in the camera's local time. */
    private fun parseExifDate(value: String?, offset: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val local = LocalDateTime.parse(value.trim(), EXIF_FORMAT)
            val zoned = if (!offset.isNullOrBlank()) {
                OffsetDateTime.of(local, ZoneOffset.of(offset.trim())).toInstant()
            } else {
                local.atZone(ZoneId.systemDefault()).toInstant()
            }
            zoned.toEpochMilli()
        }.getOrNull()?.takeIf { it > MIN_PLAUSIBLE_DATE }
    }

    /** MP4 dates look like "20261016T143215.000Z" (UTC). Many files carry a 1904/1970 zero date. */
    private fun parseVideoDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val cleaned = value.trim().replace("Z", "").substringBefore('.')
        return runCatching {
            LocalDateTime.parse(cleaned, VIDEO_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()?.takeIf { it > MIN_PLAUSIBLE_DATE }
    }

    /** ISO-6709 location strings look like "+37.4219-122.0840/". */
    private fun parseIso6709(value: String?): Pair<Double, Double>? {
        if (value.isNullOrBlank()) return null
        val match = ISO6709.find(value) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        return lat to lon
    }

    private companion object {
        val EXIF_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
        val VIDEO_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        val ISO6709 = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""")
        /** 1 Jan 1971: anything earlier is a placeholder, not a real capture date. */
        const val MIN_PLAUSIBLE_DATE = 31_536_000_000L
    }
}
