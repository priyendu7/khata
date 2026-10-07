package com.openhand.khata.core.data

import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountSpend
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransactionFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spending by account on Insights (PRD feature 5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountSpendingTest : RepositoryTest() {
    private val transactions by lazy { TransactionRepository(lazyDb) }
    private val accounts by lazy { AccountRepository(lazyDb) }

    private suspend fun account(name: String, type: AccountType = AccountType.BANK) =
        accounts.save(Account(name = name, type = type, bank = "HDFC", last4 = "5678"))

    private suspend fun add(
        direction: Direction,
        amount: Long,
        accountId: Long? = null,
        category: String = "uncategorized",
        at: Long = MONTH_START
    ) = transactions.save(
        Transaction(
            amountPaise = amount,
            direction = direction,
            timestamp = at,
            accountId = accountId,
            categoryId = db.categoryDao().getBySeedKey(category)!!.id
        )
    )

    private suspend fun spending() =
        transactions.observeAccountSpending(MONTH_START, MONTH_END).first()

    private fun List<AccountSpend>.byName() = map { (it.account?.name ?: "-") to it.spentPaise }

    @Test
    fun accountsAddUpToThePeriodsSpending() = runTest {
        val card = account("HDFC Card", AccountType.CREDIT_CARD)
        val savings = account("Savings")
        add(Direction.DEBIT, 1_000_00, card)
        add(Direction.DEBIT, 300_00, savings)
        add(Direction.DEBIT, 250_00)
        add(Direction.REFUND, 100_00, card)

        val spending = spending()
        assertEquals(
            listOf("HDFC Card" to 900_00L, "Savings" to 300_00L, "-" to 250_00L),
            spending.byName()
        )
        assertEquals(
            Account(card, "HDFC Card", AccountType.CREDIT_CARD, "HDFC", "5678"),
            spending.first().account
        )
        assertEquals(
            transactions.observeTotals(MONTH_START, MONTH_END).first().spentPaise,
            spending.sumOf { it.spentPaise }
        )
    }

    @Test
    fun noAccountCountsOnlyTransactionsWithoutOne() = runTest {
        val savings = account("Savings")
        add(Direction.DEBIT, 100_00, savings)
        add(Direction.DEBIT, 900_00)
        add(Direction.DEBIT, 50_00)

        assertEquals(listOf("-" to 950_00L, "Savings" to 100_00L), spending().byName())
    }

    @Test
    fun refundsReduceAnAccountAndTransfersAndIncomeNeverCount() = runTest {
        val card = account("HDFC Card", AccountType.CREDIT_CARD)
        val savings = account("Savings")
        val wallet = account("Wallet", AccountType.WALLET)
        val even = account("Even")
        add(Direction.DEBIT, 1_000_00, card)
        add(Direction.REFUND, 300_00, card)
        // Paying the card bill from savings is a transfer: the purchases already counted.
        add(Direction.TRANSFER, 5_000_00, savings)
        add(Direction.CREDIT, 7_000_00, savings)
        add(Direction.REFUND, 200_00, wallet)
        add(Direction.DEBIT, 200_00, even)
        add(Direction.REFUND, 200_00, even)
        add(Direction.DEBIT, 100_00)
        // Outside the period.
        add(Direction.DEBIT, 99_00, card, at = MONTH_END)

        // Even nets to zero and is left out; more refunds than spending is negative, and last.
        assertEquals(
            listOf("HDFC Card" to 700_00L, "-" to 100_00L, "Wallet" to -200_00L),
            spending().byName()
        )
    }

    @Test
    fun nothingSpentGivesNoRows() = runTest {
        add(Direction.CREDIT, 1_000_00)
        add(Direction.TRANSFER, 1_000_00, account("Savings"))

        assertEquals(emptyList<AccountSpend>(), spending())
    }

    @Test
    fun anAccountsCategoryBreakdownAddsUpToItsTotal() = runTest {
        val card = account("HDFC Card", AccountType.CREDIT_CARD)
        add(Direction.DEBIT, 1_000_00, card, category = "travel")
        add(Direction.DEBIT, 400_00, card, category = "food")
        add(Direction.REFUND, 100_00, card, category = "food")
        add(Direction.DEBIT, 250_00, category = "food")
        add(Direction.DEBIT, 70_00, account("Savings"), category = "food")

        val spending = spending()
        for (spend in spending) {
            val breakdown = CategoryBreakdown.of(
                transactions.observeCategorySpendingForAccount(
                    MONTH_START,
                    MONTH_END,
                    spend.account
                ).first()
            )
            assertEquals(spend.account?.name, spend.spentPaise, breakdown.totalPaise)
        }
        val categories = transactions.observeCategorySpendingForAccount(
            MONTH_START,
            MONTH_END,
            spending.first { it.account?.id == card }.account
        ).first()
        assertEquals(
            listOf("travel" to 1_000_00L, "food" to 300_00L),
            categories.map { it.category.seedKey to it.spentPaise }
        )
    }

    @Test
    fun theListFiltersByAccountOrNoAccount() = runTest {
        val card = account("HDFC Card", AccountType.CREDIT_CARD)
        val onCard = add(Direction.DEBIT, 100_00, card)
        val none = add(Direction.DEBIT, 300_00)
        add(Direction.DEBIT, 200_00, account("Savings"))

        suspend fun ids(filter: TransactionFilter) =
            transactions.observe(filter).first().map { it.id }.toSet()

        assertEquals(setOf(onCard), ids(TransactionFilter(accountId = card)))
        assertEquals(setOf(none), ids(TransactionFilter(noAccount = true)))
        assertEquals(
            emptySet<Long>(),
            ids(TransactionFilter(noAccount = true, from = MONTH_END, until = MONTH_END + DAY))
        )
    }

    private companion object {
        const val DAY = 86_400_000L

        /** 1 Sep 2026 00:00 IST, and 1 Oct 2026 00:00 IST. */
        const val MONTH_START = 1_788_201_000_000L
        const val MONTH_END = MONTH_START + 30 * DAY
    }
}
