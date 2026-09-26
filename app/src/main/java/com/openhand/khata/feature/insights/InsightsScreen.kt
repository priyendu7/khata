package com.openhand.khata.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.R
import com.openhand.khata.core.ui.EmptyState
import com.openhand.khata.core.ui.ScreenTitle

private const val WEEKS = 18
private const val DAYS = 7

// TODO(Phase 2): the real 12-month calendar heatmap, donut and monthly chart. With no transactions
// yet, the heatmap grid is drawn empty.
@Composable
fun InsightsScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(stringResource(R.string.nav_insights))
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.insights_heatmap_title),
                    style = MaterialTheme.typography.titleMedium
                )
                EmptyHeatmap(MaterialTheme.colorScheme.outlineVariant)
                HeatmapLegend()
            }
        }
        EmptyState(
            icon = painterResource(R.drawable.ic_insights),
            title = stringResource(R.string.insights_empty_title),
            body = stringResource(R.string.insights_empty_body)
        )
    }
}

@Composable
private fun EmptyHeatmap(cellColor: Color) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(WEEKS / DAYS.toFloat())) {
        val pitch = size.width / WEEKS
        val cell = pitch * 0.82f
        for (week in 0 until WEEKS) {
            for (day in 0 until DAYS) {
                drawRoundRect(
                    color = cellColor,
                    topLeft = Offset(week * pitch, day * pitch),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * 0.2f)
                )
            }
        }
    }
}

@Composable
private fun HeatmapLegend() {
    val empty = MaterialTheme.colorScheme.outlineVariant
    val full = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stringResource(R.string.insights_less), style = MaterialTheme.typography.labelSmall)
        for (level in 0..4) {
            Canvas(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp))) {
                drawRect(lerp(empty, full, level / 4f))
            }
        }
        Text(stringResource(R.string.insights_more), style = MaterialTheme.typography.labelSmall)
    }
}
