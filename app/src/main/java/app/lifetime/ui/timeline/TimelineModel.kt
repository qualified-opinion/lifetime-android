package app.lifetime.ui.timeline

import app.lifetime.data.Moment
import app.lifetime.ui.components.Formatting
import java.time.LocalDate

/** One row of the timeline list. Each row is one LazyColumn item, so indices line up. */
sealed interface TimelineRow {
    val key: String

    data class Year(val year: Int) : TimelineRow {
        override val key = "y$year"
    }

    data class Month(val firstDay: LocalDate) : TimelineRow {
        override val key = "m${firstDay.year}-${firstDay.monthValue}"
    }

    data class Day(val date: LocalDate, val count: Int) : TimelineRow {
        override val key = "d$date"
    }

    /** A row of 1–3 thumbnails. [columns] decides the layout for that row. */
    data class Grid(val date: LocalDate, val index: Int, val moments: List<Moment>, val columns: Int) : TimelineRow {
        override val key = "g$date-$index"
    }
}

data class TimelineState(
    val rows: List<TimelineRow> = emptyList(),
    /** Days in timeline order (newest first) with the row index of their header. */
    val dayIndex: List<Pair<LocalDate, Int>> = emptyList(),
    val totalMoments: Int = 0,
    val loading: Boolean = true,
)

/**
 * Turns a newest-first list of moments into year / month / day sections.
 *
 * Layout per day: one moment shows large, two sit side by side, four make a 2x2,
 * anything else flows in rows of three. Varying it keeps the feed from feeling like a grid
 * of identical tiles.
 */
fun buildTimeline(moments: List<Moment>): TimelineState {
    val rows = ArrayList<TimelineRow>()
    val dayIndex = ArrayList<Pair<LocalDate, Int>>()
    val byDay = moments.groupBy { Formatting.localDate(it.capturedAt) } // keeps newest-first order

    var lastYear: Int? = null
    var lastMonth: Pair<Int, Int>? = null
    for ((date, items) in byDay) {
        if (date.year != lastYear) {
            rows += TimelineRow.Year(date.year)
            lastYear = date.year
            lastMonth = null
        }
        if (date.year to date.monthValue != lastMonth) {
            rows += TimelineRow.Month(date.withDayOfMonth(1))
            lastMonth = date.year to date.monthValue
        }
        dayIndex += date to rows.size
        rows += TimelineRow.Day(date, items.size)

        val chunks = when (items.size) {
            1 -> listOf(items to 1)
            2 -> listOf(items to 2)
            4 -> items.chunked(2).map { it to 2 }
            else -> items.chunked(3).map { it to 3 }
        }
        chunks.forEachIndexed { i, (chunk, columns) ->
            rows += TimelineRow.Grid(date, i, chunk, columns)
        }
    }
    return TimelineState(rows = rows, dayIndex = dayIndex, totalMoments = moments.size, loading = false)
}
