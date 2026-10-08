package com.openhand.khata.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.BuiltInRuleOverrideRepository
import com.openhand.khata.core.data.CustomParserRepository
import com.openhand.khata.core.model.BuiltInRuleOverride
import com.openhand.khata.core.model.CustomParser
import com.openhand.khata.core.model.SenderId
import com.openhand.khata.sms.ingest.SmsInbox
import com.openhand.khata.sms.ingest.SmsIngestor
import com.openhand.khata.sms.parser.BuiltInRules
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.ParserRule
import com.openhand.khata.sms.parser.RuleCode
import com.openhand.khata.sms.parser.RuleCodeResult
import com.openhand.khata.sms.parser.SmsParser
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One rule in Settings > Parsers, custom or built-in, as the engine runs it: for an edited
 * built-in rule, [rule] is the edited version.
 */
data class RuleRow(
    val rule: ParserRule,
    val builtIn: Boolean,
    val enabled: Boolean,
    /** A custom rule's row id; null for a built-in rule. */
    val customId: Long? = null,
    /** A built-in rule the user edited. */
    val edited: Boolean = false,
    /** An app update changed this built-in rule since the user edited it. */
    val updateAvailable: Boolean = false,
    /** The built-in rule's hash as shipped now, stored by Keep mine. */
    val builtInHash: String? = null
) {
    /** The rule code that Copy code puts on the clipboard. */
    val code: String get() = RuleCode.encode(rule)
}

/** Settings > Parsers: custom rules in the engine's order, then built-in ones by bank. */
data class ParserRows(val custom: List<RuleRow>, val builtIn: List<RuleRow>)

/**
 * The rows for [custom] rules and the [builtIn] rules with the user's [overrides]. An override for
 * a rule id that isn't built in (any more) is ignored.
 */
internal fun parserRows(
    custom: List<CustomParser>,
    builtIn: List<ParserRule>,
    overrides: List<BuiltInRuleOverride>
): ParserRows {
    val byId = overrides.associateBy { it.ruleId }
    return ParserRows(
        custom = custom.mapNotNull { parser ->
            decode(parser.code)?.let { RuleRow(it, false, parser.enabled, customId = parser.id) }
        },
        builtIn = builtIn.map { original ->
            val override = byId[original.id]
            val hash = BuiltInRules.hash(original)
            val editedCode = override?.editedCode
            RuleRow(
                // An edit that can't be read runs as the original, so it's shown as that.
                rule = editedCode?.let(::decode)?.takeIf { it.id == original.id } ?: original,
                builtIn = true,
                enabled = override?.enabled ?: true,
                edited = editedCode != null,
                updateAvailable = editedCode != null && override.baseHash != hash,
                builtInHash = hash
            )
        }
    )
}

private fun decode(code: String): ParserRule? =
    (RuleCode.decode(code) as? RuleCodeResult.Decoded)?.rule

/** Settings > Parsers (PRD feature 8): the user's rules and the built-in ones, each managed alike. */
@HiltViewModel
class ParsersViewModel @Inject constructor(
    private val parsers: CustomParserRepository,
    private val overrides: BuiltInRuleOverrideRepository
) : ViewModel() {
    private val builtIn = flow { emit(BuiltInRules.all()) }.flowOn(Dispatchers.Default)

    /** Null until loaded. */
    val rows: StateFlow<ParserRows?> =
        combine(parsers.observeAll(), builtIn, overrides.observeAll(), ::parserRows)
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), null)

    fun setEnabled(row: RuleRow, enabled: Boolean) {
        viewModelScope.launch {
            val id = row.customId
            if (id != null) {
                parsers.setEnabled(id, enabled)
            } else {
                overrides.setEnabled(row.rule.id, enabled)
            }
        }
    }

    fun delete(row: RuleRow) {
        val id = row.customId ?: return
        viewModelScope.launch { parsers.delete(id) }
    }

    /** Reset to built-in, and Use new version: the edit goes. */
    fun reset(row: RuleRow) {
        viewModelScope.launch { overrides.clearEdit(row.rule.id) }
    }

    /** Keep mine: the edit stays, and the newer built-in rule is no longer offered. */
    fun keepMine(row: RuleRow) {
        val hash = row.builtInHash ?: return
        viewModelScope.launch { overrides.keepEdit(row.rule.id, hash) }
    }
}

/** Where saving a new rule has got to. */
sealed interface SaveStep {
    data object Editing : SaveStep

    /** Saved; [count] SMS waiting in To review can be read with it. */
    data class Offer(val count: Int) : SaveStep

    data object Reading : SaveStep

    data object Done : SaveStep
}

/** Settings > Parsers > Add: paste a code, test it on an SMS, save it. */
@HiltViewModel
class AddParserViewModel @Inject constructor(
    parsers: CustomParserRepository,
    private val ingestor: SmsIngestor,
    private val inbox: SmsInbox
) : ViewModel() {
    /** Rule ids already saved, to say when saving replaces one. */
    val savedIds: StateFlow<Set<String>> = parsers.observeAll()
        .map { all -> all.mapTo(mutableSetOf()) { it.ruleId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), emptySet())

    private val _recent = MutableStateFlow<List<SmsInbox.Message>>(emptyList())

    /** Recent SMS from the pasted rule's senders, newest first, to test it on. */
    val recent: StateFlow<List<SmsInbox.Message>> = _recent.asStateFlow()

    private val saving = RuleSaving(viewModelScope, parsers, ingestor)
    val step: StateFlow<SaveStep> = saving.step

    /** Needs the SMS permission. Only SMS from [headers] are read. */
    fun loadRecent(headers: Set<String>, now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val parser = ingestor.parser()
            _recent.value = withContext(Dispatchers.IO) {
                recentSms(inbox, parser, headers, now)
            }
        }
    }

    fun save(rule: CompiledRule, now: Long = System.currentTimeMillis()) = saving.save(rule, now)

    /** Records the SMS waiting in To review that the rules can now read. */
    fun readWaiting() = saving.readWaiting()

    fun skipWaiting() = saving.skipWaiting()
}

/** Saves a checked rule, then offers to record the SMS in To review that it can now read. */
internal class RuleSaving(
    private val scope: CoroutineScope,
    private val parsers: CustomParserRepository,
    private val ingestor: SmsIngestor
) {
    private val _step = MutableStateFlow<SaveStep>(SaveStep.Editing)
    val step: StateFlow<SaveStep> = _step.asStateFlow()

    fun save(rule: CompiledRule, now: Long) {
        if (_step.value != SaveStep.Editing) return
        _step.value = SaveStep.Reading
        scope.launch {
            parsers.save(rule.rule.id, rule.rule.bank, RuleCode.encode(rule.rule), now)
            val readable = ingestor.unparsedReadableBy(rule)
            _step.value = if (readable > 0) SaveStep.Offer(readable) else SaveStep.Done
        }
    }

    fun readWaiting() {
        _step.value = SaveStep.Reading
        scope.launch {
            ingestor.retryUnparsed()
            _step.value = SaveStep.Done
        }
    }

    fun skipWaiting() {
        _step.value = SaveStep.Done
    }
}

/**
 * The last [RECENT_COUNT] SMS from [headers] in the last [RECENT_DAYS] days that [parser]'s sender
 * filters let through, newest first.
 */
internal fun recentSms(
    inbox: SmsInbox,
    parser: SmsParser,
    headers: Set<String>,
    now: Long
): List<SmsInbox.Message> = try {
    inbox.businessMessages(now - TimeUnit.DAYS.toMillis(RECENT_DAYS)) { sender ->
        parser.accepts(sender) && SenderId.parse(sender)?.header in headers
    }.takeLast(RECENT_COUNT).reversed()
} catch (_: SecurityException) {
    // The permission was taken away while the screen was open.
    emptyList()
}

internal const val STOP_AFTER_MS = 5_000L
private const val RECENT_DAYS = 60L
private const val RECENT_COUNT = 10
