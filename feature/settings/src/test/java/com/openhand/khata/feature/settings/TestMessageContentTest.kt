package com.openhand.khata.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.openhand.khata.core.data.SmsImportPreview
import com.openhand.khata.core.data.TransferMatch
import com.openhand.khata.sms.ingest.SmsExplanation
import com.openhand.khata.sms.ingest.TriedRule
import com.openhand.khata.sms.parser.BuiltInRules
import com.openhand.khata.sms.parser.ParseResult
import com.openhand.khata.sms.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TestMessageContentTest {
    @get:Rule val compose = createComposeRule()

    private val parser = SmsParser(BuiltInRules.load())
    private val newPayee = SmsImportPreview(
        duplicate = null,
        account = null,
        payeeName = null,
        category = null,
        tags = emptyList(),
        transfer = null,
        needsReview = true
    )
    private val madeFrom = mutableListOf<Pair<String, String>>()
    private val openedFilters = mutableListOf<FilterSwitch>()

    /** Explains with the built-in rules, as if nothing were saved yet. */
    private fun show(preview: SmsImportPreview = newPayee) {
        compose.setContent {
            var answer by remember { mutableStateOf<SmsExplanation?>(null) }
            TestMessageContent(
                onBack = {},
                answer = answer,
                onTest = { sender, body ->
                    val explained = parser.explain(sender, body, AT)
                    answer = SmsExplanation(
                        explained.result,
                        explained.rulesTried.map { TriedRule(it, custom = false) },
                        preview.takeIf { explained.result is ParseResult.Parsed }
                    )
                },
                onMakeParser = { sender, body -> madeFrom += sender to body },
                onOpenFilter = { openedFilters += it }
            )
        }
    }

    private fun test(sender: String, body: String) {
        compose.onNodeWithText("Sender").performTextInput(sender)
        compose.onNodeWithText("SMS text").performTextInput(body)
        compose.onNodeWithText("Test").performScrollTo().performClick()
    }

    @Test
    fun aKotakSmsShowsWhatWouldBeRecorded() {
        show()

        test("JM-KOTAKB-S", KOTAK_SMS)

        compose.onNodeWithText("Read by parser kotak-upi-sent").performScrollTo().assertExists()
        compose.onNodeWithText("Amount: ₹366", substring = true).assertExists()
        compose.onNodeWithText("Payee: new payee, GENERAL STORE").assertExists()
        compose.onNodeWithText("Account: new account, Kotak 1234").assertExists()
        compose.onNodeWithText("Reference: 111122223333").assertExists()
        compose.onNodeWithText("New payee: it would wait in To review", substring = true)
            .assertExists()
    }

    @Test
    fun aCardBillPaymentSaysItWouldBeATransfer() {
        show(newPayee.copy(transfer = TransferMatch.CARD_PAYMENT, needsReview = false))

        test("JM-KOTAKB-S", KOTAK_SMS)

        compose.onNodeWithText("A credit card bill payment", substring = true)
            .performScrollTo()
            .assertExists()
        compose.onNodeWithText("New payee: it would wait in To review", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun aPhoneNumberIsNotRead() {
        show()

        test("+919876543210", "Sent Rs.5 for dinner")

        compose.onNodeWithText("Not read").performScrollTo().assertExists()
        compose.onNodeWithText("Messages from people (phone numbers) are never read.")
            .assertExists()
        // People's messages have no switch.
        compose.onNodeWithText("Change this filter").assertDoesNotExist()
    }

    @Test
    fun aReminderNamesTheGroupAndWords() {
        show()

        test("JM-KOTAKB-S", "Your card bill of Rs.500 is due on 05-10")

        compose.onNodeWithText("Filtered out after no parser read it").performScrollTo()
            .assertExists()
        compose.onNodeWithText("Bill reminder: ‘is due’", substring = true).assertExists()
        compose.onNodeWithText("Change this filter").performScrollTo().performClick()
        assertEquals(listOf(FilterSwitch.NOT_TRANSACTION), openedFilters)
    }

    @Test
    fun anSmsNoParserReadsOffersToMakeOne() {
        show()

        test("VM-HDFCBK-S", "Rs.450.00 spent on HDFC Bank Card x4321 at CITY PHARMACY")

        compose.onNodeWithText("Would go to To review").performScrollTo().assertExists()
        compose.onNodeWithText("No parser is for this sender.").assertExists()
        compose.onNodeWithText("Make a parser from this SMS").performScrollTo().performClick()
        assertEquals(
            listOf("VM-HDFCBK-S" to "Rs.450.00 spent on HDFC Bank Card x4321 at CITY PHARMACY"),
            madeFrom
        )
    }

    private companion object {
        const val AT = 1_790_000_000_000L
        const val KOTAK_SMS =
            "Sent Rs.366.00 from Kotak Bank A/c X1234 to GENERAL STORE on 23-09-26. " +
                "UPI Ref 111122223333. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
    }
}
