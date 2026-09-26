package com.openhand.khata.core.security.lock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** How the user unlocks the app (PRD privacy principle 4). */
enum class LockMethod(val stored: String) {
    /** The phone's own fingerprint, face, PIN or pattern, through BiometricPrompt. The default. */
    DEVICE("device"),

    /** A PIN just for this app, with a one-time recovery code. */
    PIN("pin")
}

/** How long the app may stay in the background before it locks again. */
enum class LockTimeout(val stored: String, val millis: Long) {
    IMMEDIATELY("immediately", 0),
    SECONDS_30("30s", 30_000),
    MINUTE_1("1m", 60_000),
    MINUTES_5("5m", 300_000)
}

class LockSettings(private val store: LockStore) {
    private val version = MutableStateFlow(0L)

    /**
     * Changes whenever any setting is saved. Every screen that shows or applies lock settings
     * observes it, so e.g. the "Block screenshots" switch reaches the window no matter which
     * screen (or ViewModel instance) changed it.
     */
    val changes: StateFlow<Long> = version.asStateFlow()

    private fun changed(block: LockStoreEditor.() -> Unit) {
        store.edit(block)
        version.update { it + 1 }
    }

    /** Ask to unlock when Khata opens. On by default; the user can turn it off in Settings. */
    var enabled: Boolean
        get() = store.getString(ENABLED) != "false"
        set(value) = changed { putString(ENABLED, if (value) null else "false") }

    /** Hide the app in recent apps and block screenshots and screen recording. On by default. */
    var blockScreenshots: Boolean
        get() = store.getString(BLOCK_SCREENSHOTS) != "false"
        set(value) = changed { putString(BLOCK_SCREENSHOTS, if (value) null else "false") }

    var method: LockMethod
        get() = LockMethod.entries.firstOrNull { it.stored == store.getString(METHOD) }
            ?: LockMethod.DEVICE
        set(value) = changed { putString(METHOD, value.stored) }

    var timeout: LockTimeout
        get() = LockTimeout.entries.firstOrNull { it.stored == store.getString(TIMEOUT) }
            ?: LockTimeout.MINUTE_1
        set(value) = changed { putString(TIMEOUT, value.stored) }

    private companion object {
        const val METHOD = "method"
        const val TIMEOUT = "timeout"
        const val ENABLED = "enabled"
        const val BLOCK_SCREENSHOTS = "block_screenshots"
    }
}
