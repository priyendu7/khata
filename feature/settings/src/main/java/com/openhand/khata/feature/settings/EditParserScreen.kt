package com.openhand.khata.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.ui.R as UiR
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.core.ui.TagInput
import com.openhand.khata.sms.ingest.SmsInbox
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.RuleAccountType
import com.openhand.khata.sms.parser.RuleCheck
import com.openhand.khata.sms.parser.RuleCode
import com.openhand.khata.sms.parser.RuleDirection

/**
 * Settings > Parsers > Edit (#111). [onRemark] opens the rule maker with the rule's code; the
 * pattern made there comes back through [REMARKED_RULE_KEY].
 */
@Composable
fun EditParserScreen(
    onDone: () -> Unit,
    onRemark: (String) -> Unit,
    viewModel: EditParserViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val form by viewModel.form.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    val canReadSms = remember { context.hasSmsPermission() }
    LaunchedEffect(step) { if (step == EditStep.DONE) onDone() }
    form?.let { current ->
        EditParserContent(
            onBack = onDone,
            form = current,
            builtIn = viewModel.builtIn,
            onForm = viewModel::update,
            canReadSms = canReadSms,
            recent = recent,
            onCheck = viewModel::loadRecent,
            onRemark = { onRemark(RuleCode.encode(current.rule())) },
            saving = step != EditStep.EDITING,
            onSave = viewModel::save
        )
    }
}

@Composable
fun EditParserContent(
    onBack: () -> Unit,
    form: RuleEditForm,
    builtIn: Boolean,
    onForm: (RuleEditForm) -> Unit,
    canReadSms: Boolean,
    recent: List<SmsInbox.Message>?,
    onCheck: (Set<String>) -> Unit,
    onRemark: () -> Unit,
    saving: Boolean,
    onSave: (CompiledRule) -> Unit
) {
    val check = remember(form) { form.check() }
    SubScreen(
        title = stringResource(R.string.parser_edit_title, form.id),
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (builtIn) ParserHint(stringResource(R.string.parser_edit_builtin_note))
            OutlinedTextField(
                value = form.bank,
                onValueChange = { onForm(form.copy(bank = it)) },
                label = { Text(stringResource(R.string.parser_make_bank)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TagInput(
                tags = form.senders,
                label = stringResource(R.string.parser_make_senders),
                suggestions = emptyList(),
                onQueryChange = {},
                onAdd = { onForm(form.addSender(it)) },
                onRemove = { onForm(form.removeSender(it)) },
                placeholder = stringResource(R.string.parser_edit_senders_hint),
                addLabel = stringResource(R.string.parser_edit_sender_add)
            )
            DirectionFields(form, onForm)
            AccountTypeField(form, onForm)
            if (form.hasDate) {
                OutlinedTextField(
                    value = form.dateFormat,
                    onValueChange = { onForm(form.copy(dateFormat = it)) },
                    label = { Text(stringResource(R.string.parser_make_date_format_other)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Advanced(form, onForm, onRemark)
            HorizontalDivider()
            when (check) {
                is RuleCheck.Invalid -> {
                    ErrorText(stringResource(R.string.parser_invalid_title))
                    check.errors.forEach { ErrorText("• " + stringResource(ruleErrorMessage(it))) }
                }
                is RuleCheck.Valid -> CheckSection(check.rule, canReadSms, recent, onCheck)
            }
            Button(
                onClick = { (check as? RuleCheck.Valid)?.let { onSave(it.rule) } },
                enabled = check is RuleCheck.Valid && !saving,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(UiR.string.save)) }
        }
    }
}

/** A fixed direction, or words in the SMS that decide it. */
@Composable
private fun DirectionFields(form: RuleEditForm, onForm: (RuleEditForm) -> Unit) {
    Text(
        stringResource(R.string.parser_field_direction),
        style = MaterialTheme.typography.titleSmall
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            true to R.string.parser_edit_direction_fixed,
            false to R.string.parser_edit_direction_words
        ).forEach { (fixed, label) ->
            FilterChip(
                selected = form.fixedDirection == fixed,
                onClick = { onForm(form.copy(fixedDirection = fixed)) },
                label = { Text(stringResource(label)) }
            )
        }
    }
    if (form.fixedDirection) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuleDirection.entries.forEach { direction ->
                FilterChip(
                    selected = form.direction == direction,
                    onClick = { onForm(form.copy(direction = direction)) },
                    label = { Text(stringResource(directionLabel(direction.direction))) }
                )
            }
        }
    } else {
        ParserHint(stringResource(R.string.parser_edit_words_hint))
        RuleDirection.entries.forEach { direction ->
            OutlinedTextField(
                value = form.words[direction].orEmpty(),
                onValueChange = { onForm(form.copy(words = form.words + (direction to it))) },
                label = {
                    Text(
                        stringResource(
                            R.string.parser_edit_words_label,
                            stringResource(directionLabel(direction.direction))
                        )
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AccountTypeField(form: RuleEditForm, onForm: (RuleEditForm) -> Unit) {
    Text(
        stringResource(R.string.parser_make_account_type),
        style = MaterialTheme.typography.titleSmall
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RuleAccountType.entries.forEach { type ->
            FilterChip(
                selected = form.accountType == type,
                onClick = { onForm(form.copy(accountType = type)) },
                label = { Text(stringResource(accountTypeLabel(type))) }
            )
        }
    }
}

/** The pattern itself, and making it again from an SMS. Hidden at first. */
@Composable
private fun Advanced(form: RuleEditForm, onForm: (RuleEditForm) -> Unit, onRemark: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = onRemark, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.parser_edit_remark))
    }
    ParserHint(stringResource(R.string.parser_edit_remark_hint))
    TextButton(onClick = { open = !open }) {
        Text(
            stringResource(
                if (open) R.string.parser_edit_advanced_hide else R.string.parser_edit_advanced
            )
        )
    }
    if (!open) return
    OutlinedTextField(
        value = form.pattern,
        onValueChange = { onForm(form.copy(pattern = it)) },
        label = { Text(stringResource(R.string.parser_edit_pattern)) },
        supportingText = { Text(stringResource(R.string.parser_edit_pattern_hint)) },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Check: what the edited rule reads from each recent SMS from its senders. */
@Composable
private fun CheckSection(
    rule: CompiledRule,
    canReadSms: Boolean,
    recent: List<SmsInbox.Message>?,
    onCheck: (Set<String>) -> Unit
) {
    if (!canReadSms) {
        ParserHint(stringResource(R.string.parser_make_check_no_permission))
        return
    }
    OutlinedButton(onClick = { onCheck(rule.headers) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.parser_edit_check))
    }
    when {
        recent == null -> Unit
        recent.isEmpty() -> ParserHint(stringResource(R.string.parser_edit_check_none))
        else -> {
            val results = recent.map { testRule(rule, it.sender, it.body, it.receivedAt) }
            Text(
                pluralStringResource(
                    R.plurals.parser_edit_check_count,
                    recent.size,
                    results.count { it != null },
                    recent.size
                ),
                style = MaterialTheme.typography.titleSmall
            )
            recent.zip(results).forEach { (sms, result) ->
                Text(
                    sms.body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                TestResult(result)
            }
        }
    }
}
