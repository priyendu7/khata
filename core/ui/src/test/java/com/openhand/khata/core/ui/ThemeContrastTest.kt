package com.openhand.khata.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG AA on both surfaces (the background and the cards), in both modes. */
class ThemeContrastTest {
    @Test
    fun cardsSitAboveTheBackground() {
        assertTrue(KhataDark.surfaceContainerLowest.luminance() > KhataDark.background.luminance())
        assertTrue(
            KhataLight.surfaceContainerLowest.luminance() > KhataLight.background.luminance()
        )
    }

    @Test
    fun darkIsDarkAndLightIsLight() {
        assertTrue(KhataDark.background.luminance() < HALF)
        assertTrue(KhataLight.background.luminance() > HALF)
    }

    @Test
    fun khataColoursMeetContrast() {
        assertContrast(KhataDark, "Khata dark")
        assertContrast(KhataLight, "Khata light")
    }

    @Test
    fun accentButtonsAreReadable() {
        listOf(KhataDark, KhataLight).forEach {
            assertAtLeast(TEXT, it.onPrimary, it.primary, "button")
        }
    }

    private fun assertContrast(scheme: ColorScheme, name: String) {
        listOf(scheme.background, scheme.surfaceContainerLowest).forEach { surface ->
            // Text, including links and amounts drawn in the accent.
            listOf(scheme.onSurface, scheme.onSurfaceVariant, scheme.primary, scheme.error)
                .forEach { assertAtLeast(TEXT, it, surface, name) }
            // Icons, outlines and dividers that carry meaning.
            assertAtLeast(GRAPHICS, scheme.outline, surface, name)
        }
    }

    private fun assertAtLeast(minimum: Float, color: Color, on: Color, name: String) {
        val ratio = contrast(color, on)
        assertTrue("$name: $color on $on is $ratio, below $minimum", ratio >= minimum)
    }

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + FLARE) / (dark + FLARE)
    }

    private companion object {
        const val TEXT = 4.5f
        const val GRAPHICS = 3f
        const val FLARE = 0.05f
        const val HALF = 0.5f
    }
}
