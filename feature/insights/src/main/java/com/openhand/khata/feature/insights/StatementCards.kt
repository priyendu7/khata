package com.openhand.khata.feature.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.CardPayment
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.model.StatementSummary
import com.openhand.khata.core.ui.IconTile
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.Segments
import com.openhand.khata.core.ui.ShareBar
import com.openhand.khata.core.ui.StatusChip
import com.openhand.khata.core.ui.SurfaceCard
import com.openhand.khata.core.ui.Tone
import kotlin.math.abs

/**
 * The bank's figure against what Khata recorded in the cycle, as two bars, and how far apart
 * they are: interest, fees or a missed SMS can make them differ.
 */
@Composable
internal fun StatementCheck(bill: StatementSummary, recordedPaise: Long) {
    val bank = bill.statement.totalPaise
    val gap = abs(bank - recordedPaise)
    val most = maxOf(bank, recordedPaise).coerceAtLeast(1)
    PageCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.card_check_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            if (gap == 0L) {
                StatusChip(stringResource(R.string.card_check_match), Tone.POSITIVE)
            } else {
                StatusChip(
                    stringResource(R.string.card_check_apart, Money.format(gap)),
                    Tone.WARNING
                )
            }
        }
        CheckBar(
            stringResource(R.string.card_check_bank),
            bank,
            bank.toFloat() / most,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        CheckBar(
            stringResource(R.string.card_check_khata),
            recordedPaise,
            recordedPaise.toFloat() / most,
            MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.card_bank_vs_khata_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CheckBar(label: String, paise: Long, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Text(Money.format(paise), style = MaterialTheme.typography.titleSmall)
        }
        ShareBar(fraction, color, height = 8.dp)
    }
}

/**
 * Payments made to this statement, or a line saying where they'll show. Tapping one opens it
 * ([onOpen]), and one "Add a payment" linked has Unlink ([onUnlink], #166). [onAdd], when set,
 * offers to add a payment Khata didn't match to the card.
 */
@Composable
internal fun PaymentsCard(
    bill: StatementSummary,
    onOpen: (Long) -> Unit = {},
    onUnlink: (Long) -> Unit = {},
    onAdd: (() -> Unit)? = null
) {
    PageCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconTile(
                painterResource(UiR.drawable.ic_download),
                if (bill.payments.isEmpty()) Tone.NEUTRAL else Tone.POSITIVE
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(
                        if (bill.payments.isEmpty()) {
                            R.string.card_no_payments
                        } else {
                            R.string.card_payments
                        }
                    ),
                    style = MaterialTheme.typography.titleSmall
                )
                if (bill.payments.isEmpty()) {
                    Text(
                        stringResource(R.string.card_payments_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        bill.payments.forEach { PaymentRow(it, onOpen, onUnlink) }
        onAdd?.let {
            TextButton(onClick = it) { Text(stringResource(R.string.card_add_payment)) }
        }
    }
}

@Composable
private fun PaymentRow(payment: CardPayment, onOpen: (Long) -> Unit, onUnlink: (Long) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(payment.transactionId) }
            .padding(vertical = 4.dp)
    ) {
        Text(
            stringResource(
                R.string.card_payment,
                Money.format(payment.amountPaise),
                longDate(payment.timestamp.toLocalDate())
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (payment.linked) {
            TextButton(onClick = { onUnlink(payment.transactionId) }) {
                Text(stringResource(R.string.card_unlink))
            }
        }
    }
}

/** A card on this page, inset from the screen's edges like the rest. */
@Composable
internal fun PageCard(content: @Composable ColumnScope.() -> Unit) {
    SurfaceCard(Modifier.padding(horizontal = Segments.Inset), content = content)
}
