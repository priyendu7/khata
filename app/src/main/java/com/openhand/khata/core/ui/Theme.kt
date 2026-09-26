package com.openhand.khata.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Ledger red and marigold from the launcher icon; used when dynamic color isn't available (API < 31).
private val KhataLight =
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

private val KhataDark =
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

/** Material 3 theme: the phone's own colors (dynamic color) on Android 12+, Khata's colors otherwise. */
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
        }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
