package com.openhand.khata.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Warm near-black with a marigold accent (the launcher icon's). Cards sit one step above the
 * background with a hairline outline; inputs and chips one step above the cards.
 */
internal val KhataDark =
    darkColorScheme(
        primary = Color(0xFFF2A93B),
        onPrimary = Color(0xFF1A1206),
        primaryContainer = Color(0xFF3A2C18),
        onPrimaryContainer = Color(0xFFF7C46E),
        secondary = Color(0xFFF2A93B),
        onSecondary = Color(0xFF1A1206),
        // The navigation bar's indicator and selected chips.
        secondaryContainer = Color(0xFF3A2C18),
        onSecondaryContainer = Color(0xFFF2A93B),
        tertiary = Color(0xFF8FB3FF),
        onTertiary = Color(0xFF0D1A33),
        tertiaryContainer = Color(0xFF1F2A40),
        onTertiaryContainer = Color(0xFFC9D8FF),
        error = Color(0xFFF28B6B),
        onError = Color(0xFF2A0E05),
        errorContainer = Color(0xFF3D2018),
        onErrorContainer = Color(0xFFFFC2AE),
        background = Color(0xFF111110),
        onBackground = Color(0xFFF3EFE9),
        surface = Color(0xFF111110),
        onSurface = Color(0xFFF3EFE9),
        surfaceVariant = Color(0xFF242220),
        onSurfaceVariant = Color(0xFFABA397),
        surfaceTint = Color.Transparent,
        surfaceDim = Color(0xFF0B0A0A),
        surfaceBright = Color(0xFF2A2724),
        surfaceContainerLowest = Color(0xFF1A1917),
        surfaceContainerLow = Color(0xFF161514),
        surfaceContainer = Color(0xFF1E1C1A),
        surfaceContainerHigh = Color(0xFF242220),
        surfaceContainerHighest = Color(0xFF2A2724),
        inverseSurface = Color(0xFFF3EFE9),
        inverseOnSurface = Color(0xFF1A1917),
        inversePrimary = Color(0xFF9C5A00),
        outline = Color(0xFF7A736A),
        outlineVariant = Color(0xFF262421),
        scrim = Color.Black
    )

/** The same family on warm paper: a deeper marigold, so text in it stays readable on white. */
internal val KhataLight =
    lightColorScheme(
        primary = Color(0xFF9C5A00),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFCE6C2),
        onPrimaryContainer = Color(0xFF3A2400),
        secondary = Color(0xFF9C5A00),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFCE6C2),
        onSecondaryContainer = Color(0xFF5A3800),
        tertiary = Color(0xFF2F5BB7),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDCE6FF),
        onTertiaryContainer = Color(0xFF0D1A33),
        error = Color(0xFFB8401E),
        onError = Color.White,
        errorContainer = Color(0xFFFFDDD2),
        onErrorContainer = Color(0xFF3D1205),
        background = Color(0xFFF6F3EE),
        onBackground = Color(0xFF1C1A17),
        surface = Color(0xFFF6F3EE),
        onSurface = Color(0xFF1C1A17),
        surfaceVariant = Color(0xFFEDE7DF),
        onSurfaceVariant = Color(0xFF5F584F),
        surfaceTint = Color.Transparent,
        surfaceDim = Color(0xFFE6E0D7),
        surfaceBright = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFBF9F6),
        surfaceContainer = Color(0xFFF0EBE4),
        surfaceContainerHigh = Color(0xFFEAE4DC),
        surfaceContainerHighest = Color(0xFFE3DCD2),
        inverseSurface = Color(0xFF2A2724),
        inverseOnSurface = Color(0xFFF3EFE9),
        inversePrimary = Color(0xFFF2A93B),
        outline = Color(0xFF8A8174),
        outlineVariant = Color(0xFFE6E0D7),
        scrim = Color.Black
    )

/**
 * Material 3 theme in Khata's own colors, light or dark with the phone. Wallpaper (dynamic)
 * colors aren't used: the design depends on the warm surfaces and the marigold accent.
 */
@Composable
fun KhataTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) KhataDark else KhataLight,
        typography = KhataTypography,
        // Cards take the same large corners as the segmented lists.
        shapes = Shapes(
            small = Segments.SmallShape,
            medium = Segments.Single,
            large = Segments.Single
        ),
        content = content
    )
}
