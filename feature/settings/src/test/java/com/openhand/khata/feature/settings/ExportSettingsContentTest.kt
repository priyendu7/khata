package com.openhand.khata.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportSettingsContentTest {
    @get:Rule val compose = createComposeRule()

    private var exported: String? = null

    private fun show(status: SettingsExportStatus = SettingsExportStatus.Idle) {
        compose.setContent {
            ExportSettingsContent(status = status, onExport = { exported = it }, onBack = {})
        }
    }

    private val password get() = compose.onNodeWithText("Password")
    private val repeat get() = compose.onNodeWithText("Password again")

    // The title says "Export settings" too; the button is the one that can be clicked.
    private fun exportButton() = compose.onNode(hasText("Export settings") and hasClickAction())

    @Test
    fun needsEightCharactersTwice() {
        show()
        compose.onNodeWithText("There's no way to recover", substring = true).assertIsDisplayed()
        exportButton().assertIsNotEnabled()

        password.performTextInput("short12")
        compose.onNodeWithText("At least 8 characters").assertIsDisplayed()
        exportButton().assertIsNotEnabled()

        password.performTextReplacement("long enough")
        repeat.performTextInput("long enougH")
        compose.onNodeWithText("The passwords don't match").assertIsDisplayed()
        exportButton().assertIsNotEnabled()

        repeat.performTextReplacement("long enough")
        exportButton().assertIsEnabled().performClick()
        assertEquals("long enough", exported)
    }

    @Test
    fun showsWhatWasSaved() {
        show(SettingsExportStatus.Done(SettingsCounts(customParsers = 2, payees = 1, events = 3)))

        compose.onNodeWithText("Settings saved").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 custom parsers").assertExists()
        compose.onNodeWithText("1 payee").assertExists()
        compose.onNodeWithText("3 events").assertExists()
    }
}
