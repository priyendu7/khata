package com.openhand.khata.sms.parser

import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A parser rule: data, never code (PRD feature 8). Built-in and custom parsers use this same
 * format. The format is documented in docs/parser-rules.md; keep the two in step.
 *
 * A rule must pass [RuleValidator] before the engine uses it.
 */
@Serializable
data class ParserRule(
    /** Format version. Only [RuleFormat.VERSION] is understood. */
    val v: Int,
    /** Short unique name, e.g. `kotak-upi-debit`. */
    val id: String,
    /** Bank name shown to the user and stored on new accounts, e.g. `Kotak`. */
    val bank: String,
    /** Sender headers without operator prefix or TRAI suffix, e.g. `KOTAKB` for `AX-KOTAKB-S`. */
    val senders: List<String>,
    /** Regex with named groups ([RuleFormat.GROUPS]), matched case-insensitively anywhere in the SMS. */
    val pattern: String,
    /** A fixed direction for every SMS this rule matches. Give this or [directionWords]. */
    val direction: RuleDirection? = null,
    /**
     * Words that decide the direction, checked in the order refund, debit, credit against the
     * `dir` group if the pattern has one, otherwise the whole SMS. Give this or [direction].
     */
    val directionWords: Map<RuleDirection, List<String>>? = null,
    val accountType: RuleAccountType,
    /** `java.time` pattern for the `date` group, e.g. `dd-MM-yy` or `dd-MMM-yyyy HH:mm`. */
    val dateFormat: String? = null
)

@Serializable
enum class RuleDirection(val direction: Direction) {
    @SerialName("debit")
    DEBIT(Direction.DEBIT),

    @SerialName("credit")
    CREDIT(Direction.CREDIT),

    @SerialName("refund")
    REFUND(Direction.REFUND)
}

@Serializable
enum class RuleAccountType(val accountType: AccountType) {
    @SerialName("bank")
    BANK(AccountType.BANK),

    @SerialName("credit_card")
    CREDIT_CARD(AccountType.CREDIT_CARD),

    @SerialName("debit_card")
    DEBIT_CARD(AccountType.DEBIT_CARD),

    @SerialName("wallet")
    WALLET(AccountType.WALLET)
}

object RuleFormat {
    const val VERSION = 1

    const val AMOUNT = "amount"
    const val PAYEE = "payee"
    const val ACCOUNT = "account"
    const val REF = "ref"
    const val DATE = "date"

    /** Read so a pattern can anchor on it, but never stored. */
    const val BALANCE = "balance"

    /** The part of the SMS that [ParserRule.directionWords] are checked against. */
    const val DIR = "dir"

    val GROUPS = setOf(AMOUNT, PAYEE, ACCOUNT, REF, DATE, BALANCE, DIR)
}
