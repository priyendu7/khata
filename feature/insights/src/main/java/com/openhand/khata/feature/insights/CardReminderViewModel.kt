package com.openhand.khata.feature.insights

import androidx.lifecycle.ViewModel
import com.openhand.khata.core.data.CardReminderRepository
import com.openhand.khata.core.model.CardReminderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Card due date reminder settings (#156); the scheduler plans again whenever they change. */
@HiltViewModel
class CardReminderViewModel @Inject constructor(
    private val reminders: CardReminderRepository,
    private val scheduler: CardDueScheduler
) : ViewModel() {
    val settings: StateFlow<CardReminderSettings> = reminders.settings

    fun setEnabled(enabled: Boolean) = reminders.setEnabled(enabled)

    fun setDay(days: Int, on: Boolean) = reminders.setDay(days, on)

    /** After the permission prompt, or coming back from the phone's settings. */
    fun recheckNotifications() = scheduler.recheckNotifications()
}
