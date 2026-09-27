package com.openhand.khata.core.model

import com.openhand.khata.core.model.Direction.CREDIT
import com.openhand.khata.core.model.Direction.DEBIT
import com.openhand.khata.core.model.Direction.REFUND
import com.openhand.khata.core.model.Direction.TRANSFER
import org.junit.Assert.assertEquals
import org.junit.Test

/** PRD feature 1: transfers never count as spending or income, and refunds reduce spending. */
class TotalsTest {
    @Test
    fun nothingIsZero() {
        assertEquals(Totals.ZERO, Totals.of(emptyList()))
    }

    @Test
    fun expensesAreSpendingAndIncomeIsIncome() {
        val totals = Totals.of(listOf(DEBIT to 500_00L, DEBIT to 250_00L, CREDIT to 50_000_00L))
        assertEquals(Totals(spentPaise = 750_00, incomePaise = 50_000_00), totals)
    }

    @Test
    fun refundsReduceSpendingNotIncome() {
        val totals = Totals.of(listOf(DEBIT to 2_000_00L, REFUND to 500_00L))
        assertEquals(Totals(spentPaise = 1_500_00, incomePaise = 0), totals)
    }

    @Test
    fun aFullRefundCancelsTheExpense() {
        assertEquals(Totals.ZERO, Totals.of(listOf(DEBIT to 999_00L, REFUND to 999_00L)))
    }

    @Test
    fun aRefundOnItsOwnIsNegativeSpending() {
        // The expense it pays back was in an earlier period.
        val totals = Totals.of(listOf(REFUND to 300_00L))
        assertEquals(Totals(spentPaise = -300_00, incomePaise = 0), totals)
    }

    @Test
    fun transfersCountAsNothing() {
        assertEquals(Totals.ZERO, Totals.of(listOf(TRANSFER to 10_000_00L, TRANSFER to 1L)))
    }

    @Test
    fun aCardPurchaseThenItsBillPaymentIsSpentOnce() {
        // ₹4,000 on the credit card, then the ₹4,000 bill paid from the bank account.
        val purchase = DEBIT to 4_000_00L
        val billPayment = TRANSFER to 4_000_00L
        assertEquals(
            Totals(spentPaise = 4_000_00, incomePaise = 0),
            Totals.of(listOf(purchase, billPayment))
        )
    }

    @Test
    fun everythingTogether() {
        val totals = Totals.of(
            listOf(
                CREDIT to 60_000_00L,
                DEBIT to 1_200_00L,
                DEBIT to 4_000_00L,
                REFUND to 200_00L,
                TRANSFER to 4_000_00L,
                TRANSFER to 10_000_00L
            )
        )
        assertEquals(Totals(spentPaise = 5_000_00, incomePaise = 60_000_00), totals)
    }
}
