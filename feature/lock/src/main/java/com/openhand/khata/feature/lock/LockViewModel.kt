package com.openhand.khata.feature.lock

import android.app.KeyguardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.security.lock.AppLockManager
import com.openhand.khata.core.security.lock.CheckResult
import com.openhand.khata.core.security.lock.LockMethod
import com.openhand.khata.core.security.lock.LockSettings
import com.openhand.khata.core.security.lock.LockTimeout
import com.openhand.khata.core.security.lock.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LockUiState(
    val enabled: Boolean,
    val blockScreenshots: Boolean,
    val method: LockMethod,
    val timeout: LockTimeout,
    val deviceSecure: Boolean,
    /** Result of the last PIN or recovery-code check, for the error line. */
    val lastCheck: CheckResult? = null,
    val checking: Boolean = false
)

/** Shared by the lock gate, the Security rows of Settings and Settings > App lock. */
@HiltViewModel
class LockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: LockSettings,
    private val pins: PinManager,
    private val appLock: AppLockManager
) : ViewModel() {
    val isLocked: StateFlow<Boolean> = appLock.isLocked

    private val _state = MutableStateFlow(currentState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    init {
        // Several instances can exist (the lock gate's and one per navigation destination); keep
        // them all in step with whatever any of them saved.
        viewModelScope.launch { settings.changes.collect { refresh() } }
    }

    private fun currentState() = LockUiState(
        enabled = settings.enabled,
        blockScreenshots = settings.blockScreenshots,
        method = settings.method,
        timeout = settings.timeout,
        deviceSecure = context.getSystemService(KeyguardManager::class.java).isDeviceSecure
    )

    /** Re-read settings and whether the phone has a screen lock (e.g. after returning from Settings). */
    fun refresh() = _state.update { currentState().copy(lastCheck = it.lastCheck) }

    fun unlock() {
        _state.update { it.copy(lastCheck = null) }
        appLock.unlock()
    }

    fun setAuthenticating(value: Boolean) {
        appLock.authenticating = value
    }

    fun checkPin(pin: String) = check { pins.checkPin(pin) }

    /** A correct recovery code doesn't unlock; the caller asks for a new PIN next. */
    fun checkRecoveryCode(code: String, onCorrect: () -> Unit) {
        runCheck({ pins.checkRecoveryCode(code) }) { onCorrect() }
    }

    private fun check(block: () -> CheckResult) = runCheck(block) { unlock() }

    private fun runCheck(block: () -> CheckResult, onCorrect: () -> Unit) {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            // PIN hashing is deliberately slow; keep it off the main thread.
            val result = withContext(Dispatchers.Default) { block() }
            _state.update {
                it.copy(
                    checking = false,
                    lastCheck = result.takeUnless { r ->
                        r ==
                            CheckResult.Correct
                    }
                )
            }
            if (result == CheckResult.Correct) onCorrect()
        }
    }

    fun clearLastCheck() = _state.update { it.copy(lastCheck = null) }

    /** Saves [pin] and returns the new recovery code to show once. Call [finishPinSetup] after. */
    suspend fun setPin(pin: String): String = withContext(Dispatchers.Default) { pins.setPin(pin) }

    /**
     * Switches to the app PIN once the user has seen the recovery code. Switching earlier would
     * swap the lock screen in before the code is shown.
     */
    fun finishPinSetup() {
        settings.method = LockMethod.PIN
        refresh()
    }

    fun useDeviceLock() {
        pins.clearPin()
        settings.method = LockMethod.DEVICE
        refresh()
    }

    fun setTimeout(timeout: LockTimeout) {
        settings.timeout = timeout
        refresh()
    }

    /** Turning the lock on doesn't lock right away; it takes effect the next time Khata opens. */
    fun setEnabled(enabled: Boolean) {
        settings.enabled = enabled
        refresh()
        if (!enabled) unlock()
    }

    fun setBlockScreenshots(block: Boolean) {
        settings.blockScreenshots = block
        refresh()
    }

    /** From the "no screen lock" screen: turn the app lock off (it can be turned on in Settings). */
    fun continueWithoutLock() = setEnabled(false)
}
