package com.openhand.khata.core.model

/**
 * A transaction read from a bank SMS, ready to be saved by SMS import. The balance an SMS shows is
 * never included, so it can't be stored.
 */
data class SmsTransaction(
    /** Always positive; [direction] gives the sign. */
    val amountPaise: Long,
    val direction: Direction,
    /** Milliseconds since the epoch. */
    val timestamp: Long,
    /** The bank the rule is for, e.g. `Kotak`. */
    val bank: String,
    val accountType: AccountType,
    val accountLast4: String?,
    /** The UPI ID, merchant or name as the SMS gives it. */
    val payee: String?,
    /** UPI or bank reference number. */
    val referenceNo: String?,
    /** The whole SMS text, kept with the transaction. */
    val rawSms: String
)
