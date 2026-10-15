package com.openhand.khata.core.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Manrope, one variable file for every weight. Hindi falls back to the phone's own font. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Manrope: FontFamily = FontFamily(
    listOf(
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold
    ).map { weight ->
        Font(
            R.font.manrope,
            weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
        )
    }
)

/** Tabular figures, so amounts line up in columns. */
private const val TABULAR = "tnum"

private fun style(size: Int, weight: FontWeight, line: Int, spacing: TextUnit = 0.sp) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing,
    fontFeatureSettings = TABULAR
)

/** Heavy titles and amounts over quiet body text, as in the design. */
internal val KhataTypography = Typography(
    displayLarge = style(48, FontWeight.ExtraBold, 54, (-1.5).sp),
    displayMedium = style(44, FontWeight.ExtraBold, 48, (-1.4).sp),
    displaySmall = style(36, FontWeight.ExtraBold, 40, (-1).sp),
    headlineLarge = style(32, FontWeight.ExtraBold, 38, (-0.8).sp),
    headlineMedium = style(28, FontWeight.ExtraBold, 34, (-0.5).sp),
    headlineSmall = style(22, FontWeight.ExtraBold, 28, (-0.4).sp),
    titleLarge = style(19, FontWeight.ExtraBold, 26),
    titleMedium = style(16, FontWeight.Bold, 22),
    titleSmall = style(14, FontWeight.Bold, 20),
    bodyLarge = style(15, FontWeight.Medium, 22),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(13, FontWeight.Normal, 18),
    labelLarge = style(14, FontWeight.Bold, 20),
    labelMedium = style(12, FontWeight.SemiBold, 16),
    labelSmall = style(12, FontWeight.Medium, 16)
)
