package com.openhand.khata.core.security.lock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app is locked right now. It starts locked (every cold start asks to unlock) and locks
 * again when it returns from the background after [LockSettings.timeout].
 *
 * [elapsedRealtime] is a monotonic clock, so changing the phone's time can't skip the lock.
 */
class AppLockManager(private val settings: LockSettings, private val elapsedRealtime: () -> Long) {
    private val locked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = locked.asStateFlow()

    private var backgroundedAt: Long? = null

    /**
     * True while the system's unlock UI is showing. On older Android versions the phone-PIN screen
     * is a separate activity, which briefly sends the app to the background; that mustn't re-lock.
     */
    @Volatile var authenticating = false

    fun onBackground() {
        if (!authenticating) backgroundedAt = elapsedRealtime()
    }

    fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (elapsedRealtime() - since >= settings.timeout.millis) lock()
    }

    fun unlock() {
        locked.value = false
    }

    fun lock() {
        locked.value = true
    }
}
