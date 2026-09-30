package com.openhand.khata.core.model

/** One card in the To review inbox (PRD feature 4): a transaction from a payee not named yet. */
data class ReviewItem(
    val transactionId: Long,
    val amountPaise: Long,
    val direction: Direction,
    val timestamp: Long,
    val accountName: String?,
    val payeeId: Long?,
    /** The UPI ID, merchant or name as the SMS gave it. */
    val payeeIdentifier: String?,
    val payeeName: String?,
    /** How many waiting transactions this payee has, this one included. */
    val payeePending: Int,
    val rawSms: String?
)
