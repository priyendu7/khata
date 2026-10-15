package com.openhand.khata.feature.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.model.CardCycles
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.StatementSummary
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.core.ui.Choice
import com.openhand.khata.core.ui.ChoiceDialog
import com.openhand.khata.core.ui.SectionHeader
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.SubScreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A credit card's page (#155), wired to its [CreditCardViewModel]. */
@Composable
fun CreditCardScreen(
    onBack: () -> Unit,
    onOpenTransactions: (TransactionFilter) -> Unit,
    viewModel: CreditCardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CreditCardContent(
        state,
        CreditCardActions(
            onBack = onBack,
            onShowStatement = viewModel::showStatement,
            onSetBillingDay = viewModel::setBillingDay,
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
                StatementCard(latest, state)
            }
            CycleSpendingCard(state, actions)
            PastStatements(state, actions.onShowStatement)
            BillingDaySetting(state.summary.card.billingDay, actions.onSetBillingDay)
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

/** Total, minimum, due date, status and payments; then the bank's figure against Khata's. */
@Composable
private fun StatementCard(bill: StatementSummary, state: CreditCardState) {
    val statement = bill.statement
    ChartCard(stringResource(R.string.card_statement_title)) {
        DetailLine(
            stringResource(R.string.card_statement_total),
            Money.format(statement.totalPaise)
        )
        statement.minDuePaise?.let {
            DetailLine(stringResource(R.string.card_statement_minimum), Money.format(it))
        }
        DetailLine(stringResource(R.string.card_statement_due_date), longDate(statement.dueDate))
        DetailLine(
            stringResource(R.string.card_statement_status),
            statusLabel(bill),
            BillTone.of(bill, state.today).color()
        )
        Text(stringResource(R.string.card_payments), style = MaterialTheme.typography.titleSmall)
        if (bill.payments.isEmpty()) {
            Text(
                stringResource(R.string.card_no_payments),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        bill.payments.forEach {
            Text(
                stringResource(
                    R.string.card_payment,
                    Money.format(it.amountPaise),
                    longDate(it.timestamp.toLocalDate())
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        state.summary.latestSpentPaise?.let { recorded ->
            Text(
                stringResource(
                    R.string.card_bank_vs_khata,
                    Money.format(statement.totalPaise),
                    Money.format(recorded)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(R.string.card_bank_vs_khata_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A label and its value on one line; the value never wraps. */
@Composable
private fun DetailLine(label: String, value: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
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
                    Text(
                        statusLabel(bill),
                        style = MaterialTheme.typography.labelLarge,
                        color = BillTone.of(bill, state.today).color()
                    )
                },
                modifier = Modifier.clickable { onShow(bill.statement.id) }
            )
        }
    }
}

/** The card's billing day (#154), picked from 1–31 or none. */
@Composable
private fun BillingDaySetting(billingDay: Int?, onSet: (Int?) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = Segments.Inset)) {
        SectionHeader(stringResource(R.string.card_settings))
        SegmentListItem(
            index = 0,
            count = 1,
            headlineContent = { Text(stringResource(R.string.card_billing_day)) },
            supportingContent = {
                Column {
                    Text(billingDayLabel(billingDay))
                    Text(
                        stringResource(R.string.card_billing_day_help),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            modifier = Modifier.clickable { choosing = true }
        )
    }
    if (choosing) {
        ChoiceDialog(
            title = stringResource(R.string.card_billing_day),
            choices = listOf(Choice<Int?>(null, billingDayLabel(null))) +
                (CardCycles.FIRST_BILLING_DAY..CardCycles.LAST_BILLING_DAY).map {
                    Choice(it, billingDayLabel(it))
                },
            selected = billingDay,
            onSelect = {
                onSet(it)
                choosing = false
            },
            onDismiss = { choosing = false }
        )
    }
}

@Composable
private fun billingDayLabel(day: Int?): String = day?.let {
    stringResource(R.string.card_billing_day_value, it)
} ?: stringResource(R.string.card_billing_day_none)

/** A payment's day, where the phone is. */
private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
