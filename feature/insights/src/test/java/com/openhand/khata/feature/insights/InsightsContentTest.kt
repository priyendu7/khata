package com.openhand.khata.feature.insights

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.CategorySpend
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InsightsContentTest {
    @get:Rule val compose = createComposeRule()

    private fun category(id: Long, seedKey: String) = Category(
        id = id,
        name = null,
        seedKey = seedKey,
        color = 0xFFE65100.toInt(),
        icon = seedKey
    )

    private val food = category(1, "food")
    private val rent = category(4, "rent")
    private val shopping = category(7, "shopping")
    private val september = DateSpan(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))

    private fun state(breakdown: CategoryBreakdown, period: ChartPeriod = ChartPeriod.MONTH) =
        DonutState(period, september, from = 0, until = 1, breakdown = breakdown)

    private val breakdown = CategoryBreakdown(
        slices = listOf(CategorySpend(rent, 15_000_00), CategorySpend(food, 4_000_00)),
        otherPaise = 1_000_00,
        refunded = listOf(CategorySpend(shopping, -500_00))
    )

    private var opened = mutableListOf<Long?>()
    private var selected = mutableListOf<ChartPeriod>()

    private fun show(state: DonutState?) = compose.setContent {
        InsightsContent(state, onSelectPeriod = {
            selected += it
        }, onOpenCategory = { opened += it })
    }

    @Test
    fun showsTheTotalAndALegendWithAmountsAndShares() {
        show(state(breakdown))

        // The Home figure: slices, "Other" and the refund together.
        compose.onNodeWithText("₹19,500").assertIsDisplayed()
        compose.onNodeWithText("1 Sep 2026 – 30 Sep 2026").assertIsDisplayed()
        compose.onNodeWithText("Rent").assertIsDisplayed()
        compose.onNodeWithText("₹15,000").assertIsDisplayed()
        compose.onNodeWithText("75%").assertIsDisplayed()
        compose.onNodeWithText("Other").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("More refunded than spent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("-₹500").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun talkBackReadsEachSlice() {
        show(state(breakdown))

        compose.onNodeWithContentDescription("Rent, ₹15,000, 75% of spending", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Other, ₹1,000, 5% of spending", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun tappingTheLegendOpensThatCategory() {
        show(state(breakdown))

        compose.onNodeWithText("Food").performScrollTo().performClick()
        compose.onNodeWithText("Other").performScrollTo().performClick()
        compose.onNodeWithText("Shopping").performScrollTo().performClick()
        assertEquals(listOf(food.id, null, shopping.id), opened)
    }

    @Test
    fun switchesPeriods() {
        show(state(breakdown))

        compose.onNodeWithText("Week").performClick()
        compose.onNodeWithText("Custom").performClick()
        assertEquals(listOf(ChartPeriod.WEEK, ChartPeriod.CUSTOM), selected)
    }

    @Test
    fun emptyPeriodSaysSo() {
        show(state(CategoryBreakdown.of(emptyList())))

        compose.onNodeWithText("Nothing spent in this period").assertIsDisplayed()
        compose.onNodeWithText("Spending by category").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "hi")
    fun showsHindi() {
        show(state(breakdown))

        compose.onNodeWithText("श्रेणी के हिसाब से खर्च").assertIsDisplayed()
        compose.onNodeWithText("महीना").assertIsDisplayed()
        compose.onNodeWithText("अन्य").performScrollTo().assertIsDisplayed()
    }
}
