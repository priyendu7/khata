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
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.ParseResult
import com.openhand.khata.sms.parser.ParsedSms
import com.openhand.khata.sms.parser.SmsParser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Trying a rule on one SMS, for Settings > Parsers > Add and the rule maker.

/**
 * What [rule] alone reads from an SMS, or null if it doesn't. A pasted SMS has no sender, so it's
 * tested as if it came from the rule's first sender.
 */
internal fun testRule(rule: CompiledRule, sender: String?, body: String, at: Long): ParsedSms? {
    val from = sender ?: rule.headers.first()
    return (SmsParser(listOf(rule)).parse(from, body, at) as? ParseResult.Parsed)?.sms
}

@Composable
internal fun TestResult(sms: ParsedSms?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (sms == null) {
                Text(
                    stringResource(R.string.parser_test_no_match),
                    color = MaterialTheme.colorScheme.error
                )
                return@Column
            }
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
