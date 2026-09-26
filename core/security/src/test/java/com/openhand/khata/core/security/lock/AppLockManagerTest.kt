package com.openhand.khata.core.security.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockManagerTest {
    private val settings = LockSettings(FakeLockStore())
    private var clock = 0L
    private val lock = AppLockManager(settings) { clock }

    @Test
    fun startsLocked() {
        assertTrue(lock.isLocked.value)
        lock.unlock()
        assertFalse(lock.isLocked.value)
    }

    @Test
    fun locksAgainOnlyAfterTheTimeout() {
        settings.timeout = LockTimeout.MINUTE_1
        lock.unlock()

        lock.onBackground()
        clock += 59_999
        lock.onForeground()
        assertFalse(lock.isLocked.value)

        lock.onBackground()
        clock += 60_000
        lock.onForeground()
        assertTrue(lock.isLocked.value)
    }

    @Test
    fun immediatelyMeansAnyTripToTheBackground() {
        settings.timeout = LockTimeout.IMMEDIATELY
        lock.unlock()

        lock.onBackground()
        lock.onForeground()

        assertTrue(lock.isLocked.value)
    }

    @Test
    fun theSystemUnlockScreenDoesNotReLock() {
        settings.timeout = LockTimeout.IMMEDIATELY
        lock.authenticating = true
        lock.onBackground()
        clock += 10_000
        lock.onForeground()
        lock.authenticating = false
        lock.unlock()

        assertFalse(lock.isLocked.value)
    }

    @Test
    fun defaultsAreThePhoneLockAndOneMinute() {
        assertTrue(settings.method == LockMethod.DEVICE)
        assertTrue(settings.timeout == LockTimeout.MINUTE_1)
        assertFalse(settings.continueWithoutDeviceLock)
    }
}
