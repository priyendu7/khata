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
