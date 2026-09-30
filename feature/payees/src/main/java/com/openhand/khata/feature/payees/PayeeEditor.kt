package com.openhand.khata.feature.payees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Payee
import com.openhand.khata.core.ui.CategoryBadge
import com.openhand.khata.core.ui.Choice
import com.openhand.khata.core.ui.ChoiceDialog
import com.openhand.khata.core.ui.PickerField
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.core.ui.TagInput
import com.openhand.khata.core.ui.categoryName

private val TagsSaver = Saver<List<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it }
)

/**
 * Full-screen editor for a payee's display name, defaults and whether it's one of the user's own
 * accounts. The identifier is shown but can't change: it's how the payee is recognised.
 */
@Composable
internal fun PayeeEditor(
    payee: Payee,
    categories: List<Category>,
    tagSuggestions: List<String>,
    onTagQueryChange: (String) -> Unit,
    onSave: (Payee) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(payee.displayName) }
    var categoryId by rememberSaveable { mutableStateOf(payee.defaultCategoryId) }
    var tags by rememberSaveable(stateSaver = TagsSaver) { mutableStateOf(payee.defaultTags) }
    var ownAccount by rememberSaveable { mutableStateOf(payee.ownAccount) }
    var picking by rememberSaveable { mutableStateOf(false) }
    // Uncategorized isn't a default: with no default, transactions keep whatever they have.
    val choices = categories.filter {
        !it.isUncategorized && (!it.archived || it.id == categoryId)
    }
    val category = choices.firstOrNull { it.id == categoryId }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        SubScreen(
            title = stringResource(R.string.payees_edit),
            onBack = onDismiss,
            actions = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSave(
                            payee.copy(
                                displayName = name,
                                defaultCategoryId = categoryId,
                                defaultTags = tags,
                                ownAccount = ownAccount
                            )
                        )
                    }
                ) { Text(stringResource(UiR.string.save)) }
            }
        ) { padding ->
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
                Text(
                    stringResource(R.string.payees_identifier, payee.identifier),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.payees_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                PickerField(
                    label = stringResource(R.string.payees_default_category),
                    value = category?.let { categoryName(it.name, it.seedKey) }
                        ?: stringResource(R.string.payees_no_default_category),
                    onClick = { picking = true },
                    leading = category?.let { { CategoryBadge(it.icon, it.color, size = 32.dp) } }
                )
                TagInput(
                    tags = tags,
                    label = stringResource(R.string.payees_default_tags),
                    suggestions = tagSuggestions.filterNot { s ->
                        tags.any { it.equals(s, ignoreCase = true) }
                    },
                    onQueryChange = onTagQueryChange,
                    onAdd = { new ->
                        if (tags.none { it.equals(new, ignoreCase = true) }) tags = tags + new
                    },
                    onRemove = { tags = tags - it }
                )
                Text(
                    stringResource(R.string.payees_defaults_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OwnAccountSwitch(ownAccount) { ownAccount = it }
            }
        }
    }
    if (picking) {
        val close = { picking = false }
        ChoiceDialog(
            title = stringResource(R.string.payees_default_category),
            choices = listOf(
                Choice<Long?>(null, stringResource(R.string.payees_no_default_category))
            ) + choices.map {
                Choice<Long?>(
                    value = it.id,
                    label = categoryName(it.name, it.seedKey),
                    leading = { CategoryBadge(it.icon, it.color, size = 32.dp) }
                )
            },
            selected = categoryId,
            onSelect = {
                categoryId = it
                close()
            },
            onDismiss = close
        )
    }
}

/** "This is my own account": payments to or from it become transfers. */
@Composable
private fun OwnAccountSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.payees_own_account))
            Text(
                stringResource(R.string.payees_own_account_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
