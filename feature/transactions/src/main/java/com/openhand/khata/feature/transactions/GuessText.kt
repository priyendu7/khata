package com.openhand.khata.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.ui.dateLabel
import com.openhand.khata.sms.parser.ParsedSms
import java.time.Instant
import java.time.ZoneId

/**
 * "Khata thinks: ₹450 paid to SWIGGY", then the account and date it read, and "Is that right?".
 * Only a suggestion: nothing is recorded until the user says yes.
 */
@Composable
internal fun GuessText(guess: ParsedSms) {
    val amount = Money.format(guess.amountPaise)
    val payee = guess.payee
    val (withPayee, withoutPayee) = when (guess.direction) {
        Direction.CREDIT -> R.string.review_guess_received to R.string.review_guess_received_only
        Direction.REFUND -> R.string.review_guess_refund to R.string.review_guess_refund_only
        else -> R.string.review_guess_paid to R.string.review_guess_paid_only
    }
    val date = Instant.ofEpochMilli(guess.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val details = listOfNotNull(
        guess.accountLast4?.let { stringResource(R.string.review_guess_account, it) },
        dateLabel(date)
    ).joinToString(" · ")
    Column(
        Modifier.padding(top = 8.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            stringResource(R.string.review_guess_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            if (payee != null) {
                stringResource(withPayee, amount, payee)
            } else {
                stringResource(withoutPayee, amount)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            details,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.review_guess_question),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
