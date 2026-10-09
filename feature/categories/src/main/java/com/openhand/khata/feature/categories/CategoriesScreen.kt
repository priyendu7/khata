package com.openhand.khata.feature.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.ui.CategoryBadge
import com.openhand.khata.core.ui.CategoryEditor
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.core.ui.categoryName
import com.openhand.khata.core.ui.defaultCategoryNames
import com.openhand.khata.core.ui.newCategory

private const val NEW = 0L
private const val NONE = -1L

@Composable
fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val all by viewModel.all.collectAsStateWithLifecycle()
    // Id of the category being edited: NEW for a new one, NONE when the editor is closed.
    var editingId by rememberSaveable { mutableLongStateOf(NONE) }

    SubScreen(
        title = stringResource(R.string.categories_title),
        onBack = onBack,
        onAdd = { editingId = NEW },
        addLabel = stringResource(UiR.string.categories_add)
    ) { padding ->
        val categories = all.orEmpty()
        val (archived, active) = categories.partition { it.archived }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = Segments.ListPadding) {
            itemsIndexed(active, key = { _, row -> row.id }) { index, category ->
                CategoryRow(category, index, active.size) { editingId = category.id }
            }
            if (archived.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.categories_archived),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                    )
                }
                itemsIndexed(archived, key = { _, row -> row.id }) { index, category ->
                    CategoryRow(category, index, archived.size) { editingId = category.id }
                }
            }
        }
    }

    if (editingId != NONE) {
        val category = all.orEmpty().firstOrNull { it.id == editingId }
            ?: newCategory(all.orEmpty())
        val defaultNames = defaultCategoryNames()
        CategoryEditor(
            category = category,
            onSave = {
                viewModel.save(it, defaultNames)
                editingId = NONE
            },
            onDismiss = { editingId = NONE },
            others = all.orEmpty()
        ) {
            ArchiveSection(category) { archive ->
                viewModel.setArchived(category, archive)
                editingId = NONE
            }
        }
    }
}

@Composable
private fun CategoryRow(category: Category, index: Int, count: Int, onClick: () -> Unit) {
    SegmentListItem(
        index = index,
        count = count,
        leadingContent = { CategoryBadge(category.icon, category.color) },
        headlineContent = { Text(categoryName(category.name, category.seedKey)) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** Archive or unarchive, with a note on what that means; Uncategorized only gets the note. */
@Composable
private fun ArchiveSection(category: Category, onArchive: (Boolean) -> Unit) {
    val note = when {
        category.isUncategorized -> R.string.categories_uncategorized_help
        !category.archived -> R.string.categories_archive_help
        else -> null
    }
    if (category.id != 0L && !category.isUncategorized) {
        OutlinedButton(onClick = {
            onArchive(!category.archived)
        }, modifier = Modifier.fillMaxWidth()) {
            val label = when {
                category.archived -> R.string.categories_unarchive
                else -> R.string.categories_archive
            }
            Text(stringResource(label))
        }
    }
    if (note != null && category.id != 0L) {
        Text(
            stringResource(note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
