package com.openhand.khata.feature.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.TransferEnd
import com.openhand.khata.core.model.TransferMove
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Transfers (PRD features 1 and 5, #113): what moved between the user's own accounts, such as
 * card bills paid. Shown, but never counted as spending or income: the card purchases were
 * counted when they were made. Tapping a move opens its transaction ([CardActions.onOpen]).
 */
@Composable
internal fun TransferCard(transfers: TransfersState, actions: CardActions<Long>) {
    ChartCard(stringResource(R.string.insights_transfers)) {
        PeriodChips(transfers.period, actions.onSelectPeriod)
        PeriodStepper(transfers, actions.onStepPeriod, actions.onSelectPast)
        val summary = transfers.summary
        if (summary.isEmpty) {
            ChartEmpty(
                stringResource(R.string.insights_transfers_empty_title),
                stringResource(R.string.insights_transfers_empty_body)
            )
            return@ChartCard
        }
        Column {
            Text(
                stringResource(R.string.insights_transfers_total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(Money.format(summary.totalPaise), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.insights_transfers_not_counted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (summary.cards.isNotEmpty() || summary.unknownCards.isNotEmpty()) {
            Text(
                stringResource(R.string.insights_card_bills),
                style = MaterialTheme.typography.titleSmall
            )
            summary.cards.forEach {
                AmountLine(accountLabel(it.card), paymentCount(it.count), it.paidPaise)
            }
            summary.unknownCards.forEach {
                AmountLine(
                    title = it.payeeName ?: stringResource(R.string.insights_card_unknown_no_payee),
                    detail = stringResource(R.string.insights_card_unknown, paymentCount(it.count)),
                    paise = it.paidPaise
                )
            }
        }
        Moves(summary.moves, actions.onOpen)
    }
}

/** The newest [SHOWN_MOVES] moves, and a button for the rest. */
@Composable
private fun Moves(moves: List<TransferMove>, onOpen: (Long) -> Unit) {
    var all by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.insights_moves), style = MaterialTheme.typography.titleSmall)
    val zone = ZoneId.systemDefault()
    val format = DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0])
    val shown = if (all) moves else moves.take(SHOWN_MOVES)
    shown.forEach { move ->
        val arrow =
            if (move.sideKnown) R.string.insights_move_from_to else R.string.insights_move_between
        AmountLine(
            title = stringResource(arrow, endLabel(move.from), endLabel(move.to)),
            detail = Instant.ofEpochMilli(move.timestamp).atZone(zone).format(format),
            paise = move.amountPaise,
            onClick = { onOpen(move.transactionId) }
        )
    }
    if (shown.size < moves.size) {
        TextButton(onClick = { all = true }) {
            Text(stringResource(R.string.insights_moves_show_all, moves.size))
        }
    }
}

/** A name and detail on the left, an amount on the right; TalkBack reads it as one item. */
@Composable
private fun AmountLine(title: String, detail: String, paise: Long, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(vertical = 4.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            Money.format(paise),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun paymentCount(count: Int): String =
    pluralStringResource(R.plurals.insights_payment_count, count, count)

/** An account as the other cards name it, else the payee, else "Not known". */
@Composable
private fun endLabel(end: TransferEnd): String = when {
    end.account != null -> accountLabel(end.account)
    end.payeeName != null -> end.payeeName.orEmpty()
    else -> stringResource(R.string.insights_move_unknown_end)
}

/** Moves listed before "Show all": enough for a month of card bills and transfers. */
private const val SHOWN_MOVES = 10
