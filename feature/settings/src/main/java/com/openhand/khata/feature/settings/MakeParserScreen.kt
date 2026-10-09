package com.openhand.khata.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.ui.SegmentListItem
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.sms.ingest.SmsInbox
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.RuleAccountType
import com.openhand.khata.sms.parser.RuleCheck
import com.openhand.khata.sms.parser.RuleCode
import com.openhand.khata.sms.parser.RuleDirection
import com.openhand.khata.sms.parser.RuleMaker

/**
 * Make a parser from an SMS (PRD feature 8): pick or paste one, mark its parts one at a time, and
 * check the rule on recent SMS from the same senders. Saving records the waiting SMS the rule now
 * reads and calls [onSaved] with a message saying how many. When re-marking a rule (Settings >
 * Parsers > Edit), the rule's code goes to [onRemarked] instead of being saved.
 */
@Composable
fun MakeParserScreen(
    onDone: () -> Unit,
    onSaved: (String) -> Unit = { onDone() },
    onRemarked: (String) -> Unit = {},
    viewModel: MakeParserViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val form by viewModel.form.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    val canReadSms = remember { context.hasSmsPermission() }
    val resources = LocalResources.current
    LaunchedEffect(step) {
        val recorded = (step as? SaveStep.Recorded)?.recorded ?: return@LaunchedEffect
        onSaved(
            if (recorded > 0) {
                resources.getQuantityString(R.plurals.parser_saved_recorded, recorded, recorded)
            } else {
                resources.getString(R.string.parser_saved)
            }
        )
    }
    val current = form
    if (current == null) {
        LaunchedEffect(canReadSms) { if (canReadSms) viewModel.loadCandidates() }
        PickSmsContent(
            onBack = onDone,
            canReadSms = canReadSms,
            candidates = candidates,
            onPick = { viewModel.choose(it.sender, it.body, it.receivedAt) },
            onPaste = { viewModel.choose(null, it, System.currentTimeMillis()) }
        )
        return
    }
    val check = remember(current, savedIds) { current.check(savedIds) }
    val headers = (check as? RuleCheck.Valid)?.rule?.headers
    LaunchedEffect(headers, canReadSms) {
        if (headers != null && canReadSms) viewModel.loadRecent(headers)
    }
    MakeParserContent(
        onBack = onDone,
        form = current,
        onForm = viewModel::update,
        check = check,
        canReadSms = canReadSms,
        recent = recent,
        saving = step != SaveStep.Editing,
        onSave = { rule ->
            if (viewModel.remarking != null) {
                onRemarked(RuleCode.encode(rule.rule))
            } else {
                viewModel.save(rule)
            }
        },
        remarking = viewModel.remarking != null
    )
}

/** Step one: pick a recent SMS from a bank, or paste one. */
@Composable
fun PickSmsContent(
    onBack: () -> Unit,
    canReadSms: Boolean,
    candidates: List<SmsInbox.Message>?,
    onPick: (SmsInbox.Message) -> Unit,
    onPaste: (String) -> Unit
) {
    var pasted by rememberSaveable { mutableStateOf("") }
    SubScreen(title = stringResource(R.string.parser_make_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.parser_make_intro),
                style = MaterialTheme.typography.bodyMedium
            )
            when {
                !canReadSms -> ParserHint(stringResource(R.string.parser_test_no_permission))
                candidates == null -> Unit
                candidates.isEmpty() -> ParserHint(
                    stringResource(R.string.parser_make_no_candidates)
                )
                else -> {
                    ParserHint(stringResource(R.string.parser_make_pick_recent))
                    // One group, without the form's spacing between its items.
                    Column {
                        candidates.forEachIndexed { index, sms ->
                            SegmentListItem(
                                index = index,
                                count = candidates.size,
                                headlineContent = {
                                    Text(sms.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                },
                                overlineContent = { Text(sms.sender) },
                                modifier = Modifier.clickable { onPick(sms) }
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text(stringResource(R.string.parser_test_paste_label)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onPaste(pasted.trim()) },
                enabled = pasted.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.parser_make_use)) }
        }
    }
}

/**
 * Step two: mark the parts of the SMS one field at a time, then fill in the details, check the
 * rule and save it.
 */
@Composable
fun MakeParserContent(
    onBack: () -> Unit,
    form: MakerForm,
    onForm: (MakerForm) -> Unit,
    check: RuleCheck?,
    canReadSms: Boolean,
    recent: List<SmsInbox.Message>,
    saving: Boolean,
    onSave: (CompiledRule) -> Unit,
    remarking: Boolean = false
) {
    SubScreen(title = stringResource(R.string.parser_make_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val step = form.step
            if (step != null) {
                MarkStep(form, step, onForm)
            } else {
                MarkSummary(form, onForm)
                SmsWords(form, onTap = null)
                HorizontalDivider()
                RuleDetails(form, onForm)
                HorizontalDivider()
                RuleResult(form, check, canReadSms, recent, saving, onSave, remarking)
            }
        }
    }
}

/** Direction, account type, bank and senders. */
@Composable
private fun RuleDetails(form: MakerForm, onForm: (MakerForm) -> Unit) {
    Text(
        stringResource(R.string.parser_field_direction),
        style = MaterialTheme.typography.titleSmall
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RuleDirection.entries.forEach { direction ->
            FilterChip(
                selected = form.direction == direction,
                onClick = { onForm(form.copy(direction = direction)) },
                label = { Text(stringResource(directionLabel(direction.direction))) }
            )
        }
    }
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
    OutlinedTextField(
        value = form.bank,
        onValueChange = { onForm(form.copy(bank = it)) },
        label = { Text(stringResource(R.string.parser_make_bank)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = form.senders,
        onValueChange = { onForm(form.copy(senders = it)) },
        label = { Text(stringResource(R.string.parser_make_senders)) },
        supportingText = { Text(stringResource(R.string.parser_make_senders_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** The rule's problems, or what it reads from this SMS and recent ones; then Save. */
@Composable
private fun RuleResult(
    form: MakerForm,
    check: RuleCheck?,
    canReadSms: Boolean,
    recent: List<SmsInbox.Message>,
    saving: Boolean,
    onSave: (CompiledRule) -> Unit,
    remarking: Boolean
) {
    when (check) {
        null -> ParserHint(stringResource(R.string.parser_make_need_amount))
        is RuleCheck.Invalid -> {
            ErrorText(stringResource(R.string.parser_invalid_title))
            check.errors.forEach { ErrorText("• " + stringResource(ruleErrorMessage(it))) }
        }
        is RuleCheck.Valid -> {
            val rule = check.rule
            val own = testRule(rule, form.sender, form.body, form.receivedAt)
            Text(
                stringResource(R.string.parser_make_check_title),
                style = MaterialTheme.typography.titleMedium
            )
            ParserHint(stringResource(R.string.parser_make_rule_id, rule.rule.id))
            Text(
                stringResource(R.string.parser_make_this_sms),
                style = MaterialTheme.typography.titleSmall
            )
            TestResult(own)
            if (canReadSms) {
                val others = recent.filterNot {
                    it.body == form.body && it.receivedAt == form.receivedAt
                }
                if (others.isNotEmpty()) {
                    ParserHint(
                        stringResource(R.string.parser_make_check_intro)
                    )
                }
                others.forEach { sms ->
                    Text(
                        sms.body,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TestResult(testRule(rule, sms.sender, sms.body, sms.receivedAt))
                }
            } else {
                ParserHint(stringResource(R.string.parser_make_check_no_permission))
            }
            if (own == null) ErrorText(stringResource(R.string.parser_make_doesnt_read_own))
            Button(
                onClick = { onSave(rule) },
                enabled = own != null && !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (remarking) {
                            R.string.parser_make_use_pattern
                        } else {
                            R.string.parser_make_save
                        }
                    )
                )
            }
        }
    }
}

internal fun fieldLabel(field: RuleMaker.Field): Int = when (field) {
    RuleMaker.Field.AMOUNT -> R.string.parser_field_amount
    RuleMaker.Field.PAYEE -> R.string.parser_field_payee
    RuleMaker.Field.ACCOUNT -> R.string.parser_field_account
    RuleMaker.Field.REF -> R.string.parser_field_reference
    RuleMaker.Field.DATE -> R.string.parser_field_date
}

internal fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Khata rule", text))
}
