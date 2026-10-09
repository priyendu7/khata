package com.openhand.khata.feature.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openhand.khata.core.data.AccountRepository
import com.openhand.khata.core.data.BackupRepository
import com.openhand.khata.core.data.CategoryRepository
import com.openhand.khata.core.data.CustomParserRepository
import com.openhand.khata.core.data.EventRepository
import com.openhand.khata.core.data.IgnoreRuleRepository
import com.openhand.khata.core.data.PayeeRepository
import com.openhand.khata.core.data.TagRepository
import com.openhand.khata.core.database.DefaultCategorySeeder
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.AppInfo
import com.openhand.khata.core.model.Event
import com.openhand.khata.core.model.IgnoreKind
import com.openhand.khata.sms.ingest.SmsImportSettings
import com.openhand.khata.sms.parser.BuiltInRules
import com.openhand.khata.sms.parser.SmsFilters
import dagger.Lazy
import java.time.LocalDate
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

/** The counts and states on the Settings rows, from a real in-memory database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {
    private lateinit var db: KhataDatabase
    private lateinit var sms: SmsImportSettings
    private val zone = ZoneId.systemDefault()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java)
            .addCallback(DefaultCategorySeeder.callback)
            .allowMainThreadQueries()
            .build()
        sms = SmsImportSettings(context)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(
        AppInfo(versionName = "1.2.3"),
        AccountRepository(Lazy { db }),
        CategoryRepository(Lazy { db }),
        TagRepository(Lazy { db }),
        PayeeRepository(Lazy { db }),
        EventRepository(Lazy { db }),
        CustomParserRepository(Lazy { db }),
        IgnoreRuleRepository(Lazy { db }),
        BackupRepository(Lazy { db }),
        sms
    )

    @Test
    fun exposesTheInstalledVersion() {
        assertEquals("1.2.3", viewModel().versionName)
    }

    @Test
    fun countsEachList() = runTest {
        AccountRepository(Lazy { db }).save(Account(name = "HDFC", type = AccountType.BANK))
        TagRepository(Lazy { db }).getOrCreate(listOf("work", "home"))
        db.payeeDao().insert(
            PayeeEntity(identifier = "swiggy@upi", displayName = "Swiggy", defaultCategoryId = null)
        )
        val events = EventRepository(Lazy { db })
        events.save(Event(name = "Goa", start = day(1), end = day(5)), zone)
        events.save(Event(name = "Diwali", start = day(20), end = day(22)), zone)
        val seeded = db.categoryDao().observeAll().first().size

        val summary = viewModel().summary.first { it.events == 2 }

        assertEquals(1, summary.accounts)
        // Each event has its own tag.
        assertEquals(4, summary.tags)
        assertEquals(1, summary.payees)
        assertEquals(seeded, summary.categories)
        assertEquals("Diwali", summary.newestEvent)
    }

    @Test
    fun showsSmsParsersAndBackupState() = runTest {
        sms.setEnabled(true)
        sms.setFilters(SmsFilters(dropPromotional = false))
        IgnoreRuleRepository(Lazy { db })
            .add(IgnoreKind.SENDER, "AD-OFFERS", null, "Big sale", now = 0)
        val exportedAt = day(3).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        BackupRepository(Lazy { db }).markExported(exportedAt)

        val summary = viewModel().summary.first { it.lastExport != null && it.builtInParsers > 0 }

        assertEquals(true, summary.smsEnabled)
        assertEquals(FilterSwitch.entries.size - 1, summary.filtersOn)
        assertEquals(1, summary.ignoreRules)
        assertEquals(BuiltInRules.all().size, summary.builtInParsers)
        assertEquals(0, summary.customParsers)
        assertEquals(day(3), summary.lastExport)
        assertNull(summary.lastScan)
    }

    @Test
    fun newestEventIsTheOneStartingLast() {
        val goa = Event(id = 1, name = "Goa", start = day(10), end = day(12))
        val diwali = Event(id = 2, name = "Diwali", start = day(1), end = day(2))
        val holi = Event(id = 3, name = "Holi", start = day(10), end = day(10))

        assertEquals(holi, newestEvent(listOf(goa, diwali, holi)))
        assertNull(newestEvent(emptyList()))
    }

    private fun day(n: Int) = LocalDate.of(2026, 10, n)
}
