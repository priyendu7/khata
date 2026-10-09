package com.openhand.khata.feature.lock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.security.lock.LockMethod
import com.openhand.khata.core.security.lock.LockTimeout
import com.openhand.khata.core.ui.Choice
import com.openhand.khata.core.ui.ChoiceDialog
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.SubScreen

private enum class LockDialog { NONE, METHOD, TIMEOUT, REMOVE_PIN, PIN_SETUP, TURN_OFF }

/** Settings > App lock: on or off, the unlock method, the app PIN and the timeout. */
@Composable
fun LockSettingsPage(onBack: () -> Unit, viewModel: LockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LockSettingsContent(
        state = state,
        onBack = onBack,
        onEnabled = viewModel::setEnabled,
        onTimeout = viewModel::setTimeout,
        onUseDeviceLock = viewModel::useDeviceLock,
        pinSetup = { close -> PinSetupFlow(viewModel, onFinished = close, onCancel = close) }
    )
}

/** [pinSetup] is the PIN flow (new PIN and recovery code), given the function that closes it. */
@Composable
fun LockSettingsContent(
    state: LockUiState,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onTimeout: (LockTimeout) -> Unit,
    onUseDeviceLock: () -> Unit,
    pinSetup: @Composable (close: () -> Unit) -> Unit
) {
    var dialog by rememberSaveable { mutableStateOf(LockDialog.NONE) }
    SubScreen(stringResource(R.string.settings_app_lock), onBack) { padding ->
        // The switch, then the method, the PIN (if one is used) and the timeout once it's on.
        val rows = when {
            !state.enabled -> 1
            state.method == LockMethod.PIN -> 4
            else -> 3
        }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Segments.Inset, vertical = 8.dp)
        ) {
            SwitchRow(
                title = stringResource(R.string.settings_app_lock),
                summary = stringResource(R.string.settings_app_lock_summary),
                checked = state.enabled,
                onCheckedChange = { on ->
                    if (on) onEnabled(true) else dialog = LockDialog.TURN_OFF
                },
                index = 0,
                count = rows
            )
            if (state.enabled) {
                SegmentListItem(
                    index = 1,
                    count = rows,
                    headlineContent = { Text(stringResource(R.string.settings_lock_method)) },
                    supportingContent = { Text(lockMethodText(state)) },
                    modifier = Modifier.clickable { dialog = LockDialog.METHOD }
                )
                if (state.method == LockMethod.PIN) {
                    SegmentListItem(
                        index = 2,
                        count = rows,
                        headlineContent = { Text(stringResource(R.string.settings_change_pin)) },
                        supportingContent = {
                            Text(stringResource(R.string.settings_change_pin_note))
                        },
                        modifier = Modifier.clickable { dialog = LockDialog.PIN_SETUP }
                    )
                }
                SegmentListItem(
                    index = rows - 1,
                    count = rows,
                    headlineContent = { Text(stringResource(R.string.settings_lock_timeout)) },
                    supportingContent = { Text(stringResource(state.timeout.label())) },
                    modifier = Modifier.clickable { dialog = LockDialog.TIMEOUT }
                )
            }
        }
    }
    LockDialogs(
        dialog = dialog,
        state = state,
        onDialog = { dialog = it },
        onEnabled = onEnabled,
        onTimeout = onTimeout,
        onUseDeviceLock = onUseDeviceLock,
        pinSetup = pinSetup
    )
}

@Composable
private fun LockDialogs(
    dialog: LockDialog,
    state: LockUiState,
    onDialog: (LockDialog) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onTimeout: (LockTimeout) -> Unit,
    onUseDeviceLock: () -> Unit,
    pinSetup: @Composable (close: () -> Unit) -> Unit
) {
    val close = { onDialog(LockDialog.NONE) }
    when (dialog) {
        LockDialog.METHOD -> ChoiceDialog(
            title = stringResource(R.string.settings_lock_method),
            choices = listOf(
                Choice(LockMethod.DEVICE, stringResource(R.string.settings_lock_method_device)),
                Choice(LockMethod.PIN, stringResource(R.string.settings_lock_method_pin))
            ),
            selected = state.method,
            onSelect = { method ->
                onDialog(
                    when {
                        method == state.method -> LockDialog.NONE
                        method == LockMethod.PIN -> LockDialog.PIN_SETUP
                        else -> LockDialog.REMOVE_PIN
                    }
                )
            },
            onDismiss = close
        )
        LockDialog.TIMEOUT -> ChoiceDialog(
            title = stringResource(R.string.settings_lock_timeout),
            choices = LockTimeout.entries.map { Choice(it, stringResource(it.label())) },
            selected = state.timeout,
            onSelect = {
                onTimeout(it)
                close()
            },
            onDismiss = close
        )
        LockDialog.REMOVE_PIN -> ConfirmDialog(
            title = R.string.settings_remove_pin_title,
            body = R.string.settings_remove_pin_body,
            confirm = R.string.settings_remove_pin_confirm,
            onConfirm = {
                onUseDeviceLock()
                close()
            },
            onDismiss = close
        )
        LockDialog.PIN_SETUP -> Dialog(
            onDismissRequest = close,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false
            )
        ) { pinSetup(close) }
        LockDialog.TURN_OFF -> ConfirmDialog(
            title = R.string.settings_turn_off_lock_title,
            body = R.string.settings_turn_off_lock_body,
            confirm = R.string.settings_turn_off_lock_confirm,
            onConfirm = {
                onEnabled(false)
                close()
            },
            onDismiss = close
        )
        LockDialog.NONE -> Unit
    }
}

@Composable
private fun ConfirmDialog(
    title: Int,
    body: Int,
    confirm: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) }
        }
    )
}
