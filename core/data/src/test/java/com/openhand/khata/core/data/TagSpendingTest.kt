package com.openhand.khata.core.data

import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.TagSpend
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransactionFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spending by tag on Insights (PRD feature 5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TagSpendingTest : RepositoryTest() {
    private val transactions by lazy { TransactionRepository(lazyDb) }

    private suspend fun add(
        direction: Direction,
        amount: Long,
        tags: List<String> = emptyList(),
        category: String = "uncategorized",
        at: Long = MONTH_START
    ) = transactions.save(
        Transaction(
            amountPaise = amount,
            direction = direction,
            timestamp = at,
            categoryId = db.categoryDao().getBySeedKey(category)!!.id,
            tags = tags
        )
    )

    private suspend fun spending() = transactions.observeTagSpending(MONTH_START, MONTH_END).first()

    private fun List<TagSpend>.byName() = associate { (it.tag?.name ?: "-") to it.spentPaise }

    @Test
    fun aTransactionWithTwoTagsCountsInBoth() = runTest {
        add(Direction.DEBIT, 1_000_00, listOf("Goa trip", "Work"))
        add(Direction.DEBIT, 500_00, listOf("Goa trip"))

        val spending = spending()
        assertEquals(listOf("Goa trip", "Work"), spending.map { it.tag?.name })
        assertEquals(listOf(1_500_00L, 1_000_00L), spending.map { it.spentPaise })
        assertEquals(listOf(2, 1), spending.map { it.count })
    }

    @Test
    fun untaggedCountsOnlyTransactionsWithNoTagAndComesLast() = runTest {
        add(Direction.DEBIT, 100_00, listOf("Work"))
        add(Direction.DEBIT, 900_00)
        add(Direction.DEBIT, 50_00)

        val spending = spending()
        assertEquals(listOf("Work", null), spending.map { it.tag?.name })
        assertEquals(TagSpend(null, 950_00, 2), spending.last())
    }

    @Test
    fun refundsReduceATagAndTransfersAndIncomeNeverCount() = runTest {
        add(Direction.DEBIT, 1_000_00, listOf("Goa trip"))
        add(Direction.REFUND, 300_00, listOf("Goa trip"))
        add(Direction.TRANSFER, 5_000_00, listOf("Goa trip"))
        add(Direction.CREDIT, 7_000_00, listOf("Goa trip"))
        add(Direction.TRANSFER, 5_000_00)
        add(Direction.REFUND, 200_00, listOf("Returns"))
        add(Direction.DEBIT, 200_00, listOf("Even"))
        add(Direction.REFUND, 200_00, listOf("Even"))
        add(Direction.DEBIT, 100_00)
        // Outside the period.
        add(Direction.DEBIT, 99_00, listOf("Goa trip"), at = MONTH_END)

        // Even nets to zero and is left out; more refunds than spending is negative, before
        // Untagged.
        assertEquals(
            listOf("Goa trip" to 700_00L, "Returns" to -200_00L, "-" to 100_00L),
            spending().byName().toList()
        )
        assertEquals(2, spending().first().count)
    }

    @Test
    fun nothingSpentGivesNoRows() = runTest {
        add(Direction.CREDIT, 1_000_00)
        add(Direction.TRANSFER, 1_000_00, listOf("Work"))

        assertEquals(emptyList<TagSpend>(), spending())
    }

    @Test
    fun aTagsCategoryBreakdownAddsUpToItsTotal() = runTest {
        add(Direction.DEBIT, 1_000_00, listOf("Goa trip", "Work"), category = "travel")
        add(Direction.DEBIT, 400_00, listOf("Goa trip"), category = "food")
        add(Direction.REFUND, 100_00, listOf("Goa trip"), category = "food")
        add(Direction.DEBIT, 250_00, category = "food")
        add(Direction.DEBIT, 70_00, listOf("Work"), category = "food")

        val spending = spending()
        for (spend in spending) {
            val breakdown = CategoryBreakdown.of(
                transactions.observeCategorySpendingForTag(MONTH_START, MONTH_END, spend.tag)
                    .first()
            )
            assertEquals(spend.tag?.name, spend.spentPaise, breakdown.totalPaise)
        }
        val goa = spending.first { it.tag?.name == "Goa trip" }.tag
        val categories =
            transactions.observeCategorySpendingForTag(MONTH_START, MONTH_END, goa).first()
        assertEquals(
            listOf("travel" to 1_000_00L, "food" to 300_00L),
            categories.map { it.category.seedKey to it.spentPaise }
        )
    }

    @Test
    fun theListFiltersByTagOrUntagged() = runTest {
        val tagged = add(Direction.DEBIT, 100_00, listOf("Work"))
        val both = add(Direction.DEBIT, 200_00, listOf("Work", "Goa trip"))
        val untagged = add(Direction.DEBIT, 300_00)
        val work = spending().first { it.tag?.name == "Work" }.tag!!

        suspend fun ids(filter: TransactionFilter) =
            transactions.observe(filter).first().map { it.id }.toSet()

        assertEquals(setOf(tagged, both), ids(TransactionFilter(tagId = work.id)))
        assertEquals(setOf(untagged), ids(TransactionFilter(untagged = true)))
        assertEquals(
            emptySet<Long>(),
            ids(TransactionFilter(untagged = true, from = MONTH_END, until = MONTH_END + DAY))
        )
    }

    private companion object {
        const val DAY = 86_400_000L

        /** 1 Sep 2026 00:00 IST, and 1 Oct 2026 00:00 IST. */
        const val MONTH_START = 1_788_201_000_000L
        const val MONTH_END = MONTH_START + 30 * DAY
    }
}
