package com.openhand.khata.feature.settings

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
import com.openhand.khata.sms.ingest.IngestOutcome
import com.openhand.khata.sms.ingest.SmsImportSettings
import com.openhand.khata.sms.ingest.SmsIngestor
import com.openhand.khata.sms.parser.RuleCheck
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

/** Saving from the rule maker records the SMS waiting in To review without asking. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuleSavingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: KhataDatabase
    private lateinit var parsers: CustomParserRepository
    private lateinit var ingestor: SmsIngestor

    private val atm = "Rs.2,000 withdrawn at ATM using card XX5678."

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java)
            .addCallback(DefaultCategorySeeder.callback)
            .allowMainThreadQueries()
            .build()
        parsers = CustomParserRepository(Lazy { db })
        ingestor = SmsIngestor(
            SmsImporter(Lazy { db }),
            UnparsedSmsRepository(Lazy { db }),
            parsers,
            BuiltInRuleOverrideRepository(Lazy { db }),
            IgnoreRuleRepository(Lazy { db }),
            SmsImportSettings(context)
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun savingReadsTheWaitingSmsAndSaysHowMany() = runTest {
        // Two SMS in this format wait in To review.
        assertEquals(IngestOutcome.UNREADABLE, ingestor.ingest("JM-KOTAKB-S", atm, AT))
        assertEquals(
            IngestOutcome.UNREADABLE,
            ingestor.ingest("JM-KOTAKB-S", atm.replace("2,000", "500"), AT + 1)
        )
        val start = MakerForm.start("JM-KOTAKB-S", atm, AT, null)
        val form = start.tapInStep(start.words.indexOfFirst { it.text == "Rs.2,000" })
        val rule = (form.check(emptySet()) as RuleCheck.Valid).rule
        val saving = RuleSaving(this, parsers, ingestor)

        saving.saveAndRead(rule, AT)

        assertEquals(SaveStep.Recorded(2), saving.step.first { it is SaveStep.Recorded })
        assertEquals(listOf(rule.rule.id), parsers.observeAll().first().map { it.ruleId })
        assertEquals(emptyList<Any>(), db.unparsedSmsDao().observeAll().first())
        assertEquals(RuleMaker.Field.AMOUNT, form.marks.single().field)
    }

    private companion object {
        const val AT = 1_790_000_000_000L
    }
}
