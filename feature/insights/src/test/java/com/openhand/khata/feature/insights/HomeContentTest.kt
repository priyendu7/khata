package com.openhand.khata.feature.insights

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.CategorySpend
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Totals
import com.openhand.khata.core.model.TransactionListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeContentTest {
    @get:Rule val compose = createComposeRule()

    private fun category(id: Long, key: String) =
        Category(id = id, name = null, seedKey = key, color = 0, icon = key)

    private val food = category(1, "food")
    private val travel = category(2, "travel")
    private val rent = category(3, "rent")
    private val health = category(4, "health")
    private val work = category(5, "work")
    private val coffee = TransactionListItem(
        id = 42,
        amountPaise = 180_00,
        direction = Direction.DEBIT,
        timestamp = System.currentTimeMillis(),
        payeeName = "Blue Tokai",
        note = null,
        accountName = null,
        category = work,
        tags = emptyList()
    )
    private val summary = HomeSummary(
        month = Totals(spentPaise = 1_23_456_50, incomePaise = 1_00_000_00),
        change = MonthChange.More(4_200_00, 12),
        categories = CategoryBreakdown.of(
            listOf(
                CategorySpend(food, 60_000_00),
                CategorySpend(rent, 40_000_00),
                CategorySpend(travel, 20_000_00),
                CategorySpend(health, 3_456_50)
            )
        ),
        recent = listOf(coffee),
        hasTransactions = true
    )

    @Test
    fun showsTheMonthAgainstLastMonth() {
        compose.setContent { HomeContent("Khata", summary) }

        compose.onNodeWithText("₹1,23,456.50").assertIsDisplayed()
        compose.onNodeWithText("₹1,00,000").assertIsDisplayed()
        compose.onNodeWithText("₹4,200 more than this time last month (12%)").assertIsDisplayed()
    }

    @Test
    fun lessAndSameAndNoComparison() {
        var change by mutableStateOf<MonthChange?>(MonthChange.Less(500_00, 5))
        compose.setContent { HomeContent("Khata", summary.copy(change = change)) }
        compose.onNodeWithText("₹500 less than this time last month (5%)").assertIsDisplayed()

        change = MonthChange.Same
        compose.onNodeWithText("Same as this time last month").assertIsDisplayed()

        change = null
        compose.onNodeWithText("last month", substring = true).assertDoesNotExist()
    }

    @Test
    fun showsTheTopThreeCategoriesWithShares() {
        compose.setContent { HomeContent("Khata", summary) }

        compose.onNodeWithText("Food").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("₹60,000").assertIsDisplayed()
        // 60,000 of 1,23,456.50.
        compose.onNodeWithText("49%").assertIsDisplayed()
        compose.onNodeWithText("Rent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Travel").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Health").assertDoesNotExist()
    }

    @Test
    fun sectionsAppearInOrder() {
        compose.setContent {
            HomeContent("Khata", summary, backupDueDays = 30, reviewCount = 2)
        }
        val order = listOf(
            "2 transactions to review",
            "This month",
            "Top categories",
            "Recent transactions",
            "Time to back up"
        ).map { compose.onNodeWithText(it).getUnclippedBoundsInRoot().top }
        assertEquals(order.sorted(), order)
    }

    @Test
    fun seeAllLinksSwitchTabsAndARecentTransactionOpens() {
        var tab = ""
        var opened = 0L
        val actions = HomeActions(
            onOpenTransaction = { opened = it },
            onSeeAllTransactions = { tab = "transactions" },
            onSeeAllInsights = { tab = "insights" }
        )
        compose.setContent { HomeContent("Khata", summary, actions = actions) }

        compose.onNodeWithText("See all insights →").performScrollTo().performClick()
        assertEquals("insights", tab)
        compose.onNodeWithText("See all →").performScrollTo().performClick()
        assertEquals("transactions", tab)
        compose.onNodeWithText("Blue Tokai").performScrollTo().performClick()
        assertEquals(42L, opened)
    }

    @Test
    fun recentTransactionsShowTheirDay() {
        compose.setContent { HomeContent("Khata", summary) }

        compose.onNodeWithText("Today ·", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("₹180").assertIsDisplayed()
    }

    @Test
    fun nothingSpentThisMonth() {
        val quiet = summary.copy(
            month = Totals(spentPaise = 0, incomePaise = 50_000_00),
            categories = CategoryBreakdown.of(emptyList())
        )
        compose.setContent { HomeContent("Khata", quiet) }

        compose.onNodeWithText("Nothing spent yet this month").performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("See all insights →").assertExists()
    }

    @Test
    fun noTransactionsShowsHowToStart() {
        var added = false
        var sms = false
        val empty = HomeSummary(
            Totals.ZERO,
            change = null,
            categories = CategoryBreakdown.of(emptyList()),
            recent = emptyList(),
            hasTransactions = false
        )
        val actions = HomeActions(onAddTransaction = { added = true }, onTurnOnSms = { sms = true })
        compose.setContent { HomeContent("Khata", empty, actions = actions) }

        compose.onNodeWithText("No transactions yet").assertIsDisplayed()
        compose.onNodeWithText("This month").assertDoesNotExist()
        compose.onNodeWithText("Recent transactions").assertDoesNotExist()
        compose.onNodeWithText("Add a transaction").performScrollTo().performClick()
        compose.onNodeWithText("Turn on SMS import").performScrollTo().performClick()
        assertTrue(added && sms)
    }

    @Test
    fun backupReminderOpensExportAndHidesWhenNotDue() {
        var opened = false
        var due by mutableStateOf<Int?>(30)
        compose.setContent {
            HomeContent(
                "Khata",
                summary,
                backupDueDays = due,
                actions = HomeActions(onBackup = { opened = true })
            )
        }
        compose.onNodeWithText("No export in 30 days", substring = true).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Export now").performClick()
        assertTrue(opened)

        due = null
        compose.onNodeWithText("Time to back up").assertDoesNotExist()
    }

    @Test
    fun reviewCardOpensTheInboxAndHidesWhenNothingWaits() {
        var opened = false
        var count by mutableStateOf(3)
        compose.setContent {
            HomeContent(
                "Khata",
                summary,
                reviewCount = count,
                actions = HomeActions(onReview = { opened = true })
            )
        }
        compose.onNodeWithText("3 transactions to review").assertIsDisplayed()
        compose.onNodeWithText("Review").performClick()
        assertTrue(opened)

        count = 0
        compose.onNodeWithText("to review", substring = true).assertDoesNotExist()
    }

    @Test
    fun refundsBeyondSpendingShowAsNegative() {
        val refunded = summary.copy(month = Totals(spentPaise = -500_00, incomePaise = 0))
        compose.setContent { HomeContent("Khata", refunded) }

        compose.onNodeWithText("-₹500").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "hi")
    fun showsHindi() {
        compose.setContent { HomeContent("Khata", summary) }

        compose.onNodeWithText("इस महीने").assertIsDisplayed()
        compose.onNodeWithText("पिछले महीने के इसी समय से ₹4,200 ज़्यादा (12%)").assertIsDisplayed()
        compose.onNodeWithText("हाल के लेन-देन").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("₹1,23,456.50").assertExists()
    }
}
