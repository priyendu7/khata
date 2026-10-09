package com.openhand.khata.feature.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.data.RenameResult
import com.openhand.khata.core.model.Tag
import com.openhand.khata.core.ui.EmptyState
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.SubScreen

private enum class TagAction { MENU, RENAME, MERGE, DELETE }

/** Tags with how often each is used; tap one to rename, merge or delete it. */
@Composable
fun TagsScreen(onBack: () -> Unit, viewModel: TagsViewModel = hiltViewModel()) {
    val all by viewModel.all.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var action by rememberSaveable { mutableStateOf(TagAction.MENU) }
    // Set when a rename hits an existing name: offer to merge into that tag instead.
    var mergeSuggestionId by rememberSaveable { mutableStateOf<Long?>(null) }

    SubScreen(title = stringResource(R.string.tags_title), onBack = onBack) { padding ->
        val tags = all
        when {
            tags == null -> Unit
            tags.isEmpty() -> EmptyState(
                icon = painterResource(UiR.drawable.ic_ledger),
                title = stringResource(R.string.tags_empty_title),
                body = stringResource(R.string.tags_empty_body),
                modifier = Modifier.padding(padding)
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = Segments.ListPadding
            ) {
                itemsIndexed(tags, key = { _, row -> row.id }) { index, tag ->
                    SegmentListItem(
                        index = index,
                        count = tags.size,
                        headlineContent = { Text(tag.name) },
                        supportingContent = {
                            Text(
                                pluralStringResource(
                                    R.plurals.tags_usage,
                                    tag.usageCount,
                                    tag.usageCount
                                )
                            )
                        },
                        modifier = Modifier.clickable {
                            selectedId = tag.id
                            action = TagAction.MENU
                        }
                    )
                }
            }
        }
    }

    val tags = all.orEmpty()
    val tag = tags.firstOrNull { it.id == selectedId } ?: return
    val close = {
        selectedId = null
        mergeSuggestionId = null
    }
    val suggestion = tags.firstOrNull { it.id == mergeSuggestionId }
    when {
        suggestion != null -> ConfirmDialog(
            title = stringResource(R.string.tags_name_taken_title, suggestion.name),
            body = stringResource(R.string.tags_merge_body, tag.name, suggestion.name),
            confirm = stringResource(R.string.tags_merge),
            onConfirm = {
                viewModel.merge(tag, suggestion)
                close()
            },
            onDismiss = close
        )
        action == TagAction.MENU -> AlertDialog(
            onDismissRequest = close,
            title = { Text(tag.name) },
            text = {
                Column {
                    MenuItem(stringResource(R.string.tags_rename)) { action = TagAction.RENAME }
                    if (tags.size >
                        1
                    ) {
                        MenuItem(stringResource(R.string.tags_merge_into)) {
                            action =
                                TagAction.MERGE
                        }
                    }
                    MenuItem(stringResource(UiR.string.delete)) { action = TagAction.DELETE }
                }
            },
            confirmButton = {
                TextButton(onClick = close) { Text(stringResource(UiR.string.cancel)) }
            }
        )
        action == TagAction.RENAME -> RenameDialog(
            tag = tag,
            onRename = { newName ->
                viewModel.rename(tag, newName) { result ->
                    when (result) {
                        is RenameResult.NameTaken -> mergeSuggestionId = result.existing.id
                        else -> close()
                    }
                }
            },
            onDismiss = close
        )
        action == TagAction.MERGE -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.tags_merge_title, tag.name)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(tags.filter { it.id != tag.id }, key = { it.id }) { other ->
                        MenuItem(other.name) {
                            viewModel.merge(tag, other)
                            close()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = close) { Text(stringResource(UiR.string.cancel)) }
            }
        )
        action == TagAction.DELETE -> ConfirmDialog(
            title = stringResource(R.string.tags_delete_title, tag.name),
            body = pluralStringResource(R.plurals.tags_delete_body, tag.usageCount, tag.usageCount),
            confirm = stringResource(UiR.string.delete),
            onConfirm = {
                viewModel.delete(tag)
                close()
            },
            onDismiss = close
        )
    }
}

@Composable
private fun RenameDialog(tag: Tag, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(tag.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_rename)) },
        text = {
            OutlinedTextField(value = name, onValueChange = {
                name = it
            }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onRename(name)
            }) { Text(stringResource(UiR.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.cancel)) }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.cancel)) }
        }
    )
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}
