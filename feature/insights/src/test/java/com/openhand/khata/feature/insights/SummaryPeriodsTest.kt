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
    fun thisAndLastMonthInThePhonesTimeZone() {
        val periods = SummaryPeriods.of(LocalDate.of(2026, 9, 27), india)

        assertEquals(millis(2026, 9, 1), periods.monthFrom)
        assertEquals(millis(2026, 10, 1), periods.monthUntil)
        // Last month up to the same day: 1 to 27 August.
        assertEquals(millis(2026, 8, 1), periods.lastMonthFrom)
        assertEquals(millis(2026, 8, 28), periods.lastMonthUntil)
    }

    @Test
    fun lastMonthStopsAtItsEndWhenItIsShorter() {
        val march = SummaryPeriods.of(LocalDate.of(2026, 3, 31), india)
        assertEquals(millis(2026, 2, 1), march.lastMonthFrom)
        assertEquals(millis(2026, 3, 1), march.lastMonthUntil)
    }

    @Test
    fun rollsOverTheYearAndLeapFebruary() {
        val december = SummaryPeriods.of(LocalDate.of(2026, 12, 31), india)
        assertEquals(millis(2027, 1, 1), december.monthUntil)

        val january = SummaryPeriods.of(LocalDate.of(2027, 1, 15), india)
        assertEquals(millis(2026, 12, 1), january.lastMonthFrom)
        assertEquals(millis(2026, 12, 16), january.lastMonthUntil)

        val february = SummaryPeriods.of(LocalDate.of(2028, 2, 29), india)
        assertEquals(millis(2028, 2, 1), february.monthFrom)
        assertEquals(millis(2028, 3, 1), february.monthUntil)
    }

    @Test
    fun daylightSavingMonthsAreStillWholeMonths() {
        val london = ZoneId.of("Europe/London")
        // Clocks go forward on 29 March 2026.
        val periods = SummaryPeriods.of(LocalDate.of(2026, 4, 30), london)
        assertEquals(millis(2026, 3, 1, london), periods.lastMonthFrom)
        // 1 to 30 March, one of them 23 hours long.
        assertEquals(millis(2026, 3, 31, london), periods.lastMonthUntil)
        assertEquals((30 * 24 - 1) * 3_600_000L, periods.lastMonthUntil - periods.lastMonthFrom)
    }
}
