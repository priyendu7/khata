package com.openhand.khata.feature.insights

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.CategorySpend
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A debit card's page (#155): its bank account and its spending by category. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebitCardContentTest {
    @get:Rule val compose = createComposeRule()

    private val savings = Account(10, "HDFC Savings", AccountType.BANK, bank = "HDFC")
    private val salary = Account(11, "Salary", AccountType.BANK, bank = "Kotak")
    private val card = Account(12, "HDFC Debit", AccountType.DEBIT_CARD, "HDFC", "5678", null, 10)
    private val food = Category(7, null, "food", 0, "food")
    private val october = DateSpan(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
    private val state = DebitCardState(
        card = card,
        linkedAccount = savings,
        bankAccounts = listOf(savings, salary),
        period = ChartPeriod.MONTH,
        span = october,
        from = 0,
        until = 1,
        spending = CategoryBreakdown.of(listOf(CategorySpend(food, 12_400_00)))
    )

    @Test
    fun showsItsAccountAndSpending() {
        compose.setContent { DebitCardContent(state) }

        compose.onNodeWithText("HDFC Debit ··5678").assertIsDisplayed()
        compose.onNodeWithText("HDFC Savings").assertIsDisplayed()
        compose.onNodeWithText("October 2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Food").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theAccountIsPickedOrUnlinked() {
        var linked: Long? = -1
        compose.setContent { DebitCardContent(state, DebitCardActions(onLink = { linked = it })) }

        compose.onNodeWithText("HDFC Savings").performClick()
        compose.onNodeWithText("Salary · Kotak").performClick()
        assertEquals(11L, linked)

        compose.onNodeWithText("HDFC Savings").performClick()
        compose.onNodeWithText("Not linked").performClick()
        assertNull(linked)
    }

    @Test
    fun tappingACategoryOpensItsTransactions() {
        var opened: Long? = -1
        compose.setContent {
            DebitCardContent(
                state,
                DebitCardActions(period = CardActions(onOpen = { opened = it }))
            )
        }

        compose.onNodeWithText("Food").performScrollTo().performClick()
        assertEquals(7L, opened)
    }
}
