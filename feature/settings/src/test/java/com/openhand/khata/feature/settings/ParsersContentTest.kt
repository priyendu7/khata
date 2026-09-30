package com.openhand.khata.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.openhand.khata.core.model.CustomParser
import com.openhand.khata.sms.ingest.SmsInbox
import com.openhand.khata.sms.parser.BuiltInRules
import com.openhand.khata.sms.parser.CodeCheck
import com.openhand.khata.sms.parser.CompiledRule
import com.openhand.khata.sms.parser.CustomRules
import com.openhand.khata.sms.parser.ParserRule
import com.openhand.khata.sms.parser.RuleAccountType
import com.openhand.khata.sms.parser.RuleCode
import com.openhand.khata.sms.parser.RuleDirection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParsersContentTest {
    @get:Rule val compose = createComposeRule()

    private val hdfc = ParserRule(
        v = 1,
        id = "hdfc-card",
        bank = "HDFC",
        senders = listOf("HDFCBK"),
        pattern = "(?<amount>Rs\\.[\\d,.]+) spent on HDFC Bank Card x(?<account>\\d{4}) " +
            "at (?<payee>.+?) on",
        direction = RuleDirection.DEBIT,
        accountType = RuleAccountType.CREDIT_CARD
    )
    private val hdfcSms = "Rs.450.00 spent on HDFC Bank Card x4321 at CITY PHARMACY on 2026-09-20."
    private val saved = mutableListOf<CompiledRule>()

    private fun showAdd(
        canReadSms: Boolean = false,
        recent: List<SmsInbox.Message> = emptyList(),
        replaces: Boolean = false
    ) {
        compose.setContent {
            var code by remember { mutableStateOf("") }
            AddParserContent(
                onBack = {},
                code = code,
                onCode = { code = it },
                check = code.takeIf { it.isNotBlank() }?.let(CustomRules::check),
                replaces = replaces,
                canReadSms = canReadSms,
                recent = recent,
                saving = false,
                onSave = { saved += it },
                now = { AT }
            )
        }
    }

    private fun paste(code: String) = compose.onNodeWithText("Rule code").performTextInput(code)

    @Test
    fun explainsACodeWithoutThePrefix() {
        showAdd()

        paste("hello")

        compose.onNodeWithText("Rule codes start with khata1:", substring = true).assertExists()
        compose.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun explainsACodeForANewerVersion() {
        showAdd()

        paste(RuleCode.encode(hdfc.copy(v = 2)))

        compose.onNodeWithText("for a newer version of Khata", substring = true).assertExists()
    }

    @Test
    fun listsEveryReasonARuleCantBeUsed() {
        showAdd()

        paste(RuleCode.encode(hdfc.copy(id = "HDFC Card", pattern = "spent on HDFC")))

        compose.onNodeWithText("This rule can't be used:").assertExists()
        compose.onNodeWithText("may only use a–z", substring = true).assertExists()
        compose.onNodeWithText("no amount group", substring = true).assertExists()
    }

    @Test
    fun testsAPastedSmsAndSaves() {
        showAdd()

        paste(RuleCode.encode(hdfc))
        compose.onNodeWithText("Senders: HDFCBK").assertExists()
        compose.onNodeWithText("turn on SMS import", substring = true).assertExists()
        compose.onNodeWithText("Or paste an SMS").performScrollTo().performTextInput(hdfcSms)

        compose.onNodeWithText("Amount: ₹450", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("Payee: CITY PHARMACY").assertExists()
        compose.onNodeWithText("Account: 4321").assertExists()
        compose.onNodeWithText("Reference: not found").assertExists()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals(listOf("hdfc-card"), saved.map { it.rule.id })
    }

    @Test
    fun testsARecentSmsFromTheBank() {
        showAdd(
            canReadSms = true,
            recent = listOf(
                SmsInbox.Message("VM-HDFCBK-S", "Your OTP is 123456", AT),
                SmsInbox.Message("VM-HDFCBK-S", hdfcSms, AT - 1)
            )
        )
        paste(RuleCode.encode(hdfc))

        compose.onNodeWithText("Your OTP is 123456").performScrollTo().performClick()
        compose.onNodeWithText("This rule doesn't read this SMS.").performScrollTo().assertExists()
        compose.onNodeWithText(hdfcSms).performScrollTo().performClick()
        compose.onNodeWithText("Payee: CITY PHARMACY").performScrollTo().assertExists()
    }

    @Test
    fun saysWhenSavingReplacesARule() {
        showAdd(replaces = true)

        paste(RuleCode.encode(hdfc))

        compose.onNodeWithText("Saving replaces it", substring = true).assertExists()
    }

    @Test
    fun testRuleUsesTheRulesSenderForAPastedSms() {
        val rule = (CustomRules.check(RuleCode.encode(hdfc)) as CodeCheck.Valid).rule

        assertEquals(45_000L, testRule(rule, null, hdfcSms, AT)?.amountPaise)
        assertEquals(null, testRule(rule, "AX-OTHRBK-S", hdfcSms, AT))
    }

    @Test
    fun listsCustomRulesWithASwitchAndDeleteAndBuiltInOnesReadOnly() {
        val toggles = mutableListOf<Pair<String, Boolean>>()
        val deleted = mutableListOf<String>()
        compose.setContent {
            ParsersContent(
                onBack = {},
                onAdd = {},
                custom = listOf(
                    CustomParser(1, "hdfc-card", "HDFC", "khata1:a", enabled = true, addedAt = 2),
                    CustomParser(2, "icici-upi", "ICICI", "khata1:b", enabled = false, addedAt = 1)
                ),
                builtIn = builtInBanks(),
                onEnabled = { parser, on -> toggles += parser.ruleId to on },
                onDelete = { deleted += it.ruleId }
            )
        }

        compose.onNodeWithText("HDFC").assertIsOn()
        compose.onNodeWithText("icici-upi · Off").assertExists()
        compose.onNodeWithText("ICICI").assertIsOff().performClick()
        assertEquals(listOf("icici-upi" to true), toggles)

        compose.onNodeWithContentDescription("Delete hdfc-card").performClick()
        compose.onNodeWithText("Delete hdfc-card?").assertExists()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("hdfc-card"), deleted)

        val kotak = BuiltInRules.load().count { it.rule.bank == "Kotak" }
        compose.onNodeWithText("Kotak").assertExists()
        compose.onNodeWithText("$kotak rules · KOTAKB").assertExists()
    }

    @Test
    fun saysWhenThereAreNoCustomRules() {
        compose.setContent {
            ParsersContent(
                onBack = {},
                onAdd = {},
                custom = emptyList(),
                builtIn = builtInBanks(),
                onEnabled = { _, _ -> },
                onDelete = {}
            )
        }

        compose.onNodeWithText("None yet", substring = true).assertExists()
    }

    private companion object {
        const val AT = 1_790_000_000_000L
    }
}
