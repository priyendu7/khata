package com.openhand.khata.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.CategoryRepository
import com.openhand.khata.core.data.ReviewRepository
import com.openhand.khata.core.data.TagRepository
import com.openhand.khata.core.data.UnparsedSmsRepository
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.ReviewItem
import com.openhand.khata.core.model.UnparsedSms
import com.openhand.khata.core.model.UnparsedSmsGroup
import com.openhand.khata.sms.ingest.SmsIgnoring
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The To review inbox, one card at a time (PRD feature 4). */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val review: ReviewRepository,
    private val unparsedSms: UnparsedSmsRepository,
    categories: CategoryRepository,
    private val tags: TagRepository,
    private val ignoring: SmsIgnoring
) : ViewModel() {
    /** Waiting transactions, newest first; null until loaded. */
    val queue: StateFlow<List<ReviewItem>?> = review.observeQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Bank SMS no rule could read, by sender, newest first; shown after the transactions. */
    val unparsed: StateFlow<List<UnparsedSmsGroup>> = unparsedSms.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val categories: StateFlow<List<Category>> = categories.observeAll()
        .map { all -> all.filter { !it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val tagQuery = MutableStateFlow("")

    /** Existing tags matching what's typed (the most used ones when nothing is). */
    val tagSuggestions: StateFlow<List<String>> = tagQuery
        .debounce(TAG_DEBOUNCE_MILLIS)
        .mapLatest { query -> tags.suggestions(query).map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun onTagQueryChange(query: String) {
        tagQuery.value = query
    }

    /** Names the payee and files it and its other waiting transactions; the next card follows. */
    fun save(item: ReviewItem, payeeName: String, categoryId: Long?, tags: List<String>) {
        viewModelScope.launch { review.review(item.transactionId, payeeName, categoryId, tags) }
    }

    fun skip(item: ReviewItem) {
        viewModelScope.launch { review.skip(item.transactionId) }
    }

    /** Deletes it; nothing is kept. */
    fun dismiss(sms: UnparsedSms) {
        viewModelScope.launch { unparsedSms.delete(sms.id) }
    }

    /** Deletes every SMS in the group, after the user confirmed. Ones arriving since stay. */
    fun dismissAll(group: UnparsedSmsGroup) {
        viewModelScope.launch { unparsedSms.delete(group.messages.map { it.id }) }
    }

    /** Ignores every SMS from its sender, and removes the ones waiting here. */
    fun ignoreSender(sms: UnparsedSms) {
        viewModelScope.launch { ignoring.ignoreSender(sms) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAG_DEBOUNCE_MILLIS = 150L
    }
}

/** How many transactions wait for review, for the badges on Home and the Transactions tab. */
@HiltViewModel
class ReviewCountViewModel @Inject constructor(review: ReviewRepository) : ViewModel() {
    val count: StateFlow<Int> = review.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
