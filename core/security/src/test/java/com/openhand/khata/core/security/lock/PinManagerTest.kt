package com.openhand.khata.core.security.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinManagerTest {
    private val store = FakeLockStore()
    private var clock = 1_000_000L

    // Few iterations keep the tests fast; production uses SecretHasher.DEFAULT_ITERATIONS.
    private val pins = PinManager(store, SecretHasher(iterations = 1_000)) { clock }

    @Test
    fun storesOnlyHashes() {
        val code = pins.setPin("482915")

        assertTrue(pins.hasPin)
        val stored = store.values.values.joinToString()
        assertFalse(stored.contains("482915"))
        assertFalse(stored.contains(code))
        assertTrue(store.getString("pin_hash")!!.startsWith("pbkdf2-sha256$1000$"))
    }

    @Test
    fun checksThePin() {
        pins.setPin("482915")

        assertEquals(CheckResult.Correct, pins.checkPin("482915"))
        assertEquals(CheckResult.Wrong(attemptsBeforeWait = 4), pins.checkPin("000000"))
    }

    @Test
    fun rejectsInvalidPins() {
        assertFalse(PinManager.isValidPin("123"))
        assertFalse(PinManager.isValidPin("12a4"))
        assertFalse(PinManager.isValidPin("1234567890123"))
        assertTrue(PinManager.isValidPin("1234"))
    }

    @Test
    fun waitsGrowAfterFiveWrongTries() {
        pins.setPin("482915")
        repeat(4) { assertTrue(pins.checkPin("000000") is CheckResult.Wrong) }

        assertEquals(CheckResult.Wait(clock + 30_000), pins.checkPin("000000"))
        // During the wait even the right PIN isn't checked.
        assertEquals(CheckResult.Wait(clock + 30_000), pins.checkPin("482915"))

        clock += 30_000
        assertEquals(CheckResult.Wait(clock + 60_000), pins.checkPin("000000"))

        clock += 60_000
        assertEquals(CheckResult.Correct, pins.checkPin("482915"))
        assertNull(pins.waitUntil())
        assertEquals(CheckResult.Wrong(attemptsBeforeWait = 4), pins.checkPin("000000"))
    }

    @Test
    fun waitsAreCappedAtAnHour() {
        assertNull(PinManager.waitAfter(4))
        assertEquals(30_000L, PinManager.waitAfter(5))
        assertEquals(120_000L, PinManager.waitAfter(7))
        assertEquals(3_600_000L, PinManager.waitAfter(50))
    }

    @Test
    fun recoveryCodeWorksUntilANewPinIsSet() {
        val code = pins.setPin("482915")
        val typed = RecoveryCode.format(code).lowercase().replace("-", " ")

        assertEquals(CheckResult.Correct, pins.checkRecoveryCode(typed))

        val newCode = pins.setPin("1357")
        assertTrue(pins.checkRecoveryCode(code) is CheckResult.Wrong)
        assertEquals(CheckResult.Correct, pins.checkRecoveryCode(newCode))
        assertEquals(CheckResult.Correct, pins.checkPin("1357"))
        assertTrue(pins.checkPin("482915") is CheckResult.Wrong)
    }

    @Test
    fun wrongRecoveryCodesCountTowardsTheWait() {
        pins.setPin("482915")
        repeat(5) { pins.checkRecoveryCode("AAAA-AAAA-AAAA-AAAA") }

        assertTrue(pins.checkPin("482915") is CheckResult.Wait)
    }

    @Test
    fun clearingRemovesEverything() {
        pins.setPin("482915")
        pins.clearPin()

        assertFalse(pins.hasPin)
        assertTrue(store.values.isEmpty())
    }
}
