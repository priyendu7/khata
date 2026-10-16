package com.openhand.khata.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.TransactionListItem
import com.openhand.khata.core.ui.IconTile
import com.openhand.khata.core.ui.NoticeCard
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.SurfaceCard
import com.openhand.khata.core.ui.Tone
import com.openhand.khata.core.ui.TransactionRow
import com.openhand.khata.core.ui.longDateLabel
import java.time.LocalDate

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
    val billDue by viewModel.billDue.collectAsStateWithLifecycle()
    HomeContent(title, summary, modifier, backupDueDays, reviewCount, actions, billDue)
}

/**
 * Top to bottom (#130): the To review count while [reviewCount] > 0; the next unpaid credit card
 * bill while there's one ([billDue], #155); this month against last;
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
    actions: HomeActions = HomeActions(),
    billDue: BillDue? = null
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HomeHeader(title)
        // Nothing until the first load, so the totals never flash ₹0.
        if (summary == null) return@Column
        Column(
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
            // Room for the "Add" button so it never covers the last card.
            modifier = Modifier.padding(
                start = Segments.Inset,
                end = Segments.Inset,
                bottom = 96.dp
            )
        ) {
            if (reviewCount > 0) ReviewCard(reviewCount, actions.onReview)
            billDue?.let { BillDueCard(it) { actions.onOpenCard(it.due.card.id) } }
            if (summary.hasTransactions) {
                MonthCard(summary, actions.onOpenTransactions)
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

/** The app's mark and name over today's date. */
@Composable
private fun HomeHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Text(
                stringResource(R.string.home_mark),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Column {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                longDateLabel(LocalDate.now()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReviewCard(count: Int, onReview: () -> Unit) {
    NoticeCard(
        icon = painterResource(UiR.drawable.ic_inbox),
        title = pluralStringResource(R.plurals.home_review_title, count, count),
        body = stringResource(R.string.home_review_body),
        tone = Tone.ACCENT,
        onClick = onReview,
        action = stringResource(R.string.home_review_action)
    )
}

/** Android backup is off, so a CSV export is the only copy of the data (PRD feature 6). */
@Composable
private fun BackupReminderCard(days: Int, onBackup: () -> Unit) {
    NoticeCard(
        icon = painterResource(UiR.drawable.ic_download),
        title = stringResource(R.string.home_backup_title),
        body = pluralStringResource(R.plurals.home_backup_body, days, days),
        tone = Tone.WARNING,
        onClick = onBackup,
        action = stringResource(R.string.home_backup_action)
    )
}

/** No transactions yet: the two ways to get some. */
@Composable
private fun StartCard(onAddTransaction: () -> Unit, onTurnOnSms: () -> Unit) {
    SurfaceCard {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconTile(painterResource(UiR.drawable.ic_ledger), Tone.ACCENT, size = 52.dp)
            Text(
                stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
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
                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() }
            )
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.home_see_all)) }
        }
        recent.forEachIndexed { index, item ->
            TransactionRow(item, index, recent.size, showDay = true) { onOpen(item.id) }
        }
    }
}

private val SECTION_GAP = 16.dp
