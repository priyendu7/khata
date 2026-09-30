package com.openhand.khata.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * A donut of [slices] with [label] and [amount] in the middle. Tapping a slice (or choosing it
 * from TalkBack's actions) calls [onSliceClick] with its index.
 */
@Composable
fun CategoryDonut(
    slices: List<DonutSlice>,
    label: String,
    amount: String,
    onSliceClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    theme: ChartTheme = chartTheme()
) {
    val fractions = fractionsOf(slices.map { it.paise })
    // The tap detector outlives recompositions; this keeps it calling the latest callback.
    val onClick by rememberUpdatedState(onSliceClick)
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(fractions) {
                    detectTapGestures { tap ->
                        sliceAt(tap, Size(size.width.toFloat(), size.height.toFloat()), fractions)
                            ?.let(onClick)
                    }
                }
                .semantics {
                    contentDescription = slices.joinToString("; ") { it.description }
                    customActions = slices.mapIndexed { i, slice ->
                        CustomAccessibilityAction(slice.description) {
                            onSliceClick(i)
                            true
                        }
                    }
                }
        ) {
            val ring = min(size.width, size.height) * RING_FRACTION
            val diameter = min(size.width, size.height) - ring
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            if (fractions.isEmpty()) {
                drawArc(theme.track, 0f, FULL_TURN, false, topLeft, arcSize, style = Stroke(ring))
                return@Canvas
            }
            var start = START_ANGLE
            fractions.forEachIndexed { i, fraction ->
                val sweep = fraction * FULL_TURN
                drawArc(
                    slices[i].color,
                    start,
                    sweep,
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(ring)
                )
                start += sweep
            }
            if (fractions.size > 1) {
                // Thin gaps at each boundary, drawn over the ring in the card's color.
                start = START_ANGLE
                fractions.forEach { fraction ->
                    drawArc(
                        color = theme.divider,
                        startAngle = start - GAP / 2,
                        sweepAngle = GAP,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(ring)
                    )
                    start += fraction * FULL_TURN
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            Text(amount, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
    }
}

/** Each value's share of the total, as fractions adding up to 1; empty when there's nothing. */
internal fun fractionsOf(values: List<Long>): List<Float> {
    val total = values.sum()
    if (total <= 0) return emptyList()
    return values.map { it.toFloat() / total }
}

/**
 * The index of the slice at [point] on a donut drawn in [size], or null for a tap in the hole,
 * outside the ring or in a corner. Slices run clockwise from the top, like the drawing.
 */
internal fun sliceAt(point: Offset, size: Size, fractions: List<Float>): Int? {
    val side = min(size.width, size.height)
    val ring = side * RING_FRACTION
    val distance = hypot(point.x - size.width / 2, point.y - size.height / 2)
    if (fractions.isEmpty() || distance < side / 2 - ring || distance > side / 2) return null
    val degrees = Math.toDegrees(
        atan2((point.y - size.height / 2).toDouble(), (point.x - size.width / 2).toDouble())
    ).toFloat()
    // atan2 measures from 3 o'clock; the slices start at 12 o'clock.
    val turn = ((degrees - START_ANGLE) % FULL_TURN + FULL_TURN) % FULL_TURN / FULL_TURN
    val ends = fractions.runningReduce(Float::plus)
    // Rounding can leave the last end a hair under 1, so anything past it is the last slice.
    return ends.indexOfFirst { turn < it }.takeIf { it >= 0 } ?: fractions.lastIndex
}

private const val RING_FRACTION = 0.2f
private const val FULL_TURN = 360f
private const val START_ANGLE = -90f
private const val GAP = 1.5f
