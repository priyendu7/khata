package com.openhand.khata.feature.lock

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.security.lock.LockMethod
import com.openhand.khata.core.security.lock.LockTimeout
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.RowChevron
import com.openhand.khata.core.ui.RowIcon
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.core.ui.Tone

/** The Security rows of Settings: App lock, which opens [LockSettingsPage], and Block screenshots. */
@Composable
fun LockSettingsRows(onOpenLock: () -> Unit, viewModel: LockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LockSettingsRows(state, onOpenLock, viewModel::setBlockScreenshots)
}

@Composable
fun LockSettingsRows(
    state: LockUiState,
    onOpenLock: () -> Unit,
    onBlockScreenshots: (Boolean) -> Unit
) {
    SegmentListItem(
        index = 0,
        count = 2,
        leadingContent = { RowIcon(UiR.drawable.ic_lock, Tone.INFO) },
        headlineContent = {
            Text(stringResource(R.string.settings_app_lock), fontWeight = FontWeight.SemiBold)
        },
        supportingContent = { Text(lockSummary(state)) },
        trailingContent = { RowChevron() },
        modifier = Modifier.clickable(onClick = onOpenLock)
    )
    SwitchRow(
        title = stringResource(R.string.settings_block_screenshots),
        summary = stringResource(R.string.settings_block_screenshots_summary),
        checked = state.blockScreenshots,
        onCheckedChange = onBlockScreenshots,
        index = 1,
        count = 2,
        icon = UiR.drawable.ic_shield
    )
}

/** "Off", or the unlock method and the timeout. */
@Composable
private fun lockSummary(state: LockUiState): String = when {
    !state.enabled -> stringResource(R.string.settings_lock_off)
    state.method == LockMethod.DEVICE && !state.deviceSecure ->
        stringResource(R.string.settings_lock_method_device_none)
    else -> stringResource(
        R.string.settings_lock_summary,
        lockMethodText(state),
        stringResource(state.timeout.label())
    )
}

@Composable
internal fun lockMethodText(state: LockUiState) = when {
    state.method == LockMethod.PIN -> stringResource(R.string.settings_lock_method_pin)
    !state.deviceSecure -> stringResource(R.string.settings_lock_method_device_none)
    else -> stringResource(R.string.settings_lock_method_device)
}

@Composable
internal fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
    /** Drawn as a tile before the title, as on the Settings tab. */
    @DrawableRes icon: Int? = null
) {
    SegmentListItem(
        index = index,
        count = count,
        leadingContent = icon?.let { { RowIcon(it, Tone.INFO) } },
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange
        )
    )
}

internal fun LockTimeout.label() = when (this) {
    LockTimeout.IMMEDIATELY -> R.string.timeout_immediately
    LockTimeout.SECONDS_30 -> R.string.timeout_30s
    LockTimeout.MINUTE_1 -> R.string.timeout_1m
    LockTimeout.MINUTES_5 -> R.string.timeout_5m
}
