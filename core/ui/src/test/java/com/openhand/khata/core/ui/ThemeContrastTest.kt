package com.openhand.khata.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** WCAG AA on both surfaces (the background and the cards), in both modes, for both palettes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeContrastTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun darkModeIsNearBlackBehindBlackCards() {
        val scheme = KhataDark.withKhataSurfaces(dark = true)
        assertEquals(Color(0xFF121212), scheme.background)
        assertEquals(Color.Black, scheme.surfaceContainerLowest)
    }

    @Test
    fun lightModeIsGreyBehindWhiteCards() {
        val scheme = KhataLight.withKhataSurfaces(dark = false)
        assertTrue(scheme.background.luminance() < 1f)
        assertEquals(Color.White, scheme.surfaceContainerLowest)
    }

    @Test
    fun dynamicColourKeepsTheWallpaperAccents() {
        val dynamic = dynamicDarkColorScheme(context)
        assertEquals(dynamic.primary, dynamic.withKhataSurfaces(dark = true).primary)
    }

    @Test
    fun khataColoursMeetContrast() {
        assertContrast(KhataDark.withKhataSurfaces(dark = true), "Khata dark")
        assertContrast(KhataLight.withKhataSurfaces(dark = false), "Khata light")
    }

    @Test
    fun dynamicColoursMeetContrast() {
        assertContrast(dynamicDarkColorScheme(context).withKhataSurfaces(dark = true), "dark")
        assertContrast(dynamicLightColorScheme(context).withKhataSurfaces(dark = false), "light")
    }

    private fun assertContrast(scheme: ColorScheme, name: String) {
        listOf(scheme.background, scheme.surfaceContainerLowest).forEach { surface ->
            // Text, including the section names drawn in the primary colour.
            listOf(scheme.onSurface, scheme.onSurfaceVariant, scheme.primary).forEach {
                assertAtLeast(TEXT, it, surface, name)
            }
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
    }
}
