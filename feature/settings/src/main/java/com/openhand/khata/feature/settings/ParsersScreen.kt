package com.openhand.khata.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.SubScreen

/** Settings > Parsers (PRD feature 8). */
@Composable
fun ParsersScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ruleId: String, builtIn: Boolean) -> Unit,
    viewModel: ParsersViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    ParsersContent(
        onBack = onBack,
        onAdd = onAdd,
        rows = rows,
        actions = RuleActions(
            onEnabled = viewModel::setEnabled,
            onEdit = { onEdit(it.rule.id, it.builtIn) },
            onCopy = { row ->
                context.copyToClipboard(row.code)
                val copied = resources.getString(R.string.parsers_copied, row.rule.id)
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            },
            onDelete = viewModel::delete,
            onReset = viewModel::reset,
            onKeepMine = viewModel::keepMine
        )
    )
}

/** What can be done to a rule from its row. Reset is also Use new version. */
class RuleActions(
    val onEnabled: (RuleRow, Boolean) -> Unit = { _, _ -> },
    val onEdit: (RuleRow) -> Unit = {},
    val onCopy: (RuleRow) -> Unit = {},
    val onDelete: (RuleRow) -> Unit = {},
    val onReset: (RuleRow) -> Unit = {},
    val onKeepMine: (RuleRow) -> Unit = {}
)

/** A question before an edit or a custom rule is thrown away. */
private enum class Confirm { DELETE, RESET, USE_NEW }

@Composable
fun ParsersContent(onBack: () -> Unit, onAdd: () -> Unit, rows: ParserRows?, actions: RuleActions) {
    var confirming by remember { mutableStateOf<Pair<Confirm, RuleRow>?>(null) }
    val rowActions = RuleActions(
        onEnabled = actions.onEnabled,
        onEdit = actions.onEdit,
        onCopy = actions.onCopy,
        onDelete = { confirming = Confirm.DELETE to it },
        // Use new version and Reset both drop the edit; only the question differs.
        onReset = { row ->
            confirming = (if (row.updateAvailable) Confirm.USE_NEW else Confirm.RESET) to row
        },
        onKeepMine = actions.onKeepMine
    )
    SubScreen(
        title = stringResource(R.string.parsers_title),
        onBack = onBack,
        onAdd = onAdd,
        addLabel = stringResource(R.string.parser_add_title)
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = Segments.ListPadding) {
            item {
                Text(
                    stringResource(R.string.parsers_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            item { Header(stringResource(R.string.parsers_custom_header)) }
            if (rows?.custom?.isEmpty() == true) {
                item { Note(stringResource(R.string.parsers_custom_empty)) }
            }
            val custom = rows?.custom.orEmpty()
            itemsIndexed(custom, key = { _, row -> "custom-" + row.rule.id }) { index, row ->
                RuleListItem(row, index, custom.size, rowActions)
            }
            item {
                Header(stringResource(R.string.parsers_builtin_header))
                Note(stringResource(R.string.parsers_builtin_note))
            }
            rows?.builtIn.orEmpty().groupBy { it.rule.bank }.forEach { (bank, bankRows) ->
                item(key = "bank-$bank") { BankHeader(bank) }
                itemsIndexed(bankRows, key = { _, row -> "builtin-" + row.rule.id }) { index, row ->
                    RuleListItem(row, index, bankRows.size, rowActions)
                }
            }
        }
    }
    confirming?.let { (kind, row) ->
        ConfirmDialog(
            kind,
            row.rule.id,
            onConfirm = {
                if (kind == Confirm.DELETE) actions.onDelete(row) else actions.onReset(row)
                confirming = null
            },
            onDismiss = { confirming = null }
        )
    }
}

@Composable
private fun ConfirmDialog(
    kind: Confirm,
    ruleId: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, body, button) = when (kind) {
        Confirm.DELETE -> Triple(
            R.string.parsers_delete_title,
            R.string.parsers_delete_body,
            UiR.string.delete
        )
        Confirm.RESET -> Triple(
            R.string.parsers_reset_title,
            R.string.parsers_reset_body,
            R.string.parsers_menu_reset
        )
        Confirm.USE_NEW -> Triple(
            R.string.parsers_use_new_title,
            R.string.parsers_use_new_body,
            R.string.parsers_menu_use_new
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title, ruleId)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(button)) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.cancel)) }
        }
    )
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun BankHeader(bank: String) {
    Text(
        bank,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
