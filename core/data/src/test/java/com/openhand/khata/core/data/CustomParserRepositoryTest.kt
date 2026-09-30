package com.openhand.khata.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomParserRepositoryTest : RepositoryTest() {
    private val parsers by lazy { CustomParserRepository(lazyDb) }

    @Test
    fun keepsRulesNewestFirstAndSwitchedOn() = runTest {
        parsers.save("hdfc-upi", "HDFC", "khata1:a", now = 1)
        parsers.save("icici-card", "ICICI", "khata1:b", now = 2)

        val all = parsers.observeAll().first()

        assertEquals(listOf("icici-card", "hdfc-upi"), all.map { it.ruleId })
        assertEquals(listOf(true, true), all.map { it.enabled })
        assertEquals(listOf("khata1:b", "khata1:a"), parsers.enabledCodes())
    }

    @Test
    fun aDisabledRuleIsLeftOutOfTheEnabledCodes() = runTest {
        parsers.save("hdfc-upi", "HDFC", "khata1:a", now = 1)
        parsers.save("icici-card", "ICICI", "khata1:b", now = 2)
        val icici = parsers.observeAll().first().first { it.ruleId == "icici-card" }

        parsers.setEnabled(icici.id, false)

        assertEquals(listOf("khata1:a"), parsers.enabledCodes())
        parsers.setEnabled(icici.id, true)
        assertEquals(listOf("khata1:b", "khata1:a"), parsers.enabledCodes())
    }

    @Test
    fun savingTheSameRuleIdReplacesIt() = runTest {
        parsers.save("hdfc-upi", "HDFC", "khata1:old", now = 1)
        val old = parsers.observeAll().first().single()
        parsers.setEnabled(old.id, false)

        parsers.save("hdfc-upi", "HDFC Bank", "khata1:new", now = 5)

        val saved = parsers.observeAll().first().single()
        assertEquals(old.id, saved.id)
        assertEquals("HDFC Bank", saved.bank)
        assertEquals("khata1:new", saved.code)
        // Pasting it again is a clear sign it should be used.
        assertEquals(true, saved.enabled)
    }

    @Test
    fun deleteRemovesIt() = runTest {
        parsers.save("hdfc-upi", "HDFC", "khata1:a", now = 1)

        parsers.delete(parsers.observeAll().first().single().id)

        assertEquals(emptyList<Any>(), parsers.observeAll().first())
        assertEquals(emptyList<String>(), parsers.enabledCodes())
    }
}
