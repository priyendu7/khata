package com.openhand.khata.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.openhand.khata.sms.parser.SmsFilters
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FiltersContentTest {
    @get:Rule val compose = createComposeRule()

    private var stored = SmsFilters()

    private fun show(initial: SmsFilters) {
        stored = initial
        compose.setContent {
            var filters by remember { mutableStateOf(initial) }
            FiltersContent(
                onBack = {},
                filters = filters,
                onChange = { switch, on ->
                    filters = switch.set(filters, on)
                    stored = filters
                }
            )
        }
    }

    private val titles = mapOf(
        FilterSwitch.PROMOTIONAL to "Drop promotional senders",
        FilterSwitch.GOVERNMENT to "Drop government senders",
        FilterSwitch.ONLY_SERVICE to "Only service senders",
        FilterSwitch.NO_AMOUNT to "Drop SMS with no amount",
        FilterSwitch.NO_TRANSACTION_WORD to "Drop SMS with no transaction word",
        FilterSwitch.NOT_TRANSACTION to "Drop OTPs, offers and reminders"
    )

    @Test
    fun everySwitchShowsItsStoredValueAndChangesIt() {
        assertEquals(FilterSwitch.entries.toSet(), titles.keys)
        show(SmsFilters(onlyService = false, noAmount = false))

        titles.forEach { (switch, title) ->
            val node = compose.onNodeWithText(title).performScrollTo()
            val wasOn = switch.isOn(stored)
            if (wasOn) node.assertIsOn() else node.assertIsOff()

            node.performClick()

            if (wasOn) node.assertIsOff() else node.assertIsOn()
            assertEquals(title, !wasOn, switch.isOn(stored))
        }
        assertEquals(
            SmsFilters(
                dropPromotional = false,
                dropGovernment = false,
                onlyService = true,
                noAmount = true,
                noTransactionWord = false,
                notTransaction = false
            ),
            stored
        )
    }

    @Test
    fun phoneNumbersAreAFixedLineAndTheNoteExplainsPastSms() {
        show(SmsFilters())

        compose.onNodeWithText("Messages from people (phone numbers) are never read.")
            .assertExists()
        compose.onNodeWithText("import past SMS again", substring = true).performScrollTo()
            .assertExists()
    }
}
