package com.openhand.khata.feature.insights

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.database.DefaultCategorySeeder
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Transaction
import dagger.Lazy
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Switching the donut's period, on a real in-memory database. */
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

    private suspend fun spend(on: LocalDate, paise: Long, category: String) = transactions.save(
        Transaction(
            amountPaise = paise,
            direction = Direction.DEBIT,
            timestamp = on.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            categoryId = db.categoryDao().getBySeedKey(category)!!.id
        )
    )

    private fun viewModel() =
        InsightsViewModel(transactions, flowOf(today), zone, firstDayOfWeek = DayOfWeek.MONDAY)

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

    private suspend fun app.cash.turbine.ReceiveTurbine<DonutState?>.awaitNotNull(): DonutState {
        var item = awaitItem()
        while (item == null) item = awaitItem()
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
