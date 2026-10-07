package com.openhand.khata.feature.insights

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DateSpanTest {
    private val today = LocalDate.of(2026, 9, 27) // A Sunday.

    @Test
    fun weekStartsOnTheLocalesFirstDay() {
        assertEquals(
            DateSpan(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27)),
            DateSpan.of(ChartPeriod.WEEK, today, DayOfWeek.MONDAY)
        )
        assertEquals(
            DateSpan(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 3)),
            DateSpan.of(ChartPeriod.WEEK, today, DayOfWeek.SUNDAY)
        )
    }

    @Test
    fun monthAndYearAreWhole() {
        assertEquals(
            DateSpan(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
            DateSpan.of(ChartPeriod.MONTH, today, DayOfWeek.MONDAY)
        )
        assertEquals(
            DateSpan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
            DateSpan.of(ChartPeriod.YEAR, today, DayOfWeek.MONDAY)
        )
    }

    @Test
    fun goingBackAWeekKeepsTheFirstDayOfTheWeek() {
        assertEquals(
            DateSpan(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20)),
            DateSpan.of(ChartPeriod.WEEK, today, DayOfWeek.MONDAY, back = 1)
        )
        assertEquals(
            DateSpan(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 19)),
            DateSpan.of(ChartPeriod.WEEK, today, DayOfWeek.SUNDAY, back = 2)
        )
    }

    @Test
    fun goingBackAMonthFromThe31stGivesTheWholeMonthBefore() {
        assertEquals(
            DateSpan(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)),
            DateSpan.of(ChartPeriod.MONTH, LocalDate.of(2026, 3, 31), DayOfWeek.MONDAY, back = 1)
        )
        assertEquals(
            DateSpan(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31)),
            DateSpan.of(ChartPeriod.MONTH, today, DayOfWeek.MONDAY, back = 9)
        )
    }

    @Test
    fun goingBackAYear() {
        assertEquals(
            DateSpan(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)),
            DateSpan.of(ChartPeriod.YEAR, today, DayOfWeek.MONDAY, back = 2)
        )
    }

    @Test
    fun untilIsTheStartOfTheDayAfterTheLast() {
        val india = ZoneId.of("Asia/Kolkata")
        val span = DateSpan(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        assertEquals(
            ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, india).toInstant().toEpochMilli(),
            span.from(india)
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, india).toInstant().toEpochMilli(),
            span.until(india)
        )
    }
}
