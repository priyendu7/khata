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
    fun defaultsAreOnWithThePhoneLockAndOneMinute() {
        assertTrue(settings.enabled)
        assertTrue(settings.blockScreenshots)
        assertTrue(settings.method == LockMethod.DEVICE)
        assertTrue(settings.timeout == LockTimeout.MINUTE_1)
    }

    @Test
    fun turnedOffItStartsUnlockedAndNeverLocks() {
        settings.enabled = false
        settings.timeout = LockTimeout.IMMEDIATELY
        val off = AppLockManager(settings) { clock }
        assertFalse(off.isLocked.value)

        off.onBackground()
        clock += 3_600_000
        off.onForeground()
        off.lock()
        assertFalse(off.isLocked.value)
    }

    @Test
    fun everySavedSettingIsAnnounced() {
        val before = settings.changes.value
        settings.blockScreenshots = false
        settings.enabled = false
        settings.timeout = LockTimeout.MINUTES_5
        settings.method = LockMethod.PIN
        assertTrue(settings.changes.value == before + 4)
    }

    @Test
    fun settingsSurviveBeingReadAgain() {
        settings.enabled = false
        settings.blockScreenshots = false
        val store = FakeLockStore()
        LockSettings(store).apply {
            enabled = false
            blockScreenshots = false
        }
        assertFalse(LockSettings(store).enabled)
        assertFalse(LockSettings(store).blockScreenshots)
        LockSettings(store).enabled = true
        assertTrue(LockSettings(store).enabled)
    }
}
