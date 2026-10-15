package com.openhand.khata.feature.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.StatementSummary
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.core.ui.AmountText
import com.openhand.khata.core.ui.SectionHeader
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.ShareBar
import com.openhand.khata.core.ui.StatusChip
import com.openhand.khata.core.ui.SubScreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A credit card's page (#155), wired to its [CreditCardViewModel]. */
@Composable
fun CreditCardScreen(
    onBack: () -> Unit,
    onOpenTransactions: (TransactionFilter) -> Unit,
    viewModel: CreditCardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val askNotifications = rememberAskNotifications {}
    CreditCardContent(
        state,
        CreditCardActions(
            onBack = onBack,
            onShowStatement = viewModel::showStatement,
            onSetBillingDay = viewModel::setBillingDay,
            onSetReminders = { on ->
                viewModel.setReminders(on)
                if (on) askNotifications()
            },
            onOpenCategory = { id -> viewModel.filter(id)?.let(onOpenTransactions) }
        )
    )
}

/**
 * The current statement and its payments, the bank's figure against Khata's, the shown cycle's
 * spending by category, the past statements, and the card's billing day.
 */
@Composable
fun CreditCardContent(state: CreditCardState?, actions: CreditCardActions = CreditCardActions()) {
    SubScreen(
        title = state?.let { cardLabel(it.summary.card) }.orEmpty(),
        onBack = actions.onBack
    ) { padding ->
        if (state == null) return@SubScreen
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            val latest = state.summary.latest
            if (latest == null) {
                NoStatementCard()
            } else {
                StatementCard(latest, state.today)
                state.summary.latestSpentPaise?.let { StatementCheck(latest, it) }
                PaymentsCard(latest)
            }
            CycleSpendingCard(state, actions)
            PastStatements(state, actions.onShowStatement)
            CardSettings(state, actions)
        }
    }
}

@Composable
private fun NoStatementCard() {
    ChartCard(stringResource(R.string.card_no_statement_title)) {
        Text(
            stringResource(R.string.card_no_statement_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The current statement: its status, the total large, when it's due, how much of it is paid as a
 * bar, and the minimum against what's paid so far.
 */
@Composable
private fun StatementCard(bill: StatementSummary, today: LocalDate) {
    val statement = bill.statement
    val days = ChronoUnit.DAYS.between(today, statement.dueDate)
    PageCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.card_statement_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            StatusChip(
                statusLabel(bill),
                BillTone.of(bill, today).tone(),
                Modifier.weight(1f, fill = false)
            )
        }
        AmountText(statement.totalPaise, style = MaterialTheme.typography.displaySmall)
        Text(
            stringResource(R.string.card_due_line, longDate(statement.dueDate), dueIn(days)),
            style = MaterialTheme.typography.bodyMedium
        )
        if (statement.totalPaise > 0) {
            ShareBar(
                bill.paidPaise.toFloat() / statement.totalPaise,
                BillTone.of(bill, today).color()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            statement.minDuePaise?.let {
                Figure(stringResource(R.string.card_statement_minimum), Money.format(it))
            }
            Figure(stringResource(R.string.card_paid_so_far), Money.format(bill.paidPaise))
        }
    }
}

/** A small label over its amount. */
@Composable
private fun RowScope.Figure(label: String, value: String) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** The shown cycle's spending by category; tapping one opens its transactions. */
@Composable
private fun CycleSpendingCard(state: CreditCardState, actions: CreditCardActions) {
    val cycle = state.shownCycle
    ChartCard(stringResource(R.string.card_spending)) {
        val dates =
            stringResource(R.string.card_cycle, shortDate(cycle.first), longDate(cycle.last))
        val which = state.shownStatement?.let {
            stringResource(R.string.card_statement_cycle, longDate(it.statement.statementDate))
        } ?: stringResource(R.string.card_this_cycle)
        Text(
            "$which · $dates",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.shownStatement != null) {
            TextButton(onClick = { actions.onShowStatement(null) }) {
                Text(stringResource(R.string.card_show_this_cycle))
            }
        }
        if (state.spending.isEmpty) {
            ChartEmpty(
                stringResource(R.string.insights_nothing_spent_title),
                stringResource(R.string.card_nothing_spent_body)
            )
        } else {
            CategoryBreakdownChart(state.spending, actions.onOpenCategory)
        }
    }
}

/** Every statement before the current one, newest first; tapping one shows its cycle. */
@Composable
private fun PastStatements(state: CreditCardState, onShow: (Long?) -> Unit) {
    val past = state.summary.statements.drop(1)
    if (past.isEmpty()) return
    Column(Modifier.padding(horizontal = Segments.Inset)) {
        SectionHeader(stringResource(R.string.card_past_statements))
        past.forEachIndexed { index, bill ->
            SegmentListItem(
                index = index,
                count = past.size,
                headlineContent = { Text(longDate(bill.statement.statementDate)) },
                supportingContent = { Text(Money.format(bill.statement.totalPaise)) },
                trailingContent = {
                    StatusChip(statusLabel(bill), BillTone.of(bill, state.today).tone())
                },
                modifier = Modifier.clickable { onShow(bill.statement.id) }
            )
        }
    }
}

/** A payment's day, where the phone is. */
internal fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
