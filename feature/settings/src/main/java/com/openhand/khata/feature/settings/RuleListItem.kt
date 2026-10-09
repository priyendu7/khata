package com.openhand.khata.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.sms.parser.ParserRule

// A rule's row in Settings > Parsers, with its switch and menu.

/** A rule: its id, bank and senders, what it reads, its state, a switch and a menu. */
@Composable
internal fun RuleListItem(row: RuleRow, index: Int, count: Int, actions: RuleActions) {
    val rule = row.rule
    SegmentListItem(
        index = index,
        count = count,
        headlineContent = { Text(rule.id) },
        supportingContent = {
            Column {
                Text(rule.bank + " · " + rule.senders.joinToString(", "))
                Text(reads(rule))
                ruleState(row)?.let {
                    Text(
                        stringResource(it),
                        color = if (row.updateAvailable) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = row.enabled, onCheckedChange = null)
                RuleMenu(row, actions)
            }
        },
        modifier = Modifier.toggleable(
            value = row.enabled,
            role = Role.Switch,
            onValueChange = { actions.onEnabled(row, it) }
        )
    )
}

/** What a rule reads, such as "Expense · Bank account". */
@Composable
private fun reads(rule: ParserRule): String {
    val directions = rule.direction?.let(::listOf) ?: rule.directionWords?.keys.orEmpty().toList()
    return (
        directions.map { stringResource(directionLabel(it.direction)) } +
            stringResource(accountTypeLabel(rule.accountType))
        ).joinToString(" · ")
}

/** Off, Edited, or Updated version available; null for a rule as shipped and on. */
private fun ruleState(row: RuleRow): Int? = when {
    row.updateAvailable -> R.string.parsers_state_update
    !row.enabled -> R.string.parsers_state_off
    row.edited -> R.string.parsers_state_edited
    else -> null
}

@Composable
private fun RuleMenu(row: RuleRow, actions: RuleActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.parsers_menu_label, row.rule.id)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menuItems(row, actions).forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        action(row)
                    }
                )
            }
        }
    }
}

/** The menu's items in order, as labels and actions. */
private fun menuItems(row: RuleRow, actions: RuleActions): List<Pair<Int, (RuleRow) -> Unit>> =
    buildList {
        if (row.updateAvailable) {
            add(R.string.parsers_menu_use_new to actions.onReset)
            add(R.string.parsers_menu_keep_mine to actions.onKeepMine)
        }
        add(R.string.parsers_menu_edit to actions.onEdit)
        add(R.string.parsers_menu_copy to actions.onCopy)
        if (row.edited && !row.updateAvailable) add(R.string.parsers_menu_reset to actions.onReset)
        if (!row.builtIn) add(UiR.string.delete to actions.onDelete)
    }
