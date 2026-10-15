package com.openhand.khata.feature.insights

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.data.CardSummary
import com.openhand.khata.core.data.DebitCardSummary
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.ui.EmptyState
import com.openhand.khata.core.ui.IconTile
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.ScreenTitle
import com.openhand.khata.core.ui.SectionHeader
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.StatusChip
import com.openhand.khata.core.ui.SurfaceCard
import com.openhand.khata.core.ui.Tone
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
            else -> Column(
                Modifier.padding(start = Segments.Inset, end = Segments.Inset, bottom = 24.dp)
            ) {
                if (state.credit.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.cards_credit))
                    Column(verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                        state.credit.forEach { card ->
                            CreditCardRow(card, state.today) { onOpenCreditCard(card.card.id) }
                        }
                    }
                    SectionHeader(stringResource(R.string.card_reminder_section))
                    reminders(Modifier.segment(0, 1))
                }
                if (state.debit.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.cards_debit))
                    Column(verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                        state.debit.forEach { card ->
                            DebitCardRow(card) { onOpenDebitCard(card.card.id) }
                        }
                    }
                }
            }
        }
    }
}

/** "ICICI ··1234" and its status, then "₹35,300 due 23 Oct"; before any statement, this cycle. */
@Composable
private fun CreditCardRow(summary: CardSummary, today: LocalDate, onClick: () -> Unit) {
    val bill = summary.latest
    val spent = stringResource(
        R.string.cards_spent_this_cycle,
        Money.format(summary.currentSpentPaise)
    )
    SurfaceCard(onClick = onClick) {
        CardHeading(summary.card) {
            bill?.let {
                // Shares the line with the name, so a long status can't push it off.
                StatusChip(
                    statusLabel(it),
                    BillTone.of(it, today).tone(),
                    Modifier.weight(1f, fill = false)
                )
            }
        }
        if (bill != null) {
            Text(dueLabel(bill), style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            spent,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "HDFC Debit ··5678", its bank account and "₹12,400 this month". */
@Composable
private fun DebitCardRow(summary: DebitCardSummary, onClick: () -> Unit) {
    val linked = summary.linkedAccount?.let {
        stringResource(R.string.cards_linked_to, accountLabel(it))
    } ?: stringResource(R.string.cards_not_linked)
    SurfaceCard(onClick = onClick) {
        CardHeading(summary.card) {}
        Text(
            stringResource(R.string.cards_spent_this_month, Money.format(summary.spentPaise)),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            linked,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A card's tile and name, with [trailing] (its status) at the end. */
@Composable
private fun CardHeading(card: Account, trailing: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(painterResource(UiR.drawable.ic_credit_card), Tone.NEUTRAL)
        Text(
            cardLabel(card),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

private val CARD_GAP = 10.dp
