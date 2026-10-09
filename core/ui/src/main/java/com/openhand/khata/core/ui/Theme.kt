package com.openhand.khata.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Ledger red and marigold from the launcher icon; used when dynamic color isn't available (API < 31).
internal val KhataLight =
    lightColorScheme(
        primary = Color(0xFFA4221F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDAD5),
        onPrimaryContainer = Color(0xFF410002),
        secondary = Color(0xFF7C5800),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDEA6),
        onSecondaryContainer = Color(0xFF271900)
    )

internal val KhataDark =
    darkColorScheme(
        primary = Color(0xFFFFB4AA),
        onPrimary = Color(0xFF690003),
        primaryContainer = Color(0xFF8C1512),
        onPrimaryContainer = Color(0xFFFFDAD5),
        secondary = Color(0xFFF9BC4B),
        onSecondary = Color(0xFF412D00),
        secondaryContainer = Color(0xFF5E4200),
        onSecondaryContainer = Color(0xFFFFDEA6)
    )

/** Near-black behind black cards in dark mode, light grey behind white ones in light mode. */
private val DarkBackground = Color(0xFF121212)
private val LightBackground = Color(0xFFF1F3F4)

/**
 * Sets the surfaces apart from the cards and lists drawn on them, as in Google's own apps. Only
 * the surfaces change: accents keep the wallpaper's colours (or Khata's).
 */
internal fun ColorScheme.withKhataSurfaces(dark: Boolean): ColorScheme {
    val background = if (dark) DarkBackground else LightBackground
    return copy(
        background = background,
        surface = background,
        surfaceContainerLowest = if (dark) Color.Black else Color.White
    )
}

/**
 * Material 3 theme: the phone's own colors (dynamic color) on Android 12+, Khata's colors
 * otherwise, on Khata's surfaces either way.
 */
@Composable
fun KhataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> KhataDark
            else -> KhataLight
        }.withKhataSurfaces(darkTheme)
    MaterialTheme(
        colorScheme = colorScheme,
        // Cards take the same large corners as the segmented lists.
        shapes = Shapes(medium = Segments.Single),
        content = content
    )
}
