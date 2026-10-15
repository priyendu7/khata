package com.openhand.khata.feature.transactions

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.dateLabel
import com.openhand.khata.core.ui.label
import com.openhand.khata.core.ui.monthLabel
import com.openhand.khata.core.ui.segment
import com.openhand.khata.core.ui.timeLabel

/** "Account · Kotak": which account it was paid from or into, under the amount. */
@Composable
internal fun AccountPill(
    form: EditorForm,
    accounts: List<Account>,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val name = accounts.firstOrNull { it.id == form.accountId }?.name
        ?: stringResource(R.string.no_account)
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(
            stringResource(R.string.field_account),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(painterResource(UiR.drawable.ic_arrow_drop_down), contentDescription = null)
    }
}

/** Payee, date and time, counts in and note, as one card of rows with icons. */
@Composable
internal fun DetailsCard(
    form: EditorForm,
    onChange: (EditorForm) -> Unit,
    show: (EditorDialog) -> Unit
) {
    Column {
        DetailRow(0, UiR.drawable.ic_person) {
            BareField(
                value = form.payee,
                onValueChange = { onChange(form.copy(payee = it)) },
                label = stringResource(R.string.field_payee),
                placeholder = stringResource(R.string.field_payee_hint),
                capitalization = KeyboardCapitalization.Words
            )
        }
        DetailRow(1, UiR.drawable.ic_calendar) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PickerText(
                    stringResource(R.string.field_date),
                    dateLabel(form.date),
                    Modifier.weight(1f)
                ) { show(EditorDialog.DATE) }
                PickerText(stringResource(R.string.field_time), timeLabel(form.time)) {
                    show(EditorDialog.TIME)
                }
            }
        }
        DetailRow(2, UiR.drawable.ic_insights) {
            PickerText(
                stringResource(R.string.field_counts_in),
                form.countsIn?.let { monthLabel(it) } ?: stringResource(R.string.counts_in_same),
                Modifier.fillMaxWidth()
            ) { show(EditorDialog.COUNTS_IN) }
        }
        DetailRow(DETAIL_ROWS - 1, UiR.drawable.ic_ledger) {
            BareField(
                value = form.note,
                onValueChange = { onChange(form.copy(note = it)) },
                label = stringResource(R.string.field_note),
                capitalization = KeyboardCapitalization.Sentences,
                singleLine = false
            )
        }
    }
}

@Composable
private fun DetailRow(index: Int, @DrawableRes icon: Int, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .segment(index, DETAIL_ROWS)
            .padding(start = 16.dp, end = 8.dp)
            .heightIn(min = 56.dp)
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

/** A label over its value, tapped to pick a new one. */
@Composable
private fun PickerText(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(Segments.SmallShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
    }
}

/** A text field with no box of its own: the card row around it is the box. */
@Composable
private fun BareField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    capitalization: KeyboardCapitalization,
    placeholder: String? = null,
    singleLine: Boolean = true
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** A section's name over its content, as the design heads Category. */
@Composable
internal fun EditorSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() }
        )
        content()
    }
}

private const val DETAIL_ROWS = 4
