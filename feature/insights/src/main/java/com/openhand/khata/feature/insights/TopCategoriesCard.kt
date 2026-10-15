package com.openhand.khata.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.CategorySpend
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.ui.StackedBar
import com.openhand.khata.core.ui.SurfaceCard
import com.openhand.khata.core.ui.categoryName

/**
 * Where this month's money went: every category as one stacked bar, then the biggest ones with
 * their shares and amounts, and a link to the Insights tab for the rest.
 */
@Composable
internal fun TopCategoriesCard(summary: HomeSummary, onSeeAll: () -> Unit) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_top_categories),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.home_see_all_insights)) }
        }
        val top = summary.topCategories
        if (top.isEmpty()) {
            Text(
                stringResource(R.string.home_nothing_spent),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            TopCategories(summary, top)
        }
    }
}

@Composable
private fun TopCategories(summary: HomeSummary, top: List<CategorySpend>) {
    val theme = chartTheme()
    val charted = summary.categories.chartedPaise
    // Only a picture: the rows below say the same in words, for TalkBack too.
    StackedBar(
        summary.categories.slices.map {
            it.spentPaise.toFloat() to theme.categoryColor(it.category.color)
        } + listOf(summary.categories.otherPaise.toFloat() to theme.other)
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        top.forEach { TopCategoryRow(it, theme, charted) }
    }
}

@Composable
private fun TopCategoryRow(spend: CategorySpend, theme: ChartTheme, charted: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(theme.categoryColor(spend.category.color), RoundedCornerShape(3.dp))
        )
        Text(
            categoryName(spend.category.name, spend.category.seedKey),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.insights_share, shareOf(spend.spentPaise, charted)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            Money.format(spend.spentPaise),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            softWrap = false
        )
    }
}
