package com.openhand.khata.core.security.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeTest {
    @Test
    fun codesAreLongAndUnambiguous() {
        repeat(100) {
            val code = RecoveryCode.generate()
            assertEquals(RecoveryCode.LENGTH, code.length)
            assertTrue(code.none { it in "01OILU" })
        }
    }

    @Test
    fun formatsAndNormalizes() {
        assertEquals("ABCD-EFGH-2345-6789", RecoveryCode.format("ABCDEFGH23456789"))
        assertEquals("ABCDEFGH23456789", RecoveryCode.normalize(" abcd-efgh 2345-6789 "))
    }
}
