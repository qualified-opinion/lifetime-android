package app.lifetime.ui.timeline

import app.lifetime.data.MediaType
import app.lifetime.data.Moment
import app.lifetime.data.MomentSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

class TimelineModelTest {

    private lateinit var originalZone: TimeZone

    @Before
    fun setZone() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    }

    @After
    fun restoreZone() = TimeZone.setDefault(originalZone)

    private var nextId = 1L
    private fun moment(iso: String) = Moment(
        id = nextId++,
        type = MediaType.PHOTO,
        source = MomentSource.CAMERA,
        relativePath = "x.jpg",
        mimeType = "image/jpeg",
        capturedAt = Instant.parse(iso).toEpochMilli(),
        addedAt = 0,
    )

    @Test
    fun groupsByYearMonthAndDayNewestFirst() {
        val moments = listOf(
            moment("2026-10-16T10:00:00Z"),
            moment("2026-10-16T09:00:00Z"),
            moment("2026-10-02T09:00:00Z"),
            moment("2025-12-31T09:00:00Z"),
        )
        val rows = buildTimeline(moments).rows

        val kinds = rows.map { it::class.simpleName }
        assertEquals(
            listOf("Year", "Month", "Day", "Grid", "Day", "Grid", "Year", "Month", "Day", "Grid"),
            kinds,
        )
        val days = rows.filterIsInstance<TimelineRow.Day>()
        assertEquals(listOf(2, 1, 1), days.map { it.count })
    }

    @Test
    fun dayUsesLocalTimeNotUtc() {
        // 23:30 UTC on 31 Dec 2025 is already New Year's Day in Berlin.
        val state = buildTimeline(listOf(moment("2025-12-31T23:30:00Z")))
        val day = state.rows.filterIsInstance<TimelineRow.Day>().single()
        assertEquals(LocalDate.of(2026, 1, 1), day.date)
    }

    @Test
    fun gridLayoutPerDayCount() {
        fun columnsFor(count: Int): List<Int> {
            val moments = (0 until count).map { moment("2026-10-16T%02d:00:00Z".format(it)) }
            return buildTimeline(moments).rows.filterIsInstance<TimelineRow.Grid>().map { it.columns }
        }
        assertEquals(listOf(1), columnsFor(1))
        assertEquals(listOf(2), columnsFor(2))
        assertEquals(listOf(3), columnsFor(3))
        assertEquals(listOf(2, 2), columnsFor(4))
        assertEquals(listOf(3, 3), columnsFor(5))
        assertEquals(listOf(3, 3, 3), columnsFor(7))
    }

    @Test
    fun dayIndexPointsAtDayHeaders() {
        val state = buildTimeline(listOf(moment("2026-10-16T10:00:00Z"), moment("2026-09-01T10:00:00Z")))
        state.dayIndex.forEach { (date, index) ->
            assertEquals(date, (state.rows[index] as TimelineRow.Day).date)
        }
    }
}
