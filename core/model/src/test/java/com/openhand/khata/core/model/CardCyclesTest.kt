package com.openhand.khata.core.model

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardCyclesTest {
    private fun day(text: String) = LocalDate.parse(text)

    private fun cycle(first: String, last: String) = CardCycle(day(first), day(last))

    @Test
    fun cycleRunsFromTheDayAfterThePreviousStatement() {
        assertEquals(
            cycle("2026-09-19", "2026-10-18"),
            CardCycles.statementCycle(day("2026-10-18"), day("2026-09-18"), billingDay = null)
        )
    }

    @Test
    fun previousStatementWinsOverTheBillingDay() {
        // The bank sent it a day after the billing day: the statements still set the cycle.
        assertEquals(
            cycle("2026-09-19", "2026-10-18"),
            CardCycles.statementCycle(day("2026-10-18"), day("2026-09-18"), billingDay = 17)
        )
    }

    @Test
    fun firstStatementsCycleStartsAfterTheBillingDayAMonthBefore() {
        assertEquals(
            cycle("2026-09-18", "2026-10-17"),
            CardCycles.statementCycle(day("2026-10-17"), previous = null, billingDay = 17)
        )
    }

    @Test
    fun firstStatementWithoutBillingDayCoversAMonth() {
        assertEquals(
            cycle("2026-09-18", "2026-10-17"),
            CardCycles.statementCycle(day("2026-10-17"), previous = null, billingDay = null)
        )
    }

    @Test
    fun missingStatementIsFilledInByTheBillingDay() {
        // August's statement came, September's didn't: October's cycle starts after 17 Sep.
        assertEquals(
            cycle("2026-09-18", "2026-10-17"),
            CardCycles.statementCycle(day("2026-10-17"), day("2026-08-17"), billingDay = 17)
        )
    }

    @Test
    fun shortMonthsUseTheirLastDay() {
        assertEquals(day("2026-02-28"), CardCycles.billingDate(YearMonth.of(2026, 2), 31))
        assertEquals(day("2028-02-29"), CardCycles.billingDate(YearMonth.of(2028, 2), 30))
        assertEquals(
            cycle("2026-03-01", "2026-03-31"),
            CardCycles.statementCycle(day("2026-03-31"), previous = null, billingDay = 31)
        )
    }

    @Test
    fun currentCycleBeforeAnyStatementComesFromTheBillingDay() {
        assertEquals(
            cycle("2026-09-18", "2026-10-17"),
            CardCycles.currentCycle(latest = null, billingDay = 17, today = day("2026-10-15"))
        )
        // The billing day itself is the last day of its cycle.
        assertEquals(
            cycle("2026-09-18", "2026-10-17"),
            CardCycles.currentCycle(latest = null, billingDay = 17, today = day("2026-10-17"))
        )
        assertEquals(
            cycle("2026-10-18", "2026-11-17"),
            CardCycles.currentCycle(latest = null, billingDay = 17, today = day("2026-10-18"))
        )
    }

    @Test
    fun currentCycleStartsAfterTheLatestStatement() {
        assertEquals(
            cycle("2026-09-19", "2026-10-17"),
            CardCycles.currentCycle(day("2026-09-18"), billingDay = 17, today = day("2026-10-05"))
        )
        assertEquals(
            cycle("2026-09-19", "2026-10-18"),
            CardCycles.currentCycle(day("2026-09-18"), billingDay = null, today = day("2026-10-05"))
        )
    }

    @Test
    fun currentCycleSkipsAMissingStatement() {
        // September's statement never came; on 20 Oct the cycle is the one after 17 Oct.
        assertEquals(
            cycle("2026-10-18", "2026-11-17"),
            CardCycles.currentCycle(day("2026-09-17"), billingDay = 17, today = day("2026-10-20"))
        )
        assertEquals(
            cycle("2026-10-18", "2026-11-17"),
            CardCycles.currentCycle(day("2026-08-17"), billingDay = null, today = day("2026-10-20"))
        )
    }

    @Test
    fun withNeitherStatementNorBillingDayItsTheCalendarMonth() {
        assertEquals(
            cycle("2026-10-01", "2026-10-31"),
            CardCycles.currentCycle(latest = null, billingDay = null, today = day("2026-10-15"))
        )
    }

    @Test
    fun billingDayMustBeADayOfTheMonth() {
        assertTrue(CardCycles.isValidBillingDay(null))
        assertTrue(CardCycles.isValidBillingDay(1))
        assertTrue(CardCycles.isValidBillingDay(31))
        assertFalse(CardCycles.isValidBillingDay(0))
        assertFalse(CardCycles.isValidBillingDay(32))
    }
}
