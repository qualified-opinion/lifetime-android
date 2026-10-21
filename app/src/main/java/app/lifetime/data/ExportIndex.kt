package app.lifetime.data

import org.json.JSONObject
import java.time.Instant

/**
 * Reads the moments.json that [MomentRepository.exportZip] writes. Entries that can't be
 * trusted are refused (counted in [Parsed.refused]), never guessed.
 */
object ExportIndex {
    const val FILE_NAME = "moments.json"
    const val VERSION = 1

    data class Entry(
        /** The zip entry name, e.g. "media/2026/10/IMG_20261016_143215_120.jpg". */
        val file: String,
        val type: MediaType,
        val source: MomentSource,
        val capturedAt: Long,
        val mimeType: String,
        val latitude: Double?,
        val longitude: Double?,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val caption: String?,
    ) {
        val extension: String get() = file.substringAfterLast('/').substringAfterLast('.', "").lowercase()
            .ifEmpty { if (type == MediaType.VIDEO) "mp4" else "jpg" }
    }

    data class Parsed(val entries: List<Entry>, val refused: Int)

    class NotAnExport(message: String) : Exception(message)

    fun parse(json: String): Parsed {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: throw NotAnExport("This isn't a Lifetime export")
        val version = root.optInt("version", -1)
        if (version != VERSION) throw NotAnExport("This export is from a newer version of Lifetime")
        val list = root.optJSONArray("moments") ?: throw NotAnExport("This isn't a Lifetime export")
        val entries = mutableListOf<Entry>()
        var refused = 0
        val seen = HashSet<String>()
        for (i in 0 until list.length()) {
            val entry = list.optJSONObject(i)?.let(::entry)
            // The same file listed twice is restored once.
            if (entry == null || !seen.add(entry.file)) refused++ else entries.add(entry)
        }
        return Parsed(entries, refused)
    }

    private fun entry(json: JSONObject): Entry? {
        val file = json.optString("file")
        if (!file.startsWith("media/") || file.length <= "media/".length || file.endsWith("/")) return null
        val type = when (json.optString("type")) {
            "photo" -> MediaType.PHOTO
            "video" -> MediaType.VIDEO
            else -> return null
        }
        val capturedAt = runCatching { Instant.parse(json.getString("capturedAt")).toEpochMilli() }.getOrNull() ?: return null
        val mimeType = json.optString("mimeType").takeIf { it.isNotBlank() }
            ?: if (type == MediaType.VIDEO) "video/mp4" else "image/jpeg"
        val location = MetadataParsing.validLocation(json.number("latitude"), json.number("longitude"))
        return Entry(
            file = file,
            type = type,
            source = if (json.optString("source") == "camera") MomentSource.CAMERA else MomentSource.IMPORT,
            capturedAt = capturedAt,
            mimeType = mimeType,
            latitude = location?.first,
            longitude = location?.second,
            width = json.optInt("width", 0).coerceAtLeast(0),
            height = json.optInt("height", 0).coerceAtLeast(0),
            durationMs = json.optLong("durationMs", 0).coerceAtLeast(0),
            caption = Captions.normalize(if (json.has("caption")) json.optString("caption") else null),
        )
    }

    private fun JSONObject.number(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null
}
