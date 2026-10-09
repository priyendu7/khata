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

/** Which side of a move between accounts a transfer is: money out of its account, or into it. */
enum class TransferSide { OUT, IN }

/** Why a transaction is a transfer (#113). */
enum class TransferKind {
    /** A credit card bill payment, from the bank side or the card side. */
    CARD_PAYMENT,

    /** To or from a payee the user marked as their own account. */
    OWN_ACCOUNT,

    /** The other side of a saved debit or credit on another of the user's accounts. */
    OTHER_SIDE,

    /** Made a transfer in the transaction editor. */
    MANUAL
}

/** Where a transaction came from. */
enum class TransactionSource { SMS, MANUAL, CSV }

enum class AccountType { BANK, CREDIT_CARD, DEBIT_CARD, WALLET }
