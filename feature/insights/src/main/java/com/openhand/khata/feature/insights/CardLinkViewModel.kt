package com.openhand.khata.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.AccountRepository
import com.openhand.khata.core.data.CardRepository
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Insights' "Choose card" for a card bill whose card isn't known (#166). */
@HiltViewModel
class CardLinkViewModel @Inject constructor(
    private val cards: CardRepository,
    accounts: AccountRepository
) : ViewModel() {
    /** The credit cards a payment can be linked to. */
    val creditCards: StateFlow<List<Account>> = accounts.observeAccounts()
        .map { all -> all.filter { it.type == AccountType.CREDIT_CARD } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** Links bill payment [transactionId] to credit card [cardId]. */
    fun link(transactionId: Long, cardId: Long) {
        viewModelScope.launch { cards.linkPayment(transactionId, cardId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
