package com.openhand.khata.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.data.SmsImportPreview
import com.openhand.khata.core.model.Money
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.core.ui.categoryName
import com.openhand.khata.core.ui.segmentCardColors
import com.openhand.khata.sms.ingest.SmsExplanation
import com.openhand.khata.sms.ingest.TriedRule
import com.openhand.khata.sms.parser.ParseResult
import com.openhand.khata.sms.parser.ParsedSms
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Settings > SMS import > Test a message (PRD feature 7): paste a sender and an SMS and see what
 * the app would do with it. Nothing is saved.
 */
@Composable
fun TestMessageScreen(
    onBack: () -> Unit,
    onMakeParser: (sender: String, body: String) -> Unit,
    onOpenFilter: (FilterSwitch) -> Unit,
    viewModel: TestMessageViewModel = hiltViewModel()
) {
    val answer by viewModel.answer.collectAsStateWithLifecycle()
    TestMessageContent(
        onBack = onBack,
        answer = answer,
        onTest = { sender, body -> viewModel.test(sender, body) },
        onMakeParser = onMakeParser,
        onOpenFilter = onOpenFilter
    )
}

@Composable
fun TestMessageContent(
    onBack: () -> Unit,
    answer: SmsExplanation?,
    onTest: (sender: String, body: String) -> Unit,
    onMakeParser: (sender: String, body: String) -> Unit,
    onOpenFilter: (FilterSwitch) -> Unit
) {
    var sender by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    // The answer is for what was tested, even if the fields are edited after.
    var tested by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    SubScreen(title = stringResource(R.string.test_sms_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.test_sms_intro),
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = sender,
                onValueChange = { sender = it },
                label = { Text(stringResource(R.string.test_sms_sender)) },
                placeholder = { Text(stringResource(R.string.test_sms_sender_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.test_sms_body)) },
                minLines = BODY_LINES,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    tested = sender to body
                    onTest(sender, body)
                },
                enabled = sender.isNotBlank() && body.isNotBlank()
            ) {
                Text(stringResource(R.string.test_sms_button))
            }
            val testedSms = tested
            if (answer != null && testedSms != null) {
                Answer(
                    answer,
                    onMakeParser = { onMakeParser(testedSms.first.trim(), testedSms.second) },
                    onOpenFilter = onOpenFilter
                )
            }
        }
    }
}

@Composable
private fun Answer(
    answer: SmsExplanation,
    onMakeParser: () -> Unit,
    onOpenFilter: (FilterSwitch) -> Unit
) {
    Card(Modifier.fillMaxWidth(), colors = segmentCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val result = answer.result
            when (result) {
                is ParseResult.Filtered -> {
                    val title = if (result.reason.isSenderFilter) {
                        R.string.test_sms_not_read
                    } else {
                        R.string.test_sms_filtered
                    }
                    Title(stringResource(title))
                    Text(filterReasonText(result.reason))
                    FilterSwitch.of(result.reason)?.let { switch ->
                        TextButton(onClick = { onOpenFilter(switch) }) {
                            Text(stringResource(R.string.test_sms_open_filter))
                        }
                    }
                }
                is ParseResult.Parsed -> Parsed(
                    result.sms,
                    answer.rulesTried.last(),
                    answer.preview
                )
                is ParseResult.Unparsed -> {
                    Title(stringResource(R.string.test_sms_to_review))
                    Text(stringResource(R.string.test_sms_to_review_note))
                    OutlinedButton(onClick = onMakeParser) {
                        Text(stringResource(R.string.test_sms_make_parser))
                    }
                }
            }
            // A sender filter drops an SMS before any rule is tried.
            if (result !is ParseResult.Filtered || !result.reason.isSenderFilter) {
                RulesTried(answer.rulesTried)
            }
        }
    }
}

@Composable
private fun Parsed(sms: ParsedSms, rule: TriedRule, preview: SmsImportPreview?) {
    Title(stringResource(R.string.test_sms_parsed, rule.id))
    Text(
        stringResource(
            if (rule.custom) R.string.test_sms_rule_custom else R.string.test_sms_rule_built_in
        ),
        style = MaterialTheme.typography.bodySmall
    )
    Field(R.string.parser_field_amount, Money.format(sms.amountPaise))
    Field(R.string.parser_field_direction, stringResource(directionLabel(sms.direction)))
    Field(
        R.string.test_sms_field_when,
        DATE_TIME.format(Instant.ofEpochMilli(sms.timestamp).atZone(ZoneId.systemDefault()))
    )
    Field(
        R.string.parser_field_account,
        preview?.account?.name
            ?: stringResource(
                R.string.test_sms_new_account,
                listOfNotNull(sms.bank, sms.accountLast4).joinToString(" ")
            )
    )
    Field(
        R.string.parser_field_payee,
        when {
            sms.payee == null -> null
            preview?.payeeName != null -> preview.payeeName
            else -> stringResource(R.string.test_sms_new_payee, sms.payee.orEmpty())
        }
    )
    Field(R.string.parser_field_reference, sms.reference)
    preview ?: return
    val transfer = preview.transfer
    when {
        transfer != null -> Text(stringResource(transferLabel(transfer)))
        preview.needsReview -> Text(stringResource(R.string.test_sms_needs_review))
        else -> {
            val category = preview.category
            Field(
                R.string.test_sms_field_category,
                categoryName(category?.name, category?.seedKey)
            )
            if (preview.tags.isNotEmpty()) {
                Field(R.string.test_sms_field_tags, preview.tags.joinToString(", "))
            }
        }
    }
    preview.duplicate?.let {
        Text(
            stringResource(duplicateLabel(it.match)),
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun RulesTried(rules: List<TriedRule>) {
    Text(
        if (rules.isEmpty()) {
            stringResource(R.string.test_sms_no_rules)
        } else {
            stringResource(R.string.test_sms_rules_tried, rules.joinToString(", ") { it.id })
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

private const val BODY_LINES = 4
private val DATE_TIME = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
