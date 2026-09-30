package com.openhand.khata.feature.settings

import androidx.annotation.StringRes
import com.openhand.khata.core.model.Direction
import com.openhand.khata.sms.parser.RuleAccountType
import com.openhand.khata.sms.parser.RuleCodeError
import com.openhand.khata.sms.parser.RuleError

// What a pasted rule code gets wrong, in words (docs/parser-rules.md lists the same codes).

@StringRes
internal fun codeErrorMessage(error: RuleCodeError): Int = when (error) {
    RuleCodeError.BAD_PREFIX -> R.string.parser_code_bad_prefix
    RuleCodeError.BAD_BASE64 -> R.string.parser_code_bad_base64
    RuleCodeError.BAD_JSON -> R.string.parser_code_bad_json
    RuleCodeError.UNKNOWN_VERSION -> R.string.parser_code_unknown_version
}

@StringRes
internal fun ruleErrorMessage(error: RuleError): Int = when (error) {
    RuleError.UNKNOWN_VERSION -> R.string.parser_rule_unknown_version
    RuleError.BAD_ID -> R.string.parser_rule_bad_id
    RuleError.BAD_BANK -> R.string.parser_rule_bad_bank
    RuleError.BAD_SENDERS -> R.string.parser_rule_bad_senders
    RuleError.DIRECTION -> R.string.parser_rule_direction
    RuleError.BAD_DATE_FORMAT -> R.string.parser_rule_bad_date_format
    RuleError.PATTERN_TOO_LONG -> R.string.parser_rule_pattern_too_long
    RuleError.UNSUPPORTED_PATTERN -> R.string.parser_rule_unsupported_pattern
    RuleError.BAD_PATTERN -> R.string.parser_rule_bad_pattern
    RuleError.MISSING_AMOUNT -> R.string.parser_rule_missing_amount
    RuleError.DATE_FORMAT_WITHOUT_DATE -> R.string.parser_rule_date_format_without_date
    RuleError.DATE_WITHOUT_FORMAT -> R.string.parser_rule_date_without_format
}

@StringRes
internal fun directionLabel(direction: Direction): Int = when (direction) {
    Direction.DEBIT -> R.string.parser_direction_debit
    Direction.CREDIT -> R.string.parser_direction_credit
    Direction.REFUND -> R.string.parser_direction_refund
    Direction.TRANSFER -> R.string.parser_direction_transfer
}

@StringRes
internal fun accountTypeLabel(type: RuleAccountType): Int = when (type) {
    RuleAccountType.BANK -> R.string.parser_account_bank
    RuleAccountType.CREDIT_CARD -> R.string.parser_account_credit_card
    RuleAccountType.DEBIT_CARD -> R.string.parser_account_debit_card
    RuleAccountType.WALLET -> R.string.parser_account_wallet
}
