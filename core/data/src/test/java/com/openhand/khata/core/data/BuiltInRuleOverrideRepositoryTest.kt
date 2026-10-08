package com.openhand.khata.core.data

import com.openhand.khata.core.model.BuiltInRuleOverride
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuiltInRuleOverrideRepositoryTest : RepositoryTest() {
    private val overrides by lazy { BuiltInRuleOverrideRepository(lazyDb) }

    @Test
    fun switchingOffAndOnLeavesNothingStored() = runTest {
        overrides.setEnabled("kotak-upi-sent", false)
        assertEquals(
            listOf(BuiltInRuleOverride("kotak-upi-sent", false, null, null)),
            overrides.getAll()
        )

        overrides.setEnabled("kotak-upi-sent", true)

        assertEquals(emptyList<Any>(), overrides.observeAll().first())
    }

    @Test
    fun anEditKeepsTheSwitchAndResetKeepsItToo() = runTest {
        overrides.setEnabled("kotak-upi-sent", false)

        overrides.saveEdit("kotak-upi-sent", "khata1:edited", "hash1")
        assertEquals(
            BuiltInRuleOverride("kotak-upi-sent", false, "khata1:edited", "hash1"),
            overrides.getAll().single()
        )

        overrides.clearEdit("kotak-upi-sent")
        assertEquals(
            BuiltInRuleOverride("kotak-upi-sent", false, null, null),
            overrides.getAll().single()
        )
    }

    @Test
    fun resettingAnEditOfARuleThatIsOnLeavesNothingStored() = runTest {
        overrides.saveEdit("kotak-upi-sent", "khata1:edited", "hash1")

        overrides.clearEdit("kotak-upi-sent")

        assertEquals(emptyList<Any>(), overrides.getAll())
    }

    @Test
    fun keepMineStoresTheNewHash() = runTest {
        overrides.saveEdit("kotak-upi-sent", "khata1:edited", "hash1")

        overrides.keepEdit("kotak-upi-sent", "hash2")

        assertEquals(
            BuiltInRuleOverride("kotak-upi-sent", true, "khata1:edited", "hash2"),
            overrides.getAll().single()
        )
    }
}
