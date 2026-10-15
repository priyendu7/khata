package com.openhand.khata.feature.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.openhand.khata.core.model.CardCycles
import com.openhand.khata.core.ui.Choice
import com.openhand.khata.core.ui.ChoiceDialog
import com.openhand.khata.core.ui.SectionHeader
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.core.ui.Segments

/** The card's billing day (#154) and its due date reminders (#156). */
@Composable
internal fun CardSettings(state: CreditCardState, actions: CreditCardActions) {
    Column(Modifier.padding(horizontal = Segments.Inset)) {
        SectionHeader(stringResource(R.string.card_settings))
        BillingDaySetting(state.summary.card.billingDay, actions.onSetBillingDay)
        ReminderSetting(state, actions.onSetReminders)
    }
}

/**
 * This card's due date reminders. With reminders off for every card, the switch can't be used
 * and says where to turn them on.
 */
@Composable
private fun ReminderSetting(state: CreditCardState, onSet: (Boolean) -> Unit) {
    val settings = state.reminders
    val on = state.summary.card.id !in settings.offCards
    SegmentListItem(
        index = 1,
        count = CARD_SETTINGS,
        headlineContent = { Text(stringResource(R.string.card_reminder_setting)) },
        supportingContent = {
            Text(
                if (settings.enabled) {
                    stringResource(R.string.card_reminder_card_help)
                } else {
                    stringResource(R.string.card_reminder_all_off)
                },
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = { Switch(checked = on && settings.enabled, onCheckedChange = null) },
        modifier = Modifier.toggleable(
            value = on,
            enabled = settings.enabled,
            role = Role.Switch,
            onValueChange = onSet
        )
    )
}

/** The card's billing day (#154), picked from 1–31 or none. */
@Composable
private fun BillingDaySetting(billingDay: Int?, onSet: (Int?) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    SegmentListItem(
        index = 0,
        count = CARD_SETTINGS,
        headlineContent = { Text(stringResource(R.string.card_billing_day)) },
        supportingContent = {
            Column {
                Text(billingDayLabel(billingDay))
                Text(
                    stringResource(R.string.card_billing_day_help),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        modifier = Modifier.clickable { choosing = true }
    )
    if (choosing) {
        ChoiceDialog(
            title = stringResource(R.string.card_billing_day),
            choices = listOf(Choice<Int?>(null, billingDayLabel(null))) +
                (CardCycles.FIRST_BILLING_DAY..CardCycles.LAST_BILLING_DAY).map {
                    Choice(it, billingDayLabel(it))
                },
            selected = billingDay,
            onSelect = {
                onSet(it)
                choosing = false
            },
            onDismiss = { choosing = false }
        )
    }
}

@Composable
private fun billingDayLabel(day: Int?): String = day?.let {
    stringResource(R.string.card_billing_day_value, it)
} ?: stringResource(R.string.card_billing_day_none)

/** Rows under the card's settings: the billing day and reminders. */
private const val CARD_SETTINGS = 2
