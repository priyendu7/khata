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
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.TransactionSource
import com.openhand.khata.sms.parser.SmsFilters
import dagger.Lazy
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InboxScannerTest {
    private lateinit var db: KhataDatabase
    private lateinit var scanner: InboxScanner
    private lateinit var settings: SmsImportSettings

    private fun at(day: Int, hour: Int = 12) = LocalDateTime.of(
        2026,
        9,
        day,
        hour,
        0
    ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun sent(amount: String, payee: String, day: Int, ref: String) =
        "Sent Rs.$amount from Kotak Bank A/c X1234 to $payee on ${"%02d".format(day)}-09-26. " +
            "UPI Ref $ref. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java)
            .addCallback(DefaultCategorySeeder.callback)
            .allowMainThreadQueries()
            .build()
        Robolectric.setupContentProvider(FakeSmsInbox::class.java, "sms")
        FakeSmsInbox.reset()
        FakeSmsInbox.messages += listOf(
            FakeSmsInbox.Sms("JM-KOTAKB-S", sent("100.00", "OLD SHOP", 1, "100000000001"), at(1)),
            FakeSmsInbox.Sms(
                "JM-KOTAKB-S",
                sent("366.00", "GENERAL STORE", 10, "100000000002"),
                at(10)
            ),
            FakeSmsInbox.Sms(
                "AX-KOTAKB-S",
                "Received Rs.1.50 in your Kotak Bank AC 1234 from ANITA SHARMA on 11-09-26.UPI Ref:100000000003",
                at(11)
            ),
            FakeSmsInbox.Sms("+919876543210", "Paid you Rs.500 for dinner", at(12)),
            FakeSmsInbox.Sms(
                "JM-KOTAKB-S",
                "123456 is the OTP for Rs.500 at SHOP. Do not share.",
                at(13)
            ),
            FakeSmsInbox.Sms("JM-KOTAKB-S", "Rs.2,000 withdrawn at ATM using card XX5678.", at(14)),
            FakeSmsInbox.Sms("VM-KOTAKB-P", "Pre-approved loan of Rs.5,00,000!", at(15))
        )
        settings = SmsImportSettings(context)
        scanner =
            InboxScanner(
                SmsInbox(context),
                SmsIngestor(
                    SmsImporter(Lazy { db }),
                    UnparsedSmsRepository(Lazy { db }),
                    CustomParserRepository(Lazy { db }),
                    IgnoreRuleRepository(Lazy { db }),
                    settings
                )
            )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun importsBankSmsSinceTheDateAndCountsTheRest() = runTest {
        val summary = scanner.scan(since = at(5), now = { 42L })

        assertEquals(
            ScanSummary(
                since = at(5),
                finishedAt = 42L,
                recorded = 2,
                toReview = 2,
                alreadyThere = 0,
                unreadable = 1,
                // The OTP. The promotional sender and the phone number are never read.
                filtered = 1
            ),
            summary
        )
        val saved = db.transactionDao().observeAll().first().sortedBy { it.timestamp }
        assertEquals(listOf(36_600L, 150L), saved.map { it.amountPaise })
        assertEquals(listOf(Direction.DEBIT, Direction.CREDIT), saved.map { it.direction })
        assertEquals(setOf(TransactionSource.SMS), saved.map { it.source }.toSet())
        // The ATM SMS no rule reads is kept for the review inbox, not the OTP or anything else.
        val unreadable = db.unparsedSmsDao().observeAll().first()
        assertEquals(
            listOf("Rs.2,000 withdrawn at ATM using card XX5678."),
            unreadable.map {
                it.body
            }
        )
    }

    @Test
    fun aBusinessTheRulesDontKnowGoesToReview() = runTest {
        val shop = "Rs.249 debited for your order at SHOPIN. Ref 123456789012."
        FakeSmsInbox.messages += FakeSmsInbox.Sms("AX-SHOPIN-S", shop, at(16))
        FakeSmsInbox.messages += FakeSmsInbox.Sms("AX-SHOPIN-S", "Your order is on its way", at(17))

        val summary = scanner.scan(since = at(16))

        assertEquals(1, summary.unreadable)
        assertEquals(1, summary.filtered)
        assertEquals(listOf(shop), db.unparsedSmsDao().observeAll().first().map { it.body })
    }

    @Test
    fun theParserIsRebuiltWhenAFilterChanges() = runTest {
        FakeSmsInbox.messages +=
            FakeSmsInbox.Sms("VM-SHOPIN-T", "Rs.249 debited at SHOPIN.", at(16))

        assertEquals(0, scanner.scan(since = at(16)).unreadable)
        assertEquals(emptyList<String>(), FakeSmsInbox.bodiesRead)

        settings.setFilters(SmsFilters(onlyService = false))

        assertEquals(1, scanner.scan(since = at(16)).unreadable)
    }

    @Test
    fun neverReadsTheTextOfSomeoneElsesSms() = runTest {
        scanner.scan(since = at(5))

        assertEquals(setOf("JM-KOTAKB-S", "AX-KOTAKB-S"), FakeSmsInbox.bodiesRead.toSet())
    }

    @Test
    fun importingAgainAddsNothing() = runTest {
        scanner.scan(since = at(5))
        val again = scanner.scan(since = at(1))

        assertEquals(1, again.recorded)
        // The two transactions, and the ATM SMS already kept for review.
        assertEquals(3, again.alreadyThere)
        assertEquals(0, again.unreadable)
        assertEquals(3, db.transactionDao().observeAll().first().size)
    }

    @Test
    fun reportsProgress() = runTest {
        val progress = mutableListOf<Pair<Int, Int>>()

        scanner.scan(since = at(5)) { done, total -> progress += done to total }

        // Only the 4 bank SMS since the date count: 2 transactions, the OTP and the ATM SMS.
        assertEquals((0..4).map { it to 4 }, progress)
    }
}
