package app.lifetime.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/**
 * Pure parsing helpers for media metadata, kept free of Android classes so they can be
 * unit tested on the JVM.
 */
object MetadataParsing {

    private val EXIF_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    private val VIDEO_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val ISO6709 = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""")
    private val COMPACT_DATE = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT)
    private val FILE_TIME = DateTimeFormatter.ofPattern("HHmmss").withResolverStyle(ResolverStyle.STRICT)
    private val NAMED_TIMESTAMP = Regex("""(?:IMG_|PXL_|VID_)?(\d{8})_(\d{6})(?:\d{3}|_\d{3})?(?:\.[A-Za-z0-9]+)?""")
    private val DASHED_TIMESTAMP = Regex("""(\d{4}-\d{2}-\d{2}) (\d{2}\.\d{2}\.\d{2})(?:\.[A-Za-z0-9]+)?""")
    private val NAMED_DATE = Regex("""(?:(?:IMG_|PXL_|VID_)?(\d{8})|IMG-(\d{8})-WA\d{4})(?:\.[A-Za-z0-9]+)?""")

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
        return validLocation(lat, lon)
    }

    /** Shared by ISO-6709 and the coordinates returned by the EXIF reader. */
    fun validLocation(latitude: Double?, longitude: Double?): Pair<Double, Double>? {
        if (latitude == null || longitude == null) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        if (latitude == 0.0 && longitude == 0.0) return null
        return latitude to longitude
    }

    /** Only known, complete filename patterns are dates; a date without a time uses local noon. */
    fun parseDisplayName(value: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val timestamp = NAMED_TIMESTAMP.matchEntire(value)
            val dashed = DASHED_TIMESTAMP.matchEntire(value)
            val dateOnly = NAMED_DATE.matchEntire(value)
            val local = when {
                timestamp != null -> LocalDateTime.of(
                    LocalDate.parse(timestamp.groupValues[1], COMPACT_DATE),
                    LocalTime.parse(timestamp.groupValues[2], FILE_TIME),
                )
                dashed != null -> LocalDateTime.of(
                    LocalDate.parse(dashed.groupValues[1]),
                    LocalTime.parse(dashed.groupValues[2].replace('.', ':')),
                )
                dateOnly != null -> LocalDate.parse(
                    dateOnly.groupValues[1].ifEmpty { dateOnly.groupValues[2] }, COMPACT_DATE,
                ).atTime(12, 0)
                else -> return null
            }
            local.atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()?.takeIf { it > MIN_PLAUSIBLE_DATE }
    }

    data class ImportDate(val capturedAt: Long, val usedNow: Boolean)

    fun importDate(
        embedded: Long?,
        providerTaken: List<Long>,
        displayName: String?,
        lastModified: Long?,
        now: () -> Long = System::currentTimeMillis,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ImportDate {
        val found = embedded
            ?: providerTaken.firstNotNullOfOrNull(::normaliseProviderDate)
            ?: parseDisplayName(displayName, zone)
            ?: lastModified?.let(::normaliseProviderDate)
        return ImportDate(found ?: now(), usedNow = found == null)
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
