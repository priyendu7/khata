package com.openhand.khata.core.data

import com.openhand.khata.core.model.IgnoreKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IgnoreRuleRepositoryTest : RepositoryTest() {
    private val rules by lazy { IgnoreRuleRepository(lazyDb) }

    @Test
    fun keepsRulesNewestFirstSwitchedOnAndDeletes() = runTest {
        val sender = rules.add(IgnoreKind.SENDER, "HDFCBK", null, "Rs.450 spent", now = 1)
        val like = rules.add(
            IgnoreKind.TEMPLATE,
            "JIOPAY",
            "^Recharge.+$",
            "Recharge done",
            now = 2
        )

        val all = rules.observeAll().first()
        assertEquals(listOf(like, sender), all.map { it.id })
        assertEquals(listOf(IgnoreKind.TEMPLATE, IgnoreKind.SENDER), all.map { it.kind })
        assertEquals(listOf("^Recharge.+$", null), all.map { it.pattern })
        assertEquals(listOf(true, true), all.map { it.enabled })

        rules.setEnabled(like, false)
        assertEquals(listOf(sender), rules.enabled().map { it.id })

        rules.delete(sender)
        assertEquals(listOf(like), rules.observeAll().first().map { it.id })
        assertEquals(emptyList<Long>(), rules.enabled().map { it.id })
    }
}
