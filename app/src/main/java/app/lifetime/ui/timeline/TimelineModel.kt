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

    /** A moment with a caption, shown like a post: full width with its words under it. */
    data class Post(val date: LocalDate, val moment: Moment) : TimelineRow {
        override val key = "p${moment.id}"
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
 * Layout per day: a captioned moment is a [TimelineRow.Post]. The uncaptioned moments between
 * posts form grids: one shows large, two sit side by side, four make a 2x2, anything else flows
 * in rows of three. Varying it keeps the feed from feeling like a grid of identical tiles.
 * Everything stays in time order.
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

        var gridIndex = 0
        val run = ArrayList<Moment>()
        fun flushGrid() {
            gridChunks(run).forEach { (chunk, columns) -> rows += TimelineRow.Grid(date, gridIndex++, chunk, columns) }
            run.clear()
        }
        for (moment in items) {
            if (moment.caption == null) {
                run += moment
            } else {
                flushGrid()
                rows += TimelineRow.Post(date, moment)
            }
        }
        flushGrid()
    }
    return TimelineState(rows = rows, dayIndex = dayIndex, totalMoments = moments.size, loading = false)
}

private fun gridChunks(items: List<Moment>): List<Pair<List<Moment>, Int>> = when (items.size) {
    0 -> emptyList()
    1 -> listOf(items.toList() to 1)
    2 -> listOf(items.toList() to 2)
    4 -> items.chunked(2).map { it to 2 }
    else -> items.chunked(3).map { it to 3 }
}
