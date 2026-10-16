package com.openhand.khata.feature.insights

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import com.openhand.khata.core.model.AccountSpend
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.CategorySpend
import com.openhand.khata.core.model.Tag
import com.openhand.khata.core.model.TagSpend
import com.openhand.khata.core.model.TransferSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Swiping a card's chart steps its period, like the ‹ › arrows (#146). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeriodSwipeTest {
    @get:Rule val compose = createComposeRule()

    private val september = DateSpan(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
    private val rent = Category(
        id = 4,
        name = null,
        seedKey = "rent",
        color = 0xFFE65100.toInt(),
        icon = "rent"
    )

    private val steps = mapOf(
        PeriodCard.CATEGORIES to mutableListOf<Int>(),
        PeriodCard.TAGS to mutableListOf(),
        PeriodCard.ACCOUNTS to mutableListOf(),
        PeriodCard.TRANSFERS to mutableListOf()
    )

    /** All four cards, [back] periods back, each recording its own steps. */
    private fun show(period: ChartPeriod = ChartPeriod.MONTH, back: Int = 1) = compose.setContent {
        InsightsContent(
            donut = DonutState(
                period,
                september,
                from = 0,
                until = 1,
                breakdown = CategoryBreakdown.of(listOf(CategorySpend(rent, 1_000_00))),
                back = back
            ),
            heatmap = null,
            monthly = null,
            onSelectPeriod = {},
            onStepPeriod = { steps.getValue(PeriodCard.CATEGORIES) += it },
            onSelectPast = {},
            onOpenCategory = {},
            onOpenDay = {},
            onSelectMonths = {},
            onOpenMonth = { _, _ -> },
            tags = TagsState(
                period,
                september,
                from = 0,
                until = 1,
                spending = listOf(TagSpend(Tag(1, "Goa trip"), 500_00, 2)),
                back = back
            ),
            tagActions = CardActions(
                onStepPeriod = { steps.getValue(PeriodCard.TAGS) += it }
            ),
            accounts = AccountsState(
                period,
                september,
                from = 0,
                until = 1,
                spending = listOf(AccountSpend(null, 700_00)),
                back = back
            ),
            accountActions = CardActions(
                onStepPeriod = { steps.getValue(PeriodCard.ACCOUNTS) += it }
            ),
            transfers = TransfersState(
                period,
                september,
                from = 0,
                until = 1,
                summary = TransferSummary.of(emptyList()),
                back = back
            ),
            transferActions = CardActions(
                onStepPeriod = { steps.getValue(PeriodCard.TRANSFERS) += it }
            )
        )
    }

    /** Something inside each card's chart to swipe on. */
    private val charts = mapOf(
        PeriodCard.CATEGORIES to "Rent",
        PeriodCard.TAGS to "Goa trip",
        PeriodCard.ACCOUNTS to "No account",
        PeriodCard.TRANSFERS to "Nothing moved in this period"
    )

    private fun chart(card: PeriodCard) =
        compose.onNodeWithText(charts.getValue(card)).performScrollTo()

    /** A quick drag of [dx] pixels from the middle of [card]'s chart; negative is left. */
    private fun swipe(card: PeriodCard, dx: Float) = chart(card).performTouchInput {
        swipe(center, center + Offset(dx, 0f), durationMillis = QUICK_MILLIS)
    }

    private fun assertSteps(expected: Map<PeriodCard, List<Int>>) = compose.runOnIdle {
        PeriodCard.entries.forEach {
            assertEquals(it.name, expected[it].orEmpty(), steps.getValue(it))
        }
    }

    @Test
    fun leftIsNextAndRightIsPreviousOnEachCard() {
        show()

        PeriodCard.entries.forEach {
            swipe(it, -FAR)
            swipe(it, FAR)
        }
        assertSteps(PeriodCard.entries.associateWith { listOf(1, -1) })
    }

    @Test
    fun swipingOneCardLeavesTheOthers() {
        show()

        swipe(PeriodCard.TAGS, FAR)
        assertSteps(mapOf(PeriodCard.TAGS to listOf(-1)))
    }

    @Test
    fun theCurrentPeriodOnlyBounces() {
        show(back = 0)

        swipe(PeriodCard.CATEGORIES, -FAR)
        assertSteps(emptyMap())
        swipe(PeriodCard.CATEGORIES, FAR)
        assertSteps(mapOf(PeriodCard.CATEGORIES to listOf(-1)))
    }

    @Test
    fun aShortSlowDragSpringsBack() {
        show()

        chart(PeriodCard.ACCOUNTS).performTouchInput {
            swipe(center, center - Offset(SHORT_DRAG, 0f), durationMillis = SLOW_MILLIS)
        }
        assertSteps(emptyMap())
    }

    @Test
    fun aVerticalDragScrollsThePage() {
        show()
        val rent = chart(PeriodCard.CATEGORIES)
        val before = rent.fetchSemanticsNode().positionInRoot.y

        rent.performTouchInput {
            swipe(center, center - Offset(0f, FAR), durationMillis = QUICK_MILLIS)
        }
        assertSteps(emptyMap())
        val after = compose.onNodeWithText("Rent").fetchSemanticsNode().positionInRoot.y
        assertNotEquals(before, after)
    }

    @Test
    fun aCustomPeriodDoesNotSwipe() {
        show(ChartPeriod.CUSTOM, back = 0)

        swipe(PeriodCard.CATEGORIES, -FAR)
        swipe(PeriodCard.CATEGORIES, FAR)
        assertSteps(emptyMap())
    }

    @Test
    fun withAnimationsOffASwipeStillSteps() {
        val resolver = ApplicationProvider.getApplicationContext<Context>()
            .contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        show()

        swipe(PeriodCard.TRANSFERS, FAR)
        assertSteps(mapOf(PeriodCard.TRANSFERS to listOf(-1)))
    }

    private companion object {
        const val FAR = 150f
        const val QUICK_MILLIS = 150L
        const val SHORT_DRAG = 30f
        const val SLOW_MILLIS = 1_000L
    }
}
