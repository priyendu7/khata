package com.openhand.khata.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A list's items share one outlined card, split by hairlines (the design's grouped lists). The
 * names are from when items were separate segments with gaps between them.
 */
object Segments {
    /** The outer corners of a group: the top of its first item and the bottom of its last. */
    val OuterCorner = 22.dp

    /** The corners between two items of a group: square, so the hairline runs edge to edge. */
    val InnerCorner = 0.dp

    /** Items touch; the hairline above each one separates them. */
    val Gap = 0.dp

    /** Width of the card's outline and of the hairlines between items. */
    val Hairline = 1.dp

    /** Space between a group and the screen's edges. */
    val Inset = 16.dp

    /** Padding for a screen-long list: inset from the edges, and clear of an "add" button. */
    val ListPadding = PaddingValues(start = Inset, top = 8.dp, end = Inset, bottom = 88.dp)

    /** The shape of a lone card, or of a list with one item. */
    val Single = RoundedCornerShape(OuterCorner)

    /** Chips, small buttons and icon tiles. */
    val SmallShape = RoundedCornerShape(12.dp)
}

/** The shape of item [index] of [count]: large corners on the group's outside, square inside it. */
fun segmentShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) Segments.OuterCorner else Segments.InnerCorner
    val bottom = if (index == count - 1) Segments.OuterCorner else Segments.InnerCorner
    return RoundedCornerShape(top, top, bottom, bottom)
}

/**
 * Draws item [index] of [count] as part of one outlined card: each item draws its sides and its
 * top edge (the hairline under the item above); only the last draws the bottom. Put it before
 * `clickable` so the ripple and focus outline follow the item's shape.
 */
@Composable
fun Modifier.segment(index: Int, count: Int): Modifier {
    val shape = segmentShape(index, count)
    val line = MaterialTheme.colorScheme.outlineVariant
    val last = index == count - 1
    return this
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .drawBehind {
            val stroke = Segments.Hairline.toPx()
            // Pushed below the item, the bottom edge is clipped away; the next item's top shows.
            val extra = if (last) 0f else stroke * 2
            val outline = shape.createOutline(
                Size(size.width, size.height + extra),
                layoutDirection,
                this
            )
            // Centred on the edge, half the stroke is clipped: what shows is one hairline wide.
            drawOutline(outline, line, style = Stroke(stroke * 2))
        }
}

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

/** ... and the lists' hairline outline. */
@Composable
fun segmentCardBorder(): BorderStroke =
    BorderStroke(Segments.Hairline, MaterialTheme.colorScheme.outlineVariant)
