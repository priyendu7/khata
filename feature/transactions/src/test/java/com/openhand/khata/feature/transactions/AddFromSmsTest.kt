package com.openhand.khata.feature.transactions

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openhand.khata.core.data.AccountRepository
import com.openhand.khata.core.data.CategoryRepository
import com.openhand.khata.core.data.PayeeRepository
import com.openhand.khata.core.data.TagRepository
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.data.UnparsedSmsRepository
import com.openhand.khata.core.database.DefaultCategorySeeder
import com.openhand.khata.core.database.KhataDatabase
import dagger.Lazy
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

/** Add by hand from the review inbox: the editor starts filled in, and the SMS goes once saved. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddFromSmsTest {
    private lateinit var db: KhataDatabase
    private val lazyDb = Lazy { db }
    private val unparsed by lazy { UnparsedSmsRepository(lazyDb) }
    private val at = 1_790_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                KhataDatabase::class.java
            )
                .addCallback(DefaultCategorySeeder.callback)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun editor(args: Map<String, Long>) = TransactionEditorViewModel(
        SavedStateHandle(mapOf(TRANSACTION_ID_ARG to 0L) + args),
        TransactionRepository(lazyDb),
        CategoryRepository(lazyDb),
        AccountRepository(lazyDb),
        TagRepository(lazyDb),
        PayeeRepository(lazyDb),
        unparsed
    )

    @Test
    fun startsWithTheSmsAmountAndTimeAndDeletesTheSmsOnceSaved() = runTest {
        unparsed.save("JM-KOTAKB-S", "Rs.2,000 withdrawn at ATM using card XX5678.", at)
        val sms = unparsed.observeAll().first().single()
        val vm = editor(
            mapOf(
                PREFILL_AMOUNT_ARG to 200_000L,
                PREFILL_AT_ARG to at,
                FROM_UNPARSED_ARG to sms.id
            )
        )

        val form = vm.form.value!!
        assertEquals("2000", form.amount)
        val received = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        assertEquals(received.toLocalDate(), form.date)
        assertEquals(received.toLocalTime().withSecond(0).withNano(0), form.time)

        vm.save()
        vm.done.first { it }

        assertEquals(emptyList<Any>(), unparsed.observeAll().first())
        assertEquals(200_000L, db.transactionDao().observeAll().first().single().amountPaise)
    }

    @Test
    fun withoutAnAmountTheFieldStartsEmpty() {
        val vm = editor(mapOf(PREFILL_AMOUNT_ARG to NO_PREFILL, PREFILL_AT_ARG to at))

        assertEquals("", vm.form.value!!.amount)
    }

    @Test
    fun findsTheFirstAmountInAnSms() {
        assertEquals(200_000L, firstAmountPaise("Rs.2,000 withdrawn at ATM using card XX5678."))
        assertEquals(1_800L, firstAmountPaise("INR 18.00 is credited to your Account"))
        assertEquals(9_900L, firstAmountPaise("Paid ₹ 99 at SHOP"))
        assertNull(firstAmountPaise("Your request will be done in 24 hours 5 minutes."))
    }
}
