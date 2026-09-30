package com.openhand.khata.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnparsedSmsRepositoryTest : RepositoryTest() {
    private val unparsed by lazy { UnparsedSmsRepository(lazyDb) }
    private val review by lazy { ReviewRepository(lazyDb) }
    private val atm = "Rs.2,000 withdrawn at ATM using card XX5678."

    @Test
    fun keepsEachSmsOnceNewestFirst() = runTest {
        assertTrue(unparsed.save("JM-KOTAKB-S", atm, AT))
        // The receiver and the inbox import can both see it, seconds apart.
        assertFalse(unparsed.save("JM-KOTAKB-S", atm, AT + 5_000))
        assertTrue(unparsed.save("JM-KOTAKB-S", "Rs.500 spent on card XX5678.", AT + HOUR))

        val all = unparsed.observeAll().first()

        assertEquals(listOf("Rs.500 spent on card XX5678.", atm), all.map { it.body })
        assertEquals("JM-KOTAKB-S", all.last().sender)
        assertEquals(AT, all.last().receivedAt)
    }

    @Test
    fun theSameTextDaysApartIsKeptTwice() = runTest {
        assertTrue(unparsed.save("JM-KOTAKB-S", atm, AT))
        assertTrue(unparsed.save("JM-KOTAKB-S", atm, AT + 3 * DAY))
    }

    @Test
    fun deletingRemovesItAndTheReviewCountFollows() = runTest {
        unparsed.save("JM-KOTAKB-S", atm, AT)
        assertEquals(1, review.observeCount().first())

        unparsed.delete(unparsed.observeAll().first().single().id)

        assertEquals(emptyList<Any>(), unparsed.observeAll().first())
        assertEquals(0, review.observeCount().first())
    }

    private companion object {
        const val AT = 1_790_000_000_000L
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
