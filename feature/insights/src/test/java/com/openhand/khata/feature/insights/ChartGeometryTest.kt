package com.openhand.khata.feature.insights

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where heatmap days sit, and the monthly chart's axis. */
class ChartGeometryTest {
    private val today = LocalDate.of(2026, 9, 27) // A Sunday.
    private val grid = HeatmapGrid(InsightsViewModel.heatmapStart(today), today, DayOfWeek.MONDAY)

    @Test
    fun heatmapCoversAYearInWeekColumns() {
        assertEquals(LocalDate.of(2025, 9, 28), grid.days.first())
        assertEquals(today, grid.days.last())
        assertEquals(365, grid.days.size)
        // 28 Sep 2025 is a Sunday: the last row of the first week; today ends the last week.
        assertEquals(0 to 6, grid.position(grid.days.first()))
        assertEquals(grid.weeks - 1 to 6, grid.position(today))
        assertEquals(53, grid.weeks)
    }

    @Test
    fun tapsFindTheirDay() {
        assertEquals(today, grid.dayAt(grid.weeks - 1 + 0.5f, 6.9f))
        assertEquals(LocalDate.of(2026, 9, 21), grid.dayAt(grid.weeks - 1f, 0f))
        // Before the first day, past the rows, and off the grid.
        assertNull(grid.dayAt(0.5f, 0.5f))
        assertNull(grid.dayAt(3f, 7f))
        assertNull(grid.dayAt(-1f, 2f))
        assertNull(grid.dayAt(grid.weeks.toFloat(), 0f))
    }

    @Test
    fun axisTicksAreRoundAndCoverTheMax() {
        assertEquals(listOf(0L, 100L), axisTicks(0))
        assertEquals(listOf(0L, 100L), axisTicks(1))
        assertEquals(listOf(0L, 500_00L, 1_000_00L, 1_500_00L), axisTicks(1_234_50))
        assertEquals(listOf(0L, 20_000_00L, 40_000_00L, 60_000_00L), axisTicks(60_000_00))
        assertEquals((0L..4L).map { it * 1_00_000_00L }, axisTicks(3_50_000_00))
    }
}
