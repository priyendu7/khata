package com.openhand.khata.core.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
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
            if (onAdd != null) AccentFab(onClick = onAdd, label = addLabel)
        },
        content = content
    )
}

/**
 * The marigold "add" button. With [text] it's the wide one Home uses; otherwise an icon alone,
 * named by [label] for TalkBack.
 */
@Composable
fun AccentFab(onClick: () -> Unit, label: String?, text: String? = null) {
    val icon = @Composable {
        Icon(painterResource(R.drawable.ic_add), contentDescription = label.takeIf { text == null })
    }
    val container = MaterialTheme.colorScheme.primary
    val content = MaterialTheme.colorScheme.onPrimary
    if (text == null) {
        FloatingActionButton(
            onClick = onClick,
            shape = FabShape,
            containerColor = container,
            contentColor = content,
            content = icon
        )
    } else {
        ExtendedFloatingActionButton(
            text = { Text(text, style = MaterialTheme.typography.titleSmall) },
            icon = icon,
            onClick = onClick,
            shape = FabShape,
            containerColor = container,
            contentColor = content
        )
    }
}

private val FabShape = RoundedCornerShape(18.dp)
