package app.lifetime.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MediaType { PHOTO, VIDEO }

enum class MomentSource { CAMERA, IMPORT }

/**
 * One photo or video in the timeline. The file itself lives in app-private storage
 * under [relativePath] (see [MediaStorage]); this row is everything we know about it.
 */
@Entity(tableName = "moments", indices = [Index("capturedAt")])
data class Moment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: MediaType,
    val source: MomentSource,
    /** Path relative to the media root, e.g. "2026/10/IMG_20261016_143215_120.jpg". */
    val relativePath: String,
    val mimeType: String,
    /** When the moment happened (epoch millis). Drives the timeline order. */
    val capturedAt: Long,
    /** When it was added to the app (epoch millis). */
    val addedAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
    val fileName: String get() = relativePath.substringAfterLast('/')
}
