package com.openhand.khata.sms.ingest

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openhand.khata.core.data.CustomParserRepository
import com.openhand.khata.core.data.IgnoreRuleRepository
import com.openhand.khata.core.data.SmsImporter
import com.openhand.khata.core.data.UnparsedSmsRepository
import com.openhand.khata.core.database.DefaultCategorySeeder
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.sms.parser.RuleMaker
import dagger.Lazy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ignore this sender and ignore messages like this, from To review (PRD feature 7). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsIgnoringTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: KhataDatabase
    private lateinit var unparsed: UnparsedSmsRepository
    private lateinit var rules: IgnoreRuleRepository
    private lateinit var ignoring: SmsIgnoring
    private lateinit var ingestor: SmsIngestor
    private val at = 1_790_000_000_000L

    private val recharge = "Recharge of Rs.299 successful. Plan: Jio Unlimited. Txn ID 4467."
    private val otherRecharge = "Recharge of Rs.749 successful. Plan: Data Pack. Txn ID 5512."
    private val cashback = "Cashback of Rs.20 credited to your JioPay wallet."
    private val hdfc = "Rs.450.00 spent on HDFC Bank Card x4321 at CITY PHARMACY."

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java)
            .addCallback(DefaultCategorySeeder.callback)
            .allowMainThreadQueries()
            .build()
        unparsed = UnparsedSmsRepository(Lazy { db })
        rules = IgnoreRuleRepository(Lazy { db })
        ignoring = SmsIgnoring(rules, unparsed)
        ingestor = SmsIngestor(
            SmsImporter(Lazy { db }),
            unparsed,
            CustomParserRepository(Lazy { db }),
            rules,
            SmsImportSettings(context)
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun waiting() = unparsed.getAll().map { it.body }

    private suspend fun waitAll() {
        listOf(
            "JM-JIOPAY-S" to recharge,
            "VK-JIOPAY-S" to otherRecharge,
            "JM-JIOPAY-S" to cashback,
            "VM-HDFCBK-S" to hdfc
        ).forEachIndexed { i, (sender, body) ->
            assertEquals(body, IngestOutcome.UNREADABLE, ingestor.ingest(sender, body, at + i))
        }
    }

    @Test
    fun ignoringASenderRemovesItsWaitingSmsAndDropsNewOnes() = runTest {
        waitAll()
        val sms = unparsed.getAll().first { it.body == cashback }

        assertEquals(3, ignoring.ignoreSender(sms, now = at))

        assertEquals(listOf(hdfc), waiting())
        assertEquals(IngestOutcome.FILTERED, ingestor.ingest("AX-JIOPAY-S", "Anything", at))
        assertEquals("JIOPAY", rules.observeAll().first().single().header)
    }

    @Test
    fun ignoringLikeThisRemovesOnlyTheMatchingWaitingSms() = runTest {
        waitAll()
        val sms = unparsed.getAll().first { it.body == recharge }
        val tapped = RuleMaker.words(recharge).filter { it.text in setOf("Jio", "Unlimited.") }
        val pattern = ignoring.template(sms, tapped)

        assertEquals(2, ignoring.waitingLike(sms, pattern))
        assertEquals(2, ignoring.ignoreLikeThis(sms, pattern, now = at))

        assertEquals(setOf(cashback, hdfc), waiting().toSet())
        val next = "Recharge of Rs.99 successful. Plan: Talktime Top Up. Txn ID 1."
        assertEquals(IngestOutcome.FILTERED, ingestor.ingest("JM-JIOPAY-S", next, at + HOUR))
        assertEquals(IngestOutcome.UNREADABLE, ingestor.ingest("JM-JIOPAY-S", "$cashback!", at))
    }

    @Test
    fun aSwitchedOffRuleIsNotUsed() = runTest {
        waitAll()
        val sms = unparsed.getAll().first { it.body == cashback }
        ignoring.ignoreSender(sms, now = at)
        val id = rules.observeAll().first().single().id

        rules.setEnabled(id, false)

        assertEquals(IngestOutcome.UNREADABLE, ingestor.ingest("JM-JIOPAY-S", cashback, at + HOUR))
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
