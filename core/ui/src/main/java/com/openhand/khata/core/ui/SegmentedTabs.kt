package com.openhand.khata.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * One of a few [options] side by side in a rounded track, the picked one raised: the design's
 * transaction type and Insights period pickers. TalkBack reads each as a tab.
 */
@Composable
fun <T> SegmentedTabs(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** The track: the card colour on the background, the background inside a card. */
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest
) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(TrackShape)
            .background(trackColor)
            .border(Segments.Hairline, colors.outlineVariant, TrackShape)
            .padding(4.dp)
            .selectableGroup()
    ) {
        options.forEach { option ->
            val picked = option == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(Segments.SmallShape)
                    .background(if (picked) colors.surfaceContainerHighest else Color.Transparent)
                    .selectable(picked, role = Role.Tab) { onSelect(option) }
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (picked) colors.onSurface else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private val TrackShape = RoundedCornerShape(16.dp)
