package com.openhand.khata.feature.settings

import androidx.lifecycle.ViewModel
import com.openhand.khata.core.data.BackupReminderRepository
import com.openhand.khata.core.model.ReminderInterval
import com.openhand.khata.core.model.ReminderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The backup reminder setting; the app reschedules the daily check whenever it changes. */
@HiltViewModel
class BackupReminderViewModel @Inject constructor(private val reminders: BackupReminderRepository) :
    ViewModel() {
    val settings: StateFlow<ReminderSettings> = reminders.settings

    fun setEnabled(enabled: Boolean) = reminders.setEnabled(enabled)

    fun setInterval(interval: ReminderInterval) = reminders.setInterval(interval)
}
