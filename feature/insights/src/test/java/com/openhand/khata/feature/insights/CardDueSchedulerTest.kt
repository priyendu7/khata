package com.openhand.khata.feature.insights

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.openhand.khata.core.data.CardReminderRepository
import com.openhand.khata.core.data.CardRepository
import com.openhand.khata.core.data.StatementSaveResult
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.database.DefaultCategorySeeder
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.SmsStatement
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransferSide
import dagger.Lazy
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

/** Due date reminders (#156) are scheduled, kept and cancelled as bills and settings change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardDueSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDate.parse("2026-10-15").atTime(12, 0).atZone(zone).toInstant()
        .toEpochMilli()
    private lateinit var db: KhataDatabase
    private lateinit var workManager: WorkManager
    private lateinit var transactions: TransactionRepository
    private lateinit var cards: CardRepository
    private lateinit var reminders: CardReminderRepository
    private var notificationsAllowed = true

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java)
            .addCallback(DefaultCategorySeeder.callback)
            .allowMainThreadQueries()
            .build()
        transactions = TransactionRepository(Lazy { db })
        cards = CardRepository(Lazy { db }, transactions)
        reminders = CardReminderRepository(context)
    }

    @After
    fun tearDown() = db.close()

    private fun scheduler() = CardDueScheduler(
        reminders,
        cards,
        { workManager },
        CardDueNotifier(context),
        { notificationsAllowed },
        { Clock.fixed(Instant.ofEpochMilli(now), zone) }
    )

    /** Plans once with what's saved now, as the running scheduler does after each change. */
    private suspend fun plan() = scheduler().run { apply(plans().first()) }

    /** The scheduled reminders' unique names, e.g. "card_due:1:3". */
    private fun scheduled(): Set<String> = workManager
        .getWorkInfosByTag(CardDueScheduler.TAG).get()
        .filter { it.state == WorkInfo.State.ENQUEUED }
        .flatMap { it.tags }
        .filter { it.startsWith("${CardDueScheduler.TAG}:") }
        .toSet()

    private suspend fun statement(
        due: String = "2026-11-02",
        statementDate: String = "2026-10-10",
        total: Long = 8_695_00
    ) = cards.save(
        SmsStatement(
            "Axis",
            "5678",
            total,
            null,
            LocalDate.parse(due),
            LocalDate.parse(statementDate),
            "Axis Bank Credit Card XX5678 statement: $total due by $due"
        )
    ) as StatementSaveResult.Saved

    private suspend fun pay(card: Long, paise: Long) = transactions.save(
        Transaction(
            amountPaise = paise,
            direction = Direction.TRANSFER,
            timestamp = now - 1,
            accountId = card,
            transferSide = TransferSide.IN,
            categoryId = db.categoryDao().getBySeedKey("uncategorized")!!.id
        ),
        zone = zone
    )

    private fun names(statementId: Long, vararg days: Int) =
        days.map { "${CardDueScheduler.TAG}:$statementId:$it" }.toSet()

    @Test
    fun threeRemindersForAnUnpaidBill() = runTest {
        val saved = statement()
        plan()
        assertEquals(names(saved.statementId, 3, 1, 0), scheduled())
    }

    @Test
    fun thePickedDaysAreScheduled() = runTest {
        val saved = statement()
        reminders.setDay(7, true)
        reminders.setDay(3, false)
        plan()
        assertEquals(names(saved.statementId, 7, 1, 0), scheduled())
    }

    @Test
    fun payingInFullCancelsThemButAPartPaymentKeepsThem() = runTest {
        val saved = statement()
        plan()
        pay(saved.cardId, 5_000_00)
        plan()
        assertEquals(names(saved.statementId, 3, 1, 0), scheduled())

        pay(saved.cardId, 3_695_00)
        plan()
        assertTrue(scheduled().isEmpty())
    }

    @Test
    fun aNewStatementReschedules() = runTest {
        val first = statement(due = "2026-10-20", statementDate = "2026-09-28")
        plan()
        assertEquals(names(first.statementId, 3, 1, 0), scheduled())

        val second = statement(due = "2026-11-18", statementDate = "2026-10-28", total = 2_000_00)
        plan()
        assertEquals(names(second.statementId, 3, 1, 0), scheduled())
    }

    @Test
    fun switchingACardOffCancelsItsReminders() = runTest {
        val saved = statement()
        plan()
        reminders.setCardEnabled(saved.cardId, false)
        plan()
        assertTrue(scheduled().isEmpty())

        reminders.setCardEnabled(saved.cardId, true)
        plan()
        assertEquals(names(saved.statementId, 3, 1, 0), scheduled())
    }

    @Test
    fun turningRemindersOffCancelsThemAll() = runTest {
        statement()
        plan()
        reminders.setEnabled(false)
        plan()
        assertTrue(scheduled().isEmpty())
    }

    @Test
    fun withNotificationsRefusedNothingIsScheduled() = runTest {
        statement()
        notificationsAllowed = false
        plan()
        assertTrue(scheduled().isEmpty())
        // The Home card still shows the bill.
        assertEquals(
            8_695_00L,
            cards.observeNextDue(LocalDate.parse("2026-10-15"), zone).first()
                ?.statement?.statement?.totalPaise
        )
    }
}
