package com.openhand.khata.feature.insights

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * A card's chart that a horizontal swipe steps through periods, like its ‹ › arrows (#146): left
 * for the next, right for the previous, as in Calendar and Photos. The chart follows the finger;
 * a short, slow drag springs back, and the current period only bounces. A custom period doesn't
 * swipe. TalkBack keeps the arrows, since a swipe there is a TalkBack gesture.
 */
@Composable
internal fun PeriodSwipe(
    shown: ShownPeriod,
    onStepPeriod: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(0) }
    val canGoNext by rememberUpdatedState(shown.canStepForward)
    val step by rememberUpdatedState(onStepPeriod)
    val flingSpeed = with(LocalDensity.current) { FLING_SPEED.toPx() }
    val animate = animationsOn()
    val drag = rememberDraggableState { delta ->
        // Past the current period the chart only gives a little, then springs back.
        val next = if (!canGoNext && offset.value + delta < 0) {
            maxOf(offset.value + delta * RESISTANCE, -width * BOUNCE)
        } else {
            offset.value + delta
        }
        // Undispatched, so the next delta already sees this one.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(next) }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
            .draggable(
                state = drag,
                orientation = Orientation.Horizontal,
                enabled = shown.period != ChartPeriod.CUSTOM,
                onDragStopped = { velocity ->
                    val far = abs(offset.value) > width * COMMIT
                    val flung = abs(velocity) > flingSpeed && velocity * offset.value > 0
                    // Dragged left (negative) is the next period, right the previous.
                    val direction = if (offset.value < 0) 1 else -1
                    val allowed = direction < 0 || canGoNext
                    if ((far || flung) && allowed) {
                        if (animate) offset.animateTo(-direction * width.toFloat(), SLIDE)
                        step(direction)
                        offset.snapTo(if (animate) direction * width.toFloat() else 0f)
                    }
                    if (animate) offset.animateTo(0f, SLIDE) else offset.snapTo(0f)
                }
            )
            .graphicsLayer {
                translationX = offset.value
                alpha = if (width == 0) 1f else 1f - abs(offset.value) / width * FADE
            }
    ) {
        content()
    }
}

/** False when the user has turned animations off, so a swipe changes the period without a slide. */
@Composable
private fun animationsOn(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** How far, as a share of the chart's width, a drag must go to change the period. */
private const val COMMIT = 0.25f

/** A flick at least this fast (per second) changes the period however short it is. */
private val FLING_SPEED = 600.dp

/** How much of a drag past the current period moves the chart, and how far it can go. */
private const val RESISTANCE = 0.3f
private const val BOUNCE = 0.15f

/** How much the chart fades as it slides a full width. */
private const val FADE = 0.6f

private const val SLIDE_MILLIS = 180
private val SLIDE = tween<Float>(SLIDE_MILLIS)
