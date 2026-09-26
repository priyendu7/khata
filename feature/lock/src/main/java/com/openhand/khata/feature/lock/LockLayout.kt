package com.openhand.khata.feature.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Shared full-screen layout for the lock screens: icon, title, body, optional fields, actions. */
@Composable
internal fun LockLayout(
    title: String,
    body: String?,
    primaryAction: Pair<String, () -> Unit>?,
    secondaryActions: List<Pair<String, () -> Unit>> = emptyList(),
    primaryEnabled: Boolean = true,
    fields: @Composable () -> Unit = {}
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            fields()
            if (primaryAction != null) {
                Button(
                    onClick = primaryAction.second,
                    enabled = primaryEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(primaryAction.first)
                }
            }
            secondaryActions.forEach { (label, onClick) ->
                TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
    }
}
