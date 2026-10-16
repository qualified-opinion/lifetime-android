package app.lifetime.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display helpers shared by the timeline, viewer and info sheet. */
object Formatting {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
    private val dayWithYearFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val shortDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    fun zoned(epochMillis: Long): ZonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)

    fun localDate(epochMillis: Long): LocalDate = zoned(epochMillis).toLocalDate()

    fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now(zone)): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) dayFormat.format(date) else dayWithYearFormat.format(date)
    }

    fun monthLabel(date: LocalDate): String = monthFormat.format(date)

    fun time(epochMillis: Long): String = timeFormat.format(zoned(epochMillis))

    fun shortDate(epochMillis: Long): String = shortDateFormat.format(zoned(epochMillis))

    fun fullDateTime(epochMillis: Long): String {
        val z = zoned(epochMillis)
        return "${dayWithYearFormat.format(z)} at ${timeFormat.format(z)}"
    }

    fun duration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun fileSize(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1e6)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1e3)
        else -> "$bytes B"
    }

    fun coordinates(lat: Double, lon: Double): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return "%.5f° %s, %.5f° %s".format(Math.abs(lat), ns, Math.abs(lon), ew)
    }
}
