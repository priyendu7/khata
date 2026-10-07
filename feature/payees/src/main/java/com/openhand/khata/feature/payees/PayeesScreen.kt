package com.openhand.khata.feature.payees

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Payee
import com.openhand.khata.core.ui.CategoryBadge
import com.openhand.khata.core.ui.EmptyState
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.core.ui.categoryName
import com.openhand.khata.core.ui.defaultCategoryNames

/** Every saved payee with its defaults; tap one to edit it or merge it into another. */
@Composable
fun PayeesScreen(onBack: () -> Unit, viewModel: PayeesViewModel = hiltViewModel()) {
    val all by viewModel.all.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val suggestions by viewModel.tagSuggestions.collectAsStateWithLifecycle()
    val defaultNames = defaultCategoryNames()
    PayeesContent(
        payees = all,
        categories = categories,
        tagSuggestions = suggestions,
        onTagQueryChange = viewModel::onTagQueryChange,
        onSave = viewModel::save,
        onAddCategory = { category, onAdded ->
            viewModel.addCategory(category, defaultNames, onAdded)
        },
        onMerge = viewModel::merge,
        onBack = onBack
    )
}

private enum class PayeeAction { MENU, EDIT, MERGE, CONFIRM_MERGE }

/** The screen without its ViewModel, so UI tests can drive it directly. */
@Composable
internal fun PayeesContent(
    payees: List<Payee>?,
    categories: List<Category>,
    tagSuggestions: List<String>,
    onTagQueryChange: (String) -> Unit,
    onSave: (Payee) -> Unit,
    onAddCategory: (Category, onAdded: (Category) -> Unit) -> Unit,
    onMerge: (from: Payee, into: Payee) -> Unit,
    onBack: () -> Unit
) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var action by rememberSaveable { mutableStateOf(PayeeAction.MENU) }
    var mergeIntoId by rememberSaveable { mutableStateOf<Long?>(null) }

    SubScreen(title = stringResource(R.string.payees_title), onBack = onBack) { padding ->
        when {
            payees == null -> Unit
            payees.isEmpty() -> EmptyState(
                icon = painterResource(UiR.drawable.ic_ledger),
                title = stringResource(R.string.payees_empty_title),
                body = stringResource(R.string.payees_empty_body),
                modifier = Modifier.padding(padding)
            )
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(payees, key = { it.id }) { payee ->
                    PayeeRow(payee, categories.firstOrNull { it.id == payee.defaultCategoryId }) {
                        selectedId = payee.id
                        action = PayeeAction.MENU
                    }
                }
            }
        }
    }

    val list = payees.orEmpty()
    val payee = list.firstOrNull { it.id == selectedId } ?: return
    val close = {
        selectedId = null
        mergeIntoId = null
    }
    val into = list.firstOrNull { it.id == mergeIntoId }
    when (action) {
        PayeeAction.MENU -> AlertDialog(
            onDismissRequest = close,
            title = { Text(payee.displayName) },
            text = {
                Column {
                    MenuItem(stringResource(R.string.payees_edit)) { action = PayeeAction.EDIT }
                    if (list.size > 1) {
                        MenuItem(stringResource(R.string.payees_merge_into)) {
                            action = PayeeAction.MERGE
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = close) { Text(stringResource(UiR.string.cancel)) }
            }
        )
        PayeeAction.EDIT -> PayeeEditor(
            payee = payee,
            categories = categories,
            tagSuggestions = tagSuggestions,
            onTagQueryChange = onTagQueryChange,
            onAddCategory = onAddCategory,
            onSave = {
                onSave(it)
                close()
            },
            onDismiss = close
        )
        PayeeAction.MERGE -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.payees_merge_title, payee.displayName)) },
            text = {
                LazyColumn(Modifier.heightIn(max = MERGE_LIST_MAX_HEIGHT.dp)) {
                    items(list.filter { it.id != payee.id }, key = { it.id }) { other ->
                        MenuItem(other.displayName) {
                            mergeIntoId = other.id
                            action = PayeeAction.CONFIRM_MERGE
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = close) { Text(stringResource(UiR.string.cancel)) }
            }
        )
        PayeeAction.CONFIRM_MERGE -> if (into != null) {
            AlertDialog(
                onDismissRequest = close,
                title = {
                    Text(
                        stringResource(
                            R.string.payees_merge_confirm_title,
                            payee.displayName,
                            into.displayName
                        )
                    )
                },
                text = {
                    Text(
                        pluralStringResource(
                            R.plurals.payees_merge_body,
                            payee.transactionCount,
                            payee.transactionCount,
                            payee.displayName,
                            into.displayName
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onMerge(payee, into)
                        close()
                    }) { Text(stringResource(R.string.payees_merge)) }
                },
                dismissButton = {
                    TextButton(onClick = close) { Text(stringResource(UiR.string.cancel)) }
                }
            )
        }
    }
}

/**
 * Name, the identifier when it differs, the defaults (or "Own account"), and how many
 * transactions it has.
 */
@Composable
private fun PayeeRow(payee: Payee, category: Category?, onClick: () -> Unit) {
    val defaults = listOfNotNull(
        stringResource(R.string.payees_own_account_label).takeIf { payee.ownAccount },
        category?.let { categoryName(it.name, it.seedKey) }
            ?: stringResource(R.string.payees_no_default_category),
        payee.defaultTags.takeIf { it.isNotEmpty() }?.joinToString(", ")
    ).joinToString(" · ")
    ListItem(
        leadingContent = category?.let { { CategoryBadge(it.icon, it.color) } },
        headlineContent = { Text(payee.displayName) },
        supportingContent = {
            Column {
                if (!payee.identifier.equals(payee.displayName, ignoreCase = true)) {
                    Text(payee.identifier)
                }
                Text(defaults)
                Text(
                    pluralStringResource(
                        R.plurals.payees_usage,
                        payee.transactionCount,
                        payee.transactionCount
                    )
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}

private const val MERGE_LIST_MAX_HEIGHT = 400
