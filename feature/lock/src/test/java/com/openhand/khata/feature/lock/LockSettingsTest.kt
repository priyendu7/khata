package com.openhand.khata.feature.lock

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
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
import com.openhand.khata.core.security.lock.LockMethod
import com.openhand.khata.core.security.lock.LockTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockSettingsTest {
    @get:Rule val compose = createComposeRule()

    private val state = LockUiState(
        enabled = true,
        blockScreenshots = false,
        method = LockMethod.DEVICE,
        timeout = LockTimeout.SECONDS_30,
        deviceSecure = true
    )

    @Test
    fun settingsRowShowsMethodAndTimeout() {
        var opened = 0
        compose.setContent {
            LockSettingsRows(state, onOpenLock = { opened++ }, onBlockScreenshots = {})
        }
        compose.onNodeWithText("Phone screen lock · 30 seconds in the background")
            .assertIsDisplayed()
            // Robolectric's simulated touch doesn't reach this ListItem; a real tap and TalkBack
            // both trigger the click action.
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, opened)
    }

    @Test
    fun settingsRowSaysOff() {
        compose.setContent {
            LockSettingsRows(state.copy(enabled = false), onOpenLock = {}, onBlockScreenshots = {})
        }
        compose.onNodeWithText("Off").assertIsDisplayed()
    }

    @Test
    fun blockScreenshotsStaysASwitch() {
        compose.setContent {
            var current by remember { mutableStateOf(state) }
            LockSettingsRows(current, onOpenLock = {}, onBlockScreenshots = {
                current = current.copy(blockScreenshots = it)
            })
        }
        val row = compose.onNodeWithText("Block screenshots")
        row.assertIsOff()
        row.performSemanticsAction(SemanticsActions.OnClick)
        row.assertIsOn()
    }

    private fun showPage() {
        compose.setContent {
            var current by remember { mutableStateOf(state) }
            LockSettingsContent(
                state = current,
                onBack = {},
                onEnabled = { current = current.copy(enabled = it) },
                onTimeout = { current = current.copy(timeout = it) },
                onUseDeviceLock = { current = current.copy(method = LockMethod.DEVICE) },
                pinSetup = { close ->
                    Text("PIN setup")
                    LaunchedEffect(Unit) {
                        current = current.copy(method = LockMethod.PIN)
                        close()
                    }
                }
            )
        }
    }

    @Test
    fun pageChangesTheTimeout() {
        showPage()
        compose.onNodeWithText("30 seconds in the background").performClick()
        compose.onNodeWithText("Immediately").performClick()
        compose.onNodeWithText("Immediately").assertIsDisplayed()
    }

    @Test
    fun turningOffAsksFirst() {
        showPage()
        compose.onNodeWithText("Ask to unlock", substring = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Turn off the app lock?").assertIsDisplayed()
        compose.onNodeWithText("Turn off").performClick()
        compose.onNodeWithText("Unlock with").assertDoesNotExist()
    }

    @Test
    fun choosingAppPinRunsPinSetup() {
        showPage()
        compose.onNodeWithText("Unlock with").performClick()
        compose.onNodeWithText("App PIN").performClick()
        compose.onNodeWithText("Change app PIN").assertIsDisplayed()
        compose.onNodeWithText("new recovery code", substring = true).assertIsDisplayed()
    }
}
