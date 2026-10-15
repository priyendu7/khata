package com.openhand.khata.core.data

import com.openhand.khata.core.database.dao.AccountDao
import com.openhand.khata.core.database.entity.AccountEntity

/** Which saved account an SMS is about, from its bank and last 4 digits. */
internal object AccountMatch {
    /**
     * The account with [last4] whose bank is [bank], or contains it (a user may have typed "Kotak
     * Mahindra Bank"), or, failing that, the only such account with no bank set. Without last 4
     * digits, only an account with none and the same bank. [accept] narrows the accounts looked
     * at, for example to credit cards.
     */
    suspend fun find(
        dao: AccountDao,
        bank: String,
        last4: String?,
        accept: (AccountEntity) -> Boolean = { true }
    ): AccountEntity? {
        val candidates = dao.getByLast4(last4).filter(accept)
        fun AccountEntity.bankIs(exact: Boolean): Boolean {
            val saved = this.bank ?: return false
            return saved.equals(bank, ignoreCase = true) || !exact && sameBank(saved, bank)
        }
        return candidates.firstOrNull { it.bankIs(exact = true) }
            ?: candidates.firstOrNull { it.bankIs(exact = false) }
            ?: candidates.singleOrNull { it.bank == null }?.takeIf { last4 != null }
    }

    private fun sameBank(a: String, b: String) =
        a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true)
}
