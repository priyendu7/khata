package com.openhand.khata.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.CustomParser
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SubScreen

/** Settings > Parsers (PRD feature 8). */
@Composable
fun ParsersScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    viewModel: ParsersViewModel = hiltViewModel()
) {
    val custom by viewModel.custom.collectAsStateWithLifecycle()
    val builtIn by viewModel.builtIn.collectAsStateWithLifecycle()
    ParsersContent(
        onBack = onBack,
        onAdd = onAdd,
        custom = custom,
        builtIn = builtIn,
        onEnabled = viewModel::setEnabled,
        onDelete = viewModel::delete
    )
}

@Composable
fun ParsersContent(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    custom: List<CustomParser>?,
    builtIn: List<BuiltInBank>,
    onEnabled: (CustomParser, Boolean) -> Unit,
    onDelete: (CustomParser) -> Unit
) {
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    SubScreen(
        title = stringResource(R.string.parsers_title),
        onBack = onBack,
        onAdd = onAdd,
        addLabel = stringResource(R.string.parser_add_title)
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    stringResource(R.string.parsers_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            item { Header(stringResource(R.string.parsers_custom_header)) }
            if (custom?.isEmpty() == true) {
                item {
                    Text(
                        stringResource(R.string.parsers_custom_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            items(custom.orEmpty(), key = { it.id }) { parser ->
                CustomRow(parser, onEnabled = { onEnabled(parser, it) }) { deleting = parser.id }
            }
            item {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Header(stringResource(R.string.parsers_builtin_header))
            }
            items(builtIn, key = { "builtin-" + it.bank }) { bank ->
                ListItem(
                    headlineContent = { Text(bank.bank) },
                    supportingContent = {
                        Text(
                            pluralStringResource(
                                R.plurals.parsers_builtin_rules,
                                bank.rules,
                                bank.rules,
                                bank.senders.joinToString(", ")
                            )
                        )
                    }
                )
            }
        }
    }
    custom?.firstOrNull { it.id == deleting }?.let { parser ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.parsers_delete_title, parser.ruleId)) },
            text = { Text(stringResource(R.string.parsers_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(parser)
                    deleting = null
                }) { Text(stringResource(UiR.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(UiR.string.cancel))
                }
            }
        )
    }
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
private fun CustomRow(parser: CustomParser, onEnabled: (Boolean) -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(parser.bank) },
        supportingContent = {
            Text(
                if (parser.enabled) {
                    parser.ruleId
                } else {
                    stringResource(R.string.parsers_rule_off, parser.ruleId)
                }
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = parser.enabled, onCheckedChange = null)
                IconButton(onClick = onDelete) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(
                            R.string.parsers_delete_label,
                            parser.ruleId
                        )
                    )
                }
            }
        },
        modifier = Modifier.toggleable(
            value = parser.enabled,
            role = Role.Switch,
            onValueChange = onEnabled
        )
    )
}
