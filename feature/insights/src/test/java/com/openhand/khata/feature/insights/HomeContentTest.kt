package com.openhand.khata.feature.insights

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.CategorySpend
import com.openhand.khata.core.model.Totals
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

    private val food = Category(id = 1, name = null, seedKey = "food", color = 0, icon = "food")
    private val summary = HomeSummary(
        month = Totals(spentPaise = 1_23_456_50, incomePaise = 1_00_000_00),
        spentTodayPaise = 250_00,
        topCategory = CategorySpend(food, 60_000_00),
        hasTransactions = true
    )

    @Test
    fun showsTheTotalsFormattedIndianStyle() {
        compose.setContent { HomeContent("Khata", summary) }

        compose.onNodeWithText("₹1,23,456.50").assertIsDisplayed()
        compose.onNodeWithText("₹1,00,000").assertIsDisplayed()
        compose.onNodeWithText("₹250").assertIsDisplayed()
        compose.onNodeWithText("Top category this month").assertIsDisplayed()
        compose.onNodeWithText("Food").assertIsDisplayed()
        compose.onNodeWithText("₹60,000").assertIsDisplayed()
        compose.onNodeWithText("No transactions yet").assertDoesNotExist()
    }

    @Test
    fun backupReminderOpensExportAndHidesWhenNotDue() {
        var opened = false
        compose.setContent {
            HomeContent("Khata", summary, backupDueDays = 30, onBackup = { opened = true })
        }
        compose.onNodeWithText("Time to back up").assertIsDisplayed()
        compose.onNodeWithText("No export in 30 days", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Export now").performClick()
        assertTrue(opened)
    }

    @Test
    fun reviewCardOpensTheInboxAndHidesWhenNothingWaits() {
        var opened = false
        compose.setContent {
            HomeContent("Khata", summary, reviewCount = 3, onReview = { opened = true })
        }
        compose.onNodeWithText("3 transactions to review").assertIsDisplayed()
        compose.onNodeWithText("Review").performClick()
        assertTrue(opened)
    }

    @Test
    fun noReviewCardWhenNothingWaits() {
        compose.setContent { HomeContent("Khata", summary, reviewCount = 0) }
        compose.onNodeWithText("to review", substring = true).assertDoesNotExist()
    }

    @Test
    fun noBackupReminderWhenNotDue() {
        compose.setContent { HomeContent("Khata", summary, backupDueDays = null) }
        compose.onNodeWithText("Time to back up").assertDoesNotExist()
    }

    @Test
    fun emptyAppShowsZeroesAndHowToStart() {
        val empty = HomeSummary(Totals.ZERO, 0, topCategory = null, hasTransactions = false)
        compose.setContent { HomeContent("Khata", empty) }

        compose.onNodeWithText("No transactions yet").assertIsDisplayed()
        compose.onNodeWithText("Top category this month").assertDoesNotExist()
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
        compose.onNodeWithText("आज का खर्च").assertIsDisplayed()
        compose.onNodeWithText("₹1,23,456.50").assertIsDisplayed()
    }
}
