package com.openhand.khata.feature.settings

import com.openhand.khata.sms.parser.RuleAccountType
import com.openhand.khata.sms.parser.RuleCheck
import com.openhand.khata.sms.parser.RuleDirection
import com.openhand.khata.sms.parser.RuleError
import com.openhand.khata.sms.parser.RuleMaker.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MakerFormTest {
    private val sms =
        "Rs.450.00 spent on HDFC Bank Credit Card x4321 at CITY PHARMACY on 2026-09-20."
    private val form = MakerForm.start("AX-HDFCBK-S", sms, AT, knownBank = null)

    private fun index(word: String) = form.words.indexOfFirst { it.text == word }

    @Test
    fun startsFromTheSenderAndGuessesFromTheWords() {
        assertEquals("HDFCBK", form.bank)
        assertEquals("HDFCBK", form.senders)
        assertEquals(RuleDirection.DEBIT, form.direction)
        assertEquals(RuleAccountType.CREDIT_CARD, form.accountType)
        assertEquals("Kotak", MakerForm.start("AX-KOTAKB-S", sms, AT, "Kotak").bank)
        assertEquals("", MakerForm.start(null, sms, AT, null).senders)
    }

    @Test
    fun aSecondTapSelectsTheWordsBetween() {
        val city = index("CITY")
        val pharmacy = index("PHARMACY")

        assertEquals(city..city, form.tap(city).selection)
        assertEquals(city..pharmacy, form.tap(pharmacy).tap(city).selection)
        assertNull(form.tap(city).tap(city).selection)
        // After a run, a tap starts again.
        assertEquals(city..city, form.tap(city).tap(pharmacy).tap(city).selection)
    }

    @Test
    fun markingReplacesTheSameFieldAndOverlappingMarks() {
        val amount = form.tap(index("Rs.450.00")).mark(Field.AMOUNT)
        val payee = amount.tap(index("CITY")).tap(index("PHARMACY")).mark(Field.PAYEE)

        assertEquals("CITY PHARMACY", payee.markedText(payee.marks.last()))
        assertEquals(Field.PAYEE, payee.fieldAt(index("PHARMACY")))
        assertNull(payee.selection)

        val moved = payee.tap(index("x4321")).mark(Field.PAYEE)
        assertEquals(listOf(Field.AMOUNT, Field.PAYEE), moved.marks.map { it.field })
        assertEquals("x4321", moved.markedText(moved.marks.last()))

        val overlapped = payee.tap(index("PHARMACY")).mark(Field.REF)
        assertEquals(listOf(Field.AMOUNT, Field.REF), overlapped.marks.map { it.field })
    }

    @Test
    fun markingADatePicksAFormatAndClearingItDropsIt() {
        val dated = form.tap(index("2026-09-20.")).mark(Field.DATE)

        assertEquals("yyyy-MM-dd", dated.dateFormat)
        assertNull(dated.unmark(Field.DATE).dateFormat)
    }

    @Test
    fun noRuleUntilTheAmountIsMarked() {
        assertNull(form.check(emptySet()))

        val check = form.tap(index("Rs.450.00")).mark(Field.AMOUNT)
            .copy(bank = "HDFC Bank")
            .check(setOf("hdfc-bank-debit"))

        assertEquals("hdfc-bank-debit-2", (check as RuleCheck.Valid).rule.rule.id)
    }

    @Test
    fun sendersAreSplitOnCommasAndSpacesAndChecked() {
        assertEquals(listOf("HDFCBK", "HDFC"), form.copy(senders = "hdfcbk,  HDFC").senderList())

        val check = form.tap(index("Rs.450.00")).mark(Field.AMOUNT)
            .copy(senders = "9876543210")
            .check(emptySet())

        assertTrue(RuleError.BAD_SENDERS in (check as RuleCheck.Invalid).errors)
    }

    private companion object {
        const val AT = 1_790_000_000_000L
    }
}
