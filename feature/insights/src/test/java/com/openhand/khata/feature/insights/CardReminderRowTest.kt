package com.openhand.khata.feature.insights

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.openhand.khata.core.model.CardReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The due date reminders row (#156), on the Cards tab and in Settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardReminderRowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsTheDaysAndPicksMore() {
        var picked: CardReminderSettings? = null
        compose.setContent {
            var settings by remember { mutableStateOf(CardReminderSettings()) }
            CardReminderRow(
                settings = settings,
                notificationsAllowed = true,
                onEnabled = { settings = settings.copy(enabled = it) },
                onDay = { days, on ->
                    settings = settings.copy(
                        daysBefore = if (on) {
                            settings.daysBefore + days
                        } else {
                            settings.daysBefore -
                                days
                        }
                    )
                    picked = settings
                }
            )
        }

        compose.onNodeWithText("3 days before, 1 day before, on the due date").performClick()
        compose.onNodeWithText("7 days before").performClick()

        assertEquals(setOf(7, 3, 1, 0), picked?.daysBefore)
    }

    @Test
    fun withNotificationsOffItSaysHomeOnly() {
        compose.setContent {
            CardReminderRow(CardReminderSettings(), false, {}, { _, _ -> })
        }

        compose.onNodeWithText("On Home only: notifications are off").assertIsDisplayed()
    }

    @Test
    fun turnedOffItSaysOff() {
        var enabled: Boolean? = null
        compose.setContent {
            CardReminderRow(CardReminderSettings(enabled = false), true, {
                enabled = it
            }, { _, _ -> })
        }

        compose.onNodeWithText("Off").performClick()
        compose.onNodeWithText("Remind me", substring = true).performClick()

        assertEquals(true, enabled)
    }
}
