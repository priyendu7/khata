package com.openhand.khata.feature.insights

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openhand.khata.core.data.CardReminderRepository
import com.openhand.khata.core.data.CardRepository
import com.openhand.khata.core.model.CardReminder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * Posts one due date reminder (#156) when its time comes. It looks again first: the bill may have
 * been paid, or reminders turned off, since it was scheduled.
 */
class CardDueWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    /** Hilt can't construct workers without a custom factory, so the worker asks for these. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun cardRepository(): CardRepository

        fun cardReminders(): CardReminderRepository
    }

    override suspend fun doWork(): Result {
        val statementId = inputData.getLong(STATEMENT_ID, 0)
        val daysBefore = inputData.getInt(DAYS_BEFORE, 0)
        val key = CardReminder.key(statementId, daysBefore)
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val reminders = deps.cardReminders()
        if (key in reminders.posted()) return Result.success()
        val zone = ZoneId.systemDefault()
        val summary = deps.cardRepository().observeCards(LocalDate.now(zone), zone).first()
            .firstOrNull { it.latest?.statement?.id == statementId }
        val bill = summary?.latest
        if (bill != null && !bill.status.isPaid && reminders.settings.value.isOn(summary.card.id)) {
            CardDueNotifier(applicationContext).show(summary.card, bill, daysBefore)
            reminders.markPosted(key)
        }
        return Result.success()
    }

    companion object {
        const val STATEMENT_ID = "statement_id"
        const val DAYS_BEFORE = "days_before"
    }
}
