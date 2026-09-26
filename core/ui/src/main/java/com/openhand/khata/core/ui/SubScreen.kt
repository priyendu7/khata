package com.openhand.khata.core.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

/**
 * A screen opened from another one: title bar with a back arrow and optional [actions], and an
 * optional "add" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAdd: (() -> Unit)? = null,
    addLabel: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = actions
            )
        },
        floatingActionButton = {
            if (onAdd != null) {
                FloatingActionButton(onClick = onAdd) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = addLabel)
                }
            }
        },
        content = content
    )
}
