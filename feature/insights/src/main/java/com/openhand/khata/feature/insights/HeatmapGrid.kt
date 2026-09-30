package com.openhand.khata.feature.insights

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Where each day from [first] to [last] sits: columns are weeks starting on [firstDayOfWeek]. */
internal data class HeatmapGrid(
    val first: LocalDate,
    val last: LocalDate,
    val firstDayOfWeek: DayOfWeek
) {
    private val start: LocalDate = first.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    val weeks: Int = (ChronoUnit.DAYS.between(start, last) / DAYS_IN_WEEK + 1).toInt()

    val days: List<LocalDate> =
        (0..ChronoUnit.DAYS.between(first, last)).map { first.plusDays(it) }

    /** The day's column (week) and row (day of the week). */
    fun position(day: LocalDate): Pair<Int, Int> {
        val offset = ChronoUnit.DAYS.between(start, day).toInt()
        return offset / DAYS_IN_WEEK to offset % DAYS_IN_WEEK
    }

    /** The day at a column and row (in cells, fractions allowed), or null outside the days shown. */
    fun dayAt(column: Float, row: Float): LocalDate? {
        if (column < 0 || row < 0 || row >= DAYS_IN_WEEK) return null
        val day = start.plusDays(column.toLong() * DAYS_IN_WEEK + row.toLong())
        return day.takeIf { it in first..last }
    }

    companion object {
        const val DAYS_IN_WEEK = 7
    }
}
