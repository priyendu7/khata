package com.openhand.khata.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.CategorySpend
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.categoryName
import com.openhand.khata.core.ui.segmentCardColors

/**
 * This month's donut beside its biggest categories, with their amounts and shares, and a link to
 * the Insights tab for the rest.
 */
@Composable
internal fun TopCategoriesCard(summary: HomeSummary, onSeeAll: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Segments.Single,
        colors = segmentCardColors()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, end = 8.dp, bottom = 8.dp)
        ) {
            Text(
                stringResource(R.string.home_top_categories),
                style = MaterialTheme.typography.titleMedium
            )
            val top = summary.topCategories
            if (top.isEmpty()) {
                Text(
                    stringResource(R.string.home_nothing_spent),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                TopCategories(summary, top)
            }
            TextButton(onClick = onSeeAll, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.home_see_all_insights))
            }
        }
    }
}

@Composable
private fun TopCategories(summary: HomeSummary, top: List<CategorySpend>) {
    val theme = chartTheme()
    val charted = summary.categories.chartedPaise
    val slices = summary.categories.slices.map {
        val name = categoryName(it.category.name, it.category.seedKey)
        DonutSlice(
            it.spentPaise,
            theme.categoryColor(it.category.color),
            stringResource(
                R.string.insights_slice_description,
                name,
                Money.format(it.spentPaise),
                shareOf(it.spentPaise, charted)
            )
        )
    } + listOfNotNull(
        summary.categories.otherPaise.takeIf { it > 0 }?.let {
            DonutSlice(it, theme.other, stringResource(R.string.insights_other))
        }
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(top = 12.dp, end = 12.dp)
    ) {
        // Only a picture here; tapping through is what See all insights is for.
        CategoryDonut(slices, "", "", onSliceClick = {}, modifier = Modifier.size(DONUT_SIZE))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            top.forEach { TopCategoryRow(it, theme, charted) }
        }
    }
}

@Composable
private fun TopCategoryRow(spend: CategorySpend, theme: ChartTheme, charted: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(10.dp).background(theme.categoryColor(spend.category.color), CircleShape))
        Text(
            categoryName(spend.category.name, spend.category.seedKey),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Money.format(spend.spentPaise),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                softWrap = false
            )
            Text(
                stringResource(R.string.insights_share, shareOf(spend.spentPaise, charted)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val DONUT_SIZE = 96.dp
