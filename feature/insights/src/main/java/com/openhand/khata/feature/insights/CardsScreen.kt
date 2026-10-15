package com.openhand.khata.feature.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.data.CardSummary
import com.openhand.khata.core.data.DebitCardSummary
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.ui.EmptyState
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.ScreenTitle
import com.openhand.khata.core.ui.SectionHeader
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.segment
import java.time.LocalDate

/** Cards tab (#155), wired to its [CardsViewModel]. The rows open each card's page by its id. */
@Composable
fun CardsScreen(
    onOpenCreditCard: (Long) -> Unit,
    onOpenDebitCard: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CardsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CardsContent(
        state,
        onOpenCreditCard,
        onOpenDebitCard,
        modifier,
        reminders = { CardReminderSection(it) }
    )
}

/**
 * Credit cards with their bills, then debit cards with this month's spending; with no cards, how
 * they get here. Nothing until the first load.
 */
@Composable
fun CardsContent(
    state: CardsState?,
    onOpenCreditCard: (Long) -> Unit,
    onOpenDebitCard: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Due date reminders (#156): their own ViewModel, so a slot that tests can leave empty. */
    reminders: @Composable (Modifier) -> Unit = {}
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(stringResource(R.string.cards_title))
        when {
            state == null -> Unit
            state.isEmpty -> EmptyState(
                icon = painterResource(UiR.drawable.ic_credit_card),
                title = stringResource(R.string.cards_empty_title),
                body = stringResource(R.string.cards_empty_body)
            )
            else -> Column(Modifier.padding(horizontal = Segments.Inset)) {
                if (state.credit.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.cards_credit))
                    state.credit.forEachIndexed { index, card ->
                        CreditCardRow(card, state.today, index, state.credit.size) {
                            onOpenCreditCard(card.card.id)
                        }
                    }
                    SectionHeader(stringResource(R.string.card_reminder_section))
                    reminders(Modifier.segment(0, 1))
                }
                if (state.debit.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.cards_debit))
                    state.debit.forEachIndexed { index, card ->
                        DebitCardRow(card, index, state.debit.size) {
                            onOpenDebitCard(card.card.id)
                        }
                    }
                }
            }
        }
    }
}

/** "ICICI ··1234", "₹35,300 due 23 Oct" and its status; before any statement, this cycle. */
@Composable
private fun CreditCardRow(
    summary: CardSummary,
    today: LocalDate,
    index: Int,
    count: Int,
    onClick: () -> Unit
) {
    val bill = summary.latest
    val spent = stringResource(
        R.string.cards_spent_this_cycle,
        Money.format(summary.currentSpentPaise)
    )
    SegmentListItem(
        index = index,
        count = count,
        headlineContent = { Text(cardLabel(summary.card)) },
        supportingContent = {
            Column {
                if (bill != null) Text(dueLabel(bill))
                Text(spent)
            }
        },
        trailingContent = bill?.let {
            {
                Text(
                    statusLabel(it),
                    style = MaterialTheme.typography.labelLarge,
                    color = BillTone.of(it, today).color()
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** "HDFC Debit ··5678", its bank account and "₹12,400 this month". */
@Composable
private fun DebitCardRow(summary: DebitCardSummary, index: Int, count: Int, onClick: () -> Unit) {
    val linked = summary.linkedAccount?.let {
        stringResource(R.string.cards_linked_to, accountLabel(it))
    } ?: stringResource(R.string.cards_not_linked)
    SegmentListItem(
        index = index,
        count = count,
        headlineContent = { Text(cardLabel(summary.card)) },
        supportingContent = {
            Column {
                Text(linked)
                Text(
                    stringResource(
                        R.string.cards_spent_this_month,
                        Money.format(summary.spentPaise)
                    )
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
