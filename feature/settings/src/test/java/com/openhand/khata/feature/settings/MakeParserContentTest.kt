package com.openhand.khata.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.openhand.khata.sms.ingest.SmsInbox
import com.openhand.khata.sms.parser.CodeCheck
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.CustomRules
import com.openhand.khata.sms.parser.RuleCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MakeParserContentTest {
    @get:Rule val compose = createComposeRule()

    private val sms = "Rs.450.00 spent on HDFC Bank Card x4321 at CITY PHARMACY on 2026-09-20. " +
        "Avl Lmt Rs.10,000"
    private val other = "Rs.99.50 spent on HDFC Bank Card x4321 at MEDPLUS on 2026-09-22. " +
        "Avl Lmt Rs.9,900"
    private val saved = mutableListOf<CompiledRule>()

    private fun showMaker(sender: String? = "AX-HDFCBK-S", canReadSms: Boolean = true) {
        compose.setContent {
            var form by remember { mutableStateOf(MakerForm.start(sender, sms, AT, null)) }
            MakeParserContent(
                onBack = {},
                form = form,
                onForm = { form = it },
                check = form.check(emptySet()),
                canReadSms = canReadSms,
                recent = listOf(
                    SmsInbox.Message("AX-HDFCBK-S", other, AT - 1),
                    SmsInbox.Message("AX-HDFCBK-S", "Rs.5 cashback credited to your card", AT - 2)
                ),
                saving = false,
                onSave = { saved += it }
            )
        }
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()

    /** Marks [words] in the current step, then goes on. */
    private fun step(vararg words: String) {
        words.forEach(::tap)
        tap("Next")
    }

    @Test
    fun marksOneFieldAtATimeChecksAndSaves() {
        showMaker()
        compose.onNodeWithText("Step 1 of 5").assertExists()
        compose.onNodeWithText("Tap the amount").assertExists()

        step("Rs.450.00")
        compose.onNodeWithText("Tap who was paid or who paid").assertExists()
        step("CITY", "PHARMACY")
        compose.onNodeWithText("Tap the account or card number").assertExists()
        step("x4321")
        tap("Skip")
        compose.onNodeWithText("Tap the date").assertExists()
        tap("2026-09-20.")
        compose.onNodeWithText("Date: 2026-09-20.").assertExists()
        // The offered format, picked, and in the field for another one.
        compose.onAllNodesWithText("yyyy-MM-dd").assertCountEquals(2)
        tap("Next")

        compose.onNodeWithText("Reference: skipped").assertExists()
        compose.onNodeWithText("Bank name").performScrollTo().performTextReplacement("HDFC Bank")
        compose.onNodeWithText("Rule name: hdfc-bank-debit").performScrollTo().assertExists()
        // Once in the chips, once in what the rule read from this SMS.
        compose.onAllNodesWithText("Payee: CITY PHARMACY").assertCountEquals(2)
        // Read from this SMS and the other one, which uses the same card.
        compose.onAllNodesWithText("Account: 4321").assertCountEquals(2)
        // The other recent SMS: one it reads, one it doesn't.
        compose.onNodeWithText("Payee: MEDPLUS").performScrollTo().assertExists()
        compose.onNodeWithText("Amount: ₹99.50").assertExists()
        compose.onNodeWithText("This rule doesn't read this SMS.").assertExists()

        tap("Save rule")

        val rule = saved.single().rule
        assertEquals("hdfc-bank-debit", rule.id)
        assertEquals("HDFC Bank", rule.bank)
        assertEquals(listOf("HDFCBK"), rule.senders)
        assertEquals("yyyy-MM-dd", rule.dateFormat)
        // Saved as its code, which Settings > Parsers reads back.
        val code = RuleCode.encode(rule)
        assertTrue(CustomRules.check(code) is CodeCheck.Valid)
    }

    @Test
    fun theAmountCantBeSkipped() {
        showMaker()

        compose.onNodeWithText("Skip").assertDoesNotExist()
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.onNodeWithText("Next").assertIsNotEnabled()
        tap("Rs.450.00")
        compose.onNodeWithText("Next").assertIsEnabled()
        // Tapped again, it's cleared.
        tap("Rs.450.00")
        compose.onNodeWithText("Next").assertIsNotEnabled()
    }

    @Test
    fun backKeepsWhatWasMarked() {
        showMaker()

        step("Rs.450.00")
        step("CITY", "PHARMACY")
        tap("Back")
        compose.onNodeWithText("Tap who was paid or who paid").assertExists()
        compose.onNodeWithText("Payee: CITY PHARMACY").assertExists()
        tap("Back")
        compose.onNodeWithText("Amount: Rs.450.00").assertExists()
    }

    @Test
    fun aChipOpensItsStepAgainAndComesBack() {
        showMaker()
        step("Rs.450.00")
        repeat(4) { tap("Skip") }

        tap("Account: skipped")
        compose.onNodeWithText("Tap the account or card number").assertExists()
        step("x4321")

        compose.onNodeWithText("Account: x4321").assertExists()
        compose.onNodeWithText("Bank name").assertExists()
    }

    @Test
    fun aPastedSmsNeedsASender() {
        showMaker(sender = null, canReadSms = false)

        step("Rs.450.00")
        repeat(4) { tap("Skip") }

        compose.onNodeWithText("The senders must be", substring = true).performScrollTo()
            .assertExists()
        compose.onNodeWithText("The bank name is empty", substring = true).assertExists()
        compose.onNodeWithText("Sender IDs").performScrollTo().performTextInput("HDFCBK")
        compose.onNodeWithText("Bank name").performScrollTo().performTextInput("HDFC")
        compose.onNodeWithText("turn on SMS import", substring = true).performScrollTo()
            .assertExists()
        compose.onNodeWithText("Save rule").performScrollTo().assertExists()
    }

    @Test
    fun picksARecentSmsOrAPastedOne() {
        val picked = mutableListOf<String>()
        compose.setContent {
            PickSmsContent(
                onBack = {},
                canReadSms = true,
                candidates = listOf(SmsInbox.Message("AX-HDFCBK-S", sms, AT)),
                onPick = { picked += it.body },
                onPaste = { picked += it }
            )
        }

        compose.onNodeWithText(sms).performClick()
        compose.onNodeWithText("Or paste an SMS").performTextInput("  $other ")
        tap("Use this SMS")

        assertEquals(listOf(sms, other), picked)
    }

    private companion object {
        const val AT = 1_790_000_000_000L
    }
}
