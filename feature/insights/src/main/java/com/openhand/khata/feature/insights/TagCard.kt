package com.openhand.khata.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.TagSpend
import com.openhand.khata.core.ui.R as UiR

/**
 * Spending by tag (PRD feature 5): a bar per tag, biggest first, then Untagged. Bars rather than a
 * donut, because a transaction with several tags counts in each and the bars can add up to more
 * than was spent.
 */
@Composable
internal fun TagCard(tags: TagsState, actions: TagActions) {
    ChartCard(stringResource(R.string.insights_by_tag)) {
        PeriodChips(tags.period, actions.onSelectPeriod)
        PeriodStepper(tags, actions.onStepPeriod, actions.onSelectPast)
        if (tags.spending.isEmpty()) {
            ChartEmpty(
                stringResource(R.string.insights_nothing_spent_title),
                stringResource(R.string.insights_nothing_spent_body)
            )
        } else {
            // Bars are sized against the biggest; a tag with more refunds than spending has none.
            val biggest = tags.spending.maxOf { it.spentPaise }
            tags.spending.forEach { spend ->
                TagBar(spend, biggest) { actions.onOpenTag(spend.tag) }
            }
            Text(
                stringResource(R.string.insights_tag_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A tag (or Untagged), its amount and count over a bar; TalkBack reads it as one item. */
@Composable
private fun TagBar(spend: TagSpend, biggest: Long, onClick: () -> Unit) {
    val theme = chartTheme()
    val color = if (spend.tag == null) theme.other else theme.spending
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                spend.tag?.name ?: stringResource(UiR.string.tag_untagged),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                Money.format(spend.spentPaise),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                softWrap = false
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .background(theme.track, BAR_SHAPE)
        ) {
            if (spend.spentPaise > 0 && biggest > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(spend.spentPaise.toFloat() / biggest)
                        .height(BAR_HEIGHT)
                        .background(color, BAR_SHAPE)
                )
            }
        }
        Text(
            pluralStringResource(R.plurals.insights_tag_count, spend.count, spend.count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val BAR_HEIGHT = 8.dp
private val BAR_SHAPE = RoundedCornerShape(4.dp)
