package com.openhand.khata.core.security.lock

/** Result of checking a PIN or recovery code. */
sealed interface CheckResult {
    data object Correct : CheckResult

    /** Wrong; [attemptsBeforeWait] more tries before the waits start (0 once they have). */
    data class Wrong(val attemptsBeforeWait: Int) : CheckResult

    /** Too many wrong tries: nothing is checked until [untilMillis] (wall-clock). */
    data class Wait(val untilMillis: Long) : CheckResult
}

/**
 * The app-only PIN (PRD privacy principle 4). Stores only salted, slow hashes of the PIN and of a
 * one-time recovery code. Wrong PINs and wrong recovery codes share one attempt counter: after
 * [FREE_ATTEMPTS] wrong tries, each further one waits 30 seconds, doubling up to an hour.
 */
class PinManager(
    private val store: LockStore,
    private val hasher: SecretHasher = SecretHasher(),
    private val now: () -> Long = System::currentTimeMillis
) {
    val hasPin: Boolean get() = store.getString(PIN_HASH) != null

    /**
     * Sets a new PIN and returns a new recovery code to show the user once. Any earlier recovery
     * code stops working.
     */
    fun setPin(pin: String): String {
        require(isValidPin(pin)) { "PIN must be $MIN_LENGTH-$MAX_LENGTH digits" }
        val code = RecoveryCode.generate()
        val pinHash = hasher.hash(pin)
        val codeHash = hasher.hash(code)
        store.edit {
            putString(PIN_HASH, pinHash)
            putString(RECOVERY_HASH, codeHash)
            putLong(FAILURES, null)
            putLong(WAIT_UNTIL, null)
        }
        return code
    }

    fun checkPin(pin: String): CheckResult = check(PIN_HASH) { hash -> hasher.verify(pin, hash) }

    /** A correct code only proves who the user is; the caller then asks for a new PIN ([setPin]). */
    fun checkRecoveryCode(input: String): CheckResult =
        check(RECOVERY_HASH) { hash -> hasher.verify(RecoveryCode.normalize(input), hash) }

    fun clearPin() = store.edit {
        putString(PIN_HASH, null)
        putString(RECOVERY_HASH, null)
        putLong(FAILURES, null)
        putLong(WAIT_UNTIL, null)
    }

    /** When the current wait ends, or null if there's no wait. */
    fun waitUntil(): Long? = store.getLong(WAIT_UNTIL)?.takeIf { it > now() }

    private fun check(key: String, matches: (String) -> Boolean): CheckResult {
        val waitUntil = waitUntil()
        val hash = store.getString(key)
        return when {
            waitUntil != null -> CheckResult.Wait(waitUntil)
            hash != null && matches(hash) -> {
                store.edit {
                    putLong(FAILURES, null)
                    putLong(WAIT_UNTIL, null)
                }
                CheckResult.Correct
            }
            else -> recordFailure()
        }
    }

    private fun recordFailure(): CheckResult {
        val failures = (store.getLong(FAILURES) ?: 0) + 1
        val wait = waitAfter(failures)
        val until = wait?.let { now() + it }
        store.edit {
            putLong(FAILURES, failures)
            putLong(WAIT_UNTIL, until)
        }
        return if (until !=
            null
        ) {
            CheckResult.Wait(until)
        } else {
            CheckResult.Wrong((FREE_ATTEMPTS - failures).toInt())
        }
    }

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 12
        const val FREE_ATTEMPTS = 5
        private const val FIRST_WAIT_MILLIS = 30_000L
        private const val MAX_WAIT_MILLIS = 60 * 60_000L

        private const val PIN_HASH = "pin_hash"
        private const val RECOVERY_HASH = "recovery_hash"
        private const val FAILURES = "failures"
        private const val WAIT_UNTIL = "wait_until"

        fun isValidPin(pin: String) =
            pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

        /** No wait for the first [FREE_ATTEMPTS] failures; then 30 s, 1 min, 2 min, … up to 1 hour. */
        fun waitAfter(failures: Long): Long? {
            if (failures < FREE_ATTEMPTS) return null
            val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(20)
            return (FIRST_WAIT_MILLIS shl doublings.toInt()).coerceAtMost(MAX_WAIT_MILLIS)
        }
    }
}
