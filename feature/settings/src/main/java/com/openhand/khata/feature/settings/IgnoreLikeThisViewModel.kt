package com.openhand.khata.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.UnparsedSmsRepository
import com.openhand.khata.core.model.UnparsedSms
import com.openhand.khata.sms.ingest.SmsIgnoring
import com.openhand.khata.sms.parser.RuleMaker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The SMS in To review to make an "ignore messages like this" rule from. */
const val IGNORE_FROM_UNPARSED_ARG = "unparsedId"

/**
 * Ignore messages like this (PRD feature 7): the user taps the words that change, sees how many
 * waiting SMS the rule matches, and saves it, which removes them from To review.
 */
@HiltViewModel
class IgnoreLikeThisViewModel @Inject constructor(
    savedState: SavedStateHandle,
    unparsed: UnparsedSmsRepository,
    private val ignoring: SmsIgnoring
) : ViewModel() {
    private val _sms = MutableStateFlow<UnparsedSms?>(null)

    /** Null until loaded, or if it's gone from To review. */
    val sms: StateFlow<UnparsedSms?> = _sms.asStateFlow()

    private val _matches = MutableStateFlow<Int?>(null)

    /** How many waiting SMS the rule matches with the words tapped now. */
    val matches: StateFlow<Int?> = _matches.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    init {
        viewModelScope.launch {
            val id = savedState.get<Long>(IGNORE_FROM_UNPARSED_ARG) ?: return@launch
            _sms.value = unparsed.get(id)
            count(emptySet())
        }
    }

    /** Counts again for these tapped words. */
    fun count(tapped: Set<RuleMaker.Word>) {
        val sms = _sms.value ?: return
        viewModelScope.launch {
            _matches.value = ignoring.waitingLike(sms, ignoring.template(sms, tapped))
        }
    }

    fun save(tapped: Set<RuleMaker.Word>) {
        val sms = _sms.value ?: return
        viewModelScope.launch {
            ignoring.ignoreLikeThis(sms, ignoring.template(sms, tapped))
            _done.value = true
        }
    }
}
