package com.openhand.khata.feature.csv

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.openhand.khata.core.ui.Choice
import com.openhand.khata.core.ui.ChoiceDialog
import com.openhand.khata.core.ui.PickerField

/** Which field of [ColumnMapping] a column picker sets. */
private enum class Field(@StringRes val label: Int, val required: Boolean) {
    DATE(R.string.column_date, true),
    AMOUNT(R.string.column_amount, true),
    DEBIT(R.string.column_debit, false),
    CREDIT(R.string.column_credit, false),
    DESCRIPTION(R.string.column_description, false),
    CATEGORY(R.string.column_category, false);

    fun of(mapping: ColumnMapping): Int? = when (this) {
        DATE -> mapping.date
        AMOUNT -> mapping.amount
        DEBIT -> mapping.debit
        CREDIT -> mapping.credit
        DESCRIPTION -> mapping.description
        CATEGORY -> mapping.category
    }

    fun set(mapping: ColumnMapping, column: Int?): ColumnMapping = when (this) {
        DATE -> mapping.copy(date = column)
        AMOUNT -> mapping.copy(amount = column)
        DEBIT -> mapping.copy(debit = column)
        CREDIT -> mapping.copy(credit = column)
        DESCRIPTION -> mapping.copy(description = column)
        CATEGORY -> mapping.copy(category = column)
    }
}

/** A CSV from another app: the user says which column is which, and how dates and amounts look. */
@Composable
internal fun MatchingStep(
    state: ImportState.Matching,
    onMapping: (ColumnMapping) -> Unit,
    onConfirm: () -> Unit
) {
    val mapping = state.mapping
    var picking by rememberSaveable { mutableStateOf<Field?>(null) }
    var pickingDateFormat by rememberSaveable { mutableStateOf(false) }
    var pickingAmountStyle by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.matching_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.matching_intro), style = MaterialTheme.typography.bodyMedium)

    val columnName = { column: Int? ->
        column?.let { state.header.getOrNull(it) } ?: ""
    }
    val none = stringResource(R.string.column_none)
    val fields = buildList {
        add(Field.DATE)
        if (mapping.amountStyle == AmountStyle.SEPARATE_COLUMNS) {
            add(Field.DEBIT)
            add(Field.CREDIT)
        } else {
            add(Field.AMOUNT)
        }
        add(Field.DESCRIPTION)
        add(Field.CATEGORY)
    }
    PickerField(
        label = stringResource(R.string.matching_amount_style),
        value = stringResource(amountStyleLabel(mapping.amountStyle)),
        onClick = { pickingAmountStyle = true }
    )
    fields.forEach { field ->
        PickerField(
            label = stringResource(field.label),
            value = columnName(field.of(mapping)).ifEmpty { none },
            onClick = { picking = field }
        )
        if (field == Field.DATE) {
            PickerField(
                label = stringResource(R.string.matching_date_format),
                value = mapping.dateFormat.example,
                onClick = { pickingDateFormat = true }
            )
        }
    }
    Button(
        onClick = onConfirm,
        enabled = mapping.isComplete,
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.matching_continue)) }

    picking?.let { field ->
        val choices = buildList {
            if (!field.required) add(Choice(NO_COLUMN, none))
            state.header.forEachIndexed { index, name ->
                val sample = state.sample.getOrNull(index)?.trim().orEmpty()
                val label = if (sample.isEmpty()) {
                    name
                } else {
                    stringResource(R.string.column_with_sample, name, sample)
                }
                add(Choice(index, label))
            }
        }
        ChoiceDialog(
            title = stringResource(field.label),
            choices = choices,
            selected = field.of(mapping) ?: NO_COLUMN,
            onSelect = {
                picking = null
                onMapping(field.set(mapping, it.takeIf { column -> column != NO_COLUMN }))
            },
            onDismiss = { picking = null }
        )
    }
    if (pickingDateFormat) {
        ChoiceDialog(
            title = stringResource(R.string.matching_date_format),
            choices = DateFormat.entries.map { Choice(it, it.example) },
            selected = mapping.dateFormat,
            onSelect = {
                pickingDateFormat = false
                onMapping(mapping.copy(dateFormat = it))
            },
            onDismiss = { pickingDateFormat = false }
        )
    }
    if (pickingAmountStyle) {
        ChoiceDialog(
            title = stringResource(R.string.matching_amount_style),
            choices = AmountStyle.entries.map { Choice(it, stringResource(amountStyleLabel(it))) },
            selected = mapping.amountStyle,
            onSelect = {
                pickingAmountStyle = false
                onMapping(mapping.copy(amountStyle = it))
            },
            onDismiss = { pickingAmountStyle = false }
        )
    }
}

@StringRes
private fun amountStyleLabel(style: AmountStyle): Int = when (style) {
    AmountStyle.SIGNED -> R.string.amount_style_signed
    AmountStyle.ALL_SPENDING -> R.string.amount_style_all_spending
    AmountStyle.SEPARATE_COLUMNS -> R.string.amount_style_separate
}

private const val NO_COLUMN = -1
