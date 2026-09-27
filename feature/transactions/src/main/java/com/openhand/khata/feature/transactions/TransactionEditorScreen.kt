package com.openhand.khata.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.ui.CategoryBadge
import com.openhand.khata.core.ui.Choice
import com.openhand.khata.core.ui.ChoiceDialog
import com.openhand.khata.core.ui.PickerField
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.core.ui.TagInput
import com.openhand.khata.core.ui.categoryName
import com.openhand.khata.core.ui.focusOnAppear

/** Add (transaction id 0) or edit a transaction; [onDone] closes the screen. */
@Composable
fun TransactionEditorScreen(
    onDone: () -> Unit,
    viewModel: TransactionEditorViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val showErrors by viewModel.showErrors.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val suggestions by viewModel.tagSuggestions.collectAsStateWithLifecycle()

    LaunchedEffect(done) { if (done) onDone() }

    TransactionEditorContent(
        isNew = viewModel.isNew,
        form = form,
        showErrors = showErrors,
        categories = categories,
        accounts = accounts,
        tagSuggestions = suggestions,
        onChange = viewModel::update,
        onTagQueryChange = viewModel::onTagQueryChange,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onDone
    )
}

private enum class EditorDialog { DATE, TIME, CATEGORY, ACCOUNT, DELETE }

/** The editor without its ViewModel, so UI tests can drive it directly. */
@Composable
internal fun TransactionEditorContent(
    isNew: Boolean,
    form: EditorForm?,
    showErrors: Boolean,
    categories: List<Category>,
    accounts: List<Account>,
    tagSuggestions: List<String>,
    onChange: (EditorForm) -> Unit,
    onTagQueryChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var dialog by rememberSaveable { mutableStateOf<EditorDialog?>(null) }
    SubScreen(
        title = stringResource(
            if (isNew) R.string.transactions_add else R.string.transactions_edit
        ),
        onBack = onBack,
        actions = {
            TextButton(onClick = onSave, enabled = form != null) {
                Text(stringResource(UiR.string.save))
            }
        }
    ) { padding ->
        if (form == null) return@SubScreen
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            DirectionChips(form.direction) { onChange(form.copy(direction = it)) }
            AmountField(form, showErrors, focus = isNew) { onChange(form.copy(amount = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(
                    label = stringResource(R.string.field_date),
                    value = dateLabel(form.date),
                    onClick = { dialog = EditorDialog.DATE },
                    modifier = Modifier.weight(1f)
                )
                PickerField(
                    label = stringResource(R.string.field_time),
                    value = timeLabel(form.time),
                    onClick = { dialog = EditorDialog.TIME },
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = form.payee,
                onValueChange = { onChange(form.copy(payee = it)) },
                label = { Text(stringResource(R.string.field_payee)) },
                placeholder = { Text(stringResource(R.string.field_payee_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
            PayeeMemory(form, onChange)
            val category = categories.firstOrNull { it.id == form.categoryId }
                ?: categories.firstOrNull { it.isUncategorized }
            PickerField(
                label = stringResource(R.string.field_category),
                value = category?.let { categoryName(it.name, it.seedKey) }
                    ?: stringResource(UiR.string.category_uncategorized),
                onClick = { dialog = EditorDialog.CATEGORY },
                leading = category?.let { { CategoryBadge(it.icon, it.color, size = 32.dp) } }
            )
            PickerField(
                label = stringResource(R.string.field_account),
                value = accounts.firstOrNull { it.id == form.accountId }?.name
                    ?: stringResource(R.string.no_account),
                onClick = { dialog = EditorDialog.ACCOUNT }
            )
            TagInput(
                tags = form.tags,
                label = stringResource(R.string.field_tags),
                suggestions = tagSuggestions,
                onQueryChange = onTagQueryChange,
                onAdd = { onChange(form.withTag(it)) },
                onRemove = { onChange(form.withoutTag(it)) }
            )
            OutlinedTextField(
                value = form.note,
                onValueChange = { onChange(form.copy(note = it)) },
                label = { Text(stringResource(R.string.field_note)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (!isNew) {
                TextButton(onClick = { dialog = EditorDialog.DELETE }) {
                    Text(
                        stringResource(R.string.transaction_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        EditorDialogs(dialog, form, categories, accounts, onChange, onDelete) { dialog = null }
    }
}

@Composable
private fun EditorDialogs(
    dialog: EditorDialog?,
    form: EditorForm,
    categories: List<Category>,
    accounts: List<Account>,
    onChange: (EditorForm) -> Unit,
    onDelete: () -> Unit,
    close: () -> Unit
) {
    fun pick(new: EditorForm) {
        onChange(new)
        close()
    }
    when (dialog) {
        null -> Unit
        EditorDialog.DATE -> DateDialog(form.date, { pick(form.copy(date = it)) }, close)
        EditorDialog.TIME -> TimeDialog(form.time, { pick(form.copy(time = it)) }, close)
        EditorDialog.CATEGORY -> ChoiceDialog(
            title = stringResource(R.string.field_category),
            choices = categories.sortedBy { it.isUncategorized }.map {
                Choice<Long?>(
                    value = if (it.isUncategorized) null else it.id,
                    label = categoryName(it.name, it.seedKey),
                    leading = { CategoryBadge(it.icon, it.color, size = 32.dp) }
                )
            },
            selected = form.categoryId?.takeUnless { id ->
                categories.any { it.id == id && it.isUncategorized }
            },
            onSelect = { pick(form.copy(categoryId = it)) },
            onDismiss = close
        )
        EditorDialog.ACCOUNT -> ChoiceDialog(
            title = stringResource(R.string.field_account),
            choices = listOf(Choice<Long?>(null, stringResource(R.string.no_account))) +
                accounts.map { Choice(it.id, it.name) },
            selected = form.accountId,
            onSelect = { pick(form.copy(accountId = it)) },
            onDismiss = close
        )
        EditorDialog.DELETE -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.transaction_delete_title)) },
            text = { Text(stringResource(R.string.transaction_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    close()
                    onDelete()
                }) { Text(stringResource(UiR.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = close) { Text(stringResource(UiR.string.cancel)) }
            }
        )
    }
}

/**
 * Under the payee: says the category and tags came from a saved payee (and that changing them here
 * doesn't change it), or offers to remember them for a payee that has none yet.
 */
@Composable
private fun PayeeMemory(form: EditorForm, onChange: (EditorForm) -> Unit) {
    val known = form.knownPayee
    if (known != null && known.hasDefaults) {
        Text(
            stringResource(R.string.payee_filled_in, known.displayName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    } else if (form.canRememberPayee) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = form.rememberPayee,
                    role = Role.Checkbox,
                    onValueChange = { onChange(form.copy(rememberPayee = it)) }
                )
        ) {
            Checkbox(checked = form.rememberPayee, onCheckedChange = null)
            Text(
                stringResource(R.string.payee_remember),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectionChips(selected: Direction, onSelect: (Direction) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Direction.entries.forEach { direction ->
            FilterChip(
                selected = direction == selected,
                onClick = { onSelect(direction) },
                label = { Text(stringResource(direction.label())) }
            )
        }
    }
}

@Composable
private fun AmountField(
    form: EditorForm,
    showErrors: Boolean,
    focus: Boolean,
    onChange: (String) -> Unit
) {
    val error = form.amountError.takeIf { showErrors }
    OutlinedTextField(
        value = form.amount,
        onValueChange = { if (EditorForm.isAmountInput(it)) onChange(it) },
        label = { Text(stringResource(R.string.field_amount)) },
        prefix = { Text("₹") },
        textStyle = MaterialTheme.typography.headlineSmall,
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it.message())) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AMOUNT_FIELD_TAG)
            .then(if (focus) Modifier.focusOnAppear(Unit) else Modifier)
    )
}

internal const val AMOUNT_FIELD_TAG = "amount"

private fun AmountError.message() = when (this) {
    AmountError.EMPTY -> R.string.amount_error_empty
    AmountError.INVALID -> R.string.amount_error_invalid
    AmountError.ZERO -> R.string.amount_error_zero
}
