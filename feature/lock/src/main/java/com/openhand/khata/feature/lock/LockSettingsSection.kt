package com.openhand.khata.feature.lock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.security.lock.LockMethod
import com.openhand.khata.core.security.lock.LockTimeout

private enum class LockDialog { NONE, METHOD, TIMEOUT, REMOVE_PIN, PIN_SETUP }

/** The app lock rows of the Settings screen. */
@Composable
fun LockSettingsSection(viewModel: LockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf(LockDialog.NONE) }

    val methodText = when {
        state.method == LockMethod.PIN -> stringResource(R.string.settings_lock_method_pin)
        !state.deviceSecure -> stringResource(R.string.settings_lock_method_device_none)
        else -> stringResource(R.string.settings_lock_method_device)
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_lock_method)) },
        supportingContent = { Text(methodText) },
        modifier = Modifier.clickable { dialog = LockDialog.METHOD }
    )
    if (state.method == LockMethod.PIN) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_change_pin)) },
            modifier = Modifier.clickable { dialog = LockDialog.PIN_SETUP }
        )
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_lock_timeout)) },
        supportingContent = { Text(stringResource(state.timeout.label())) },
        modifier = Modifier.clickable { dialog = LockDialog.TIMEOUT }
    )

    val close = { dialog = LockDialog.NONE }
    when (dialog) {
        LockDialog.METHOD -> ChoiceDialog(
            title = stringResource(R.string.settings_lock_method),
            options = listOf(
                LockMethod.DEVICE to stringResource(R.string.settings_lock_method_device),
                LockMethod.PIN to stringResource(R.string.settings_lock_method_pin)
            ),
            selected = state.method,
            onSelect = { method ->
                dialog = when {
                    method == state.method -> LockDialog.NONE
                    method == LockMethod.PIN -> LockDialog.PIN_SETUP
                    else -> LockDialog.REMOVE_PIN
                }
            },
            onDismiss = close
        )
        LockDialog.TIMEOUT -> ChoiceDialog(
            title = stringResource(R.string.settings_lock_timeout),
            options = LockTimeout.entries.map { it to stringResource(it.label()) },
            selected = state.timeout,
            onSelect = {
                viewModel.setTimeout(it)
                close()
            },
            onDismiss = close
        )
        LockDialog.REMOVE_PIN -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.settings_remove_pin_title)) },
            text = { Text(stringResource(R.string.settings_remove_pin_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.useDeviceLock()
                    close()
                }) { Text(stringResource(R.string.settings_remove_pin_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = close) { Text(stringResource(R.string.lock_cancel)) }
            }
        )
        LockDialog.PIN_SETUP -> Dialog(
            onDismissRequest = close,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false
            )
        ) {
            PinSetupFlow(viewModel, onFinished = close, onCancel = close)
        }
        LockDialog.NONE -> Unit
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = value == selected, onClick = { onSelect(value) })
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) }
        }
    )
}

private fun LockTimeout.label() = when (this) {
    LockTimeout.IMMEDIATELY -> R.string.timeout_immediately
    LockTimeout.SECONDS_30 -> R.string.timeout_30s
    LockTimeout.MINUTE_1 -> R.string.timeout_1m
    LockTimeout.MINUTES_5 -> R.string.timeout_5m
}
