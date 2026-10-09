package com.openhand.khata.feature.insights

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.openhand.khata.core.data.BackupReminderRepository
import com.openhand.khata.core.data.BackupRepository
import com.openhand.khata.core.data.ReviewRepository
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.database.DefaultCategorySeeder
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Transaction
import dagger.Lazy
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home's figures (#130), on a real in-memory database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelTest {
    private lateinit var db: KhataDatabase
    private lateinit var transactions: TransactionRepository
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 9, 27)
    private val lastMonth = LocalDate.of(2026, 8, 10)
    private val viewModels = mutableListOf<HomeViewModel>()

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
        // Stops the database queries before Main is reset, or a late result can race it.
        viewModels.forEach { it.viewModelScope.cancel() }
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun save(
        on: LocalDate,
        paise: Long,
        category: String = "food",
        direction: Direction = Direction.DEBIT,
        hour: Int = 12,
        countsIn: YearMonth? = null
    ) = transactions.save(
        Transaction(
            amountPaise = paise,
            direction = direction,
            timestamp = on.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
            categoryId = db.categoryDao().getBySeedKey(category)!!.id,
            countsIn = countsIn
        ),
        zone = zone
    )

    private fun viewModel() = HomeViewModel(
        transactions,
        BackupReminderRepository(
            ApplicationProvider.getApplicationContext(),
            BackupRepository(Lazy { db }),
            transactions
        ),
        ReviewRepository(Lazy { db }),
        flowOf(today),
        zone
    ).also { viewModels += it }

    private suspend fun HomeViewModel.loaded() = summary.filterNotNull().first()

    @Test
    fun moreThanLastMonth() = runTest {
        save(lastMonth, 1_000_00)
        save(today, 1_250_00)

        val summary = viewModel().loaded()
        assertEquals(MonthChange.More(250_00, 25), summary.change)
    }

    @Test
    fun lessThanLastMonthWithRefundsAndTransfersByInsightsRules() = runTest {
        save(lastMonth, 2_000_00)
        save(lastMonth, 500_00, direction = Direction.TRANSFER)
        save(today, 1_000_00)
        save(today, 500_00, direction = Direction.REFUND)

        val summary = viewModel().loaded()
        assertEquals(500_00, summary.month.spentPaise)
        assertEquals(MonthChange.Less(1_500_00, 75), summary.change)
    }

    @Test
    fun countsInMovesSpendingToLastMonth() = runTest {
        // Paid on 2 September for August's rent.
        save(LocalDate.of(2026, 9, 2), 1_000_00, "rent", countsIn = YearMonth.of(2026, 8))
        save(today, 1_000_00)

        val summary = viewModel().loaded()
        assertEquals(1_000_00, summary.month.spentPaise)
        assertEquals(MonthChange.Same, summary.change)
    }

    @Test
    fun comparesWithLastMonthOnlyUpToTheSameDay() = runTest {
        save(lastMonth, 1_000_00)
        // After 27 August, so not yet "this time last month".
        save(LocalDate.of(2026, 8, 28), 5_000_00)
        save(today, 1_000_00)

        assertEquals(MonthChange.Same, viewModel().loaded().change)
    }

    @Test
    fun noComparisonWhenNothingWasSpentLastMonth() = runTest {
        save(lastMonth, 5_000_00, direction = Direction.CREDIT)
        save(today, 100_00)

        assertNull(viewModel().loaded().change)
    }

    @Test
    fun topThreeCategoriesBiggestFirst() = runTest {
        save(today, 100_00, "food")
        save(today, 400_00, "rent")
        save(today, 300_00, "travel")
        save(today, 200_00, "health")

        val top = viewModel().loaded().topCategories
        assertEquals(listOf("rent", "travel", "health"), top.map { it.category.seedKey })
        assertEquals(listOf(400_00L, 300_00L, 200_00L), top.map { it.spentPaise })
    }

    @Test
    fun lastFiveTransactionsNewestFirst() = runTest {
        (1..7).forEach { save(today, it * 100_00L, hour = it + 8) }

        val recent = viewModel().loaded().recent
        assertEquals(
            listOf(700_00L, 600_00L, 500_00L, 400_00L, 300_00L),
            recent.map { it.amountPaise }
        )
    }

    @Test
    fun updatesLiveAsTransactionsChange() = runTest {
        val viewModel = viewModel()
        viewModel.summary.filterNotNull().test {
            val empty = awaitItem()
            assertFalse(empty.hasTransactions)
            assertTrue(empty.topCategories.isEmpty())

            save(today, 300_00)
            var summary = awaitItem()
            // The figures come from several queries, so wait for the last of them to catch up.
            while (
                summary.month.spentPaise == 0L ||
                summary.recent.isEmpty() ||
                summary.topCategories.isEmpty()
            ) {
                summary = awaitItem()
            }
            assertEquals(300_00, summary.month.spentPaise)
            assertEquals(1, summary.recent.size)
            assertEquals("food", summary.topCategories.single().category.seedKey)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
