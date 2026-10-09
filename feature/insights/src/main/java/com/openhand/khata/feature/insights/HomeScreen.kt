package com.openhand.khata.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.TransactionListItem
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.ScreenTitle
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.TransactionRow
import com.openhand.khata.core.ui.incomeColor
import com.openhand.khata.core.ui.segmentCardColors

/** Home tab, wired to its [HomeViewModel] through Hilt. */
@Composable
fun HomeScreen(
    title: String,
    modifier: Modifier = Modifier,
    actions: HomeActions = HomeActions(),
    viewModel: HomeViewModel = hiltViewModel()
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val backupDueDays by viewModel.backupDueDays.collectAsStateWithLifecycle()
    val reviewCount by viewModel.reviewCount.collectAsStateWithLifecycle()
    HomeContent(title, summary, modifier, backupDueDays, reviewCount, actions)
}

/**
 * Top to bottom (#130): the To review count while [reviewCount] > 0; this month against last;
 * the top categories; the latest transactions; and the backup reminder while [backupDueDays] is
 * set. With no transactions at all, a card on how to start takes the middle three's place.
 */
@Composable
fun HomeContent(
    title: String,
    summary: HomeSummary?,
    modifier: Modifier = Modifier,
    backupDueDays: Int? = null,
    reviewCount: Int = 0,
    actions: HomeActions = HomeActions()
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(title)
        // Nothing until the first load, so the totals never flash ₹0.
        if (summary == null) return@Column
        Column(
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
            // Room for the "+" button so it never covers the last card.
            modifier = Modifier.padding(
                start = Segments.Inset,
                end = Segments.Inset,
                bottom = 88.dp
            )
        ) {
            if (reviewCount > 0) ReviewCard(reviewCount, actions.onReview)
            if (summary.hasTransactions) {
                MonthCard(summary)
                TopCategoriesCard(summary, actions.onSeeAllInsights)
                RecentTransactions(
                    summary.recent,
                    actions.onOpenTransaction,
                    actions.onSeeAllTransactions
                )
            } else {
                StartCard(actions.onAddTransaction, actions.onTurnOnSms)
            }
            backupDueDays?.let { BackupReminderCard(it, actions.onBackup) }
        }
    }
}

@Composable
private fun ReviewCard(count: Int, onReview: () -> Unit) {
    Card(
        onClick = onReview,
        modifier = Modifier.fillMaxWidth(),
        shape = Segments.Single,
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

/** Android backup is off, so a CSV export is the only copy of the data (PRD feature 6). */
@Composable
private fun BackupReminderCard(days: Int, onBackup: () -> Unit) {
    Card(
        onClick = onBackup,
        modifier = Modifier.fillMaxWidth(),
        shape = Segments.Single,
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

/** No transactions yet: the two ways to get some. */
@Composable
private fun StartCard(onAddTransaction: () -> Unit, onTurnOnSms: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Segments.Single,
        colors = segmentCardColors()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painterResource(UiR.drawable.ic_ledger),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onAddTransaction, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_add_transaction))
            }
            OutlinedButton(onClick = onTurnOnSms, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_turn_on_sms))
            }
        }
    }
}

@Composable
private fun MonthCard(summary: HomeSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Segments.Single,
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
            summary.change?.let {
                Text(changeText(it), style = MaterialTheme.typography.bodyMedium)
            }
            AmountLine(
                stringResource(R.string.home_income),
                summary.month.incomePaise,
                color = if (summary.month.incomePaise > 0) incomeColor() else Color.Unspecified
            )
        }
    }
}

@Composable
private fun changeText(change: MonthChange): String = when (change) {
    is MonthChange.More -> stringResource(
        R.string.home_more_than_last_month,
        Money.format(change.paise),
        change.percent
    )
    is MonthChange.Less -> stringResource(
        R.string.home_less_than_last_month,
        Money.format(change.paise),
        change.percent
    )
    MonthChange.Same -> stringResource(R.string.home_same_as_last_month)
}

/** The last few transactions as the Transactions tab draws them, with their days. */
@Composable
private fun RecentTransactions(
    recent: List<TransactionListItem>,
    onOpen: (Long) -> Unit,
    onSeeAll: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_recent),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 16.dp).semantics { heading() }
            )
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.home_see_all)) }
        }
        recent.forEachIndexed { index, item ->
            TransactionRow(item, index, recent.size, showDay = true) { onOpen(item.id) }
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

private val SECTION_GAP = 16.dp
