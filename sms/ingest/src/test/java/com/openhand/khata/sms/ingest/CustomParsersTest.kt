package com.openhand.khata.sms.ingest

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openhand.khata.core.data.CustomParserRepository
import com.openhand.khata.core.data.DuplicateMatch
import com.openhand.khata.core.data.SmsImporter
import com.openhand.khata.core.data.UnparsedSmsRepository
import com.openhand.khata.core.database.DefaultCategorySeeder
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.sms.parser.CodeCheck
import com.openhand.khata.sms.parser.CustomRules
import com.openhand.khata.sms.parser.ParserRule
import com.openhand.khata.sms.parser.RuleAccountType
import com.openhand.khata.sms.parser.RuleCode
import com.openhand.khata.sms.parser.RuleDirection
import dagger.Lazy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Rules from Settings > Parsers (PRD feature 8) in the receiver, the inbox import and To review. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomParsersTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: KhataDatabase
    private lateinit var parsers: CustomParserRepository
    private lateinit var ingestor: SmsIngestor
    private lateinit var settings: SmsImportSettings

    private val hdfc = ParserRule(
        v = 1,
        id = "hdfc-card",
        bank = "HDFC",
        senders = listOf("HDFCBK"),
        pattern = "(?<amount>Rs\\.[\\d,.]+) spent on HDFC Bank Card x(?<account>\\d{4}) " +
            "at (?<payee>.+?) on",
        direction = RuleDirection.DEBIT,
        accountType = RuleAccountType.CREDIT_CARD
    )
    private val hdfcSms = "Rs.450.00 spent on HDFC Bank Card x4321 at CITY PHARMACY on 2026-09-20."
    private val kotakAtm = ParserRule(
        v = 1,
        id = "kotak-atm",
        bank = "Kotak",
        senders = listOf("KOTAKB"),
        pattern = "(?<amount>Rs\\.[\\d,]+) withdrawn at ATM using card XX(?<account>\\d{4})",
        direction = RuleDirection.DEBIT,
        accountType = RuleAccountType.DEBIT_CARD
    )
    private val atmSms = "Rs.2,000 withdrawn at ATM using card XX5678."
    private val upiSms =
        "Sent Rs.366.00 from Kotak Bank A/c X1234 to GENERAL STORE on 23-09-26. " +
            "UPI Ref 111122223333. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
    private val at = 1_790_000_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java)
            .addCallback(DefaultCategorySeeder.callback)
            .allowMainThreadQueries()
            .build()
        parsers = CustomParserRepository(Lazy { db })
        settings = SmsImportSettings(context)
        ingestor = SmsIngestor(
            SmsImporter(Lazy { db }),
            UnparsedSmsRepository(Lazy { db }),
            parsers,
            settings
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun add(rule: ParserRule, now: Long = at) =
        parsers.save(rule.id, rule.bank, RuleCode.encode(rule), now)

    private suspend fun idOf(ruleId: String) =
        parsers.observeAll().first().first { it.ruleId == ruleId }.id

    private suspend fun saved() = db.transactionDao().observeAll().first()

    @Test
    fun aCustomRuleReadsASenderThatWouldOtherwiseGoToReview() = runTest {
        assertEquals(IngestOutcome.UNREADABLE, ingestor.ingest("VM-HDFCBK-S", hdfcSms, at))

        add(hdfc)

        assertEquals(IngestOutcome.RECORDED_FOR_REVIEW, ingestor.ingest("VM-HDFCBK-S", hdfcSms, at))
        with(saved().single()) {
            assertEquals(45_000L, amountPaise)
            assertEquals(hdfcSms, rawSms)
        }
    }

    @Test
    fun aDisabledRuleIsNotUsed() = runTest {
        add(hdfc)
        parsers.setEnabled(idOf("hdfc-card"), false)

        assertEquals(IngestOutcome.UNREADABLE, ingestor.ingest("VM-HDFCBK-S", hdfcSms, at))
        assertEquals(0, saved().size)

        parsers.setEnabled(idOf("hdfc-card"), true)
        assertEquals(IngestOutcome.RECORDED_FOR_REVIEW, ingestor.ingest("VM-HDFCBK-S", hdfcSms, at))
    }

    @Test
    fun aCustomRuleWinsOverTheBuiltInOne() = runTest {
        val mine = kotakAtm.copy(
            id = "my-kotak-upi",
            bank = "My Kotak",
            pattern = "Sent (?<amount>Rs\\.[\\d.,]+) from Kotak Bank A/c X(?<account>\\d{4})",
            accountType = RuleAccountType.BANK
        )
        add(mine)

        ingestor.ingest("JM-KOTAKB-S", upiSms, at)

        val account = db.accountDao().getById(saved().single().accountId!!)!!
        assertEquals("My Kotak", account.bank)
    }

    @Test
    fun theNewSmsReceiverKnowsCustomSenders() = runTest {
        shadowOf(context as Application)
            .grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        settings.setEnabled(true)
        val handler = NewSmsHandler(context, settings, ingestor)
        add(hdfc)

        val outcomes = handler.handle(listOf(SmsPart("VM-HDFCBK-S", hdfcSms, at)))

        assertEquals(listOf(IngestOutcome.RECORDED_FOR_REVIEW), outcomes)
    }

    @Test
    fun theInboxImportReadsCustomSenders() = runTest {
        Robolectric.setupContentProvider(FakeSmsInbox::class.java, "sms")
        FakeSmsInbox.reset()
        FakeSmsInbox.messages += FakeSmsInbox.Sms("VM-HDFCBK-S", hdfcSms, at)
        FakeSmsInbox.messages += FakeSmsInbox.Sms("+919876543210", "Rs.500 for dinner", at)
        add(hdfc)

        val summary = InboxScanner(SmsInbox(context), ingestor).scan(since = 0)

        assertEquals(1, summary.recorded)
        assertEquals(listOf("VM-HDFCBK-S"), FakeSmsInbox.bodiesRead)
    }

    @Test
    fun aNewRuleReadsTheSmsWaitingInToReview() = runTest {
        assertEquals(IngestOutcome.UNREADABLE, ingestor.ingest("JM-KOTAKB-S", atmSms, at))
        val rule = (CustomRules.check(RuleCode.encode(kotakAtm)) as CodeCheck.Valid).rule

        assertEquals(1, ingestor.unparsedReadableBy(rule))
        add(kotakAtm)
        assertEquals(1, ingestor.retryUnparsed())

        assertEquals(listOf(200_000L), saved().map { it.amountPaise })
        assertEquals(emptyList<Any>(), db.unparsedSmsDao().observeAll().first())
        // Nothing left to read the second time.
        assertEquals(0, ingestor.retryUnparsed())
    }

    @Test
    fun smsNoRuleReadsStayInToReview() = runTest {
        ingestor.ingest("JM-KOTAKB-S", atmSms, at)
        add(hdfc)

        assertEquals(0, ingestor.retryUnparsed())
        assertEquals(1, db.unparsedSmsDao().observeAll().first().size)
    }

    @Test
    fun explainSavesNothingAndMatchesWhatIngestDoes() = runTest {
        add(kotakAtm)

        val custom = ingestor.explain("JM-KOTAKB-S", atmSms, at)
        assertEquals(listOf(TriedRule("kotak-atm", custom = true)), custom.rulesTried)
        assertTrue(custom.preview!!.needsReview)
        val builtIn = ingestor.explain("JM-KOTAKB-S", upiSms, at)
        assertEquals(TriedRule("kotak-upi-sent", custom = false), builtIn.rulesTried.last())
        assertEquals(0, saved().size)
        assertEquals(0, db.unparsedSmsDao().observeAll().first().size)

        ingestor.ingest("JM-KOTAKB-S", upiSms, at)
        val again = ingestor.explain("JM-KOTAKB-S", upiSms, at)
        assertEquals(DuplicateMatch.REFERENCE, again.preview!!.duplicate!!.match)
    }
}
