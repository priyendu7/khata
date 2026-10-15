package com.openhand.khata.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.ui.segmentCardBorder
import com.openhand.khata.core.ui.segmentCardColors
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.ParseResult
import com.openhand.khata.sms.parser.ParsedSms
import com.openhand.khata.sms.parser.ParsedStatement
import com.openhand.khata.sms.parser.SmsParser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Trying a rule on one SMS, for Settings > Parsers > Add and the rule maker.

/**
 * What [rule] alone reads from an SMS, a transaction or a statement, or null if it reads nothing.
 * A pasted SMS has no sender, so it's tested as if it came from the rule's first sender (any bank
 * sender, for a rule for any sender).
 */
internal fun testRule(rule: CompiledRule, sender: String?, body: String, at: Long): ParseResult? {
    val from = sender ?: rule.headers.firstOrNull() ?: ANY_BANK_SENDER
    return SmsParser(listOf(rule)).parse(from, body, at)
        .takeIf { it is ParseResult.Parsed || it is ParseResult.Statement }
}

@Composable
internal fun TestResult(read: ParseResult?) {
    Card(
        Modifier.fillMaxWidth(),
        colors = segmentCardColors(),
        border = segmentCardBorder()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (read) {
                is ParseResult.Parsed -> ParsedFields(read.sms)
                is ParseResult.Statement -> {
                    Text(
                        stringResource(R.string.parser_test_found),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(statementText(read.statement), style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(
                    stringResource(R.string.parser_test_no_match),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ParsedFields(sms: ParsedSms) {
    Text(
        stringResource(R.string.parser_test_found),
        style = MaterialTheme.typography.titleSmall
    )
    Field(R.string.parser_field_amount, Money.format(sms.amountPaise))
    Field(R.string.parser_field_direction, stringResource(directionLabel(sms.direction)))
    Field(R.string.parser_field_payee, sms.payee)
    Field(R.string.parser_field_account, sms.accountLast4)
    Field(R.string.parser_field_reference, sms.reference)
    Field(
        R.string.parser_field_date,
        DATE.format(Instant.ofEpochMilli(sms.timestamp).atZone(ZoneId.systemDefault()))
    )
}

/** "Statement for card ··1234: total ₹35,299.72, minimum ₹3,450.00, due 23 Oct 2026". */
@Composable
internal fun statementText(statement: ParsedStatement): String {
    val total = Money.format(statement.totalPaise)
    val due = STATEMENT_DUE.format(statement.dueDate)
    val minimum = statement.minDuePaise
    return if (minimum != null) {
        stringResource(
            R.string.parser_statement,
            statement.cardLast4,
            total,
            Money.format(minimum),
            due
        )
    } else {
        stringResource(R.string.parser_statement_no_minimum, statement.cardLast4, total, due)
    }
}

@Composable
internal fun Field(label: Int, value: String?) {
    Text(
        stringResource(
            R.string.parser_field,
            stringResource(label),
            value ?: stringResource(R.string.parser_field_none)
        ),
        style = MaterialTheme.typography.bodyMedium
    )
}

private val DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val STATEMENT_DUE = DateTimeFormatter.ofPattern("d MMM yyyy")

/** A pasted SMS for a rule for any sender is tested as if a bank sent it. */
private const val ANY_BANK_SENDER = "AX-BANK-S"
