package com.openhand.khata.feature.insights

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.openhand.khata.core.model.CardReminderSettings
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.CategorySpend
import com.openhand.khata.core.model.PaymentStatus
import com.openhand.khata.feature.insights.CardSamples.TODAY
import com.openhand.khata.feature.insights.CardSamples.bill
import com.openhand.khata.feature.insights.CardSamples.creditCard
import com.openhand.khata.feature.insights.CardSamples.summary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A credit card's page (#155). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreditCardContentTest {
    @get:Rule val compose = createComposeRule()

    private val food = Category(7, null, "food", 0, "food")
    private val card = creditCard(1, "Axis", "5678")
    private val current = bill(
        2,
        1,
        8_695_00,
        LocalDate.of(2026, 11, 2),
        PaymentStatus.PARTLY_PAID,
        paid = 2_000_00,
        minDue = 500_00
    )
    private val past = bill(1, 1, 12_000_00, LocalDate.of(2026, 10, 2), PaymentStatus.PAID_IN_FULL)
    private val state = CreditCardState(
        summary = summary(card, listOf(current, past), latestSpent = 8_120_00),
        shownStatement = null,
        spending = CategoryBreakdown.of(listOf(CategorySpend(food, 3_250_00))),
        today = TODAY
    )

    @Test
    fun showsTheCurrentStatementAndItsPayments() {
        compose.setContent { CreditCardContent(state) }

        compose.onNodeWithText("Axis ··5678").assertIsDisplayed()
        compose.onAllNodesWithText("₹8,695").onFirst().assertIsDisplayed()
        compose.onNodeWithText("₹500").assertIsDisplayed()
        compose.onNodeWithText("Due 2 Nov 2026", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Partly paid (₹2,000 of ₹8,695)").assertIsDisplayed()
        compose.onNodeWithText("₹2,000 on 13 Oct 2026").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun comparesTheBanksFigureWithKhatas() {
        compose.setContent { CreditCardContent(state) }

        compose.onNodeWithText("Bank says due").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("₹575 apart").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Khata recorded this cycle").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("₹8,120").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Interest, fees, EMIs or a missed SMS can make these differ.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun cycleSpendingByCategoryOpensItsTransactions() {
        var opened: Long? = -1
        compose.setContent {
            CreditCardContent(state, CreditCardActions(onOpenCategory = { opened = it }))
        }

        compose.onNodeWithText("This cycle · 2 Oct – 1 Nov 2026").performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Food").performScrollTo().performClick()

        assertEquals(7L, opened)
    }

    @Test
    fun aPastStatementShowsItsCycle() {
        var shown: Long? = -1
        compose.setContent {
            CreditCardContent(state, CreditCardActions(onShowStatement = { shown = it }))
        }

        compose.onNodeWithText("Past statements").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("₹12,000").performScrollTo().performClick()
        assertEquals(1L, shown)
    }

    @Test
    fun aShownStatementCanGoBackToThisCycle() {
        var shown: Long? = -1
        compose.setContent {
            CreditCardContent(
                state.copy(shownStatement = past),
                CreditCardActions(onShowStatement = { shown = it })
            )
        }

        compose.onNodeWithText("Statement of 10 Sep 2026", substring = true).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Show this cycle").performScrollTo().performClick()
        assertNull(shown)
    }

    @Test
    fun theBillingDayIsPicked() {
        var day: Int? = null
        compose.setContent {
            CreditCardContent(state, CreditCardActions(onSetBillingDay = { day = it }))
        }

        compose.onNodeWithText("Not set").performScrollTo().performClick()
        compose.onNodeWithText("3 of each month").performClick()

        assertEquals(3, day)
    }

    @Test
    fun theCardsRemindersAreSwitched() {
        var on: Boolean? = null
        compose.setContent {
            CreditCardContent(state, CreditCardActions(onSetReminders = { on = it }))
        }

        compose.onNodeWithText("Due date reminders").performScrollTo().performClick()

        assertEquals(false, on)
    }

    @Test
    fun withRemindersOffForAllTheCardSaysWhereToTurnThemOn() {
        var on: Boolean? = null
        compose.setContent {
            CreditCardContent(
                state.copy(reminders = CardReminderSettings(enabled = false)),
                CreditCardActions(onSetReminders = { on = it })
            )
        }

        compose.onNodeWithText("off for every card", substring = true).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Due date reminders").performClick()
        assertNull(on)
    }

    @Test
    fun aCardWithNoStatementSaysSo() {
        compose.setContent {
            CreditCardContent(state.copy(summary = summary(card, currentSpent = 3_250_00)))
        }

        compose.onNodeWithText("No statement yet").assertIsDisplayed()
        compose.onNodeWithText("Food").performScrollTo().assertIsDisplayed()
    }
}
