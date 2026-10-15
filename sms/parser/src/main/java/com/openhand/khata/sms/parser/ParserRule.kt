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
    /**
     * Format version: 1, or 2 for a rule that uses anything v2 adds ([kind], [dueDateFormat],
     * [RuleFormat.ANY_SENDER] and the v2 groups). Newer versions are refused.
     */
    val v: Int,
    /** Short unique name, e.g. `kotak-upi-debit`. */
    val id: String,
    /** Bank name shown to the user and stored on new accounts, e.g. `Kotak`. */
    val bank: String,
    /**
     * Sender headers without operator prefix or TRAI suffix, e.g. `KOTAKB` for `AX-KOTAKB-S`. In
     * v2, `["*"]` ([RuleFormat.ANY_SENDER]) is any business sender, tried after every rule that
     * names the sender.
     */
    val senders: List<String>,
    /** Regex with named groups ([RuleFormat.GROUPS]), matched case-insensitively anywhere in the SMS. */
    val pattern: String,
    /** v2: what the rule reads. Null is a transaction, as every v1 rule is. */
    val kind: RuleKind? = null,
    /** A fixed direction for every SMS this rule matches. Give this or [directionWords]. */
    val direction: RuleDirection? = null,
    /**
     * Words that decide the direction, checked in the order refund, debit, credit against the
     * `dir` group if the pattern has one, otherwise the whole SMS. Give this or [direction].
     */
    val directionWords: Map<RuleDirection, List<String>>? = null,
    /** Needed by a transaction rule. A statement is always a credit card's, so it has none. */
    val accountType: RuleAccountType? = null,
    /**
     * `java.time` pattern for the `date` group (`statementDate` in a statement rule), e.g.
     * `dd-MM-yy` or `dd-MMM-yyyy HH:mm`. Optional sections read several: `[dd-MMM-yy][dd-MM-yy]`.
     */
    val dateFormat: String? = null,
    /** v2, statement rules: the `java.time` pattern for the `dueDate` group. */
    val dueDateFormat: String? = null
) {
    val isStatement: Boolean get() = kind == RuleKind.STATEMENT

    /** Tried for any business sender, after the rules that name it. */
    val anySender: Boolean get() = senders == listOf(RuleFormat.ANY_SENDER)
}

@Serializable
enum class RuleKind {
    /** A debit, credit or refund: today's behaviour, and the default. */
    @SerialName("transaction")
    TRANSACTION,

    /** A credit card statement: total due, minimum due and due date. */
    @SerialName("statement")
    STATEMENT
}

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
    /** The newest format this Khata reads. */
    const val VERSION = 2

    /**
     * The first format. A rule that needs nothing newer is still written as v1 ([versionFor]), so
     * older versions of Khata can read its code.
     */
    const val V1 = 1

    /** The only sender of a rule tried for any business sender (v2). */
    const val ANY_SENDER = "*"

    const val AMOUNT = "amount"
    const val PAYEE = "payee"
    const val ACCOUNT = "account"
    const val REF = "ref"
    const val DATE = "date"

    /** Read so a pattern can anchor on it, but never stored. */
    const val BALANCE = "balance"

    /** The part of the SMS that [ParserRule.directionWords] are checked against. */
    const val DIR = "dir"

    /** v2: the bank named in the SMS, used instead of [ParserRule.bank] when it matches. */
    const val BANK = "bank"

    /** v2 statement groups. */
    const val TOTAL = "total"
    const val MIN_DUE = "minDue"
    const val DUE_DATE = "dueDate"
    const val STATEMENT_DATE = "statementDate"

    val V1_GROUPS = setOf(AMOUNT, PAYEE, ACCOUNT, REF, DATE, BALANCE, DIR)
    val TRANSACTION_GROUPS = V1_GROUPS + BANK
    val STATEMENT_GROUPS = setOf(ACCOUNT, BANK, TOTAL, MIN_DUE, DUE_DATE, STATEMENT_DATE, BALANCE)
    val GROUPS = TRANSACTION_GROUPS + STATEMENT_GROUPS

    /** The groups a rule of [kind] may use. */
    fun groupsFor(kind: RuleKind?): Set<String> =
        if (kind == RuleKind.STATEMENT) STATEMENT_GROUPS else TRANSACTION_GROUPS

    /** The oldest version that can hold a rule with these fields and [pattern]'s groups. */
    fun versionFor(rule: ParserRule): Int {
        val groups = RegexSubset.namedGroups(rule.pattern).orEmpty()
        val v2 = rule.kind != null ||
            rule.dueDateFormat != null ||
            ANY_SENDER in rule.senders ||
            groups.any { it !in V1_GROUPS }
        return if (v2) VERSION else V1
    }
}
