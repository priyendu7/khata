package com.openhand.khata.feature.insights

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.openhand.khata.core.data.CardReminderRepository
import com.openhand.khata.core.data.CardRepository
import com.openhand.khata.core.data.CardSummary
import com.openhand.khata.core.model.CardReminder
import com.openhand.khata.core.model.CardReminderSettings
import com.openhand.khata.core.model.CardReminders
import com.openhand.khata.core.model.DueBill
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps credit card due date reminders (#156) scheduled with WorkManager, one job per reminder.
 * It plans again whenever a statement or payment is saved or a setting changes, and on app start
 * (so after an update too). With reminders off or notifications not allowed it schedules nothing;
 * the Home card is the reminder then.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CardDueScheduler internal constructor(
    private val reminders: CardReminderRepository,
    private val cards: CardRepository,
    private val workManager: dagger.Lazy<WorkManager>,
    private val notifier: CardDueNotifier,
    private val canNotify: () -> Boolean,
    /** Read afresh each time, so a new time zone is followed. */
    private val clock: () -> Clock
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        reminders: CardReminderRepository,
        cards: CardRepository,
        workManager: dagger.Lazy<WorkManager>
    ) : this(
        reminders,
        cards,
        workManager,
        CardDueNotifier(context),
        { CardDueNotifier.canNotify(context) },
        Clock::systemDefaultZone
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notificationsAllowed = MutableStateFlow(canNotify())

    /** Call once from `Application.onCreate`; follows settings and bills from then on. */
    fun start() {
        scope.launch { plans().collect(::apply) }
    }

    /** Notifications may have been allowed or blocked in the phone's settings since. */
    fun recheckNotifications() {
        notificationsAllowed.value = canNotify()
    }

    /** Plans once, for when something outside the app changed, like the time zone. */
    suspend fun refresh() {
        recheckNotifications()
        apply(plans().first())
    }

    /** The reminders to schedule, planned again as settings, permission and bills change. */
    internal fun plans(): Flow<Plan> = combine(reminders.settings, notificationsAllowed, ::Pair)
        .flatMapLatest { (settings, allowed) ->
            // Off: the database isn't even opened.
            if (!settings.enabled || !allowed) return@flatMapLatest flowOf(Plan())
            val clock = clock()
            cards.observeCards(LocalDate.now(clock), clock.zone).map { plan(it, settings, clock) }
        }
        .distinctUntilChanged()

    internal fun apply(plan: Plan) {
        val work = workManager.get()
        work.cancelAllWorkByTag(TAG)
        plan.reminders.forEach { reminder ->
            val request = OneTimeWorkRequestBuilder<CardDueWorker>()
                .setInitialDelay(
                    (reminder.at - clock().millis()).coerceAtLeast(0),
                    TimeUnit.MILLISECONDS
                )
                .setInputData(
                    workDataOf(
                        CardDueWorker.STATEMENT_ID to reminder.statementId,
                        CardDueWorker.DAYS_BEFORE to reminder.daysBefore
                    )
                )
                .addTag(TAG)
                // Its own tag too, so each reminder can be found.
                .addTag("$TAG:${reminder.key}")
                .build()
            work.enqueueUniqueWork("$TAG:${reminder.key}", ExistingWorkPolicy.REPLACE, request)
        }
        plan.quietCards.forEach(notifier::cancel)
    }

    private fun plan(cards: List<CardSummary>, settings: CardReminderSettings, clock: Clock): Plan {
        val bills = cards.mapNotNull { summary ->
            summary.latest?.let {
                DueBill(
                    cardId = summary.card.id,
                    statementId = it.statement.id,
                    dueDate = it.statement.dueDate,
                    paidInFull = it.status.isPaid
                )
            }
        }
        reminders.keepPostedFor(bills.mapTo(mutableSetOf()) { it.statementId })
        return Plan(
            reminders = CardReminders.plan(
                bills,
                settings,
                reminders.posted(),
                clock.millis(),
                clock.zone
            ),
            quietCards = bills.filter { it.paidInFull || !settings.isOn(it.cardId) }
                .mapTo(mutableSetOf()) { it.cardId }
        )
    }

    /** Reminders to schedule, and cards whose posted reminder should come down. */
    internal data class Plan(
        val reminders: List<CardReminder> = emptyList(),
        val quietCards: Set<Long> = emptySet()
    )

    companion object {
        const val TAG = "card_due"
    }
}
