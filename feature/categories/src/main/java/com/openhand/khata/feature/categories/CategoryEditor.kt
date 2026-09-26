package com.openhand.khata.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.ui.CategoryBadge
import com.openhand.khata.core.ui.CategoryColors
import com.openhand.khata.core.ui.CategoryIcons
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.categoryName

/** Full-screen editor: name, colour, icon, and archive (not for Uncategorized). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryEditor(
    category: Category,
    onSave: (Category) -> Unit,
    onArchive: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(category.name.orEmpty()) }
    var color by rememberSaveable { mutableIntStateOf(category.color) }
    var icon by rememberSaveable { mutableStateOf(category.icon) }
    val isNew = category.id == 0L
    val isDefault = category.seedKey != null
    val defaultName = if (isDefault) categoryName(null, category.seedKey) else null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CategoryBadge(icon, color, size = 56.dp)
                    Text(
                        stringResource(
                            if (isNew) R.string.categories_add else R.string.categories_edit
                        ),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.categories_name)) },
                    // A default category with no name of its own shows its translated name.
                    placeholder = defaultName?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isDefault && name.isNotBlank()) {
                    TextButton(onClick = {
                        name = ""
                    }) {
                        Text(
                            stringResource(
                                R.string.categories_use_default_name,
                                defaultName.orEmpty()
                            )
                        )
                    }
                }

                Text(
                    stringResource(R.string.categories_colour),
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryColors.forEach { option ->
                        ColourSwatch(
                            option,
                            selected =
                            option == color
                        ) { color = option }
                    }
                }

                Text(
                    stringResource(R.string.categories_icon),
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryIcons.all.keys.forEach { key ->
                        val selected = key == icon
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .border(
                                    if (selected) 3.dp else 0.dp,
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                                .semantics { this.selected = selected }
                                .clickable { icon = key }
                        ) { CategoryBadge(key, color, size = 44.dp) }
                    }
                }

                Button(
                    enabled = name.isNotBlank() || isDefault,
                    onClick = { onSave(category.copy(name = name, color = color, icon = icon)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(UiR.string.save)) }
                ArchiveSection(category, onArchive)
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(UiR.string.cancel))
                }
            }
        }
    }
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

@Composable
private fun ColourSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.categories_colour)
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .clickable(onClick = onClick)
    )
}
