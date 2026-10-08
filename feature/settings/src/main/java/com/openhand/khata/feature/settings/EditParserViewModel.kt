package com.openhand.khata.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.BuiltInRuleOverrideRepository
import com.openhand.khata.core.data.CustomParserRepository
import com.openhand.khata.sms.ingest.SmsInbox
import com.openhand.khata.sms.ingest.SmsIngestor
import com.openhand.khata.sms.parser.BuiltInRules
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.ParserRule
import com.openhand.khata.sms.parser.RuleCode
import com.openhand.khata.sms.parser.RuleCodeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The rule to edit, by id, and whether it's built in. */
const val EDIT_RULE_ID_ARG = "ruleId"
const val EDIT_BUILTIN_ARG = "builtIn"

/** Where the rule maker leaves a re-marked rule's code for the edit screen to pick up. */
const val REMARKED_RULE_KEY = "remarkedRule"

/**
 * Settings > Parsers > Edit (#111): one screen for built-in and custom rules. A custom rule is
 * replaced by its id; a built-in rule's edit is stored apart and runs in its place.
 */
@HiltViewModel
class EditParserViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val parsers: CustomParserRepository,
    private val overrides: BuiltInRuleOverrideRepository,
    private val ingestor: SmsIngestor,
    private val inbox: SmsInbox
) : ViewModel() {
    private val ruleId: String = checkNotNull(savedState[EDIT_RULE_ID_ARG])
    val builtIn: Boolean = savedState[EDIT_BUILTIN_ARG] ?: false

    /** The built-in rule as shipped; null for a custom rule. */
    private var original: ParserRule? = null

    private val _form = MutableStateFlow<RuleEditForm?>(null)

    /** Null until loaded. */
    val form: StateFlow<RuleEditForm?> = _form.asStateFlow()

    private val _recent = MutableStateFlow<List<SmsInbox.Message>?>(null)

    /** Recent SMS from the rule's senders, newest first, once Check is tapped. */
    val recent: StateFlow<List<SmsInbox.Message>?> = _recent.asStateFlow()

    private val _step = MutableStateFlow(EditStep.EDITING)
    val step: StateFlow<EditStep> = _step.asStateFlow()

    init {
        viewModelScope.launch {
            val rule = load()
            // Deleted meanwhile: there's nothing to edit.
            if (rule == null) _step.value = EditStep.DONE
            _form.value = rule?.let(RuleEditForm::from)
            // A pattern made again in the rule maker comes back through the saved state.
            savedState.getStateFlow<String?>(REMARKED_RULE_KEY, null).filterNotNull().collect {
                remarked(it)
                savedState[REMARKED_RULE_KEY] = null
            }
        }
    }

    private suspend fun load(): ParserRule? = if (builtIn) {
        val shipped = withContext(Dispatchers.Default) {
            BuiltInRules.all().firstOrNull { it.id == ruleId }
        }
        original = shipped
        val edited = overrides.getAll().firstOrNull { it.ruleId == ruleId }?.editedCode
        edited?.let(::decode)?.takeIf { it.id == ruleId } ?: shipped
    } else {
        parsers.observeAll().first().firstOrNull { it.ruleId == ruleId }?.code?.let(::decode)
    }

    fun update(form: RuleEditForm) {
        _form.value = form
        _recent.value = null
    }

    private fun remarked(code: String) {
        val rule = decode(code) ?: return
        _form.value?.let { update(it.remarked(rule.pattern, rule.dateFormat)) }
    }

    /** Check: needs the SMS permission. Only SMS from [headers] are read. */
    fun loadRecent(headers: Set<String>, now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val parser = ingestor.parser()
            _recent.value = withContext(Dispatchers.IO) { recentSms(inbox, parser, headers, now) }
        }
    }

    /** Saves the edit, then reads the SMS waiting in To review again with it. */
    fun save(rule: CompiledRule, now: Long = System.currentTimeMillis()) {
        if (_step.value != EditStep.EDITING) return
        _step.value = EditStep.SAVING
        viewModelScope.launch {
            val edited = rule.rule
            val shipped = original
            when {
                !builtIn -> parsers.edit(edited.id, edited.bank, RuleCode.encode(edited), now)
                // Edited back to the rule as shipped: nothing to keep.
                shipped == null || edited == shipped -> overrides.clearEdit(edited.id)
                else -> overrides.saveEdit(
                    edited.id,
                    RuleCode.encode(edited),
                    BuiltInRules.hash(shipped)
                )
            }
            ingestor.retryUnparsed()
            _step.value = EditStep.DONE
        }
    }

    private fun decode(code: String): ParserRule? =
        (RuleCode.decode(code) as? RuleCodeResult.Decoded)?.rule
}

enum class EditStep { EDITING, SAVING, DONE }
