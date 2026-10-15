package com.openhand.khata.core.data

import android.content.Context
import com.openhand.khata.core.model.CardReminderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Credit card due date reminders (#156): their settings, and which reminders have gone out. Kept
 * in app-private SharedPreferences, like the backup reminder's, so reading them at startup doesn't
 * open the database; they hold only card and statement ids, no amounts.
 */
@Singleton
class CardReminderRepository @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<CardReminderSettings> = _settings.asStateFlow()

    fun setEnabled(enabled: Boolean) = save(_settings.value.copy(enabled = enabled))

    /** Turns the reminder [days] before the due date on or off. */
    fun setDay(days: Int, on: Boolean) = _settings.value.let {
        save(it.copy(daysBefore = if (on) it.daysBefore + days else it.daysBefore - days))
    }

    fun setCardEnabled(cardId: Long, on: Boolean) = _settings.value.let {
        save(it.copy(offCards = if (on) it.offCards - cardId else it.offCards + cardId))
    }

    /** Keys of the reminders already posted (CardReminder.key). */
    fun posted(): Set<String> = prefs.getStringSet(POSTED, null).orEmpty()

    fun markPosted(key: String) {
        prefs.edit().putStringSet(POSTED, posted() + key).apply()
    }

    /** Forgets posted reminders of statements that no longer need any, so the list stays short. */
    fun keepPostedFor(statementIds: Set<Long>) {
        val kept = posted().filterTo(mutableSetOf()) {
            it.substringBefore(':').toLongOrNull() in statementIds
        }
        prefs.edit().putStringSet(POSTED, kept).apply()
    }

    private fun readSettings() = CardReminderSettings(
        enabled = prefs.getBoolean(ENABLED, true),
        daysBefore = prefs.getStringSet(DAYS, null)?.mapNotNullTo(mutableSetOf()) {
            it.toIntOrNull()
        } ?: CardReminderSettings.DEFAULT_DAYS,
        offCards = prefs.getStringSet(OFF_CARDS, null).orEmpty().mapNotNullTo(mutableSetOf()) {
            it.toLongOrNull()
        }
    )

    private fun save(settings: CardReminderSettings) {
        prefs.edit()
            .putBoolean(ENABLED, settings.enabled)
            .putStringSet(DAYS, settings.daysBefore.mapTo(mutableSetOf()) { it.toString() })
            .putStringSet(OFF_CARDS, settings.offCards.mapTo(mutableSetOf()) { it.toString() })
            .apply()
        _settings.value = settings
    }

    private companion object {
        const val PREFS = "khata_card_reminders"
        const val ENABLED = "enabled"
        const val DAYS = "days_before"
        const val OFF_CARDS = "off_cards"
        const val POSTED = "posted"
    }
}
