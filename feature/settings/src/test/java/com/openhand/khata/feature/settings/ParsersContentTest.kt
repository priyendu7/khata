package com.openhand.khata.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.openhand.khata.core.model.BuiltInRuleOverride
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Tall, so the whole list of rules is laid out.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
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
    private var makes = 0

    private fun showAdd(
        canReadSms: Boolean = false,
        recent: List<SmsInbox.Message> = emptyList(),
        replaces: Boolean = false
    ) {
        compose.setContent {
            var code by remember { mutableStateOf("") }
            AddParserContent(
                onBack = {},
                onMake = { makes++ },
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
    fun offersToMakeARuleFromAnSms() {
        showAdd()

        compose.onNodeWithText("Make one from an SMS").assertExists()
        compose.onNodeWithText("Make a parser").performClick()

        assertEquals(1, makes)
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

    private val custom = listOf(
        CustomParser(1, "hdfc-card", "HDFC", RuleCode.encode(hdfc), enabled = true, addedAt = 2),
        CustomParser(
            2,
            "icici-upi",
            "ICICI",
            RuleCode.encode(
                hdfc.copy(id = "icici-upi", bank = "ICICI", senders = listOf("ICICIB"))
            ),
            enabled = false,
            addedAt = 1
        )
    )
    private val sent = BuiltInRules.all().first { it.id == "kotak-upi-sent" }
    private val received = BuiltInRules.all().first { it.id == "kotak-upi-received" }
    private val calls = mutableListOf<String>()
    private val actions = RuleActions(
        onEnabled = { row, on -> calls += "switch ${row.rule.id} $on" },
        onEdit = { calls += "edit ${it.rule.id}" },
        onCopy = { calls += "copy ${it.code}" },
        onDelete = { calls += "delete ${it.rule.id}" },
        onReset = { calls += "reset ${it.rule.id}" },
        onKeepMine = { calls += "keep ${it.rule.id}" }
    )

    private fun showList(overrides: List<BuiltInRuleOverride> = emptyList()) {
        val rows = parserRows(custom, BuiltInRules.all(), overrides)
        compose.setContent {
            ParsersContent(onBack = {}, onAdd = {}, rows = rows, actions = actions)
        }
    }

    private fun menu(ruleId: String) =
        compose.onNodeWithContentDescription("More for $ruleId").performScrollTo().performClick()

    @Test
    fun listsCustomRulesFirstThenEveryBuiltInRuleByBank() {
        val rows = parserRows(custom, BuiltInRules.all(), emptyList())

        assertEquals(listOf("hdfc-card", "icici-upi"), rows.custom.map { it.rule.id })
        assertEquals(BuiltInRules.all().map { it.id }, rows.builtIn.map { it.rule.id })
        showList()

        compose.onNodeWithText("hdfc-card").assertExists()
        compose.onNodeWithText("HDFC · HDFCBK").assertExists()
        compose.onAllNodesWithText("Expense · Credit card").assertCountEquals(2)
        compose.onNodeWithText("Kotak").performScrollTo().assertExists()
        BuiltInRules.all().forEach {
            compose.onNodeWithText(it.id).performScrollTo().assertExists()
        }
        compose.onNodeWithText("kotak-upi-received").assertExists()
        compose.onAllNodesWithText("Kotak · KOTAKB").assertCountEquals(BuiltInRules.all().size)
        compose.onAllNodesWithText("Income · Bank account").assertCountEquals(2)
    }

    @Test
    fun everyRowHasASwitch() {
        showList(listOf(BuiltInRuleOverride(sent.id, false, null, null)))

        compose.onNodeWithText("hdfc-card").assertIsOn()
        compose.onNodeWithText("icici-upi").assertIsOff().performClick()
        compose.onNodeWithText("kotak-upi-sent").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithText("kotak-upi-received").performScrollTo().assertIsOn().performClick()

        assertEquals(
            listOf(
                "switch icici-upi true",
                "switch kotak-upi-sent true",
                "switch kotak-upi-received false"
            ),
            calls
        )
    }

    @Test
    fun aCustomRulesMenuHasEditCopyAndDelete() {
        showList()

        menu("hdfc-card")
        compose.onNodeWithText("Reset to built-in").assertDoesNotExist()
        compose.onNodeWithText("Edit").performClick()
        menu("hdfc-card")
        compose.onNodeWithText("Copy code").performClick()
        menu("hdfc-card")
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete hdfc-card?").assertExists()
        compose.onNodeWithText("Delete").performClick()

        assertEquals(
            listOf("edit hdfc-card", "copy ${RuleCode.encode(hdfc)}", "delete hdfc-card"),
            calls
        )
    }

    @Test
    fun aBuiltInRuleHasNoDeleteAndAnEditedOneCanBeReset() {
        val edited = sent.copy(bank = "My Kotak")
        showList(
            listOf(
                BuiltInRuleOverride(
                    sent.id,
                    true,
                    RuleCode.encode(edited),
                    BuiltInRules.hash(sent)
                )
            )
        )

        menu("kotak-upi-received")
        compose.onNodeWithText("Edit").assertExists()
        compose.onNodeWithText("Copy code").assertExists()
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.onNodeWithText("Reset to built-in").assertDoesNotExist()
        compose.onNodeWithText("Copy code").performClick()

        compose.onNodeWithText("Edited").performScrollTo().assertExists()
        menu("kotak-upi-sent")
        compose.onNodeWithText("Copy code").performClick()
        menu("kotak-upi-sent")
        compose.onNodeWithText("Reset to built-in").performClick()
        compose.onNodeWithText("Reset kotak-upi-sent?").assertExists()
        compose.onNodeWithText("Reset to built-in").performClick()

        assertEquals(
            listOf(
                "copy ${RuleCode.encode(received)}",
                // The edited version, not the built-in one.
                "copy ${RuleCode.encode(edited)}",
                "reset kotak-upi-sent"
            ),
            calls
        )
    }

    @Test
    fun anUpdatedBuiltInRuleOffersTheNewVersionOrKeepingTheEdit() {
        val edit = BuiltInRuleOverride(
            sent.id,
            true,
            RuleCode.encode(sent.copy(bank = "My Kotak")),
            // The hash of the rule as it was when edited, before the app update changed it.
            BuiltInRules.hash(sent.copy(pattern = sent.pattern + "x"))
        )
        val row = parserRows(emptyList(), BuiltInRules.all(), listOf(edit)).builtIn
            .first { it.rule.id == sent.id }
        assertEquals(BuiltInRules.hash(sent), row.builtInHash)
        showList(listOf(edit))

        compose.onNodeWithText("Updated version available").performScrollTo().assertExists()
        menu("kotak-upi-sent")
        compose.onNodeWithText("Keep mine").performClick()
        menu("kotak-upi-sent")
        compose.onNodeWithText("Use new version").performClick()
        compose.onNodeWithText("Use the new version of kotak-upi-sent?").assertExists()
        compose.onNodeWithText("Use new version").performClick()

        assertEquals(listOf("keep kotak-upi-sent", "reset kotak-upi-sent"), calls)
    }

    @Test
    fun anEditMadeFromTheCurrentRuleIsNotAnUpdate() {
        val edit = BuiltInRuleOverride(
            sent.id,
            true,
            RuleCode.encode(sent.copy(bank = "My Kotak")),
            BuiltInRules.hash(sent)
        )

        val row = parserRows(emptyList(), BuiltInRules.all(), listOf(edit)).builtIn
            .first { it.rule.id == sent.id }

        assertTrue(row.edited)
        assertEquals(false, row.updateAvailable)
        assertEquals("My Kotak", row.rule.bank)
    }

    @Test
    fun anOverrideForARemovedRuleIsIgnored() {
        val rows = parserRows(
            emptyList(),
            BuiltInRules.all(),
            listOf(BuiltInRuleOverride("kotak-gone", false, null, null))
        )

        assertEquals(BuiltInRules.all().map { it.id }, rows.builtIn.map { it.rule.id })
        assertTrue(rows.builtIn.all { it.enabled && !it.edited })
    }

    @Test
    fun saysWhenThereAreNoCustomRules() {
        compose.setContent {
            ParsersContent(
                onBack = {},
                onAdd = {},
                rows = parserRows(emptyList(), BuiltInRules.all(), emptyList()),
                actions = actions
            )
        }

        compose.onNodeWithText("None yet", substring = true).assertExists()
    }

    private companion object {
        const val AT = 1_790_000_000_000L
    }
}
