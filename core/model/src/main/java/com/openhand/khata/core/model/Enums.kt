package com.openhand.khata.core.model

/** Which way money moved. Amounts are always positive; the direction gives the sign. */
enum class Direction {
    /** Money out: an expense. */
    DEBIT,

    /** Money in: income. */
    CREDIT,

    /** Money back for an earlier expense; reduces spending in its category. */
    REFUND,

    /** Between the user's own accounts (e.g. paying a card bill); never counts as spending or income. */
    TRANSFER
}

/** Where a transaction came from. */
enum class TransactionSource { SMS, MANUAL, CSV }

enum class AccountType { BANK, CREDIT_CARD, DEBIT_CARD, WALLET }
