package com.openhand.khata.sms.ingest

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openhand.khata.core.data.BuiltInRuleOverrideRepository
import com.openhand.khata.core.data.CustomParserRepository
import com.openhand.khata.core.data.IgnoreRuleRepository
import com.openhand.khata.core.data.SmsImporter
import com.openhand.khata.core.data.UnparsedSmsRepository
import com.openhand.khata.core.database.DefaultCategorySeeder
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.sms.parser.SmsFilters
import dagger.Lazy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewSmsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: KhataDatabase
    private lateinit var settings: SmsImportSettings
    private lateinit var handler: NewSmsHandler

    private val body =
        "Sent Rs.70.00 from Kotak Bank A/c X1234 to SHRI GANESH VEG FOODS on 22-09-26. " +
            "UPI Ref 100000000009. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
    private val at = 1_790_000_000_000L

    /** The SMS as the phone delivers it: a long SMS in two parts. */
    private val parts = listOf(
        SmsPart("JM-KOTAKB-S", body.substring(0, 60), at),
        SmsPart("JM-KOTAKB-S", body.substring(60), at + 1)
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java)
            .addCallback(DefaultCategorySeeder.callback)
            .allowMainThreadQueries()
            .build()
        settings = SmsImportSettings(context)
        handler =
            NewSmsHandler(
                context,
                settings,
                SmsIngestor(
                    SmsImporter(Lazy { db }),
                    UnparsedSmsRepository(Lazy { db }),
                    CustomParserRepository(Lazy { db }),
                    BuiltInRuleOverrideRepository(Lazy { db }),
                    IgnoreRuleRepository(Lazy { db }),
                    settings
                )
            )
    }

    @After
    fun tearDown() = db.close()

    private fun allowSms() = shadowOf(context as Application)
        .grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    private suspend fun saved() = db.transactionDao().observeAll().first()

    @Test
    fun joinsPartsBySenderInOrder() {
        val joined = joinParts(
            parts + SmsPart("AX-OTHRBK-S", "other", at + 2) +
                SmsPart("AX-OTHRBK-S", " bank", at + 3)
        )

        assertEquals(
            listOf(
                SmsInbox.Message("JM-KOTAKB-S", body, at),
                SmsInbox.Message(
                    "AX-OTHRBK-S",
                    "other bank",
                    at + 2
                )
            ),
            joined
        )
    }

    @Test
    fun recordsANewBankSmsWhenOn() = runTest {
        allowSms()
        settings.setEnabled(true)

        assertEquals(listOf(IngestOutcome.RECORDED_FOR_REVIEW), handler.handle(parts))
        with(saved().single()) {
            assertEquals(7_000L, amountPaise)
            assertEquals(body, rawSms)
        }
    }

    @Test
    fun anSmsTheInboxImportAlsoSeesIsSavedOnce() = runTest {
        allowSms()
        settings.setEnabled(true)

        handler.handle(parts)
        val again = handler.handle(listOf(SmsPart("JM-KOTAKB-S", body, at + 3_000)))

        assertEquals(listOf(IngestOutcome.ALREADY_THERE), again)
        assertEquals(1, saved().size)
    }

    @Test
    fun ignoresOtherPeoplesSms() = runTest {
        allowSms()
        settings.setEnabled(true)

        assertEquals(
            emptyList<IngestOutcome>(),
            handler.handle(listOf(SmsPart("+919876543210", "Rs.500 for dinner", at)))
        )
        assertEquals(0, saved().size)
    }

    @Test
    fun aTransactionalSenderIsDroppedUnlessOnlyServiceIsOff() = runTest {
        allowSms()
        settings.setEnabled(true)
        val fromT = parts.map { it.copy(sender = "JM-KOTAKB-T") }

        assertEquals(emptyList<IngestOutcome>(), handler.handle(fromT))

        settings.setFilters(SmsFilters(onlyService = false))
        assertEquals(listOf(IngestOutcome.RECORDED_FOR_REVIEW), handler.handle(fromT))
    }

    @Test
    fun doesNothingWhenOffOrWithoutPermission() = runTest {
        settings.setEnabled(true)
        assertEquals(emptyList<IngestOutcome>(), handler.handle(parts))

        allowSms()
        settings.setEnabled(false)
        assertEquals(emptyList<IngestOutcome>(), handler.handle(parts))
        assertEquals(0, saved().size)
    }

    @Test
    fun theReceiverIsOnlyEnabledWithTheSwitch() {
        val receiver = ComponentName(context, SmsReceiver::class.java)
        val pm = context.packageManager
        val info = pm.getReceiverInfo(receiver, PackageManager.MATCH_DISABLED_COMPONENTS)
        assertEquals("android.permission.BROADCAST_SMS", info.permission)
        assertEquals(false, info.enabled)

        settings.setEnabled(true)
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            pm.getComponentEnabledSetting(receiver)
        )
        settings.setEnabled(false)
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            pm.getComponentEnabledSetting(receiver)
        )
    }
}
