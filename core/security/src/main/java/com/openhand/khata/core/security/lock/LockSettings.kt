package com.openhand.khata.core.security.lock

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
    var method: LockMethod
        get() = LockMethod.entries.firstOrNull { it.stored == store.getString(METHOD) }
            ?: LockMethod.DEVICE
        set(value) = store.edit { putString(METHOD, value.stored) }

    var timeout: LockTimeout
        get() = LockTimeout.entries.firstOrNull { it.stored == store.getString(TIMEOUT) }
            ?: LockTimeout.MINUTE_1
        set(value) = store.edit { putString(TIMEOUT, value.stored) }

    /**
     * The user chose to continue without any lock because the phone has no screen lock. The lock
     * comes back automatically once a screen lock is set, or if they choose an app PIN.
     */
    var continueWithoutDeviceLock: Boolean
        get() = store.getString(NO_DEVICE_LOCK) == "true"
        set(value) = store.edit { putString(NO_DEVICE_LOCK, if (value) "true" else null) }

    private companion object {
        const val METHOD = "method"
        const val TIMEOUT = "timeout"
        const val NO_DEVICE_LOCK = "continue_without_device_lock"
    }
}
