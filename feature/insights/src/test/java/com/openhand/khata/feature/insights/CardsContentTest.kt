package com.openhand.khata.feature.insights

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.openhand.khata.core.data.DebitCardSummary
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.PaymentStatus
import com.openhand.khata.feature.insights.CardSamples.TODAY
import com.openhand.khata.feature.insights.CardSamples.bill
import com.openhand.khata.feature.insights.CardSamples.creditCard
import com.openhand.khata.feature.insights.CardSamples.summary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Cards tab (#155): credit cards with each status, debit cards, and no cards at all. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardsContentTest {
    @get:Rule val compose = createComposeRule()

    private val icici = creditCard(1, "ICICI")
    private val axis = creditCard(2, "Axis", "5678")
    private val hdfc = creditCard(3, "HDFC", "1111")
    private val sbi = creditCard(4, "SBI", "2222")
    private val kotak = creditCard(5, "Kotak", "3333")
    private val savings = Account(10, "HDFC Savings", AccountType.BANK, bank = "HDFC")
    private val debit = Account(11, "HDFC Debit", AccountType.DEBIT_CARD, "HDFC", "5678", null, 10)

    private val state = CardsState(
        credit = listOf(
            summary(
                icici,
                listOf(bill(1, 1, 35_300_00, LocalDate.of(2026, 10, 23), PaymentStatus.NOT_PAID))
            ),
            summary(
                axis,
                listOf(
                    bill(
                        2,
                        2,
                        20_000_00,
                        LocalDate.of(2026, 11, 2),
                        PaymentStatus.PARTLY_PAID,
                        5_000_00
                    )
                )
            ),
            summary(
                hdfc,
                listOf(
                    bill(
                        3,
                        3,
                        9_000_00,
                        LocalDate.of(2026, 10, 5),
                        PaymentStatus.PAID_IN_FULL,
                        9_000_00
                    )
                )
            ),
            summary(
                sbi,
                listOf(bill(4, 4, 4_000_00, LocalDate.of(2026, 10, 10), PaymentStatus.OVERDUE))
            ),
            summary(kotak, currentSpent = 8_120_00)
        ),
        debit = listOf(
            DebitCardSummary(debit, savings, 12_400_00),
            DebitCardSummary(debit.copy(id = 12, name = "Spare", linkedAccountId = null), null, 0)
        ),
        today = TODAY
    )

    private fun show(onCredit: (Long) -> Unit = {}, onDebit: (Long) -> Unit = {}) =
        compose.setContent { CardsContent(state, onCredit, onDebit) }

    @Test
    fun creditCardsShowTheirBillAndStatus() {
        show()

        compose.onNodeWithText("ICICI ··1234").assertIsDisplayed()
        // The statement's total, and its due date beside it.
        compose.onAllNodesWithText("Statement due").onFirst().assertIsDisplayed()
        compose.onNodeWithText("23 Oct").assertIsDisplayed()
        compose.onNodeWithText("Not paid").assertIsDisplayed()
        compose.onNodeWithText("Partly paid (₹5,000 of ₹20,000)").performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Paid").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Overdue").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aCardWithNoStatementShowsThisCyclesSpending() {
        show()

        compose.onNodeWithText("Kotak ··3333").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Spent this cycle").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("₹8,120").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun debitCardsShowTheirAccountAndThisMonth() {
        show()

        compose.onNodeWithText("HDFC Debit ··5678").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Linked to HDFC Savings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("₹12,400 this month").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not linked to an account").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingACardOpensItsPage() {
        var credit: Long? = null
        var debitCard: Long? = null
        show(onCredit = { credit = it }, onDebit = { debitCard = it })

        compose.onNodeWithText("Axis ··5678").performScrollTo().performClick()
        compose.onNodeWithText("HDFC Debit ··5678").performScrollTo().performClick()

        assertEquals(2L, credit)
        assertEquals(11L, debitCard)
    }

    @Test
    fun noCardsSaysHowTheyGetHere() {
        compose.setContent {
            CardsContent(CardsState(emptyList(), emptyList(), TODAY), {}, {})
        }

        compose.onNodeWithText("No cards yet").assertIsDisplayed()
        compose.onNodeWithText(
            "Cards appear here from your bank's SMS, or you can add one in Settings > Accounts."
        ).assertIsDisplayed()
    }
}
