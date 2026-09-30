package com.openhand.khata.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.CustomParserRepository
import com.openhand.khata.core.model.CustomParser
import com.openhand.khata.sms.ingest.SmsInbox
import com.openhand.khata.sms.ingest.SmsIngestor
import com.openhand.khata.sms.parser.BuiltInRules
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.RuleCode
import com.openhand.khata.sms.parser.SenderId
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A bank the app reads out of the box, for the read-only list in Settings > Parsers. */
data class BuiltInBank(val bank: String, val rules: Int, val senders: List<String>)

internal fun builtInBanks(rules: List<CompiledRule> = BuiltInRules.load()): List<BuiltInBank> =
    rules.groupBy { it.rule.bank }.map { (bank, bankRules) ->
        BuiltInBank(bank, bankRules.size, bankRules.flatMap { it.headers }.distinct().sorted())
    }

/** Settings > Parsers (PRD feature 8): the user's rules, and the built-in ones. */
@HiltViewModel
class ParsersViewModel @Inject constructor(private val parsers: CustomParserRepository) :
    ViewModel() {
    /** Null until loaded. */
    val custom: StateFlow<List<CustomParser>?> = parsers.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), null)

    val builtIn: StateFlow<List<BuiltInBank>> = flow { emit(builtInBanks()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), emptyList())

    fun setEnabled(parser: CustomParser, enabled: Boolean) {
        viewModelScope.launch { parsers.setEnabled(parser.id, enabled) }
    }

    fun delete(parser: CustomParser) {
        viewModelScope.launch { parsers.delete(parser.id) }
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
    ingestor: SmsIngestor,
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
            _recent.value = withContext(Dispatchers.IO) {
                recentSms(inbox, headers, now)
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

/** The last [RECENT_COUNT] SMS from [headers] in the last [RECENT_DAYS] days, newest first. */
internal fun recentSms(inbox: SmsInbox, headers: Set<String>, now: Long): List<SmsInbox.Message> =
    try {
        inbox.bankMessages(now - TimeUnit.DAYS.toMillis(RECENT_DAYS)) { sender ->
            SenderId.parse(sender)?.let { !it.promotional && it.header in headers } == true
        }.takeLast(RECENT_COUNT).reversed()
    } catch (_: SecurityException) {
        // The permission was taken away while the screen was open.
        emptyList()
    }

internal const val STOP_AFTER_MS = 5_000L
private const val RECENT_DAYS = 60L
private const val RECENT_COUNT = 10
