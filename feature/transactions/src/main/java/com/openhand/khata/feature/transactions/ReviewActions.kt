package com.openhand.khata.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** One filled button, then text buttons: the same on every card. */
@Composable
internal fun ActionRow(primary: Pair<Int, () -> Unit>?, others: List<Pair<Int, () -> Unit>>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        // Without a filled button first, the text buttons' own padding would indent the row.
        modifier = if (primary == null) Modifier.offset(x = -TEXT_BUTTON_INSET) else Modifier
    ) {
        primary?.let { (label, onClick) ->
            Button(onClick = onClick) { Text(stringResource(label)) }
        }
        others.forEach { (label, onClick) ->
            TextButton(onClick = onClick) { Text(stringResource(label)) }
        }
    }
}

@Composable
internal fun MoreMenu(items: List<Pair<Int, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.review_more)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (label, onClick) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        onClick()
                    }
                )
            }
        }
    }
}

private val TEXT_BUTTON_INSET = 12.dp
