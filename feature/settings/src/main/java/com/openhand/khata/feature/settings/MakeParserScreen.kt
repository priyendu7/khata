package com.openhand.khata.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openhand.khata.core.ui.SubScreen
import com.openhand.khata.sms.ingest.SmsInbox
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.DateFormats
import com.openhand.khata.sms.parser.RuleAccountType
import com.openhand.khata.sms.parser.RuleCheck
import com.openhand.khata.sms.parser.RuleCode
import com.openhand.khata.sms.parser.RuleDirection
import com.openhand.khata.sms.parser.RuleMaker

/**
 * Make a parser from an SMS (PRD feature 8): pick or paste one, mark its parts, check the rule on
 * recent SMS from the same senders, save it, and share its code if you like. When re-marking a
 * rule (Settings > Parsers > Edit), the rule's code goes to [onRemarked] instead of being saved.
 */
@Composable
fun MakeParserScreen(
    onDone: () -> Unit,
    onRemarked: (String) -> Unit = {},
    viewModel: MakeParserViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val form by viewModel.form.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val canReadSms = remember { context.hasSmsPermission() }
    val current = form
    val savedRule = saved
    when {
        // Saved: the offer to read waiting SMS, if any, shows on top.
        savedRule != null ->
            SavedRuleContent(
                onDone = onDone,
                rule = savedRule,
                onCopy = { context.copyToClipboard(it) }
            )
        current == null -> {
            LaunchedEffect(canReadSms) { if (canReadSms) viewModel.loadCandidates() }
            PickSmsContent(
                onBack = onDone,
                canReadSms = canReadSms,
                candidates = candidates,
                onPick = { viewModel.choose(it.sender, it.body, it.receivedAt) },
                onPaste = { viewModel.choose(null, it, System.currentTimeMillis()) }
            )
        }
        else -> {
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
    }
    (step as? SaveStep.Offer)?.let {
        ReadWaitingDialog(
            it.count,
            onRead = viewModel::readWaiting,
            onSkip = viewModel::skipWaiting
        )
    }
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
                    candidates.forEach { sms ->
                        ListItem(
                            headlineContent = {
                                Text(sms.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            },
                            overlineContent = { Text(sms.sender) },
                            modifier = Modifier.clickable { onPick(sms) }
                        )
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

/** Step two: mark the parts of the SMS, fill in the details, check the rule, save it. */
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
            ParserHint(stringResource(R.string.parser_make_mark_hint))
            SmsWords(form, onTap = { onForm(form.tap(it)) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuleMaker.Field.entries.forEach { field ->
                    OutlinedButton(
                        onClick = { onForm(form.mark(field)) },
                        enabled = form.selection != null
                    ) { Text(stringResource(fieldLabel(field))) }
                }
            }
            Marks(form, onForm)
            HorizontalDivider()
            RuleDetails(form, onForm)
            HorizontalDivider()
            RuleResult(form, check, canReadSms, recent, saving, onSave, remarking)
        }
    }
}

/** The SMS as tappable words, coloured by what they're marked as. */
@Composable
private fun SmsWords(form: MakerForm, onTap: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            form.words.forEachIndexed { index, word ->
                val selected = form.selection?.contains(index) == true
                val marked = form.fieldAt(index) != null
                val colors = MaterialTheme.colorScheme
                Surface(
                    color = when {
                        selected -> colors.primary
                        marked -> colors.tertiaryContainer
                        else -> colors.surface
                    },
                    contentColor = when {
                        selected -> colors.onPrimary
                        marked -> colors.onTertiaryContainer
                        else -> colors.onSurface
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .semantics { this.selected = selected }
                        .clickable(role = Role.Button) { onTap(index) }
                ) {
                    Text(
                        word.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/** What's marked so far, each with a way to clear it. */
@Composable
private fun Marks(form: MakerForm, onForm: (MakerForm) -> Unit) {
    form.marks.sortedBy { it.start }.forEach { mark ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    R.string.parser_field,
                    stringResource(fieldLabel(mark.field)),
                    form.markedText(mark)
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onForm(form.unmark(mark.field)) }) {
                Text(stringResource(R.string.parser_make_unmark))
            }
        }
    }
    val date = form.marks.firstOrNull { it.field == RuleMaker.Field.DATE } ?: return
    Text(
        stringResource(R.string.parser_make_date_format),
        style = MaterialTheme.typography.titleSmall
    )
    val offered = DateFormats.matching(form.markedText(date)).ifEmpty { DateFormats.COMMON }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        offered.forEach { format ->
            FilterChip(
                selected = form.dateFormat == format,
                onClick = { onForm(form.copy(dateFormat = format)) },
                label = { Text(format) }
            )
        }
    }
    OutlinedTextField(
        value = form.dateFormat.orEmpty(),
        onValueChange = { onForm(form.copy(dateFormat = it)) },
        label = { Text(stringResource(R.string.parser_make_date_format_other)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth()
    )
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

/** Step three: saved. Shows the rule code to share, and that the app sends nothing. */
@Composable
fun SavedRuleContent(onDone: () -> Unit, rule: CompiledRule, onCopy: (String) -> Unit) {
    val code = remember(rule) { RuleCode.encode(rule.rule) }
    var copied by rememberSaveable { mutableStateOf(false) }
    SubScreen(title = stringResource(R.string.parser_make_title), onBack = onDone) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.parser_saved_title, rule.rule.id),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.parser_saved_body),
                style = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.parser_share_title),
                style = MaterialTheme.typography.titleMedium
            )
            Card(Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(
                        code,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            OutlinedButton(onClick = {
                onCopy(code)
                copied = true
            }) {
                Text(
                    stringResource(
                        if (copied) R.string.parser_share_copied else R.string.parser_share_copy
                    )
                )
            }
            ParserHint(stringResource(R.string.parser_share_note))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.parser_make_done))
            }
        }
    }
}

private fun fieldLabel(field: RuleMaker.Field): Int = when (field) {
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
