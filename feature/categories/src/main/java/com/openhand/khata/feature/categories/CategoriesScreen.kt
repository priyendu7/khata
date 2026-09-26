package com.openhand.khata.feature.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import com.openhand.khata.core.ui.CategoryIcons
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.core.ui.categoryName

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
        addLabel = stringResource(R.string.categories_add)
    ) { padding ->
        val categories = all.orEmpty()
        val (archived, active) = categories.partition { it.archived }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(active, key = { it.id }) { CategoryRow(it) { editingId = it.id } }
            if (archived.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.categories_archived),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                    )
                }
                items(archived, key = { it.id }) { CategoryRow(it) { editingId = it.id } }
            }
        }
    }

    if (editingId != NONE) {
        val category = all.orEmpty().firstOrNull { it.id == editingId }
            ?: Category(
                name = null,
                color = com.openhand.khata.core.ui.CategoryColors.first(),
                icon = CategoryIcons.all.keys.first()
            )
        CategoryEditor(
            category = category,
            onSave = {
                viewModel.save(it)
                editingId = NONE
            },
            onArchive = { archive ->
                viewModel.setArchived(category, archive)
                editingId = NONE
            },
            onDismiss = { editingId = NONE }
        )
    }
}

@Composable
private fun CategoryRow(category: Category, onClick: () -> Unit) {
    ListItem(
        leadingContent = { CategoryBadge(category.icon, category.color) },
        headlineContent = { Text(categoryName(category.name, category.seedKey)) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
