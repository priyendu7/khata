package com.openhand.khata.feature.insights

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.openhand.khata.core.data.CategoryRepository
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.database.DefaultCategorySeeder
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransactionFilter
import dagger.Lazy
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Switching the donut's and tag card's periods, on a real in-memory database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InsightsViewModelTest {
    private lateinit var db: KhataDatabase
    private lateinit var transactions: TransactionRepository
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 9, 27) // A Sunday.

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            KhataDatabase::class.java
        ).addCallback(DefaultCategorySeeder.callback).allowMainThreadQueries().build()
        transactions = TransactionRepository(Lazy { db })
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun spend(
        on: LocalDate,
        paise: Long,
        category: String,
        tags: List<String> = emptyList()
    ) = transactions.save(
        Transaction(
            amountPaise = paise,
            direction = Direction.DEBIT,
            timestamp = on.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            categoryId = db.categoryDao().getBySeedKey(category)!!.id,
            tags = tags
        )
    )

    private fun viewModel() = InsightsViewModel(
        transactions,
        CategoryRepository(Lazy { db }),
        flowOf(today),
        zone,
        firstDayOfWeek = DayOfWeek.MONDAY
    )

    @Test
    fun switchingPeriodsChangesTheDatesAndTheSpending() = runTest {
        spend(today, 100_00, "food") // This week.
        spend(LocalDate.of(2026, 9, 2), 200_00, "rent") // Earlier this month.
        spend(LocalDate.of(2026, 3, 15), 400_00, "travel") // Earlier this year.
        spend(LocalDate.of(2025, 12, 31), 800_00, "food") // Last year.

        val viewModel = viewModel()
        viewModel.donut.test {
            var state = awaitNotNull()
            assertEquals(ChartPeriod.MONTH, state.period)
            assertEquals(DateSpan(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), state.span)
            assertEquals(300_00L, state.breakdown.totalPaise)

            viewModel.selectPeriod(ChartPeriod.WEEK)
            state = awaitPeriod(ChartPeriod.WEEK)
            assertEquals(DateSpan(LocalDate.of(2026, 9, 21), today), state.span)
            assertEquals(listOf("food"), state.breakdown.slices.map { it.category.seedKey })
            assertEquals(100_00L, state.breakdown.totalPaise)

            viewModel.selectPeriod(ChartPeriod.YEAR)
            state = awaitPeriod(ChartPeriod.YEAR)
            assertEquals(700_00L, state.breakdown.totalPaise)
            assertEquals(
                listOf("travel", "rent", "food"),
                state.breakdown.slices.map { it.category.seedKey }
            )

            viewModel.selectRange(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 3, 31))
            state = awaitPeriod(ChartPeriod.CUSTOM)
            assertEquals(1_200_00L, state.breakdown.totalPaise)
            assertEquals(state.span.from(zone), state.from)
            assertEquals(state.span.until(zone), state.until)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stepsBackAndForwardButNotPastTheCurrentPeriod() = runTest {
        spend(LocalDate.of(2026, 8, 10), 300_00, "rent")

        val viewModel = viewModel()
        viewModel.donut.test {
            val current = awaitNotNull()
            assertEquals(0, current.back)
            assertEquals(false, current.canStepForward)

            viewModel.stepPeriod(-1)
            var state = awaitBack(1)
            assertEquals(DateSpan(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)), state.span)
            assertEquals(300_00L, state.breakdown.totalPaise)
            assertEquals(true, state.canStepForward)
            // A category opens with the shown month's range.
            assertEquals(state.span.from(zone), state.from)
            assertEquals(state.span.until(zone), state.until)

            viewModel.stepPeriod(1)
            state = awaitBack(0)
            assertEquals(current.span, state.span)

            viewModel.stepPeriod(1)
            viewModel.stepPeriod(1)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun switchingChipsGoesBackToTheCurrentPeriod() = runTest {
        val viewModel = viewModel()
        viewModel.donut.test {
            awaitNotNull()
            viewModel.selectPast(5)
            assertEquals(LocalDate.of(2026, 4, 1), awaitBack(5).span.first)

            viewModel.selectPeriod(ChartPeriod.YEAR)
            val state = awaitPeriod(ChartPeriod.YEAR)
            assertEquals(0, state.back)
            assertEquals(LocalDate.of(2026, 1, 1), state.span.first)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun listsTwoYearsOfMonthsAndEveryYearWithTransactions() = runTest {
        spend(LocalDate.of(2023, 6, 1), 100_00, "food")

        val viewModel = viewModel()
        viewModel.donut.test {
            val months = awaitNotNull().choices
            assertEquals(24, months.size)
            assertEquals(LocalDate.of(2026, 9, 1), months.first().first)
            assertEquals(LocalDate.of(2024, 10, 1), months.last().first)

            viewModel.selectPeriod(ChartPeriod.YEAR)
            var state = awaitPeriod(ChartPeriod.YEAR)
            while (state.choices.size != 4) state = awaitNotNull()
            assertEquals(listOf(2026, 2025, 2024, 2023), state.choices.map { it.first.year })

            viewModel.selectPeriod(ChartPeriod.WEEK)
            assertEquals(emptyList<DateSpan>(), awaitPeriod(ChartPeriod.WEEK).choices)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updatesWhenATransactionIsAdded() = runTest {
        val viewModel = viewModel()
        viewModel.donut.test {
            assertEquals(true, awaitNotNull().breakdown.isEmpty)
            spend(today, 250_00, "health")
            var state = awaitNotNull()
            while (state.breakdown.isEmpty) state = awaitNotNull()
            assertEquals(250_00L, state.breakdown.totalPaise)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun heatmapCoversTheLastTwelveMonths() = runTest {
        spend(today, 100_00, "food")
        spend(today.minusYears(1).plusDays(1), 200_00, "rent")
        spend(today.minusYears(1), 400_00, "travel") // A day too early.

        viewModel().heatmap.test {
            var state = awaitItem()
            while (state == null) state = awaitItem()
            assertEquals(LocalDate.of(2025, 9, 28), state.first)
            assertEquals(
                mapOf(today to 100_00L, LocalDate.of(2025, 9, 28) to 200_00L),
                state.days
            )
            assertEquals(4, state.levels.level(200_00))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun monthlySwitchesBetweenSixAndTwelveMonths() = runTest {
        spend(today, 100_00, "food")
        spend(LocalDate.of(2026, 1, 10), 300_00, "rent")

        val viewModel = viewModel()
        viewModel.monthly.test {
            var state = awaitItem()
            while (state == null) state = awaitItem()
            assertEquals(6, state.months)
            assertEquals(
                (5L downTo 0L).map { YearMonth.of(2026, 9).minusMonths(it) },
                state.comparison.bars.map { it.month }
            )
            assertEquals(100_00L, state.comparison.bars.sumOf { it.spentPaise })

            viewModel.selectMonths(12)
            do state = awaitItem() while (state?.months != 12)
            assertEquals(YearMonth.of(2025, 10), state.comparison.bars.first().month)
            assertEquals(400_00L, state.comparison.bars.sumOf { it.spentPaise })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theTagCardsPeriodIsIndependentOfTheCategoryCards() = runTest {
        spend(today, 100_00, "food", listOf("Office lunch"))
        spend(LocalDate.of(2026, 8, 10), 300_00, "travel", listOf("Goa trip"))

        val viewModel = viewModel()
        viewModel.donut.test {
            awaitNotNull()
            viewModel.tags.test {
                var tags = awaitUntil { it.period == ChartPeriod.MONTH }
                assertEquals(listOf("Office lunch"), tags.spending.map { it.tag?.name })

                viewModel.stepPeriod(-1, PeriodCard.TAGS)
                tags = awaitUntil { it.back == 1 }
                assertEquals(LocalDate.of(2026, 8, 1), tags.span.first)
                assertEquals(listOf(300_00L), tags.spending.map { it.spentPaise })

                viewModel.selectPeriod(ChartPeriod.YEAR, PeriodCard.TAGS)
                tags = awaitUntil { it.period == ChartPeriod.YEAR }
                assertEquals(listOf("Goa trip", "Office lunch"), tags.spending.map { it.tag?.name })

                viewModel.selectRange(today, today, PeriodCard.TAGS)
                assertEquals(
                    DateSpan(today, today),
                    awaitUntil {
                        it.period == ChartPeriod.CUSTOM
                    }.span
                )
                cancelAndIgnoreRemainingEvents()
            }
            // The category card never moved.
            val donut = viewModel.donut.value!!
            assertEquals(ChartPeriod.MONTH, donut.period)
            assertEquals(0, donut.back)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun openingATagGivesItsBreakdownForTheShownPeriod() = runTest {
        spend(LocalDate.of(2026, 8, 10), 1_000_00, "travel", listOf("Goa trip"))
        spend(LocalDate.of(2026, 8, 11), 400_00, "food", listOf("Goa trip", "Work"))
        spend(LocalDate.of(2026, 8, 12), 50_00, "food")
        spend(today, 200_00, "food", listOf("Goa trip"))

        val viewModel = viewModel()
        viewModel.tagBreakdown.test {
            assertNull(awaitItem())
            viewModel.stepPeriod(-1, PeriodCard.TAGS)
            val goa = viewModel.tags.first { it?.back == 1 }!!.spending.first().tag
            assertEquals("Goa trip", goa?.name)

            viewModel.openTag(goa)
            var state = awaitUntil { it.tag == goa }
            assertEquals(DateSpan(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)), state.span)
            assertEquals(1_400_00L, state.breakdown.totalPaise)
            assertEquals(
                listOf("travel", "food"),
                state.breakdown.slices.map { it.category.seedKey }
            )
            assertEquals(
                TransactionFilter(
                    categoryId = 7,
                    tagId = goa!!.id,
                    from = state.from,
                    until = state.until
                ),
                state.filter(7)
            )

            viewModel.openTag(null)
            state = awaitUntil { it.tag == null }
            assertEquals(50_00L, state.breakdown.totalPaise)
            assertEquals(true, state.filter().untagged)

            viewModel.closeTag()
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rangesAreLocalDaysAndMonths() {
        val viewModel = viewModel()
        val day = viewModel.rangeOf(today)
        assertEquals(DateSpan(today, today).from(zone) to DateSpan(today, today).until(zone), day)
        val (from, until) = viewModel.rangeOf(YearMonth.of(2026, 2))
        assertEquals(LocalDate.of(2026, 2, 1).atStartOfDay(zone).toInstant().toEpochMilli(), from)
        assertEquals(LocalDate.of(2026, 3, 1).atStartOfDay(zone).toInstant().toEpochMilli(), until)
    }

    private suspend fun <T : Any> app.cash.turbine.ReceiveTurbine<T?>.awaitUntil(
        check: (T) -> Boolean
    ): T {
        while (true) {
            val item = awaitItem()
            if (item != null && check(item)) return item
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<DonutState?>.awaitNotNull(): DonutState {
        var item = awaitItem()
        while (item == null) item = awaitItem()
        return item
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<DonutState?>.awaitBack(
        back: Int
    ): DonutState {
        var item = awaitNotNull()
        while (item.back != back) item = awaitNotNull()
        return item
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<DonutState?>.awaitPeriod(
        period: ChartPeriod
    ): DonutState {
        var item = awaitNotNull()
        while (item.period != period) item = awaitNotNull()
        return item
    }
}
