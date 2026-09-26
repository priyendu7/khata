package com.openhand.khata.feature.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.security.lock.LockMethod

/**
 * Wraps the whole app. While locked, [content] isn't composed at all, so nothing from the database
 * is on screen; its saved state (e.g. the selected tab) is kept for when it comes back.
 */
@Composable
fun LockGate(viewModel: LockViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val locked by viewModel.isLocked.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val holder = rememberSaveableStateHolder()

    // The screen lock may have been added or removed while we were away.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    SecureWindowEffect(state.blockScreenshots)

    val noDeviceLock = state.method == LockMethod.DEVICE && !state.deviceSecure
    when {
        !locked || !state.enabled -> holder.SaveableStateProvider("app") { content() }
        state.method == LockMethod.PIN -> PinLockScreen(viewModel)
        noDeviceLock -> NoScreenLockScreen(viewModel)
        else -> DeviceLockScreen(viewModel)
    }
}
