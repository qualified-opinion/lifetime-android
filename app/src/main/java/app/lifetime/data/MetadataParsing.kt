package app.lifetime.data

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Pure parsing helpers for media metadata, kept free of Android classes so they can be
 * unit tested on the JVM.
 */
object MetadataParsing {

    private val EXIF_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    private val VIDEO_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val ISO6709 = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""")

    /** 1 Jan 1971: anything earlier is a placeholder, not a real capture date. */
    const val MIN_PLAUSIBLE_DATE = 31_536_000_000L

    /**
     * EXIF dates look like "2026:10:16 14:32:15" in the camera's local time. With an
     * offset ("+02:00") the instant is exact; without one we assume the phone's zone.
     */
    fun parseExifDate(value: String?, offset: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val local = LocalDateTime.parse(value.trim(), EXIF_FORMAT)
            val instant = if (!offset.isNullOrBlank()) {
                OffsetDateTime.of(local, ZoneOffset.of(offset.trim())).toInstant()
            } else {
                local.atZone(zone).toInstant()
            }
            instant.toEpochMilli()
        }.getOrNull()?.takeIf { it > MIN_PLAUSIBLE_DATE }
    }

    /** MP4 dates look like "20261016T143215.000Z" (UTC). Many files carry a 1904/1970 zero date. */
    fun parseVideoDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val cleaned = value.trim().replace("Z", "").substringBefore('.')
        return runCatching {
            LocalDateTime.parse(cleaned, VIDEO_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()?.takeIf { it > MIN_PLAUSIBLE_DATE }
    }

    /** ISO-6709 location strings look like "+37.4219-122.0840/". */
    fun parseIso6709(value: String?): Pair<Double, Double>? {
        if (value.isNullOrBlank()) return null
        val match = ISO6709.find(value) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }

    /**
     * MediaStore uses seconds for some date columns and millis for others; returns millis,
     * or null for empty/zero values.
     */
    fun normaliseProviderDate(raw: Long): Long? {
        val millis = if (raw < 100_000_000_000L) raw * 1000 else raw // small values are seconds
        return millis.takeIf { it > MIN_PLAUSIBLE_DATE }
    }
}
