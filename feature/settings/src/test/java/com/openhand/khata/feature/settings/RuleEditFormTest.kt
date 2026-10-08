package com.openhand.khata.feature.settings

import com.openhand.khata.sms.parser.BuiltInRules
import com.openhand.khata.sms.parser.RuleCheck
import com.openhand.khata.sms.parser.RuleDirection
import com.openhand.khata.sms.parser.RuleError
import com.openhand.khata.sms.parser.RuleMaker.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEditFormTest {
    private val sent = BuiltInRules.all().first { it.id == "kotak-upi-sent" }
    private val form = RuleEditForm.from(sent)

    private fun errors(form: RuleEditForm) = (form.check() as RuleCheck.Invalid).errors

    @Test
    fun anUnchangedFormIsTheSameRule() {
        assertEquals(sent, form.rule())
        assertTrue(form.check() is RuleCheck.Valid)
        assertTrue(form.hasDate)
    }

    @Test
    fun sendersAreAddedAsHeadersOnceAndRemoved() {
        val more = form.addSender(" kotak ").addSender("KOTAK").addSender("")

        assertEquals(listOf("KOTAKB", "KOTAK"), more.senders)
        assertEquals(listOf("KOTAK"), more.removeSender("KOTAKB").senders)
    }

    @Test
    fun sendersAreCheckedAsBadSendersIs() {
        assertEquals(listOf(RuleError.BAD_SENDERS), errors(form.removeSender("KOTAKB")))
        assertEquals(listOf(RuleError.BAD_SENDERS), errors(form.addSender("+919876543210")))
    }

    @Test
    fun directionWordsReplaceAFixedDirection() {
        val words = form.copy(
            fixedDirection = false,
            words = mapOf(RuleDirection.DEBIT to "sent, paid ,", RuleDirection.CREDIT to " ")
        )

        val rule = words.rule()
        assertNull(rule.direction)
        assertEquals(mapOf(RuleDirection.DEBIT to listOf("sent", "paid")), rule.directionWords)
        assertTrue(words.check() is RuleCheck.Valid)
        assertEquals(listOf(RuleError.DIRECTION), errors(words.copy(words = emptyMap())))
        assertEquals(words.words, RuleEditForm.from(rule).copy(words = words.words).words)
        assertFalse(RuleEditForm.from(rule).fixedDirection)
    }

    @Test
    fun theDateFormatIsOnlyKeptWhileThePatternHasADate() {
        val noDate = form.copy(pattern = "Sent (?<amount>Rs\\.[\\d.,]+) from Kotak")

        assertFalse(noDate.hasDate)
        assertNull(noDate.rule().dateFormat)
        assertTrue(noDate.check() is RuleCheck.Valid)
        assertEquals(listOf(RuleError.DATE_WITHOUT_FORMAT), errors(form.copy(dateFormat = " ")))
        assertEquals(listOf(RuleError.BAD_DATE_FORMAT), errors(form.copy(dateFormat = "dd-bb")))
    }

    @Test
    fun theSameMessagesAsPastingACode() {
        val bad = form.copy(bank = "", pattern = "Sent (?<amout>[\\d.]+)")

        assertEquals(listOf(RuleError.BAD_BANK, RuleError.UNSUPPORTED_PATTERN), errors(bad))
    }

    @Test
    fun aReMarkedPatternKeepsTheRuleId() {
        val sms = "Rs.2,000 withdrawn at ATM using card XX5678 on 01-10-26."
        val maker = MakerForm.start("JM-KOTAKB-S", sms, 0L, knownBank = null).remarking(sent)
        fun tap(word: String) = maker.words.indexOfFirst { it.text == word }
        val marked = maker.tap(tap("Rs.2,000")).mark(Field.AMOUNT)
            .let { it.tap(it.words.indexOfFirst { w -> w.text == "01-10-26." }).mark(Field.DATE) }
        val remade = (marked.check(taken = setOf(sent.id)) as RuleCheck.Valid).rule.rule

        assertEquals(sent.id, remade.id)
        assertEquals("Kotak", remade.bank)
        val edited = form.remarked(remade.pattern, remade.dateFormat)
        assertEquals(sent.id, edited.rule().id)
        assertEquals(remade.pattern, edited.pattern)
        assertEquals("dd-MM-yy", edited.dateFormat)
        assertTrue(edited.check() is RuleCheck.Valid)
    }
}
