package com.openhand.khata.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Tags on the transaction as removable chips, a field to add more (Enter, a comma or the + button
 * adds), and existing tags to pick from. New names become new tags when the form is saved.
 * Settings > Parsers uses it for sender IDs too, with its own [placeholder] and [addLabel].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagInput(
    tags: List<String>,
    label: String,
    suggestions: List<String>,
    onQueryChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.field_tags_hint),
    addLabel: String = stringResource(R.string.tag_add)
) {
    var text by rememberSaveable { mutableStateOf("") }
    fun add(name: String) {
        if (name.isNotBlank()) onAdd(name.trim())
        text = ""
        onQueryChange("")
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.tag_remove, tag),
                                modifier = Modifier.size(InputChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                if (new.endsWith(',')) {
                    add(new.dropLast(1))
                } else {
                    text = new
                    onQueryChange(new)
                }
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            // Keeps the keyboard open so several tags can be added in a row.
            keyboardActions = KeyboardActions(onDone = { add(text) }),
            trailingIcon = {
                if (text.isNotBlank()) {
                    IconButton(onClick = { add(text) }) {
                        Icon(
                            painterResource(R.drawable.ic_add),
                            contentDescription = addLabel
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { suggestion ->
                    SuggestionChip(onClick = { add(suggestion) }, label = { Text(suggestion) })
                }
            }
        }
    }
}
