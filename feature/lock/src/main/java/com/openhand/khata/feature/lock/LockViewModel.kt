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
    val method: LockMethod,
    val timeout: LockTimeout,
    val deviceSecure: Boolean,
    val continueWithoutDeviceLock: Boolean,
    /** Result of the last PIN or recovery-code check, for the error line. */
    val lastCheck: CheckResult? = null,
    val checking: Boolean = false
)

/** One instance per activity: shared by the lock gate and the lock section of Settings. */
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

    private fun currentState() = LockUiState(
        method = settings.method,
        timeout = settings.timeout,
        deviceSecure = context.getSystemService(KeyguardManager::class.java).isDeviceSecure,
        continueWithoutDeviceLock = settings.continueWithoutDeviceLock
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

    fun continueWithoutDeviceLock() {
        settings.continueWithoutDeviceLock = true
        refresh()
        unlock()
    }
}
