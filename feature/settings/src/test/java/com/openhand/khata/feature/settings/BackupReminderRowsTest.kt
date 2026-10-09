package com.openhand.khata.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.openhand.khata.core.model.ReminderSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupReminderRowsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(notificationsAllowed: Boolean = true) {
        compose.setContent {
            var settings by remember { mutableStateOf(ReminderSettings()) }
            BackupReminderRows(
                settings = settings,
                notificationsAllowed = notificationsAllowed,
                onEnabled = { settings = settings.copy(enabled = it) },
                onInterval = { settings = settings.copy(interval = it) }
            )
        }
    }

    private val switch get() = compose.onNodeWithText("Remind me when", substring = true)

    @Test
    fun rowShowsTheInterval() {
        show()
        compose.onNodeWithText("30 days without an export").assertIsDisplayed()
    }

    @Test
    fun changesTheIntervalInTheDialog() {
        show()
        compose.onNodeWithText("Backup reminder").performClick()
        switch.assertIsOn()
        listOf(7, 14, 60).forEach {
            compose.onNodeWithText("$it days without an export").assertIsDisplayed()
        }
        compose.onNodeWithText("7 days without an export").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("7 days without an export").assertIsDisplayed()
    }

    @Test
    fun turnsOffInTheDialog() {
        show()
        compose.onNodeWithText("Backup reminder").performClick()
        // Robolectric's simulated touch doesn't reach a toggleable ListItem; the click action
        // is what TalkBack and a real tap both trigger.
        switch.performSemanticsAction(SemanticsActions.OnClick)
        switch.assertIsOff()
        compose.onNodeWithText("Remind after").assertDoesNotExist()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Off").assertIsDisplayed()
    }

    @Test
    fun saysItShowsOnHomeWhenNotificationsAreOff() {
        show(notificationsAllowed = false)
        compose.onNodeWithText("on Home only", substring = true).assertIsDisplayed()
    }
}
