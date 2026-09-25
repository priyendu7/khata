package com.bahikhata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Smoke test that keeps the unit-test pipeline exercised until feature work adds real tests
// (see CONTRIBUTING.md "Testing expectations").
class BuildConfigTest {
    @Test
    fun applicationIdMatchesNamespace() {
        assertEquals("com.bahikhata", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun versionNameIsSet() {
        assertTrue(BuildConfig.VERSION_NAME.isNotBlank())
    }
}
