package com.openhand.khata.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun parsesTypedAmounts() {
        assertEquals(25_000L, Money.parsePaise("250"))
        assertEquals(25_050L, Money.parsePaise("250.5"))
        assertEquals(25_055L, Money.parsePaise(" 250.55 "))
        assertEquals(125_000L, Money.parsePaise("1,250"))
        assertEquals(50L, Money.parsePaise(".5"))
        assertEquals(25_000L, Money.parsePaise("250."))
        assertEquals(0L, Money.parsePaise("0"))
        assertEquals(99_999_999_999_99L, Money.parsePaise("99999999999.99"))
    }

    @Test
    fun rejectsAnythingElse() {
        listOf("", " ", ".", "-5", "1.234", "1.2.3", "abc", "₹5", "1e3", "٣", "123456789012")
            .forEach { assertNull(it, Money.parsePaise(it)) }
    }

    @Test
    fun inputRoundTrips() {
        listOf(0L, 1L, 10L, 100L, 12_345L, 12_340L, 99_999_999_999_99L).forEach {
            assertEquals(it, Money.parsePaise(Money.toInput(it)))
        }
        assertEquals("250", Money.toInput(25_000))
        assertEquals("123.40", Money.toInput(12_340))
        assertEquals("0.05", Money.toInput(5))
    }

    @Test
    fun formatsIndianStyle() {
        assertEquals("₹0", Money.format(0))
        assertEquals("₹0.05", Money.format(5))
        assertEquals("₹999", Money.format(99_900))
        assertEquals("₹1,000", Money.format(100_000))
        assertEquals("₹1,234.50", Money.format(123_450))
        assertEquals("₹1,00,000", Money.format(10_000_000))
        assertEquals("₹12,34,567", Money.format(123_456_700))
        assertEquals("₹12,34,56,78,901.12", Money.format(1_234_567_890_112L))
    }

    @Test
    fun totalsLeaveTransfersOutAndRefundsReduceSpending() {
        val totals = Totals.of(
            listOf(
                Direction.DEBIT to 50_000L,
                Direction.DEBIT to 25_000L,
                Direction.REFUND to 10_000L,
                Direction.CREDIT to 1_00_000L,
                Direction.TRANSFER to 5_00_000L
            )
        )
        assertEquals(Totals(spentPaise = 65_000, incomePaise = 1_00_000), totals)
        assertEquals(Totals.ZERO, Totals.of(emptyList()))
    }
}
