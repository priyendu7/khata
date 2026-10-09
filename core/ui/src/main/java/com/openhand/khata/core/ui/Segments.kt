package com.openhand.khata.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Corner sizes and gap of a segmented list, as in Google's own apps (Pixel Settings, Files). */
object Segments {
    /** The outer corners of a group: the top of its first item and the bottom of its last. */
    val OuterCorner = 20.dp

    /** The corners between two items of a group. */
    val InnerCorner = 4.dp

    /** The background shows through this gap between items. */
    val Gap = 2.dp

    /** Space between a group and the screen's edges. */
    val Inset = 16.dp

    /** Padding for a screen-long list: inset from the edges, and clear of an "add" button. */
    val ListPadding = PaddingValues(start = Inset, top = 8.dp, end = Inset, bottom = 88.dp)

    /** The shape of a lone card, or of a list with one item. */
    val Single = RoundedCornerShape(OuterCorner)
}

/** The shape of item [index] of [count]: large corners on the group's outside, small inside it. */
fun segmentShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) Segments.OuterCorner else Segments.InnerCorner
    val bottom = if (index == count - 1) Segments.OuterCorner else Segments.InnerCorner
    return RoundedCornerShape(top, top, bottom, bottom)
}

/**
 * Draws item [index] of [count] as a segment on the card colour, with a gap above all but the
 * first. Put it before `clickable` so the ripple and focus outline follow the segment's shape.
 */
@Composable
fun Modifier.segment(index: Int, count: Int): Modifier = this
    .padding(top = if (index > 0) Segments.Gap else 0.dp)
    .clip(segmentShape(index, count))
    .background(MaterialTheme.colorScheme.surfaceContainerLowest)

/** A [ListItem] drawn as item [index] of [count] of a segmented list. */
@Composable
fun SegmentListItem(
    index: Int,
    count: Int,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
) {
    ListItem(
        headlineContent = headlineContent,
        modifier = Modifier.segment(index, count).then(modifier),
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = colors
    )
}

/** Cards share the lists' colour, so a card and a list on one screen look like one family. */
@Composable
fun segmentCardColors(): CardColors =
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
