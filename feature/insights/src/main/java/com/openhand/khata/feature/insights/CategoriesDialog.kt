package com.openhand.khata.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.ui.CategoryBadge
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.categoryName

/**
 * Categories with checkboxes for the monthly chart (#139); "All" clears them, and Done hands back
 * the picked ids. Archived categories come after the active ones: old months may still use them.
 */
@Composable
internal fun CategoriesDialog(
    categories: List<Category>,
    picked: Set<Long>,
    onDone: (Set<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var ticked by rememberSaveable { mutableStateOf(picked.toList()) }
    val ordered = categories.sortedWith(compareBy({ it.archived }, { it.isUncategorized }))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.insights_categories)) },
        text = {
            LazyColumn {
                item {
                    CheckRow(
                        label = stringResource(R.string.insights_categories_all_option),
                        checked = ticked.isEmpty(),
                        onToggle = { ticked = emptyList() }
                    )
                }
                items(ordered, key = { it.id }) { category ->
                    CheckRow(
                        label = categoryName(category.name, category.seedKey),
                        checked = category.id in ticked,
                        onToggle = {
                            ticked = if (it) ticked + category.id else ticked - category.id
                        }
                    ) { CategoryBadge(category.icon, category.color, size = BADGE_SIZE) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(ticked.toSet()) }) {
                Text(stringResource(R.string.insights_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.cancel)) }
        }
    )
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onToggle)
            .padding(vertical = 8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        leading?.invoke()
        Text(label)
    }
}

private val BADGE_SIZE = 32.dp
