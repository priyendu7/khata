package com.openhand.khata.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.ui.R as UiR

/**
 * The type filter's checklist (#164): one or more of Spent, Received, Refund and Transfer.
 * "All types" clears the ticks, and Done hands back the picked ones (empty means any).
 */
@Composable
internal fun TypeDialog(
    picked: Set<Direction>,
    onDone: (Set<Direction>) -> Unit,
    onDismiss: () -> Unit
) {
    var ticked by rememberSaveable { mutableStateOf(picked.toList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_type)) },
        text = {
            Column {
                CheckRow(stringResource(R.string.filter_any_type), ticked.isEmpty()) {
                    ticked = emptyList()
                }
                Direction.entries.forEach { direction ->
                    CheckRow(stringResource(typeName(direction)), direction in ticked) {
                        ticked = if (it) ticked + direction else ticked - direction
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(ticked.toSet()) }) {
                Text(stringResource(R.string.filter_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.cancel)) }
        }
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onToggle)
            .padding(vertical = 8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label)
    }
}
