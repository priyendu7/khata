package com.openhand.khata.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.CustomParserRepository
import com.openhand.khata.core.data.UnparsedSmsRepository
import com.openhand.khata.sms.ingest.SmsInbox
import com.openhand.khata.sms.ingest.SmsIngestor
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.RuleMaker
import com.openhand.khata.sms.parser.SmsParser
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens the rule maker on this SMS from To review; absent to pick or paste one. */
const val MAKE_FROM_UNPARSED_ARG = "unparsedId"
const val NO_UNPARSED = -1L

/** Or opens it on this sender and SMS text, from Test a message. */
const val MAKE_FROM_SENDER_ARG = "sender"
const val MAKE_FROM_BODY_ARG = "body"

/**
 * Make a parser from an SMS (PRD feature 8): pick or paste an SMS, mark its parts, check the rule
 * on recent SMS from that bank, save it and share its code. Nothing leaves the phone.
 */
@HiltViewModel
class MakeParserViewModel @Inject constructor(
    savedState: SavedStateHandle,
    parsers: CustomParserRepository,
    private val unparsed: UnparsedSmsRepository,
    private val ingestor: SmsIngestor,
    private val inbox: SmsInbox
) : ViewModel() {
    private val _form = MutableStateFlow<MakerForm?>(null)

    /** Null until an SMS is picked or pasted. */
    val form: StateFlow<MakerForm?> = _form.asStateFlow()

    /** Rule ids already saved, so a new rule gets its own. */
    val savedIds: StateFlow<Set<String>> = parsers.observeAll()
        .map { all -> all.mapTo(mutableSetOf()) { it.ruleId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), emptySet())

    private val _candidates = MutableStateFlow<List<SmsInbox.Message>?>(null)

    /** Recent SMS from any business sender that may be a transaction. Null until loaded. */
    val candidates: StateFlow<List<SmsInbox.Message>?> = _candidates.asStateFlow()

    private val _recent = MutableStateFlow<List<SmsInbox.Message>>(emptyList())

    /** Recent SMS from the rule's senders, newest first, to check it on. */
    val recent: StateFlow<List<SmsInbox.Message>> = _recent.asStateFlow()

    private val _saved = MutableStateFlow<CompiledRule?>(null)

    /** The rule as saved, to share its code. */
    val saved: StateFlow<CompiledRule?> = _saved.asStateFlow()

    private val saving = RuleSaving(viewModelScope, parsers, ingestor)
    val step = saving.step

    init {
        val id = savedState.get<Long>(MAKE_FROM_UNPARSED_ARG) ?: NO_UNPARSED
        val body = savedState.get<String>(MAKE_FROM_BODY_ARG)
        when {
            id != NO_UNPARSED -> viewModelScope.launch {
                unparsed.get(id)?.let { choose(it.sender, it.body, it.receivedAt) }
            }
            body != null ->
                choose(
                    savedState.get<String>(MAKE_FROM_SENDER_ARG),
                    body,
                    System.currentTimeMillis()
                )
        }
    }

    /** Needs the SMS permission. Senders are checked before any text is read. */
    fun loadCandidates(now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val parser = ingestor.parser()
            _candidates.value = withContext(Dispatchers.IO) { candidateSms(inbox, parser, now) }
        }
    }

    /** Starts marking this SMS. [sender] is null for a pasted one. */
    fun choose(sender: String?, body: String, receivedAt: Long) {
        viewModelScope.launch {
            val known = sender?.let { ingestor.parser().bankOf(it) }
            _form.value = MakerForm.start(sender, body, receivedAt, known)
        }
    }

    fun update(form: MakerForm) {
        _form.value = form
    }

    /** Needs the SMS permission. Only SMS from [headers] are read. */
    fun loadRecent(headers: Set<String>, now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val parser = ingestor.parser()
            _recent.value = withContext(Dispatchers.IO) { recentSms(inbox, parser, headers, now) }
        }
    }

    fun save(rule: CompiledRule, now: Long = System.currentTimeMillis()) {
        _saved.value = rule
        saving.save(rule, now)
    }

    fun readWaiting() = saving.readWaiting()

    fun skipWaiting() = saving.skipWaiting()
}

/**
 * The last [CANDIDATE_COUNT] SMS in the last [CANDIDATE_DAYS] days that get past [parser]'s
 * filters (a business sender, and text that may be a transaction), newest first.
 */
internal fun candidateSms(inbox: SmsInbox, parser: SmsParser, now: Long): List<SmsInbox.Message> =
    try {
        inbox.businessMessages(now - TimeUnit.DAYS.toMillis(CANDIDATE_DAYS), parser::accepts)
            .filter { RuleMaker.mayBeTransaction(it.body, parser.filters) }
            .takeLast(CANDIDATE_COUNT).reversed()
    } catch (_: SecurityException) {
        // The permission was taken away while the screen was open.
        emptyList()
    }

private const val CANDIDATE_DAYS = 60L
private const val CANDIDATE_COUNT = 30
