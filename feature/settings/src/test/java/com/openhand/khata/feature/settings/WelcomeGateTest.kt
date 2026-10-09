package com.openhand.khata.feature.settings

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WelcomeGateTest {
    @get:Rule val compose = createComposeRule()

    /** A lock that's locked: it shows its prompt and never its content. */
    private fun show(welcoming: Boolean, beforeLock: Boolean) {
        compose.setContent {
            WelcomeGate(
                welcoming = welcoming,
                beforeLock = beforeLock,
                welcome = { Text("welcome") },
                lock = { Text("unlock") },
                app = { Text("home") }
            )
        }
    }

    @Test
    fun onFirstOpenTheWelcomeComesBeforeTheLock() {
        show(welcoming = true, beforeLock = true)

        compose.onNodeWithText("welcome").assertIsDisplayed()
        compose.onNodeWithText("unlock").assertDoesNotExist()
    }

    @Test
    fun onLaterOpensTheLockComesFirst() {
        show(welcoming = false, beforeLock = true)

        compose.onNodeWithText("unlock").assertIsDisplayed()
        compose.onNodeWithText("welcome").assertDoesNotExist()
    }

    @Test
    fun afterAnUpdateTheWelcomeWaitsBehindTheLock() {
        show(welcoming = true, beforeLock = false)

        compose.onNodeWithText("unlock").assertIsDisplayed()
        compose.onNodeWithText("welcome").assertDoesNotExist()
    }

    @Test
    fun unlockedItShowsTheWelcomeThenTheApp() {
        compose.setContent {
            WelcomeGate(
                welcoming = true,
                beforeLock = false,
                welcome = { Text("welcome") },
                lock = { it() },
                app = { Text("home") }
            )
        }

        compose.onNodeWithText("welcome").assertIsDisplayed()
        compose.onNodeWithText("home").assertDoesNotExist()
    }

    @Test
    fun theWelcomeIsSeenOnce() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(WelcomeSettings(context).seen)
        assertTrue(WelcomeSettings(context).freshInstall)

        WelcomeSettings(context).seen = true
        assertTrue(WelcomeSettings(context).seen)
    }
}
