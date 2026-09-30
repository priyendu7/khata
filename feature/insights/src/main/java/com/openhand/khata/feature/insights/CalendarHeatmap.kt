package com.openhand.khata.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * A GitHub-style grid of [state]'s days: weeks as columns, days of the week as rows, month names
 * on top, darker for more spent. It's wider than a phone, so it scrolls sideways and starts at
 * today. Tapping a day (or choosing it in TalkBack) calls [onDayClick].
 */
@Composable
fun CalendarHeatmap(
    state: HeatmapState,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    theme: ChartTheme = chartTheme()
) {
    val grid = HeatmapGrid(state.first, state.today, state.firstDayOfWeek)
    val onClick by rememberUpdatedState(onDayClick)
    val measurer = rememberTextMeasurer()
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = DateTimeFormatter.ofPattern("MMM", locale)
    val scroll = rememberScrollState()
    // Start at today, the right-hand end; again if the width changes.
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }

    val pitch = with(LocalDensity.current) { CELL_PITCH.toPx() }
    val labelHeight = with(LocalDensity.current) { LABEL_HEIGHT.toPx() }
    Box(modifier.horizontalScroll(scroll)) {
        Box(
            Modifier.size(
                CELL_PITCH * grid.weeks,
                LABEL_HEIGHT + CELL_PITCH * HeatmapGrid.DAYS_IN_WEEK
            )
        ) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(grid) {
                    detectTapGestures { tap ->
                        grid.dayAt(tap.x / pitch, (tap.y - labelHeight) / pitch)
                            ?.let(onClick)
                    }
                }
            ) {
                val cell = pitch * CELL_FILL
                grid.days.forEach { day ->
                    val (week, weekday) = grid.position(day)
                    drawRoundRect(
                        color = theme.heat[state.levels.level(state.days[day] ?: 0L)],
                        topLeft = Offset(week * pitch, labelHeight + weekday * pitch),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(cell * CORNER)
                    )
                    if (day.dayOfMonth == 1) {
                        drawText(
                            measurer,
                            day.format(monthFormat),
                            topLeft = Offset(week * pitch, 0f),
                            style = theme.axisText
                        )
                    }
                }
            }
            DaySemantics(grid, state, onClick)
        }
    }
}

/** An invisible node on each day, so TalkBack can read and open days one by one, in order. */
@Composable
private fun DaySemantics(grid: HeatmapGrid, state: HeatmapState, onClick: (LocalDate) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)
    Layout(
        content = {
            grid.days.forEachIndexed { i, day ->
                val paise = state.days[day]
                val date = day.format(dateFormat)
                val label = if (paise == null) {
                    stringResource(R.string.insights_day_nothing, date)
                } else {
                    stringResource(R.string.insights_day_spent, date, Money.format(paise))
                }
                Box(
                    Modifier.semantics {
                        contentDescription = label
                        traversalIndex = i.toFloat()
                        onClick {
                            onClick(day)
                            true
                        }
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize().semantics { isTraversalGroup = true }
    ) { measurables, constraints ->
        val pitch = CELL_PITCH.roundToPx()
        val top = LABEL_HEIGHT.roundToPx()
        val placeables = measurables.map { it.measure(Constraints.fixed(pitch, pitch)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, placeable ->
                val (week, weekday) = grid.position(grid.days[i])
                placeable.place(week * pitch, top + weekday * pitch)
            }
        }
    }
}

private val CELL_PITCH = 16.dp
private val LABEL_HEIGHT = 18.dp
private const val CELL_FILL = 0.82f
private const val CORNER = 0.2f
