package com.openhand.khata.feature.transactions

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openhand.khata.core.data.AccountRepository
import com.openhand.khata.core.data.CategoryRepository
import com.openhand.khata.core.data.TagRepository
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.database.DefaultCategorySeeder
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Transaction
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

/** The list opened from Insights' tag card: one tag's transactions, or the untagged ones. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionsViewModelTest {
    private lateinit var db: KhataDatabase
    private val lazyDb = Lazy { db }
    private val transactions by lazy { TransactionRepository(lazyDb) }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            KhataDatabase::class.java
        ).addCallback(DefaultCategorySeeder.callback).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun add(at: Long, tags: List<String> = emptyList()) = transactions.save(
        Transaction(amountPaise = 100_00, direction = Direction.DEBIT, timestamp = at, tags = tags)
    )

    private fun list(args: Map<String, Long>) = TransactionsViewModel(
        SavedStateHandle(args),
        transactions,
        CategoryRepository(lazyDb),
        TagRepository(lazyDb),
        AccountRepository(lazyDb)
    )

    private suspend fun TransactionsViewModel.shownIds() =
        days.first { it != null }!!.days!!.flatMap { day -> day.items.map { it.id } }.toSet()

    @Test
    fun opensWithATagAndARange() = runTest {
        val goa = add(AT, listOf("Goa trip"))
        val both = add(AT + 1, listOf("Goa trip", "Work"))
        add(AT + 2)
        add(UNTIL, listOf("Goa trip")) // After the range.
        val tagId = db.tagDao().search("Goa", 1).single().id

        val viewModel = list(
            mapOf(FILTER_TAG_ARG to tagId, FILTER_FROM_ARG to AT, FILTER_UNTIL_ARG to UNTIL)
        )

        assertEquals(tagId, viewModel.filter.value.tagId)
        assertEquals(setOf(goa, both), viewModel.shownIds())
    }

    @Test
    fun opensWithUntagged() = runTest {
        add(AT, listOf("Goa trip"))
        val untagged = add(AT + 1)

        val viewModel = list(
            mapOf(
                FILTER_TAG_ARG to FILTER_UNTAGGED,
                FILTER_FROM_ARG to AT,
                FILTER_UNTIL_ARG to UNTIL
            )
        )

        assertEquals(true, viewModel.filter.value.untagged)
        assertEquals(null, viewModel.filter.value.tagId)
        assertEquals(setOf(untagged), viewModel.shownIds())

        // Picking a tag from the filter replaces Untagged.
        viewModel.setTag(db.tagDao().search("Goa", 1).single().id)
        assertEquals(false, viewModel.filter.value.untagged)
    }

    private companion object {
        const val AT = 1_790_000_000_000L
        const val UNTIL = AT + 86_400_000L
    }
}
