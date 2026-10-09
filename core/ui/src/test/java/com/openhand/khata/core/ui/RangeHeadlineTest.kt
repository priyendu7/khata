package com.openhand.khata.core.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// Real text measuring, so line breaks are as on a phone.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RangeHeadlineTest {
    @get:Rule val compose = createComposeRule()

    private val start = LocalDate.of(2026, 10, 1)
    private val end = LocalDate.of(2026, 10, 4)
    private val nbsp = ' '

    private fun text(start: LocalDate?, end: LocalDate?) =
        rangeText(start, end, Locale.UK, "Start date", "End date").replace(nbsp, ' ')

    @Test
    fun theYearIsWrittenOnceWhenBothDaysShareIt() {
        assertEquals("1 Oct – 4 Oct 2026", text(start, end))
        assertEquals("30 Dec 2025 – 4 Jan 2026", text(LocalDate.of(2025, 12, 30), end.withMonth(1)))
        assertEquals("1 Oct 2026 – End date", text(start, null))
        assertEquals("Start date – End date", text(null, null))
    }

    @Test
    fun fitsOneLineAtTheDefaultSize() {
        assertEquals(1, lines(fontScale = 1f).size)
    }

    @Test
    fun wrapsOnlyAtTheDashAtTheLargestSize() {
        val large = lines(fontScale = 2f)
        assertEquals(large.toString(), 2, large.size)
        // No date is split: each line starts with a day number or the dash.
        large.forEach { assertTrue(it, it.trim().first().let { c -> c.isDigit() || c == '–' }) }
    }

    /** The headline's lines at the width the dialog leaves it on a small phone. */
    private fun lines(fontScale: Float): List<String> {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                RangeHeadline(start, end, Modifier.width(HEADLINE_WIDTH.dp))
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("1${nbsp}Oct – 4${nbsp}Oct${nbsp}2026").fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val layout = layouts.single()
        val text = layout.layoutInput.text.text
        return (0 until layout.lineCount).map {
            text.substring(layout.getLineStart(it), layout.getLineEnd(it))
        }
    }

    private companion object {
        // A 360 dp phone, less the dialog's margins and the headline's padding.
        const val HEADLINE_WIDTH = 260
    }
}
