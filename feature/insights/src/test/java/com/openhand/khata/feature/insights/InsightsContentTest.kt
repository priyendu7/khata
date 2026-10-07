package com.openhand.khata.feature.insights

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountSpend
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.CategoryBreakdown
import com.openhand.khata.core.model.CategoryChange
import com.openhand.khata.core.model.CategorySpend
import com.openhand.khata.core.model.Change
import com.openhand.khata.core.model.HeatLevels
import com.openhand.khata.core.model.MonthBar
import com.openhand.khata.core.model.MonthlyComparison
import com.openhand.khata.core.model.Tag
import com.openhand.khata.core.model.TagSpend
import com.openhand.khata.core.model.TransactionFilter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun state(
        breakdown: CategoryBreakdown,
        period: ChartPeriod = ChartPeriod.MONTH,
        span: DateSpan = september,
        back: Int = 0
    ) = DonutState(
        period,
        span,
        from = 0,
        until = 1,
        breakdown = breakdown,
        back = back,
        choices = if (period == ChartPeriod.MONTH) {
            (0L until 24L).map {
                val month = YearMonth.of(2026, 9).minusMonths(it)
                DateSpan(month.atDay(1), month.atEndOfMonth())
            }
        } else {
            emptyList()
        }
    )

    private val breakdown = CategoryBreakdown(
        slices = listOf(CategorySpend(rent, 15_000_00), CategorySpend(food, 4_000_00)),
        otherPaise = 1_000_00,
        refunded = listOf(CategorySpend(shopping, -500_00))
    )

    private var opened = mutableListOf<Long?>()
    private var selected = mutableListOf<ChartPeriod>()
    private val steps = mutableListOf<Int>()
    private val pasts = mutableListOf<Int>()

    private val days = mutableListOf<LocalDate>()
    private val months = mutableListOf<YearMonth>()
    private val monthCounts = mutableListOf<Int>()

    private val openedTags = mutableListOf<Tag?>()
    private val tagPeriods = mutableListOf<ChartPeriod>()
    private val filters = mutableListOf<TransactionFilter>()
    private var closed = 0
    private val openedAccounts = mutableListOf<Account?>()
    private val accountPeriods = mutableListOf<ChartPeriod>()
    private val accountSteps = mutableListOf<Int>()

    private fun show(
        state: DonutState?,
        heatmap: HeatmapState? = null,
        monthly: MonthlyState? = null,
        tags: TagsState? = null,
        accounts: AccountsState? = null,
        breakdown: BreakdownState? = null
    ) = compose.setContent {
        InsightsContent(
            donut = state,
            heatmap = heatmap,
            monthly = monthly,
            onSelectPeriod = { selected += it },
            onStepPeriod = { steps += it },
            onSelectPast = { pasts += it },
            onOpenCategory = { opened += it },
            onOpenDay = { days += it },
            onSelectMonths = { monthCounts += it },
            onOpenMonth = { months += it },
            tags = tags,
            accounts = accounts,
            breakdown = breakdown,
            tagActions = CardActions(
                onSelectPeriod = { tagPeriods += it },
                onOpen = { openedTags += it }
            ),
            accountActions = CardActions(
                onSelectPeriod = { accountPeriods += it },
                onStepPeriod = { accountSteps += it },
                onOpen = { openedAccounts += it }
            ),
            onCloseBreakdown = { closed++ },
            onOpenTransactions = { filters += it }
        )
    }

    private val goa = Tag(1, "Goa trip")
    private val work = Tag(2, "Work")
    private val returns = Tag(3, "Returns")

    private fun tags(spending: List<TagSpend>) = TagsState(
        ChartPeriod.MONTH,
        september,
        from = 100,
        until = 200,
        spending = spending
    )

    private val tagSpending = listOf(
        TagSpend(goa, 15_000_00, 4),
        TagSpend(work, 2_000_00, 1),
        TagSpend(returns, -300_00, 1),
        TagSpend(null, 900_00, 3)
    )

    /** Where a row starts, even when it's scrolled out of view. */
    private fun top(text: String) =
        compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    private val today = LocalDate.of(2026, 9, 27)

    private fun heatmap(spending: Map<LocalDate, Long>) = HeatmapState(
        first = InsightsViewModel.heatmapStart(today),
        today = today,
        firstDayOfWeek = DayOfWeek.MONDAY,
        days = spending,
        levels = HeatLevels.of(spending.values)
    )

    private fun monthly(): MonthlyState {
        val sep = YearMonth.of(2026, 9)
        val aug = sep.minusMonths(1)
        val bars = listOf(
            MonthBar(
                aug,
                spentPaise = 1_000_00,
                incomePaise = 50_000_00,
                segments = listOf(1_000_00, 0)
            ),
            MonthBar(sep, spentPaise = 1_180_00, incomePaise = 0, segments = listOf(1_180_00, 0))
        )
        return MonthlyState(
            months = 6,
            comparison = MonthlyComparison(
                bars = bars,
                stacked = listOf(food),
                change = Change.Percent(18),
                categoryChanges = listOf(CategoryChange(food, Change.Percent(18)))
            )
        )
    }

    @Test
    fun talkBackReadsEachDayAndTappingOpensIt() {
        show(null, heatmap = heatmap(mapOf(today to 1_250_00L)))

        compose.onNodeWithText("Spending by day").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sun, 27 Sep 2026: ₹1,250 spent")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Sat, 26 Sep 2026: nothing spent").assertExists()
        // A year back from today, today included; not a day more.
        compose.onNodeWithContentDescription("Sun, 28 Sep 2025: nothing spent").assertExists()
        compose.onNodeWithContentDescription("Sat, 27 Sep 2025: nothing spent").assertDoesNotExist()
        assertEquals(listOf(today), days)
    }

    @Test
    fun monthlyShowsTheChangeAndOpensAMonth() {
        show(null, monthly = monthly())

        compose.onNodeWithText("Month by month").assertIsDisplayed()
        compose.onNodeWithText("September vs August").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("All spending").performScrollTo().assertIsDisplayed()
        // All spending and Food.
        compose.onAllNodesWithText("+18%").assertCountEquals(2)
        val chart = compose.onNodeWithContentDescription(
            "September 2026: spent ₹1,180, income ₹0",
            substring = true
        ).fetchSemanticsNode()
        compose.runOnIdle {
            chart.config[SemanticsActions.CustomActions]
                .first { it.label.startsWith("September") }
                .action()
        }
        compose.onNodeWithText("12 months").performClick()
        assertEquals(listOf(YearMonth.of(2026, 9)), months)
        assertEquals(listOf(12), monthCounts)
    }

    @Test
    fun changeLabels() {
        val labels = mutableListOf<String>()
        compose.setContent {
            labels += changeLabel(Change.Percent(1_250))
            labels += changeLabel(Change.Percent(-5))
            labels += changeLabel(Change.Percent(0))
            labels += changeLabel(Change.New)
        }
        compose.waitForIdle()
        assertEquals(listOf("+1,250%", "−5%", "+0%", "New"), labels.take(4))
    }

    @Test
    fun showsTheTotalAndALegendWithAmountsAndShares() {
        show(state(breakdown))

        // The Home figure: slices, "Other" and the refund together.
        compose.onNodeWithText("₹19,500").assertIsDisplayed()
        compose.onNodeWithText("September 2026").assertIsDisplayed()
        compose.onNodeWithText("Rent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("₹15,000").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("75%").performScrollTo().assertIsDisplayed()
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
    fun arrowsStepAndForwardIsOffOnTheCurrentPeriod() {
        show(state(breakdown))

        compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous month").performClick()
        assertEquals(listOf(-1), steps)
    }

    @Test
    fun forwardWorksOnAnEarlierPeriod() {
        val week = DateSpan(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20))
        show(state(breakdown, ChartPeriod.WEEK, week, back = 1))

        compose.onNodeWithText("14–20 Sep").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next week").assertIsEnabled().performClick()
        assertEquals(listOf(1), steps)
    }

    @Test
    fun theMonthListJumpsToTheChosenMonth() {
        show(state(breakdown))

        compose.onNode(hasText("September 2026") and hasClickAction()).performClick()
        compose.onNodeWithText("Choose a month").assertIsDisplayed()
        compose.onNodeWithText("June 2026").performClick()
        assertEquals(listOf(3), pasts)
        compose.onNodeWithText("Choose a month").assertDoesNotExist()
    }

    @Test
    fun aCustomPeriodShowsItsDatesWithoutArrows() {
        show(state(breakdown, ChartPeriod.CUSTOM))

        compose.onNodeWithText("1 Sep 2026 – 30 Sep 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous month").assertDoesNotExist()
    }

    @Test
    fun emptyPeriodSaysSo() {
        show(state(CategoryBreakdown.of(emptyList())))

        compose.onNodeWithText("Nothing spent in this period").assertIsDisplayed()
        compose.onNodeWithText("Other").assertDoesNotExist()
    }

    @Test
    fun tagBarsGoBiggestFirstWithUntaggedLastAndTheNote() {
        show(null, tags = tags(tagSpending))

        compose.onNodeWithText("Spending by tag").assertIsDisplayed()
        compose.onNodeWithText("4 transactions").assertIsDisplayed()
        compose.onAllNodesWithText("1 transaction").assertCountEquals(2)
        compose.onNodeWithText("-₹300").assertExists()
        assertTrue(top("Goa trip") < top("Work"))
        assertTrue(top("Work") < top("Returns"))
        assertTrue(top("Returns") < top("Untagged"))
        compose.onNodeWithText("A transaction with several tags counts in each.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tappingATagOpensItAndTheTagCardHasItsOwnPeriods() {
        show(null, tags = tags(tagSpending))

        compose.onNodeWithText("Year").performClick()
        assertEquals(listOf(ChartPeriod.YEAR), tagPeriods)

        compose.onNodeWithText("Work").performScrollTo().performClick()
        compose.onNodeWithText("Untagged").performScrollTo().performClick()
        assertEquals(listOf(work, null), openedTags)
    }

    @Test
    fun noTagSpendingSaysSo() {
        show(null, tags = tags(emptyList()))

        compose.onNodeWithText("Nothing spent in this period").assertIsDisplayed()
        compose.onNodeWithText("A transaction with several tags counts in each.")
            .assertDoesNotExist()
    }

    @Test
    fun theTagSheetShowsItsCategoriesAndOpensTheirTransactions() {
        val sheet = BreakdownState(
            of = BreakdownOf.OfTag(goa),
            period = ChartPeriod.MONTH,
            span = september,
            from = 100,
            until = 200,
            breakdown = CategoryBreakdown.of(
                listOf(CategorySpend(rent, 12_000_00), CategorySpend(food, 3_000_00))
            )
        )
        show(null, tags = tags(tagSpending), breakdown = sheet)

        compose.onNodeWithText("Goa trip · September 2026").assertIsDisplayed()
        compose.onNodeWithText("₹15,000 spent").assertIsDisplayed()
        compose.onNodeWithText("Food").performScrollTo().performClick()
        compose.onNodeWithText("See transactions").performScrollTo().performClick()
        assertEquals(
            listOf(
                TransactionFilter(categoryId = food.id, tagId = goa.id, from = 100, until = 200),
                TransactionFilter(tagId = goa.id, from = 100, until = 200)
            ),
            filters
        )
    }

    @Test
    fun theUntaggedSheetOpensUntaggedTransactions() {
        val sheet = BreakdownState(
            of = BreakdownOf.OfTag(null),
            period = ChartPeriod.MONTH,
            span = september,
            from = 100,
            until = 200,
            breakdown = CategoryBreakdown.of(listOf(CategorySpend(food, 900_00)))
        )
        show(null, breakdown = sheet)

        compose.onNodeWithText("Untagged · September 2026").assertIsDisplayed()
        compose.onNodeWithText("See transactions").performScrollTo().performClick()
        assertEquals(listOf(TransactionFilter(untagged = true, from = 100, until = 200)), filters)
    }

    private val card = Account(1, "HDFC Card", AccountType.CREDIT_CARD, "HDFC", "5678")
    private val savings = Account(2, "Savings", AccountType.BANK, "SBI", null)
    private val wallet = Account(3, "Paytm", AccountType.WALLET, null, null)

    private fun accounts(spending: List<AccountSpend>) = AccountsState(
        ChartPeriod.MONTH,
        september,
        from = 100,
        until = 200,
        spending = spending
    )

    private val accountSpending = listOf(
        AccountSpend(card, 6_000_00),
        AccountSpend(null, 3_000_00),
        AccountSpend(savings, 1_000_00),
        AccountSpend(wallet, -200_00)
    )

    @Test
    fun accountSlicesAndLegendShowNoAccountAndTheRefundedOneLast() {
        show(null, accounts = accounts(accountSpending))

        compose.onNodeWithText("Spending by account").assertIsDisplayed()
        // The middle is the period's spending: every account, refunds included.
        compose.onNodeWithText("₹9,800").assertIsDisplayed()
        compose.onNodeWithText("HDFC Card ••5678").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("60%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No account").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("30%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Savings · SBI").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("More refunded than spent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("-₹200").performScrollTo().assertIsDisplayed()
        assertTrue(top("Savings · SBI") < top("Paytm"))
        compose.onNodeWithContentDescription(
            "No account, ₹3,000, 30% of spending",
            substring = true
        ).assertExists()
    }

    @Test
    fun tappingAnAccountOpensItAndTheAccountCardHasItsOwnPeriods() {
        show(null, accounts = accounts(accountSpending))

        compose.onNodeWithText("Week").performClick()
        compose.onNodeWithContentDescription("Previous month").performClick()
        assertEquals(listOf(ChartPeriod.WEEK), accountPeriods)
        assertEquals(listOf(-1), accountSteps)
        assertEquals(emptyList<ChartPeriod>(), selected)

        compose.onNodeWithText("HDFC Card ••5678").performScrollTo().performClick()
        compose.onNodeWithText("No account").performScrollTo().performClick()
        compose.onNodeWithText("Paytm").performScrollTo().performClick()
        assertEquals(listOf(card, null, wallet), openedAccounts)
    }

    @Test
    fun noAccountSpendingSaysSo() {
        show(null, accounts = accounts(emptyList()))

        compose.onNodeWithText("Spending by account").assertIsDisplayed()
        compose.onNodeWithText("Nothing spent in this period").assertIsDisplayed()
    }

    @Test
    fun theAccountSheetShowsItsCategoriesAndOpensItsTransactions() {
        val sheet = BreakdownState(
            of = BreakdownOf.OfAccount(card),
            period = ChartPeriod.MONTH,
            span = september,
            from = 100,
            until = 200,
            breakdown = CategoryBreakdown.of(
                listOf(CategorySpend(rent, 5_000_00), CategorySpend(food, 1_000_00))
            )
        )
        show(null, accounts = accounts(accountSpending), breakdown = sheet)

        compose.onNodeWithText("HDFC Card ••5678 · September 2026").assertIsDisplayed()
        compose.onNodeWithText("₹6,000 spent").assertIsDisplayed()
        compose.onNodeWithText("Food").performScrollTo().performClick()
        compose.onNodeWithText("See transactions").performScrollTo().performClick()
        assertEquals(
            listOf(
                TransactionFilter(
                    categoryId = food.id,
                    accountId = card.id,
                    from = 100,
                    until = 200
                ),
                TransactionFilter(accountId = card.id, from = 100, until = 200)
            ),
            filters
        )
    }

    @Test
    fun theNoAccountSheetOpensTransactionsWithoutAnAccount() {
        val sheet = BreakdownState(
            of = BreakdownOf.OfAccount(null),
            period = ChartPeriod.MONTH,
            span = september,
            from = 100,
            until = 200,
            breakdown = CategoryBreakdown.of(listOf(CategorySpend(food, 3_000_00)))
        )
        show(null, breakdown = sheet)

        compose.onNodeWithText("No account · September 2026").assertIsDisplayed()
        compose.onNodeWithText("See transactions").performScrollTo().performClick()
        assertEquals(listOf(TransactionFilter(noAccount = true, from = 100, until = 200)), filters)
    }

    @Test
    fun anAccountKeepsItsColorAndColorsDiffer() {
        assertEquals(accountColor(card.id), accountColor(card.id))
        assertTrue(accountColor(card.id) != accountColor(savings.id))
    }

    @Test
    @Config(qualifiers = "hi")
    fun showsHindi() {
        show(state(breakdown))

        compose.onNodeWithText("श्रेणी के हिसाब से खर्च").assertIsDisplayed()
        compose.onNodeWithText("महीना").assertIsDisplayed()
        compose.onNodeWithText("अन्य").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "hi")
    fun showsTheAccountCardInHindi() {
        show(null, accounts = accounts(accountSpending))

        compose.onNodeWithText("खाते के हिसाब से खर्च").assertIsDisplayed()
        compose.onNodeWithText("कोई खाता नहीं").performScrollTo().assertIsDisplayed()
    }
}
