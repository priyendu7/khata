package com.openhand.khata.feature.insights

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.TransferEntry
import com.openhand.khata.core.model.TransferKind
import com.openhand.khata.core.model.TransferSide
import com.openhand.khata.core.model.TransferSummary
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Insights Transfers card (#113): totals, card bills and moves, never counted as spending. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransferCardTest {
    @get:Rule val compose = createComposeRule()

    private val savings = Account(1, "Kotak Savings", AccountType.BANK, "Kotak")
    private val card = Account(2, "HDFC Card", AccountType.CREDIT_CARD, "HDFC", "5678")
    private val october = DateSpan(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

    /** Noon UTC, so it's 3 Oct in any time zone the tests run in. */
    private val oct3 = Instant.parse("2026-10-03T12:00:00Z").toEpochMilli()

    private val opened = mutableListOf<Long>()
    private val periods = mutableListOf<ChartPeriod>()

    private fun show(entries: List<TransferEntry>) = compose.setContent {
        InsightsContent(
            donut = null,
            heatmap = null,
            monthly = null,
            onSelectPeriod = {},
            onStepPeriod = {},
            onSelectPast = {},
            onOpenCategory = {},
            onOpenDay = {},
            onSelectMonths = {},
            onOpenMonth = {},
            transfers = TransfersState(
                ChartPeriod.MONTH,
                october,
                from = 0,
                until = 1,
                summary = TransferSummary.of(entries)
            ),
            transferActions = CardActions(
                onSelectPeriod = { periods += it },
                onOpen = { opened += it }
            )
        )
    }

    private fun entry(
        id: Long,
        side: TransferSide?,
        account: Account,
        payee: String? = null,
        pair: Pair<Long, Account>? = null,
        amount: Long = 18_400_00
    ) = TransferEntry(
        id = id,
        amountPaise = amount,
        timestamp = oct3,
        side = side,
        kind = TransferKind.CARD_PAYMENT,
        account = account,
        payeeName = payee,
        pairId = pair?.first,
        pairAccount = pair?.second
    )

    @Test
    fun aPairedCardPaymentShowsFromToOnceUnderItsCard() {
        show(
            listOf(
                entry(2, TransferSide.IN, card, pair = 1L to savings),
                entry(1, TransferSide.OUT, savings, payee = "CRED", pair = 2L to card)
            )
        )

        compose.onNodeWithText("Transfers").assertIsDisplayed()
        compose.onNodeWithText("Total moved").assertIsDisplayed()
        compose.onNodeWithText("Not counted as spending or income").assertIsDisplayed()
        compose.onNodeWithText("Card bills paid").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("HDFC Card ••5678").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 payment").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Kotak Savings → HDFC Card ••5678")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("3 Oct").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anUnpairedCredPaymentGoesToThePayeeUnderCardNotKnown() {
        show(listOf(entry(1, TransferSide.OUT, savings, payee = "CRED")))

        compose.onNodeWithText("Kotak Savings → CRED").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Card bill · card not known · 1 payment")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun aMoveWithAnUnknownSideShowsBothEndsWithoutADirection() {
        show(listOf(entry(1, null, savings, payee = "Mum").copy(kind = TransferKind.MANUAL)))

        compose.onNodeWithText("Kotak Savings ↔ Mum").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Card bills paid").assertDoesNotExist()
    }

    @Test
    fun tappingAMoveOpensItsTransaction() {
        show(listOf(entry(7, TransferSide.OUT, savings, payee = "CRED")))

        compose.onNodeWithText("Kotak Savings → CRED").performScrollTo().performClick()

        assertEquals(listOf(7L), opened)
    }

    @Test
    fun theCardHasItsOwnPeriods() {
        show(listOf(entry(1, TransferSide.OUT, savings, payee = "CRED")))

        compose.onNodeWithText("Year").performClick()
        compose.onNodeWithContentDescription("Previous month").assertIsDisplayed()

        assertEquals(listOf(ChartPeriod.YEAR), periods)
    }

    @Test
    fun manyMovesShowTheNewestTenFirst() {
        show((1L..12L).map { entry(it, TransferSide.OUT, savings, payee = "Payee $it") })

        compose.onNodeWithText("Kotak Savings → Payee 11").assertDoesNotExist()
        compose.onNodeWithText("Show all 12").performScrollTo().performClick()
        compose.onNodeWithText("Kotak Savings → Payee 12").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun nothingMovedSaysSo() {
        show(emptyList())

        compose.onNodeWithText("Nothing moved in this period").assertIsDisplayed()
    }
}
