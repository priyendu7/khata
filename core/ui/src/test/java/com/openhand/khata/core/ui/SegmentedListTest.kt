package com.openhand.khata.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SegmentedListTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun darkListIsBlackOnNearBlack() = checkColours(dark = true)

    @Test
    fun lightListIsWhiteOnGrey() = checkColours(dark = false)

    @Test
    fun itemsStayClickable() {
        compose.setContent {
            var clicked by remember { mutableStateOf("") }
            KhataTheme(darkTheme = true, dynamicColor = false) {
                Column {
                    ROWS.forEachIndexed { index, row ->
                        SegmentListItem(
                            index = index,
                            count = ROWS.size,
                            headlineContent = { Text(row) },
                            modifier = Modifier.clickable { clicked = row }
                        )
                    }
                    Text("clicked:$clicked")
                }
            }
        }
        compose.onNodeWithText("Second").assertHasClickAction().performClick()
        compose.onNodeWithText("clicked:Second").assertExists()
    }

    /** Down the left of the list: three runs of the card colour, with the background between. */
    private fun checkColours(dark: Boolean) {
        var background = Color.Unspecified
        var card = Color.Unspecified
        compose.setContent {
            KhataTheme(darkTheme = dark, dynamicColor = false) {
                background = MaterialTheme.colorScheme.background
                card = MaterialTheme.colorScheme.surfaceContainerLowest
                Column(
                    Modifier
                        .testTag(LIST)
                        .background(background)
                        .padding(horizontal = Segments.Inset)
                ) {
                    ROWS.forEachIndexed { index, row ->
                        SegmentListItem(index, ROWS.size, headlineContent = { Text(row) })
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag(LIST).captureToImage().toPixelMap()
        // Inside the items, left of their text.
        val x = with(compose.density) { (Segments.Inset + INSIDE).roundToPx() }
        val runs = (0 until pixels.height).map { pixels[x, it] }
            .filter { it == card || it == background }
            .fold(emptyList<Color>()) { seen, color ->
                if (seen.lastOrNull() == color) seen else seen + color
            }
        assertEquals(ROWS.size, runs.count { it == card })
        assertEquals(
            listOf(card, background, card, background, card),
            runs.dropWhile {
                it != card
            }.dropLastWhile {
                it !=
                    card
            }
        )
        assertEquals(background, pixels[1, pixels.height / 2])
    }

    private companion object {
        const val LIST = "list"
        val INSIDE = 8.dp
        val ROWS = listOf("First", "Second", "Third")
    }
}
