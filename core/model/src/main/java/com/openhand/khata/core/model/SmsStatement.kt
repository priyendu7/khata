package com.openhand.khata.core.model

import java.time.LocalDate

/** A credit card statement read from a bank SMS, ready to be saved (#154). */
data class SmsStatement(
    /** The bank the rule is for, e.g. `ICICI`. */
    val bank: String,
    val cardLast4: String,
    val totalPaise: Long,
    /** Null when the SMS doesn't give a minimum. */
    val minDuePaise: Long?,
    val dueDate: LocalDate,
    /** The date the SMS gives, or else the day it arrived. */
    val statementDate: LocalDate,
    /** The whole SMS text; links are taken out before it's saved. */
    val rawSms: String
)
