package com.openhand.khata.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.AccountRepository
import com.openhand.khata.core.data.CategoryRepository
import com.openhand.khata.core.data.PayeeRepository
import com.openhand.khata.core.data.TagRepository
import com.openhand.khata.core.data.TransactionRepository
import com.openhand.khata.core.data.UnparsedSmsRepository
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Navigation argument: the transaction to edit, or 0 to add a new one. */
const val TRANSACTION_ID_ARG = "transactionId"

/** For a new transaction started from an SMS no rule could read: its amount in paise, if found. */
const val PREFILL_AMOUNT_ARG = "amount"

/** …when it arrived (epoch millis). */
const val PREFILL_AT_ARG = "at"

/** …and that SMS, deleted from the review inbox once the transaction is saved. */
const val FROM_UNPARSED_ARG = "unparsed"

/** The value an optional editor argument has when it isn't given. */
const val NO_PREFILL = -1L

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
// Hilt injects one repository per kind of data the editor reads or writes.
@Suppress("LongParameterList")
class TransactionEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    accounts: AccountRepository,
    private val tags: TagRepository,
    private val payees: PayeeRepository,
    private val unparsed: UnparsedSmsRepository
) : ViewModel() {
    val transactionId: Long = savedState[TRANSACTION_ID_ARG] ?: 0L
    val isNew: Boolean get() = transactionId == 0L
    private val fromUnparsed: Long = savedState[FROM_UNPARSED_ARG] ?: NO_PREFILL

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val _form = MutableStateFlow(if (isNew) newForm(savedState) else null)

    /** Null while an existing transaction loads. */
    val form: StateFlow<EditorForm?> = _form.asStateFlow()

    private val _showErrors = MutableStateFlow(false)
    val showErrors: StateFlow<Boolean> = _showErrors.asStateFlow()

    private val _done = MutableStateFlow(false)

    /** True once saved or deleted (or the transaction no longer exists): the screen closes. */
    val done: StateFlow<Boolean> = _done.asStateFlow()

    private var busy = false

    /** Active categories, plus the selected one if it has since been archived. */
    val categories: StateFlow<List<Category>> = combine(
        categoryRepository.observeAll(),
        _form.map { it?.categoryId }.distinctUntilChanged()
    ) { all, selected -> all.filter { !it.archived || it.id == selected } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val accounts: StateFlow<List<Account>> = accounts.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val tagQuery = MutableStateFlow("")

    /** Existing tags matching what's typed (the most used ones when nothing is), minus added ones. */
    val tagSuggestions: StateFlow<List<String>> = combine(
        tagQuery.debounce(TAG_DEBOUNCE_MILLIS).mapLatest { tags.suggestions(it) },
        _form.map { it?.tags.orEmpty() }.distinctUntilChanged()
    ) { found, added ->
        found.map { it.name }.filterNot { name -> added.any { it.equals(name, ignoreCase = true) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** The payee name as typed, once the user changes it; looked up to fill in its defaults. */
    private val typedPayee = MutableStateFlow<String?>(null)

    init {
        if (!isNew) {
            viewModelScope.launch {
                val transaction = transactions.get(transactionId)
                if (transaction == null) {
                    _done.value = true
                } else {
                    // The saved category and tags stay as they are; the payee only drives the hint.
                    val payee = transaction.payeeName?.let { payees.find(it) }
                    _form.value = EditorForm.from(transaction, zone).copy(knownPayee = payee)
                }
            }
        }
        viewModelScope.launch {
            typedPayee.filterNotNull().debounce(PAYEE_DEBOUNCE_MILLIS)
                .mapLatest { name -> name to payees.find(name) }
                .collect { (name, match) ->
                    // Skipped if the name changed again while the lookup ran.
                    _form.update { form ->
                        if (form?.payee == name) form.withKnownPayee(match) else form
                    }
                }
        }
    }

    fun update(form: EditorForm) {
        val payeeChanged = _form.value?.payee != form.payee
        _form.value = form
        if (payeeChanged) typedPayee.value = form.payee
    }

    fun onTagQueryChange(query: String) {
        tagQuery.value = query
    }

    /**
     * Adds a category from the picker (or finds the one already called that) and selects it. It
     * stays even if this transaction is then discarded, as when added from Settings.
     */
    fun addCategory(category: Category, defaultNames: Map<String, String>) {
        viewModelScope.launch {
            val picked = categoryRepository.addOrFind(category, defaultNames)
            // The form's Uncategorized is no category at all.
            _form.update { it?.copy(categoryId = picked.id.takeUnless { picked.isUncategorized }) }
        }
    }

    fun save() {
        val form = _form.value ?: return
        if (form.amountError != null) {
            _showErrors.value = true
            return
        }
        runOnce {
            transactions.save(
                form.toTransaction(transactionId, zone),
                rememberPayeeDefaults = form.rememberPayee
            )
            // Added by hand from the review inbox: that SMS has been dealt with.
            if (fromUnparsed != NO_PREFILL) unparsed.delete(fromUnparsed)
        }
    }

    fun delete() {
        if (!isNew) runOnce { transactions.delete(transactionId) }
    }

    /** Ignores a second tap while the first save or delete is still running. */
    private fun runOnce(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
                _done.update { true }
            } finally {
                busy = false
            }
        }
    }

    /** Dated now, or filled in from an SMS no rule could read. */
    private fun newForm(savedState: SavedStateHandle): EditorForm {
        val at = savedState.get<Long>(PREFILL_AT_ARG)?.takeIf { it != NO_PREFILL }
        val amount = savedState.get<Long>(PREFILL_AMOUNT_ARG)?.takeIf { it > 0 }
        val form = EditorForm.new(
            at?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: ZonedDateTime.now()
        )
        return amount?.let { form.copy(amount = Money.toInput(it)) } ?: form
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAG_DEBOUNCE_MILLIS = 150L
        const val PAYEE_DEBOUNCE_MILLIS = 300L
    }
}
