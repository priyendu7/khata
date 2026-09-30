package com.openhand.khata.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.MonthlyComparison
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Spending and income bars for each of [comparison]'s months, with the spending split by
 * category when [stacked]. Tapping a month (or choosing it in TalkBack) calls [onMonthClick].
 */
@Composable
fun MonthlyBars(
    comparison: MonthlyComparison,
    stacked: Boolean,
    onMonthClick: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    theme: ChartTheme = chartTheme()
) {
    val onClick by rememberUpdatedState(onMonthClick)
    val measurer = rememberTextMeasurer()
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = DateTimeFormatter.ofPattern("MMM", locale)
    val longFormat = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    val bars = comparison.bars
    val descriptions = bars.map {
        stringResource(
            R.string.insights_month_description,
            it.month.format(longFormat),
            Money.format(it.spentPaise),
            Money.format(it.incomePaise)
        )
    }
    val segmentColors = comparison.stacked.map { theme.categoryColor(it.color) } + theme.other
    val ticks = axisTicks(bars.maxOfOrNull { maxOf(it.spentPaise, it.incomePaise) } ?: 0L)
    val tickLabels = ticks.map { Money.format(it) }
    val labelGap = with(LocalDensity.current) { LABEL_GAP.toPx() }
    // As wide as the widest amount on the axis, so crores never run into the bars.
    val gutter = remember(tickLabels, theme.axisText) {
        tickLabels.maxOf { measurer.measure(it, theme.axisText).size.width } + labelGap
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .pointerInput(bars.size, gutter) {
                detectTapGestures { tap ->
                    val group = (size.width - gutter) / bars.size
                    val index = ((tap.x - gutter) / group).toInt()
                    if (tap.x >= gutter && index in bars.indices) onClick(bars[index].month)
                }
            }
            .semantics {
                contentDescription = descriptions.joinToString("; ")
                customActions = bars.mapIndexed { i, bar ->
                    CustomAccessibilityAction(descriptions[i]) {
                        onClick(bar.month)
                        true
                    }
                }
            }
    ) {
        val labelSpace = theme.axisText.fontSize.toPx() * LABEL_LINES
        val plotHeight = size.height - labelSpace
        val top = ticks.last().coerceAtLeast(1L)
        fun y(paise: Long) = plotHeight * (1 - paise.coerceIn(0L, top).toFloat() / top)

        ticks.forEachIndexed { i, tick ->
            drawLine(theme.gridLine, Offset(gutter, y(tick)), Offset(size.width, y(tick)))
            val label = Offset(0f, y(tick) - labelSpace / LABEL_LINES / 2)
            drawText(measurer, tickLabels[i], label, theme.axisText)
        }
        val group = (size.width - gutter) / bars.size
        val barWidth = group * BAR_FRACTION
        // With 12 months there may not be room for every name; then every other one.
        val every = if (group < theme.axisText.fontSize.toPx() * MIN_LABEL_EMS) 2 else 1
        bars.forEachIndexed { i, bar ->
            val left = gutter + i * group + (group - 2 * barWidth) / 2
            if (stacked) {
                var bottom = plotHeight
                bar.segments.forEachIndexed { s, paise ->
                    // Clipped to the month's spending, which refunds can make less than the parts.
                    val height = (y(0) - y(paise)).coerceAtMost(bottom - y(bar.spentPaise))
                    if (height > 0) {
                        val topLeft = Offset(left, bottom - height)
                        drawRect(segmentColors[s], topLeft, Size(barWidth, height))
                        bottom -= height
                    }
                }
            } else {
                drawBar(theme.spending, left, y(bar.spentPaise), barWidth, plotHeight)
            }
            drawBar(theme.income, left + barWidth, y(bar.incomePaise), barWidth, plotHeight)
            if (i % every == (bars.size - 1) % every) {
                // Centered under the month's bars.
                val label = measurer.measure(bar.month.format(monthFormat), theme.axisText)
                val x = gutter + i * group + (group - label.size.width) / 2
                drawText(label, topLeft = Offset(x, plotHeight + labelSpace / LABEL_LINES / 2))
            }
        }
    }
}

private fun DrawScope.drawBar(color: Color, left: Float, top: Float, width: Float, bottom: Float) {
    if (bottom - top > 0) drawRect(color, Offset(left, top), Size(width, bottom - top))
}

/**
 * Round grid-line values from 0 up to at least [max] paise: steps of 1, 2 or 5 times a power of
 * ten, and at most [MAX_STEPS] of them above zero. Integer maths, like all money.
 */
internal fun axisTicks(max: Long): List<Long> {
    if (max <= 0) return listOf(0L, RUPEE)
    var magnitude = RUPEE
    while (true) {
        for (multiple in NICE_MULTIPLES) {
            val step = magnitude * multiple
            val steps = (max + step - 1) / step
            if (steps <= MAX_STEPS) return (0..steps).map { it * step }
        }
        magnitude *= TEN
    }
}

private const val RUPEE = 100L
private const val TEN = 10L

/** Steps of 1, 2 or 5 times a power of ten read naturally: ₹500, ₹2,000, ₹10,000. */
private val NICE_MULTIPLES = longArrayOf(1, 2, 5)
private const val MAX_STEPS = 4
private val CHART_HEIGHT = 200.dp
private val LABEL_GAP = 6.dp
private const val BAR_FRACTION = 0.3f
private const val LABEL_LINES = 2
private const val MIN_LABEL_EMS = 2.5f
