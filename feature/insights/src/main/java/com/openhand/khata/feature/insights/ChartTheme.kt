package com.openhand.khata.feature.insights

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import com.openhand.khata.core.model.HeatLevels
import com.openhand.khata.core.ui.incomeColor

/**
 * Colors and text shared by the Insights charts. Everything comes from the Material theme (so the
 * phone's dynamic colors and dark mode apply) or from the category's own color.
 */
@Immutable
data class ChartTheme(
    /** Axis labels and other small chart text. */
    val axisText: TextStyle,
    val gridLine: Color,
    /** The "Other" slice or bar, kept neutral so it never looks like a category. */
    val other: Color,
    /** What an empty chart is drawn in. */
    val track: Color,
    /** Drawn between slices, so two categories of the same color stay apart. */
    val divider: Color,
    /** Spending bars when they aren't split by category. */
    val spending: Color,
    val income: Color,
    /** Heatmap colors from "nothing spent" ([track]) to the most, one per level. */
    val heat: List<Color>,
    val isDark: Boolean
) {
    /**
     * A category's color on the chart. The category colors are deep shades that read well on a
     * light card; on a dark one they're lightened so they don't sink into the background.
     */
    fun categoryColor(argb: Int): Color {
        val color = Color(argb)
        return if (isDark) lerp(color, Color.White, DARK_LIGHTEN) else color
    }

    private companion object {
        const val DARK_LIGHTEN = 0.35f
    }
}

@Composable
fun chartTheme(): ChartTheme {
    val colors = MaterialTheme.colorScheme
    return ChartTheme(
        axisText = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant),
        gridLine = colors.outlineVariant,
        other = colors.outline,
        // Card backgrounds are close to surfaceVariant, so empty cells need the outline color.
        track = colors.outlineVariant,
        divider = CardDefaults.cardColors().containerColor,
        spending = colors.primary,
        income = incomeColor(),
        heat = (0..HeatLevels.MAX).map {
            lerp(colors.outlineVariant, colors.primary, it / HeatLevels.MAX.toFloat())
        },
        isDark = isSystemInDarkTheme()
    )
}
