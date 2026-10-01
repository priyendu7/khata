package com.openhand.khata.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.sms.ingest.SmsExplanation
import com.openhand.khata.sms.ingest.SmsIngestor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Settings > SMS import > Test a message (PRD feature 7). Nothing is saved. */
@HiltViewModel
class TestMessageViewModel @Inject constructor(private val ingestor: SmsIngestor) : ViewModel() {
    private val _answer = MutableStateFlow<SmsExplanation?>(null)

    /** Null until the first test. */
    val answer: StateFlow<SmsExplanation?> = _answer.asStateFlow()

    /** Treats the SMS as received [now]. */
    fun test(sender: String, body: String, now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            _answer.value = ingestor.explain(sender.trim(), body, now)
        }
    }
}
