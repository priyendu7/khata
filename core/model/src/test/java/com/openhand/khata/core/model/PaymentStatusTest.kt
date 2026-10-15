package com.openhand.khata.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentStatusTest {
    private val due = LocalDate.parse("2026-10-23")

    private fun status(paid: Long, minDue: Long? = 3_450_00, today: LocalDate = due) =
        PaymentStatus.of(35_299_72, minDue, paid, due, today)

    @Test
    fun eachStatus() {
        assertEquals(PaymentStatus.PAID_IN_FULL, status(paid = 35_299_72))
        assertEquals(PaymentStatus.PAID_IN_FULL, status(paid = 40_000_00))
        assertEquals(PaymentStatus.MINIMUM_PAID, status(paid = 3_450_00))
        assertEquals(PaymentStatus.PARTLY_PAID, status(paid = 1_000_00))
        assertEquals(PaymentStatus.NOT_PAID, status(paid = 0))
    }

    @Test
    fun pastTheDueDateItsOverdueUnlessPaidInFull() {
        val late = due.plusDays(1)
        assertEquals(PaymentStatus.OVERDUE, status(paid = 0, today = late))
        assertEquals(PaymentStatus.OVERDUE, status(paid = 3_450_00, today = late))
        assertEquals(PaymentStatus.PAID_IN_FULL, status(paid = 35_299_72, today = late))
    }

    @Test
    fun withoutAMinimumSomePaidIsPartlyPaid() {
        assertEquals(PaymentStatus.PARTLY_PAID, status(paid = 5_000_00, minDue = null))
    }

    @Test
    fun zeroMinimumWithNothingPaidIsNotPaid() {
        assertEquals(PaymentStatus.NOT_PAID, status(paid = 0, minDue = 0))
    }
}
