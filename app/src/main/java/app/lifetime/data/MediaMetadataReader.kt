package app.lifetime.data

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import java.io.File

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
        val takenAt = MetadataParsing.parseExifDate(
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
            val location = MetadataParsing.parseIso6709(key(MediaMetadataRetriever.METADATA_KEY_LOCATION))

            return MediaMeta(
                takenAt = MetadataParsing.parseVideoDate(key(MediaMetadataRetriever.METADATA_KEY_DATE)),
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

}
