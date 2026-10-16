package com.openhand.khata.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.MonthBar
import com.openhand.khata.core.model.MonthlyComparison
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * A bar for each of [comparison]'s months with the spending split by category, and beside it
 * income unless categories are picked. Tapping a month's spending or income (or choosing it in
 * TalkBack) calls [onMonthClick] with the month and whether it was the income bar (#164).
 */
@Composable
fun MonthlyBars(
    comparison: MonthlyComparison,
    onMonthClick: (month: YearMonth, income: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    theme: ChartTheme = chartTheme()
) {
    val onClick by rememberUpdatedState(onMonthClick)
    val measurer = rememberTextMeasurer()
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = DateTimeFormatter.ofPattern("MMM", locale)
    val longFormat = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    val bars = comparison.bars
    val picked = comparison.picked
    val descriptions = bars.map { monthDescription(it, picked, longFormat) }
    val incomeActions = bars.map {
        stringResource(R.string.insights_month_income_action, it.month.format(longFormat))
    }
    val categoryColors = comparison.stacked.map { theme.categoryColor(it.color) }
    val segmentColors = if (picked) categoryColors else categoryColors + theme.other
    val ticks = axisTicks(bars.maxOfOrNull { maxOf(it.spentPaise, it.incomePaise) } ?: 0L)
    val tickLabels = ticks.map { compactRupees(it) }
    val current = theme.axisText.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold
    )
    val density = LocalDensity.current
    val corner = with(density) { BAR_CORNER.toPx() }
    val gap = with(density) { BAR_GAP.toPx() }
    val labelGap = with(LocalDensity.current) { LABEL_GAP.toPx() }
    // As wide as the widest amount on the axis, so crores never run into the bars.
    val gutter = remember(tickLabels, theme.axisText) {
        tickLabels.maxOf { measurer.measure(it, theme.axisText).size.width } + labelGap
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .pointerInput(bars.size, gutter, picked) {
                detectTapGestures { tap ->
                    barAt(tap.x - gutter, size.width - gutter, bars.size, !picked)
                        ?.let { (index, income) -> onClick(bars[index].month, income) }
                }
            }
            .semantics {
                contentDescription = descriptions.joinToString("; ")
                val incomeLabels = incomeActions.takeUnless { picked }
                customActions = monthActions(bars, descriptions, incomeLabels) { month, income ->
                    onClick(month, income)
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
        // Spending alone takes a wider bar than spending beside income.
        val barWidth = group * if (picked) SINGLE_BAR_FRACTION else BAR_FRACTION
        // With 12 months there may not be room for every name; then every other one.
        val every = if (group < theme.axisText.fontSize.toPx() * MIN_LABEL_EMS) 2 else 1
        // Spending and income side by side, or spending alone when categories are picked.
        val perGroup = if (picked) 1 else 2
        val pairWidth = perGroup * barWidth + (perGroup - 1) * gap
        bars.forEachIndexed { i, bar ->
            val left = gutter + i * group + (group - pairWidth) / 2
            val geometry = BarGeometry(left, barWidth, gap, plotHeight, corner)
            drawMonth(bar, geometry, ::y, segmentColors, theme.income.takeUnless { picked })
            if (i % every == (bars.size - 1) % every) {
                // Centered under the month's bars; the latest month stands out.
                val style = if (i == bars.size - 1) current else theme.axisText
                val label = measurer.measure(bar.month.format(monthFormat), style)
                val x = gutter + i * group + (group - label.size.width) / 2
                drawText(label, topLeft = Offset(x, plotHeight + labelSpace / LABEL_LINES / 2))
            }
        }
    }
}

/**
 * TalkBack's actions: each month's spending under its [descriptions], and its income under
 * [incomeLabels] unless that's null (no income bars).
 */
private fun monthActions(
    bars: List<MonthBar>,
    descriptions: List<String>,
    incomeLabels: List<String>?,
    onClick: (YearMonth, Boolean) -> Unit
): List<CustomAccessibilityAction> = bars.flatMapIndexed { i, bar ->
    listOfNotNull(descriptions[i] to false, incomeLabels?.let { it[i] to true })
        .map { (label, income) ->
            CustomAccessibilityAction(label) {
                onClick(bar.month, income)
                true
            }
        }
}

/**
 * Which of [count] months a tap [x] pixels into a plot [width] wide falls on, and whether it's
 * on the income bar: the right half, when there are income bars ([hasIncome]). Null when it's
 * off the plot.
 */
private fun barAt(x: Float, width: Float, count: Int, hasIncome: Boolean): Pair<Int, Boolean>? {
    val group = width / count
    val index = (x / group).toInt()
    if (x < 0 || index !in 0 until count) return null
    return index to (hasIncome && x % group > group / 2)
}

/** What TalkBack says for a month: its spending, and its income unless categories are picked. */
@Composable
private fun monthDescription(bar: MonthBar, picked: Boolean, format: DateTimeFormatter): String =
    if (picked) {
        stringResource(
            R.string.insights_month_picked_description,
            bar.month.format(format),
            Money.format(bar.spentPaise)
        )
    } else {
        stringResource(
            R.string.insights_month_description,
            bar.month.format(format),
            Money.format(bar.spentPaise),
            Money.format(bar.incomePaise)
        )
    }

/** Where a month's bars go: from [left], each [width] wide with [gap] between, down to [bottom]. */
private class BarGeometry(
    val left: Float,
    val width: Float,
    val gap: Float,
    val bottom: Float,
    val corner: Float
)

/**
 * A month's spending as a rounded-top bar split by category, and beside it its income bar when
 * [income] has a colour (it's null when categories are picked).
 */
private fun DrawScope.drawMonth(
    bar: MonthBar,
    at: BarGeometry,
    y: (Long) -> Float,
    segmentColors: List<Color>,
    income: Color?
) {
    // One rounded-top shape per bar; the category segments are drawn inside it.
    clipPath(topRounded(at.left, y(bar.spentPaise), at.width, at.bottom, at.corner)) {
        var bottom = at.bottom
        bar.segments.forEachIndexed { s, paise ->
            // Clipped to the month's spending, which refunds can make less than the parts.
            val height = (y(0) - y(paise)).coerceAtMost(bottom - y(bar.spentPaise))
            if (height > 0) {
                drawRect(segmentColors[s], Offset(at.left, bottom - height), Size(at.width, height))
                bottom -= height
            }
        }
    }
    if (income != null) {
        val left = at.left + at.width + at.gap
        val top = y(bar.incomePaise)
        clipPath(topRounded(left, top, at.width, at.bottom, at.corner)) {
            drawBar(income, left, top, at.width, at.bottom)
        }
    }
}

/** A bar from [top] down to [bottom] with only its top corners rounded. */
private fun topRounded(left: Float, top: Float, width: Float, bottom: Float, corner: Float): Path {
    val r = minOf(corner, width / 2, (bottom - top).coerceAtLeast(0f))
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = left + width,
                bottom = bottom,
                topLeftCornerRadius = CornerRadius(r),
                topRightCornerRadius = CornerRadius(r)
            )
        )
    }
}

/** "₹2L", "₹50K", "₹1.5Cr": axis amounts kept short, in the lakh and crore people use. */
internal fun compactRupees(paise: Long): String {
    val rupees = paise / RUPEE
    fun short(unit: Long, suffix: String): String {
        val tenths = rupees * TEN / unit
        val rest = tenths % TEN
        return "₹" + tenths / TEN + (if (rest == 0L) "" else ".$rest") + suffix
    }
    return when {
        rupees >= CRORE -> short(CRORE, "Cr")
        rupees >= LAKH -> short(LAKH, "L")
        rupees >= THOUSAND -> short(THOUSAND, "K")
        else -> Money.format(paise)
    }
}

private const val THOUSAND = 1_000L
private const val LAKH = 1_00_000L
private const val CRORE = 1_00_00_000L

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
private const val SINGLE_BAR_FRACTION = 0.55f
private val BAR_CORNER = 6.dp
private val BAR_GAP = 2.dp
private const val LABEL_LINES = 2
private const val MIN_LABEL_EMS = 2.5f
