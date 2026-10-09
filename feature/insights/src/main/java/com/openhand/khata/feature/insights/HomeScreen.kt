package com.openhand.khata.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.CategorySpend
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.ui.CategoryBadge
import com.openhand.khata.core.ui.EmptyState
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.ScreenTitle
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.categoryName
import com.openhand.khata.core.ui.incomeColor
import com.openhand.khata.core.ui.segmentCardColors
import com.openhand.khata.core.ui.segmentShape

/** Home tab, wired to its [HomeViewModel] through Hilt. */
@Composable
fun HomeScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBackup: () -> Unit = {},
    onReview: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val backupDueDays by viewModel.backupDueDays.collectAsStateWithLifecycle()
    val reviewCount by viewModel.reviewCount.collectAsStateWithLifecycle()
    HomeContent(title, summary, modifier, backupDueDays, onBackup, reviewCount, onReview)
}

/**
 * This month's spending and income, today's spending and the top category, with the backup
 * reminder on top while [backupDueDays] is set, and the To review count while [reviewCount] > 0.
 */
@Composable
fun HomeContent(
    title: String,
    summary: HomeSummary?,
    modifier: Modifier = Modifier,
    backupDueDays: Int? = null,
    onBackup: () -> Unit = {},
    reviewCount: Int = 0,
    onReview: () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(title)
        // Nothing until the first load, so the totals never flash ₹0.
        if (summary == null) return@Column
        // The cards are one segmented group; each is given its shape in the group.
        val cards = buildList<@Composable (Shape) -> Unit> {
            if (reviewCount > 0) add { ReviewCard(reviewCount, onReview, it) }
            backupDueDays?.let { days -> add { BackupReminderCard(days, onBackup, it) } }
            add { MonthCard(summary, it) }
            add { TodayCard(summary.spentTodayPaise, it) }
            summary.topCategory?.let { top -> add { TopCategoryCard(top, it) } }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Segments.Gap),
            modifier = Modifier.padding(horizontal = Segments.Inset)
        ) {
            cards.forEachIndexed { index, card -> card(segmentShape(index, cards.size)) }
        }
        if (!summary.hasTransactions) {
            EmptyState(
                icon = painterResource(UiR.drawable.ic_ledger),
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.home_empty_body)
            )
        }
    }
}

/** Android backup is off, so a CSV export is the only copy of the data (PRD feature 6). */
@Composable
private fun ReviewCard(count: Int, onReview: () -> Unit, shape: Shape) {
    Card(
        onClick = onReview,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                pluralStringResource(R.plurals.home_review_title, count, count),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(R.string.home_review_body))
            Text(
                stringResource(R.string.home_review_action),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BackupReminderCard(days: Int, onBackup: () -> Unit, shape: Shape) {
    Card(
        onClick = onBackup,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.home_backup_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(pluralStringResource(R.plurals.home_backup_body, days, days))
            Text(
                stringResource(R.string.home_backup_action),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MonthCard(summary: HomeSummary, shape: Shape) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.home_this_month),
                style = MaterialTheme.typography.titleMedium
            )
            AmountLine(stringResource(R.string.home_spent), summary.month.spentPaise)
            AmountLine(
                stringResource(R.string.home_income),
                summary.month.incomePaise,
                color = if (summary.month.incomePaise > 0) incomeColor() else Color.Unspecified
            )
        }
    }
}

@Composable
private fun TodayCard(spentPaise: Long, shape: Shape) {
    Card(modifier = Modifier.fillMaxWidth(), shape = shape, colors = segmentCardColors()) {
        Column(Modifier.padding(20.dp)) {
            AmountLine(stringResource(R.string.home_spent_today), spentPaise)
        }
    }
}

@Composable
private fun TopCategoryCard(top: CategorySpend, shape: Shape) {
    Card(modifier = Modifier.fillMaxWidth(), shape = shape, colors = segmentCardColors()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.home_top_category),
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryBadge(top.category.icon, top.category.color)
                Text(
                    categoryName(top.category.name, top.category.seedKey),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(Money.format(top.spentPaise), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A label that wraps if it must, and an amount that never does (long Hindi words, crores). */
@Composable
private fun AmountLine(label: String, paise: Long, color: Color = Color.Unspecified) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        Text(
            Money.format(paise),
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}
