package com.openhand.khata.feature.settings

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.openhand.khata.core.model.UnparsedSms
import com.openhand.khata.sms.parser.RuleMaker
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IgnoreLikeThisContentTest {
    @get:Rule val compose = createComposeRule()

    private val sms = UnparsedSms(
        id = 1,
        sender = "JM-JIOPAY-S",
        body = "Recharge of Rs.299 successful. Plan: Jio Unlimited.",
        receivedAt = 1_790_000_000_000
    )
    private val counted = mutableListOf<Set<String>>()
    private val saved = mutableListOf<Set<String>>()

    @Test
    fun tappedWordsChangeTheCountAndAreSaved() {
        compose.setContent {
            IgnoreLikeThisContent(
                onBack = {},
                sms = sms,
                matches = 2,
                onTap = { counted += it.texts() },
                onSave = { saved += it.texts() }
            )
        }

        compose.onNodeWithText("Matches 2 SMS waiting", substring = true).assertExists()
        // Numbers already change, so they can't be tapped.
        compose.onNodeWithText("Rs.299").assertIsNotEnabled()
        compose.onNodeWithText("Jio").performClick()
        compose.onNodeWithText("Unlimited.").performClick()
        compose.onNodeWithText("Jio").performClick()
        // The title says the same; the button is the one that can be clicked.
        compose.onNode(hasText("Ignore messages like this") and hasClickAction())
            .performScrollTo()
            .performClick()

        assertEquals(
            listOf(setOf("Jio"), setOf("Jio", "Unlimited."), setOf("Unlimited.")),
            counted
        )
        assertEquals(listOf(setOf("Unlimited.")), saved)
    }

    private fun Set<RuleMaker.Word>.texts() = mapTo(mutableSetOf()) { it.text }
}
