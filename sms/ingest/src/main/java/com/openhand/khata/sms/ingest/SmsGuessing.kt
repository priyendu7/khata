package com.openhand.khata.sms.ingest

import com.openhand.khata.core.data.CustomParserRepository
import com.openhand.khata.core.model.SenderId
import com.openhand.khata.core.model.UnparsedSms
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.FieldGuesser
import com.openhand.khata.sms.parser.ParseResult
import com.openhand.khata.sms.parser.ParsedSms
import com.openhand.khata.sms.parser.RuleCheck
import com.openhand.khata.sms.parser.RuleCode
import com.openhand.khata.sms.parser.RuleDirection
import com.openhand.khata.sms.parser.RuleMaker
import com.openhand.khata.sms.parser.RuleValidator
import com.openhand.khata.sms.parser.SmsParser
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Khata's best guess at an SMS in To review (PRD feature 8): its fields guessed by
 * [FieldGuesser] and made into a rule, as Make a parser → Looks right would. A guess is offered
 * only when it has an amount and a direction and its rule reads the SMS. Nothing is saved until
 * the user says yes.
 */
class SmsGuessing @Inject constructor(
    private val ingestor: SmsIngestor,
    private val parsers: CustomParserRepository
) {
    /** The rule made from the guesses, and what it reads from the SMS. */
    data class Guess(val rule: CompiledRule, val sms: ParsedSms)

    /** A guess for each of [list] that has one, by id. */
    suspend fun guesses(list: List<UnparsedSms>): Map<Long, Guess> {
        if (list.isEmpty()) return emptyMap()
        val parser = ingestor.parser()
        val taken = takenIds()
        return list.mapNotNull { sms -> guess(sms, parser, taken)?.let { sms.id to it } }.toMap()
    }

    /**
     * "Yes, save": saves the guessed rule, then reads the waiting SMS again, which records [sms]
     * and clears others like it. Returns how many were recorded; 0 when there's no guess.
     */
    suspend fun accept(sms: UnparsedSms, now: Long = System.currentTimeMillis()): Int {
        val rule = guess(sms, ingestor.parser(), takenIds())?.rule?.rule ?: return 0
        parsers.save(rule.id, rule.bank, RuleCode.encode(rule), now)
        return ingestor.retryUnparsed()
    }

    private suspend fun takenIds(): Set<String> =
        parsers.observeAll().first().mapTo(mutableSetOf()) { it.ruleId }

    private fun guess(sms: UnparsedSms, parser: SmsParser, taken: Set<String>): Guess? {
        val rule = rule(sms, parser, taken) ?: return null
        val read = SmsParser(listOf(rule)).parse(sms.sender, sms.body, sms.receivedAt)
        return (read as? ParseResult.Parsed)?.let { Guess(rule, it.sms) }
    }

    /** The rule made from [sms]'s guesses: null without an amount and a direction, or if invalid. */
    private fun rule(sms: UnparsedSms, parser: SmsParser, taken: Set<String>): CompiledRule? {
        val header = SenderId.parse(sms.sender)?.header ?: return null
        val guesses = FieldGuesser.guess(sms.body)
        // The bank the rules already name for this sender, as Make a parser starts with.
        val bank = parser.bankOf(sms.sender) ?: header
        val id = RuleMaker.ruleId(bank, guesses.direction ?: RuleDirection.DEBIT, taken)
        val check = guesses.draft(id, bank, listOf(header))
            ?.let { RuleValidator.validate(RuleMaker.rule(it)) }
        return (check as? RuleCheck.Valid)?.rule
    }
}
