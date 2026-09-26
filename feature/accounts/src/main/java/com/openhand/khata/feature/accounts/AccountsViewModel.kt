package com.openhand.khata.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.AccountRepository
import com.openhand.khata.core.model.Account
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AccountsViewModel @Inject constructor(private val accounts: AccountRepository) : ViewModel() {
    /** Null until the database has answered, so the screen doesn't flash "no accounts". */
    val list: StateFlow<List<Account>?> = accounts.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun save(account: Account) {
        viewModelScope.launch { accounts.save(account) }
    }

    /** How many transactions would need moving; [onResult] runs on the main thread. */
    fun countTransactions(accountId: Long, onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(accounts.transactionCount(accountId)) }
    }

    fun delete(accountId: Long, moveTransactionsTo: Long?) {
        viewModelScope.launch { accounts.delete(accountId, moveTransactionsTo) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
