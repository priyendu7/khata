package com.openhand.khata.feature.settings

import com.openhand.khata.sms.parser.ParseResult
import com.openhand.khata.sms.parser.RuleAccountType
import com.openhand.khata.sms.parser.RuleCheck
import com.openhand.khata.sms.parser.RuleDirection
import com.openhand.khata.sms.parser.RuleError
import com.openhand.khata.sms.parser.RuleKind
import com.openhand.khata.sms.parser.RuleMaker.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MakerFormTest {
    private val sms =
        "Rs.450.00 spent on HDFC Bank Credit Card x4321 at CITY PHARMACY on 2026-09-20."
    private val guessed = MakerForm.start("AX-HDFCBK-S", sms, AT, knownBank = null)
    private val form = guessed.unguessed()

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
    fun opensWithTheGuessesMarked() {
        val marked = guessed.marks.associate { it.field to guessed.markedText(it) }
        assertEquals(
            mapOf(
                Field.AMOUNT to "Rs.450.00",
                Field.ACCOUNT to "x4321",
                Field.PAYEE to "CITY PHARMACY",
                Field.DATE to "2026-09-20"
            ),
            marked
        )
        assertEquals("yyyy-MM-dd", guessed.dateFormat)
        assertEquals(Field.AMOUNT, guessed.step)
        assertTrue(guessed.isGuess(Field.AMOUNT))
        assertTrue(guessed.canGoOn)
    }

    @Test
    fun looksRightConfirmsEveryGuessAndGoesToTheCheck() {
        val done = guessed.copy(bank = "HDFC Bank").confirmAll()

        assertNull(done.step)
        assertEquals(emptySet<Field>(), done.guessed)
        val rule = (done.check(emptySet()) as RuleCheck.Valid).rule
        val read = (testRule(rule, done.sender, sms, AT) as ParseResult.Parsed).sms
        assertEquals(45_000L, read.amountPaise)
        assertEquals("CITY PHARMACY", read.payee)
        assertEquals("4321", read.accountLast4)
        // Without an amount there's nothing to confirm.
        assertFalse(form.canConfirmAll)
        assertEquals(form, form.confirmAll())
    }

    @Test
    fun fixingOneGuessKeepsTheOthers() {
        val payee = guessed.next()
        assertFalse(payee.isGuess(Field.AMOUNT))
        assertTrue(payee.isGuess(Field.PAYEE))

        val fixed = payee.tapInStep(index("PHARMACY"))
        assertEquals("PHARMACY", fixed.markedText(fixed.marks.first { it.field == Field.PAYEE }))
        assertFalse(fixed.isGuess(Field.PAYEE))
        assertEquals(setOf(Field.ACCOUNT, Field.DATE), fixed.guessed)
        assertTrue(fixed.isMarked(Field.AMOUNT) && fixed.isMarked(Field.DATE))
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

    @Test
    fun stepsGoInOrderAndTheAmountCantBeSkipped() {
        assertEquals(Field.AMOUNT, form.step)
        assertFalse(form.canSkip)
        assertFalse(form.canGoOn)
        assertEquals(form, form.skip())

        val amount = form.tapInStep(index("Rs.450.00"))
        assertTrue(amount.canGoOn)
        val steps = generateSequence(amount.next()) { it.step?.let { _ -> it.skip() } }
            .map { it.step }
            .toList()
        assertEquals(listOf(Field.PAYEE, Field.ACCOUNT, Field.REF, Field.DATE, null), steps)
    }

    @Test
    fun aTapInAStepMarksAndASecondTapTakesTheRun() {
        val payee = form.tapInStep(index("Rs.450.00")).next()
            .tapInStep(index("CITY")).tapInStep(index("PHARMACY"))

        val mark = payee.marks.first { it.field == Field.PAYEE }
        assertEquals("CITY PHARMACY", payee.markedText(mark))
        // Tapping the amount's word can't take it into the payee.
        assertEquals(payee, payee.tapInStep(index("Rs.450.00")))
        // The only marked word, tapped again, is cleared.
        val one = payee.tapInStep(index("x4321"))
        assertFalse(one.tapInStep(index("x4321")).isMarked(Field.PAYEE))
    }

    @Test
    fun skipClearsBackKeepsAndAChipComesBackToTheRest() {
        val payee = form.tapInStep(index("Rs.450.00")).next().tapInStep(index("CITY"))

        assertFalse(payee.skip().isMarked(Field.PAYEE))
        val back = payee.next().back()
        assertEquals(Field.PAYEE, back.step)
        assertTrue(back.isMarked(Field.PAYEE))

        val done = generateSequence(payee.next()) { it.step?.let { _ -> it.skip() } }.last()
        assertNull(done.step)
        val revisited = done.revisit(Field.ACCOUNT).tapInStep(index("x4321")).next()
        assertNull(revisited.step)
        assertTrue(revisited.isMarked(Field.ACCOUNT))
    }

    private companion object {
        const val AT = 1_790_000_000_000L
    }

    @Test
    fun aStatementIsMarkedAsOne() {
        val body = "HDFC Bank Credit Card XX4321 Statement:\nTotal due: Rs.8,363.00\n" +
            "Min.due: Rs.2,268.00\nPay by 02-10-2025\nView: https://example.com/xyz"
        val statement = MakerForm.start("VM-HDFCBK-S", body, AT, knownBank = null)

        assertEquals(RuleKind.STATEMENT, statement.kind)
        assertEquals(
            listOf(Field.ACCOUNT, Field.TOTAL, Field.MIN_DUE, Field.DUE_DATE),
            statement.fields
        )
        assertEquals(Field.ACCOUNT, statement.step)
        assertFalse(statement.canSkip)
        assertEquals("dd-MM-yyyy", statement.dateFormat)
        val atMinimum = statement.next().next()
        assertEquals(Field.MIN_DUE, atMinimum.step)
        // The minimum is optional; the card, total and due date aren't.
        assertTrue(atMinimum.canSkip)
        val rule = (statement.confirmAll().check(emptySet()) as RuleCheck.Valid).rule
        assertEquals("hdfcbk-statement", rule.rule.id)
        assertTrue(rule.rule.isStatement)
        val read = testRule(rule, statement.sender, body, AT) as ParseResult.Statement
        assertEquals(836_300L, read.statement.totalPaise)
        assertEquals(226_800L, read.statement.minDuePaise)
    }

    @Test
    fun switchingKindGuessesAgainAndStartsOver() {
        val transaction = MakerForm.start("VM-HDFCBK-S", sms, AT, null)
        val statement = transaction.withKind(RuleKind.STATEMENT)

        assertEquals(Field.ACCOUNT, statement.step)
        assertTrue(statement.marks.none { it.field == Field.PAYEE })
        // Nothing to make a statement rule from in a spend SMS.
        assertNull(statement.check(emptySet()))
        assertEquals(Field.AMOUNT, statement.withKind(RuleKind.TRANSACTION).step)
    }
}
