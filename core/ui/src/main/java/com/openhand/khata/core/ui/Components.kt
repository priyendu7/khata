package com.openhand.khata.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Large screen title used at the top of each tab. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
            .semantics { heading() }
    )
}

/** A section's name above its rows, marked as a heading so TalkBack can jump between sections. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

/** Centered icon, title and explanation shown while a screen has nothing to display yet. */
@Composable
fun EmptyState(icon: Painter, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(icon, Tone.NEUTRAL, size = 56.dp)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** An icon on a rounded square of its tone's tint, as beside every notice and setting. */
@Composable
fun IconTile(icon: Painter, tone: Tone, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * TILE_CORNER))
            .background(tone.tint())
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tone.color(),
            modifier = Modifier.size(size / 2)
        )
    }
}

/** The outlined card everything on a tab sits in, padded like the design's sections. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
    if (onClick == null) {
        Card(
            modifier.fillMaxWidth(),
            shape = Segments.Single,
            colors = segmentCardColors(),
            border = segmentCardBorder(),
            content = inner
        )
    } else {
        Card(
            onClick,
            modifier.fillMaxWidth(),
            shape = Segments.Single,
            colors = segmentCardColors(),
            border = segmentCardBorder(),
            content = inner
        )
    }
}

/** A small pill of text in its tone: a status, a date range, a change against last month. */
@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = tone.color(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tone.tint())
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/** Shares side by side in one bar, as the design shows categories, accounts and card dues. */
@Composable
fun StackedBar(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    Row(
        modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        parts.filter { it.first > 0f }.forEach { (weight, color) ->
            Box(
                Modifier
                    .weight(weight)
                    .height(height)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

/** A progress line: [fraction] of a track, in [color]. */
@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(color)
        )
    }
}

/** Green for money coming in: it must never read as the accent or as a loss. */
@Composable
fun incomeColor(): Color = if (isDarkTheme()) Color(0xFF6FD3A8) else Color(0xFF1E7F57)

/** An icon tile's corners, as a share of its size. */
private const val TILE_CORNER = 0.3f
