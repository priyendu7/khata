package com.openhand.khata.feature.insights

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryPeriodsTest {
    private val india = ZoneId.of("Asia/Kolkata")

    private fun millis(year: Int, month: Int, day: Int, zone: ZoneId = india) =
        ZonedDateTime.of(year, month, day, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun monthAndDayInThePhonesTimeZone() {
        val periods = SummaryPeriods.of(LocalDate.of(2026, 9, 27), india)

        assertEquals(millis(2026, 9, 1), periods.monthFrom)
        assertEquals(millis(2026, 10, 1), periods.monthUntil)
        assertEquals(millis(2026, 9, 27), periods.todayFrom)
        assertEquals(millis(2026, 9, 28), periods.todayUntil)
    }

    @Test
    fun rollsOverTheYearAndLeapFebruary() {
        val december = SummaryPeriods.of(LocalDate.of(2026, 12, 31), india)
        assertEquals(millis(2027, 1, 1), december.monthUntil)
        assertEquals(millis(2027, 1, 1), december.todayUntil)

        val february = SummaryPeriods.of(LocalDate.of(2028, 2, 29), india)
        assertEquals(millis(2028, 2, 1), february.monthFrom)
        assertEquals(millis(2028, 3, 1), february.monthUntil)
    }

    @Test
    fun daylightSavingDaysAreStillWholeDays() {
        val london = ZoneId.of("Europe/London")
        // Clocks go forward on 29 March 2026, so that day is 23 hours long.
        val periods = SummaryPeriods.of(LocalDate.of(2026, 3, 29), london)
        assertEquals(millis(2026, 3, 29, london), periods.todayFrom)
        assertEquals(millis(2026, 3, 30, london), periods.todayUntil)
        assertEquals(23 * 3_600_000L, periods.todayUntil - periods.todayFrom)
    }
}
