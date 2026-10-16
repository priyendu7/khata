package com.openhand.khata.feature.transactions

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.openhand.khata.core.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Type filter's chip label and checklist (#164). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TypeFilterTest {
    @get:Rule val compose = createComposeRule()

    private fun label(directions: Set<Direction>): String {
        var shown = ""
        compose.setContent { shown = typeLabel(directions) }
        compose.waitForIdle()
        return shown
    }

    @Test
    fun noTypeSaysType() = assertEquals("Type", label(emptySet()))

    @Test
    fun oneTypeShowsItsName() = assertEquals("Received", label(setOf(Direction.CREDIT)))

    @Test
    fun twoTypesShowBothInOrder() =
        assertEquals("Spent, Refund", label(setOf(Direction.REFUND, Direction.DEBIT)))

    @Test
    fun moreTypesShowACount() = assertEquals(
        "3 types",
        label(setOf(Direction.DEBIT, Direction.CREDIT, Direction.TRANSFER))
    )

    @Test
    @Config(qualifiers = "hi")
    fun showsHindi() = assertEquals("आय", label(setOf(Direction.CREDIT)))

    @Test
    fun ticksTypesAndClearsThem() {
        val picked = mutableListOf<Set<Direction>>()
        compose.setContent {
            TypeDialog(picked = setOf(Direction.CREDIT), onDone = { picked += it }, onDismiss = {})
        }

        compose.onNodeWithText("Received").assertIsOn()
        compose.onNodeWithText("All types").assertIsOff()
        compose.onNodeWithText("Spent").performClick()
        compose.onNodeWithText("Done").performClick()

        compose.onNodeWithText("All types").performClick()
        compose.onNodeWithText("Received").assertIsOff()
        compose.onNodeWithText("Done").performClick()

        assertEquals(listOf(setOf(Direction.DEBIT, Direction.CREDIT), emptySet()), picked)
    }
}
