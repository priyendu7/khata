package com.openhand.khata.core.data

import app.cash.turbine.test
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Totals
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransactionFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Home summary's database totals (PRD features 1 and 5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionTotalsTest : RepositoryTest() {
    private val transactions by lazy { TransactionRepository(lazyDb) }

    private suspend fun add(
        direction: Direction,
        amount: Long,
        at: Long = MONTH_START,
        category: String = "uncategorized",
        accountId: Long? = null
    ) = transactions.save(
        Transaction(
            amountPaise = amount,
            direction = direction,
            timestamp = at,
            accountId = accountId,
            categoryId = db.categoryDao().getBySeedKey(category)!!.id
        )
    )

    private suspend fun totals(from: Long = MONTH_START, until: Long = MONTH_END) =
        transactions.observeTotals(from, until).first()

    /** The same period's totals, worked out in Kotlin from the transactions list. */
    private suspend fun totalsFromList(from: Long = MONTH_START, until: Long = MONTH_END) =
        Totals.of(
            transactions.observe(TransactionFilter(from = from, until = until)).first()
                .map { it.direction to it.amountPaise }
        )

    @Test
    fun emptyPeriodIsZero() = runTest {
        assertEquals(Totals.ZERO, totals())
        assertNull(transactions.observeTopCategory(MONTH_START, MONTH_END).first())
    }

    @Test
    fun aCardPurchaseThenItsBillPaymentIsSpentOnce() = runTest {
        val accounts = AccountRepository(lazyDb)
        val card = accounts.save(Account(name = "HDFC card", type = AccountType.CREDIT_CARD))
        val bank = accounts.save(Account(name = "SBI", type = AccountType.BANK))
        add(Direction.DEBIT, 4_000_00, category = "shopping", accountId = card)
        add(Direction.TRANSFER, 4_000_00, at = MONTH_START + DAY, accountId = bank)

        assertEquals(Totals(spentPaise = 4_000_00, incomePaise = 0), totals())
        val top = transactions.observeTopCategory(MONTH_START, MONTH_END).first()!!
        assertEquals("shopping", top.category.seedKey)
        assertEquals(4_000_00L, top.spentPaise)
    }

    @Test
    fun matchesTheTotalsWorkedOutFromTheList() = runTest {
        add(Direction.CREDIT, 60_000_00)
        add(Direction.DEBIT, 1_234_50, category = "food")
        add(Direction.DEBIT, 800_00, category = "shopping")
        add(Direction.REFUND, 300_00, category = "shopping")
        add(Direction.TRANSFER, 10_000_00)
        // Just outside the month on both sides: never counted.
        add(Direction.DEBIT, 99_00, at = MONTH_START - 1)
        add(Direction.CREDIT, 99_00, at = MONTH_END)

        val expected = Totals(spentPaise = 1_734_50, incomePaise = 60_000_00)
        assertEquals(expected, totals())
        assertEquals(expected, totalsFromList())
        // A single day inside the month, and the whole of time, agree too.
        val dayEnd = MONTH_START + DAY
        assertEquals(totalsFromList(MONTH_START, dayEnd), totals(MONTH_START, dayEnd))
        assertEquals(totalsFromList(0, Long.MAX_VALUE), totals(0, Long.MAX_VALUE))
    }

    @Test
    fun topCategoryCountsRefundsAndIgnoresTransfersAndIncome() = runTest {
        add(Direction.DEBIT, 1_000_00, category = "shopping")
        add(Direction.REFUND, 600_00, category = "shopping")
        add(Direction.DEBIT, 500_00, category = "food")
        add(Direction.TRANSFER, 50_000_00, category = "bills_utilities")
        add(Direction.CREDIT, 50_000_00, category = "work")

        val top = transactions.observeTopCategory(MONTH_START, MONTH_END).first()!!
        assertEquals("food", top.category.seedKey)
        assertEquals(500_00L, top.spentPaise)
    }

    @Test
    fun noTopCategoryWhenRefundsCancelTheSpending() = runTest {
        add(Direction.DEBIT, 700_00, category = "shopping")
        add(Direction.REFUND, 700_00, category = "shopping")

        assertNull(transactions.observeTopCategory(MONTH_START, MONTH_END).first())
        assertEquals(Totals.ZERO, totals())
    }

    @Test
    fun categorySpendingFollowsTheHomeRules() = runTest {
        add(Direction.DEBIT, 1_000_00, category = "shopping")
        add(Direction.REFUND, 600_00, category = "shopping")
        add(Direction.DEBIT, 500_00, category = "food")
        add(Direction.DEBIT, 200_00, category = "health")
        add(Direction.REFUND, 200_00, category = "health")
        add(Direction.TRANSFER, 50_000_00, category = "bills_utilities")
        add(Direction.CREDIT, 50_000_00, category = "work")
        add(Direction.DEBIT, 99_00, category = "travel", at = MONTH_END)

        val spending = transactions.observeCategorySpending(MONTH_START, MONTH_END).first()
        // Health nets to zero, and transfers, income and next month don't count.
        assertEquals(
            listOf("food" to 500_00L, "shopping" to 400_00L),
            spending.map { it.category.seedKey to it.spentPaise }
        )
        assertEquals(transactions.observeTopCategory(MONTH_START, MONTH_END).first(), spending[0])
    }

    @Test
    fun slicesOtherAndRefundsAddUpToTheHomeTotal() = runTest {
        val seeded = listOf(
            "food",
            "groceries",
            "travel",
            "rent",
            "work",
            "bills_utilities",
            "shopping",
            "health"
        )
        seeded.forEachIndexed { i, key -> add(Direction.DEBIT, (i + 1) * 100_00L, category = key) }
        // More refunded than spent: a negative category the donut can't draw.
        add(Direction.DEBIT, 300_00, category = "entertainment")
        add(Direction.REFUND, 1_000_00, category = "entertainment")
        add(Direction.TRANSFER, 5_000_00, category = "rent")

        val breakdown = CategoryBreakdown.of(
            transactions.observeCategorySpending(MONTH_START, MONTH_END).first()
        )
        assertEquals(5, breakdown.slices.size)
        assertEquals(listOf("entertainment"), breakdown.refunded.map { it.category.seedKey })
        assertEquals(-700_00L, breakdown.refunded.single().spentPaise)
        val total = totals().spentPaise
        assertEquals(total, breakdown.totalPaise)
        assertEquals(
            total,
            breakdown.slices.sumOf { it.spentPaise } + breakdown.otherPaise +
                breakdown.refunded.sumOf { it.spentPaise }
        )
    }

    @Test
    fun updatesAsTransactionsChange() = runTest {
        transactions.observeTotals(MONTH_START, MONTH_END).test {
            assertEquals(Totals.ZERO, awaitItem())
            val id = add(Direction.DEBIT, 250_00)
            assertEquals(Totals(spentPaise = 250_00, incomePaise = 0), awaitItem())
            transactions.save(transactions.get(id)!!.copy(direction = Direction.TRANSFER))
            assertEquals(Totals.ZERO, awaitItem())
            add(Direction.CREDIT, 1_000_00)
            assertEquals(Totals(spentPaise = 0, incomePaise = 1_000_00), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun knowsWhetherThereAreAnyTransactions() = runTest {
        transactions.observeAny().test {
            assertFalse(awaitItem())
            val id = add(Direction.TRANSFER, 1_00)
            assertTrue(awaitItem())
            transactions.delete(id)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val DAY = 86_400_000L

        /** 1 Sep 2026 00:00 IST, and 1 Oct 2026 00:00 IST. */
        const val MONTH_START = 1_788_201_000_000L
        const val MONTH_END = MONTH_START + 30 * DAY
    }
}
