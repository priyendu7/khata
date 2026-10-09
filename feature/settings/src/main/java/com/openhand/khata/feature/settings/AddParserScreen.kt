package com.openhand.khata.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.core.ui.segmentCardColors
import com.openhand.khata.sms.ingest.SmsInbox
import com.openhand.khata.sms.parser.CodeCheck
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.CustomRules

/** Settings > Parsers > Add (PRD feature 8): paste a rule code, test it on an SMS, save it. */
@Composable
fun AddParserScreen(
    onDone: () -> Unit,
    onMake: () -> Unit,
    viewModel: AddParserViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    val check = remember(code) { code.takeIf { it.isNotBlank() }?.let(CustomRules::check) }
    val rule = (check as? CodeCheck.Valid)?.rule
    val canReadSms = remember { context.hasSmsPermission() }
    LaunchedEffect(rule?.headers, canReadSms) {
        if (rule != null && canReadSms) viewModel.loadRecent(rule.headers)
    }
    LaunchedEffect(step) { if (step == SaveStep.Done) onDone() }
    AddParserContent(
        onBack = onDone,
        onMake = onMake,
        code = code,
        onCode = { code = it },
        check = check,
        replaces = rule != null && rule.rule.id in savedIds,
        canReadSms = canReadSms,
        recent = if (rule != null) recent else emptyList(),
        saving = step != SaveStep.Editing,
        onSave = viewModel::save
    )
    (step as? SaveStep.Offer)?.let {
        ReadWaitingDialog(
            it.count,
            onRead = viewModel::readWaiting,
            onSkip = viewModel::skipWaiting
        )
    }
}

@Composable
fun AddParserContent(
    onBack: () -> Unit,
    onMake: () -> Unit,
    code: String,
    onCode: (String) -> Unit,
    check: CodeCheck?,
    replaces: Boolean,
    canReadSms: Boolean,
    recent: List<SmsInbox.Message>,
    saving: Boolean,
    onSave: (CompiledRule) -> Unit,
    now: () -> Long = System::currentTimeMillis
) {
    SubScreen(title = stringResource(R.string.parser_add_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MakeCard(onMake)
            Text(
                stringResource(R.string.parser_add_intro),
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = code,
                onValueChange = onCode,
                label = { Text(stringResource(R.string.parser_code_label)) },
                isError = check != null && check !is CodeCheck.Valid,
                minLines = 3,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false
                ),
                modifier = Modifier.fillMaxWidth()
            )
            when (check) {
                null -> Unit
                is CodeCheck.Unreadable -> ErrorText(stringResource(codeErrorMessage(check.error)))
                is CodeCheck.Invalid -> {
                    ErrorText(stringResource(R.string.parser_invalid_title))
                    check.errors.forEach { ErrorText("• " + stringResource(ruleErrorMessage(it))) }
                }
                is CodeCheck.Valid -> {
                    RuleSummary(check.rule, replaces)
                    HorizontalDivider()
                    RuleTest(check.rule, canReadSms, recent, now)
                    Button(
                        onClick = { onSave(check.rule) },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(UiR.string.save)) }
                }
            }
        }
    }
}

/** Most people make a rule from an SMS on the phone rather than paste one. */
@Composable
private fun MakeCard(onMake: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = segmentCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.parser_add_make_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.parser_add_make_body),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onMake) { Text(stringResource(R.string.parser_make_title)) }
        }
    }
}

@Composable
internal fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun RuleSummary(rule: CompiledRule, replaces: Boolean) {
    Card(Modifier.fillMaxWidth(), colors = segmentCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(rule.rule.bank, style = MaterialTheme.typography.titleMedium)
            Text(rule.rule.id, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(
                    R.string.parser_rule_senders,
                    rule.headers.sorted().joinToString(", ")
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(accountTypeLabel(rule.rule.accountType)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    if (replaces) {
        Text(
            stringResource(R.string.parser_replaces, rule.rule.id),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Try the rule on a recent SMS from its bank, or on one the user pastes. */
@Composable
private fun RuleTest(
    rule: CompiledRule,
    canReadSms: Boolean,
    recent: List<SmsInbox.Message>,
    now: () -> Long
) {
    var picked by rememberSaveable(rule.rule) { mutableStateOf<Int?>(null) }
    var pasted by rememberSaveable(rule.rule) { mutableStateOf("") }
    Text(stringResource(R.string.parser_test_title), style = MaterialTheme.typography.titleMedium)
    when {
        !canReadSms -> ParserHint(stringResource(R.string.parser_test_no_permission))
        recent.isEmpty() -> ParserHint(stringResource(R.string.parser_test_no_recent))
        else -> {
            ParserHint(stringResource(R.string.parser_test_recent))
            // One group, without the form's spacing between its items.
            Column {
                recent.forEachIndexed { index, sms ->
                    SegmentListItem(
                        index = index,
                        count = recent.size,
                        headlineContent = {
                            Text(sms.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        },
                        overlineContent = { Text(sms.sender) },
                        leadingContent = {
                            RadioButton(selected = picked == index, onClick = null)
                        },
                        modifier = Modifier.selectable(
                            selected = picked == index,
                            role = Role.RadioButton
                        ) {
                            picked = index
                            pasted = ""
                        }
                    )
                }
            }
        }
    }
    OutlinedTextField(
        value = pasted,
        onValueChange = {
            pasted = it
            picked = null
        },
        label = { Text(stringResource(R.string.parser_test_paste_label)) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth()
    )
    val sms = picked?.let(recent::getOrNull)
    val result = when {
        sms != null -> testRule(rule, sms.sender, sms.body, sms.receivedAt)
        pasted.isNotBlank() -> testRule(rule, null, pasted, now())
        else -> return
    }
    TestResult(result)
}

@Composable
internal fun ParserHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** After saving: offer to read the SMS waiting in To review with the new rule. */
@Composable
internal fun ReadWaitingDialog(count: Int, onRead: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(pluralStringResource(R.plurals.parser_retry_title, count, count)) },
        text = { Text(pluralStringResource(R.plurals.parser_retry_body, count, count)) },
        confirmButton = {
            TextButton(onClick = onRead) { Text(stringResource(R.string.parser_retry_yes)) }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.parser_retry_no)) }
        }
    )
}
