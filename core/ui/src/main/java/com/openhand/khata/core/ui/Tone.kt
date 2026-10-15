package com.openhand.khata.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** What a notice, chip or icon tile means, and so its colour. */
enum class Tone {
    /** Needs a look: the accent. */
    ACCENT,

    /** Good news: money in, less spent, paid. */
    POSITIVE,

    /** Late or more spent. */
    WARNING,

    /** Settings and tools: blue, kept apart from the money colours. */
    INFO,

    /** Plain information. */
    NEUTRAL;

    /** The text and icon colour. */
    @Composable
    fun color(): Color = when (this) {
        ACCENT -> MaterialTheme.colorScheme.primary
        POSITIVE -> incomeColor()
        WARNING -> MaterialTheme.colorScheme.error
        INFO -> MaterialTheme.colorScheme.tertiary
        NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    /** The faint fill behind it, on a card or the background. */
    @Composable
    fun tint(): Color = if (this == NEUTRAL) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        color().copy(alpha = if (isDarkTheme()) TINT_DARK else TINT_LIGHT)
    }

    private companion object {
        const val TINT_DARK = 0.16f
        const val TINT_LIGHT = 0.12f
    }
}

/** Whether the current theme is the dark one, whatever the phone's setting. */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < HALF

private const val HALF = 0.5f
