package com.openhand.khata.feature.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.openhand.khata.sms.ingest.SmsInbox
import com.openhand.khata.sms.parser.BuiltInRules
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.RuleCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditParserContentTest {
    @get:Rule val compose = createComposeRule()

    private val sent = BuiltInRules.all().first { it.id == "kotak-upi-sent" }
    private val upiSms = "Sent Rs.366.00 from Kotak Bank A/c X1234 to GENERAL STORE on 23-09-26. " +
        "UPI Ref 111122223333."
    private val saved = mutableListOf<CompiledRule>()
    private val checked = mutableListOf<Set<String>>()
    private var remarks = 0

    private fun show(
        canReadSms: Boolean = true,
        recent: List<SmsInbox.Message>? = null,
        builtIn: Boolean = true
    ) {
        compose.setContent {
            var form by remember { mutableStateOf(RuleEditForm.from(sent)) }
            EditParserContent(
                onBack = {},
                form = form,
                builtIn = builtIn,
                onForm = { form = it },
                canReadSms = canReadSms,
                recent = recent,
                onCheck = { checked += it },
                onRemark = { remarks++ },
                saving = false,
                onSave = { saved += it }
            )
        }
    }

    @Test
    fun editsTheFormAndSavesTheRuleUnderItsId() {
        show()

        compose.onNodeWithText("This rule comes with the app", substring = true).assertExists()
        compose.onNodeWithText("Kotak").performTextClearance()
        compose.onNodeWithText("Bank name").performTextInput("My Kotak")
        compose.onNodeWithText("Sender IDs").performTextInput("kotak,")
        compose.onNodeWithText("Credit card").performScrollTo().performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()

        with(saved.single().rule) {
            assertEquals("kotak-upi-sent", id)
            assertEquals("My Kotak", bank)
            assertEquals(listOf("KOTAKB", "KOTAK"), senders)
            assertEquals("credit_card", accountType.name.lowercase())
        }
    }

    @Test
    fun showsTheSameMessagesAsPastingACodeAndWontSave() {
        show()

        compose.onNodeWithContentDescription("Remove KOTAKB").performClick()
        compose.onNodeWithText("Advanced: show the pattern").performScrollTo().performClick()
        compose.onNodeWithText("Pattern").performScrollTo().performTextClearance()
        compose.onNodeWithText("Pattern").performTextInput("Sent (?<amout>[\\d.]+)")

        compose.onNodeWithText("This rule can't be used:").performScrollTo().assertExists()
        compose.onNodeWithText("The senders must be", substring = true).assertExists()
        compose.onNodeWithText("unknown group name", substring = true).assertExists()
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun checksTheRuleOnRecentSmsFromItsSenders() {
        show(
            recent = listOf(
                SmsInbox.Message("JM-KOTAKB-S", upiSms, AT),
                SmsInbox.Message("JM-KOTAKB-S", "Your OTP is 123456", AT - 1)
            )
        )

        compose.onNodeWithText("Check on recent SMS").performScrollTo().performClick()
        assertEquals(listOf(setOf("KOTAKB")), checked)
        compose.onNodeWithText("Reads 1 of 2 recent SMS").performScrollTo().assertExists()
        compose.onNodeWithText("Payee: GENERAL STORE").performScrollTo().assertExists()
        compose.onNodeWithText("This rule doesn't read this SMS.").performScrollTo().assertExists()
    }

    @Test
    fun saysWhenThereIsNothingToCheckOrNoPermission() {
        show(recent = emptyList())
        compose.onNodeWithText("No SMS from these senders", substring = true)
            .performScrollTo().assertExists()
    }

    @Test
    fun withoutTheSmsPermissionCheckIsntOffered() {
        show(canReadSms = false, builtIn = false)

        compose.onNodeWithText("This rule comes with the app", substring = true)
            .assertDoesNotExist()
        compose.onNodeWithText("turn on SMS import", substring = true)
            .performScrollTo().assertExists()
        compose.onNodeWithText("Check on recent SMS").assertDoesNotExist()
    }

    @Test
    fun reMarkOpensTheRuleMaker() {
        show()

        compose.onNodeWithText("Re-mark on an SMS").performScrollTo().performClick()

        assertEquals(1, remarks)
    }

    @Test
    fun copyCodePutsTheRulesCodeOnTheClipboard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val row = RuleRow(sent.copy(bank = "My Kotak"), builtIn = true, enabled = true)

        context.copyToClipboard(row.code)

        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals(RuleCode.encode(sent.copy(bank = "My Kotak")), clip.getItemAt(0).text)
    }

    private companion object {
        const val AT = 1_790_000_000_000L
    }
}
