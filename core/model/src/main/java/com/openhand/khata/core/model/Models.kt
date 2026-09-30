package com.openhand.khata.core.model

/** A bank account, card or wallet. Only the last 4 digits of any number are ever stored. */
data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val bank: String? = null,
    val last4: String? = null
) {
    companion object {
        const val LAST_DIGITS = 4

        /** Empty, or exactly [LAST_DIGITS] digits. */
        fun isValidLast4(value: String?) =
            value.isNullOrEmpty() || (value.length == LAST_DIGITS && value.all(Char::isDigit))
    }
}

/**
 * A category. Default categories have a [seedKey] and, until the user renames them, a null [name]
 * (shown in the current language by the UI).
 */
data class Category(
    val id: Long = 0,
    val name: String?,
    val seedKey: String? = null,
    val color: Int,
    val icon: String,
    val archived: Boolean = false
) {
    val defaultCategory: DefaultCategory? get() = DefaultCategory.fromKey(seedKey)

    /** Uncategorized is the fallback for every transaction: it can't be archived or deleted. */
    val isUncategorized: Boolean get() = seedKey == DefaultCategory.UNCATEGORIZED.key
}

/** A tag and how many transactions use it. */
data class Tag(val id: Long = 0, val name: String, val usageCount: Int = 0)

/**
 * Payee memory (PRD feature 3): what a UPI ID, merchant or person is called, and the category and
 * tags its transactions get by default. Manual entries use the typed name as the [identifier].
 */
data class Payee(
    val id: Long = 0,
    val identifier: String,
    val displayName: String,
    /** Null means no default: the transaction keeps whatever category it has. */
    val defaultCategoryId: Long? = null,
    val defaultTags: List<String> = emptyList(),
    /** One of the user's own accounts: money to or from it is a transfer, not spending (#56). */
    val ownAccount: Boolean = false,
    val transactionCount: Int = 0
) {
    /** Whether there's anything to fill in; a payee without defaults is remembered on next use. */
    val hasDefaults: Boolean get() = defaultCategoryId != null || defaultTags.isNotEmpty()
}
