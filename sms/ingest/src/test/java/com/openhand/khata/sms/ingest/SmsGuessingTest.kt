package com.openhand.khata.sms.ingest

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openhand.khata.core.data.BuiltInRuleOverrideRepository
import com.openhand.khata.core.data.CustomParserRepository
import com.openhand.khata.core.data.IgnoreRuleRepository
import com.openhand.khata.core.data.SmsImporter
import com.openhand.khata.core.data.UnparsedSmsRepository
import com.openhand.khata.core.database.DefaultCategorySeeder
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.Direction
import dagger.Lazy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Khata's best guess in To review: offered only when it reads the SMS, saved only on Yes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsGuessingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: KhataDatabase
    private lateinit var unparsed: UnparsedSmsRepository
    private lateinit var parsers: CustomParserRepository
    private lateinit var guessing: SmsGuessing
    private val at = 1_790_000_000_000L

    private val swiggy = "Rs.450.00 debited from a/c **1234 to VPA swiggy@icici (UPI Ref No " +
        "512345678901). Not you? Call 18002586161"
    private val zomato = "Rs.120.00 debited from a/c **1234 to VPA zomato@hdfc (UPI Ref No " +
        "512345678902). Not you? Call 18002586161"
    private val recharge = "Recharge of Rs.299 successful. Plan: Jio Unlimited."

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java)
            .addCallback(DefaultCategorySeeder.callback)
            .allowMainThreadQueries()
            .build()
        unparsed = UnparsedSmsRepository(Lazy { db })
        parsers = CustomParserRepository(Lazy { db })
        val ingestor = SmsIngestor(
            SmsImporter(Lazy { db }),
            unparsed,
            parsers,
            BuiltInRuleOverrideRepository(Lazy { db }),
            IgnoreRuleRepository(Lazy { db }),
            SmsImportSettings(context)
        )
        guessing = SmsGuessing(ingestor, parsers)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun wait(vararg bodies: String) = bodies.forEachIndexed { index, body ->
        unparsed.save("VM-NEWBNK-S", body, at + index)
    }

    @Test
    fun guessesOnlyWithAnAmountAndDirection() = runTest {
        wait(swiggy, recharge)
        val all = unparsed.getAll()
        val guesses = guessing.guesses(all)
        val guess = guesses.getValue(all.single { it.body == swiggy }.id)
        assertEquals(1, guesses.size)
        assertEquals(45_000L, guess.sms.amountPaise)
        assertEquals(Direction.DEBIT, guess.sms.direction)
        assertEquals("swiggy@icici", guess.sms.payee)
        assertEquals("1234", guess.sms.accountLast4)
        assertEquals("NEWBNK", guess.rule.rule.bank)
        // Only a suggestion: nothing saved yet.
        assertEquals(emptyList<Any>(), parsers.observeAll().first())
        assertEquals(2, unparsed.getAll().size)
    }

    @Test
    fun yesSavesTheRuleAndClearsSimilarSms() = runTest {
        wait(swiggy, zomato, recharge)
        val sms = unparsed.getAll().single { it.body == swiggy }
        assertEquals(2, guessing.accept(sms, now = at))
        assertEquals(listOf("newbnk-debit"), parsers.observeAll().first().map { it.ruleId })
        assertEquals(listOf(recharge), unparsed.getAll().map { it.body })
        val saved = db.transactionDao().observeAll().first()
        assertTrue(saved.map { it.amountPaise }.containsAll(listOf(45_000L, 12_000L)))
    }

    @Test
    fun noGuessSavesNothing() = runTest {
        wait(recharge)
        assertEquals(0, guessing.accept(unparsed.getAll().single(), now = at))
        assertEquals(emptyList<Any>(), parsers.observeAll().first())
    }
}
