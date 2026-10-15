package com.openhand.khata.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.segmentCardColors
import com.openhand.khata.sms.parser.DateFormats
import com.openhand.khata.sms.parser.RuleKind
import com.openhand.khata.sms.parser.RuleMaker

// The rule maker's marking: one field at a time, then a chip per field to change one.

/** One field: what to tap, the SMS, and Back, Skip and Next. */
@Composable
internal fun MarkStep(form: MakerForm, step: RuleMaker.Field, onForm: (MakerForm) -> Unit) {
    Text(
        stringResource(R.string.parser_step_count, form.fields.indexOf(step) + 1, form.fields.size),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val guess = form.isGuess(step)
    Text(
        stringResource(if (guess) guessPrompt(step) else stepPrompt(step)),
        style = MaterialTheme.typography.titleMedium
    )
    ParserHint(stringResource(if (guess) R.string.parser_guess_hint else R.string.parser_step_hint))
    SmsWords(form, onTap = { onForm(form.tapInStep(it)) })
    form.marks.firstOrNull { it.field == step }?.let { mark ->
        Text(
            stringResource(
                R.string.parser_field,
                stringResource(fieldLabel(step)),
                form.markedText(mark)
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        if (step.isDate) DateFormatChoice(form, mark, onForm)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (step != form.fields.first()) {
            TextButton(onClick = { onForm(form.back()) }) { Text(stringResource(UiR.string.back)) }
        }
        Spacer(Modifier.weight(1f))
        if (form.canSkip) {
            TextButton(onClick = { onForm(form.skip()) }) {
                Text(stringResource(R.string.parser_step_skip))
            }
        }
        Button(onClick = { onForm(form.next()) }, enabled = form.canGoOn) {
            Text(
                stringResource(if (guess) R.string.parser_guess_yes else R.string.parser_step_next)
            )
        }
    }
    if (form.canConfirmAll) {
        OutlinedButton(
            onClick = { onForm(form.confirmAll()) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.parser_guess_all)) }
    }
}

/**
 * What kind of SMS this is: a transaction, or a card statement. Changing it guesses the fields
 * again and starts marking over. Not offered when re-marking a rule, whose kind stays.
 */
@Composable
internal fun KindChoice(form: MakerForm, onForm: (MakerForm) -> Unit) {
    if (form.fixedId != null) return
    Text(stringResource(R.string.parser_make_kind), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            RuleKind.TRANSACTION to R.string.parser_kind_transaction,
            RuleKind.STATEMENT to R.string.parser_kind_statement
        ).forEach { (kind, label) ->
            FilterChip(
                selected = form.kind == kind,
                onClick = { onForm(form.withKind(kind)) },
                label = { Text(stringResource(label)) }
            )
        }
    }
}

/** A chip per field, saying what's marked; a tap opens that step again. */
@Composable
internal fun MarkSummary(form: MakerForm, onForm: (MakerForm) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        form.fields.forEach { field ->
            val mark = form.marks.firstOrNull { it.field == field }
            val label = stringResource(fieldLabel(field))
            FilterChip(
                selected = mark != null,
                onClick = { onForm(form.revisit(field)) },
                label = {
                    Text(
                        if (mark != null) {
                            stringResource(R.string.parser_field, label, form.markedText(mark))
                        } else {
                            stringResource(R.string.parser_step_skipped, label)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = fieldColor(field).copy(alpha = MARK_ALPHA)
                )
            )
        }
    }
}

/**
 * The SMS as words, each marked one in its field's colour; the field being marked is solid.
 * [onTap] is null once marking is done. Words marked as another field can't be tapped.
 */
@Composable
internal fun SmsWords(form: MakerForm, onTap: ((Int) -> Unit)?) {
    Card(Modifier.fillMaxWidth(), colors = segmentCardColors()) {
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            form.words.forEachIndexed { index, word ->
                val field = form.fieldAt(index)
                val current = field != null && field == form.step
                val tappable = onTap != null && (field == null || current)
                Surface(
                    color = when {
                        current -> fieldColor(field)
                        field != null -> fieldColor(field).copy(alpha = MARK_ALPHA)
                        else -> MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (current) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .semantics { selected = current }
                        .clickable(enabled = tappable, role = Role.Button) { onTap?.invoke(index) }
                ) {
                    Text(
                        word.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/** The formats that read the marked date, to pick one, or another typed in. */
@Composable
private fun DateFormatChoice(form: MakerForm, date: RuleMaker.Mark, onForm: (MakerForm) -> Unit) {
    Text(
        stringResource(R.string.parser_make_date_format),
        style = MaterialTheme.typography.titleSmall
    )
    val offered = DateFormats.matching(form.markedText(date)).ifEmpty { DateFormats.COMMON }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        offered.forEach { format ->
            FilterChip(
                selected = form.dateFormat == format,
                onClick = { onForm(form.copy(dateFormat = format)) },
                label = { Text(format) }
            )
        }
    }
    OutlinedTextField(
        value = form.dateFormat.orEmpty(),
        onValueChange = { onForm(form.copy(dateFormat = it)) },
        label = { Text(stringResource(R.string.parser_make_date_format_other)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun stepPrompt(field: RuleMaker.Field): Int = when (field) {
    RuleMaker.Field.AMOUNT -> R.string.parser_step_amount
    RuleMaker.Field.PAYEE -> R.string.parser_step_payee
    RuleMaker.Field.ACCOUNT -> R.string.parser_step_account
    RuleMaker.Field.REF -> R.string.parser_step_reference
    RuleMaker.Field.DATE -> R.string.parser_step_date
    RuleMaker.Field.TOTAL -> R.string.parser_step_total
    RuleMaker.Field.MIN_DUE -> R.string.parser_step_min_due
    RuleMaker.Field.DUE_DATE -> R.string.parser_step_due_date
}

private fun guessPrompt(field: RuleMaker.Field): Int = when (field) {
    RuleMaker.Field.AMOUNT -> R.string.parser_guess_amount
    RuleMaker.Field.PAYEE -> R.string.parser_guess_payee
    RuleMaker.Field.ACCOUNT -> R.string.parser_guess_account
    RuleMaker.Field.REF -> R.string.parser_guess_reference
    RuleMaker.Field.DATE -> R.string.parser_guess_date
    RuleMaker.Field.TOTAL -> R.string.parser_guess_total
    RuleMaker.Field.MIN_DUE -> R.string.parser_guess_min_due
    RuleMaker.Field.DUE_DATE -> R.string.parser_guess_due_date
}

/**
 * A colour of its own for each field of a kind, readable with white text and in both themes. A
 * statement's fields borrow the transaction colours: the two kinds are never marked together.
 */
private fun fieldColor(field: RuleMaker.Field): Color = when (field) {
    RuleMaker.Field.AMOUNT, RuleMaker.Field.TOTAL -> AmountColor
    RuleMaker.Field.PAYEE -> PayeeColor
    RuleMaker.Field.ACCOUNT -> AccountColor
    RuleMaker.Field.REF, RuleMaker.Field.MIN_DUE -> ReferenceColor
    RuleMaker.Field.DATE, RuleMaker.Field.DUE_DATE -> DateColor
}

private val AmountColor = Color(0xFF2E7D32)
private val PayeeColor = Color(0xFF1565C0)
private val AccountColor = Color(0xFF6A1B9A)
private val ReferenceColor = Color(0xFFC75B00)
private val DateColor = Color(0xFF00838F)

/** Marks of fields other than the one being marked are tinted, not solid. */
private const val MARK_ALPHA = 0.35f
