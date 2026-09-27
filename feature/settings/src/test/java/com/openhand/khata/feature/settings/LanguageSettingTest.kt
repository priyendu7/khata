package com.openhand.khata.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanguageSettingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun choosesALanguage() {
        val chosen = mutableListOf<AppLanguage>()
        compose.setContent {
            var language by remember { mutableStateOf(AppLanguage.SYSTEM) }
            SettingsScreen(versionName = "1.0", language = language, onLanguage = {
                chosen += it
                language = it
            })
        }

        compose.onNodeWithText("System default").performScrollTo().performClick()
        compose.onNodeWithText("हिन्दी").performClick()
        assertEquals(listOf(AppLanguage.HINDI), chosen)
        // The row now shows the choice.
        compose.onNodeWithText("हिन्दी").performScrollTo().performClick()
        compose.onNodeWithText("English").performClick()
        assertEquals(listOf(AppLanguage.HINDI, AppLanguage.ENGLISH), chosen)
    }

    @Test
    @Config(qualifiers = "hi")
    fun languageNamesStayInTheirOwnScript() {
        compose.setContent { SettingsScreen(versionName = "1.0") }

        compose.onNodeWithText("भाषा").performScrollTo().performClick()
        compose.onNodeWithText("English").assertIsDisplayed()
        compose.onNodeWithText("हिन्दी").assertIsDisplayed()
        // Once in the Language row, once in the dialog.
        compose.onAllNodesWithText("सिस्टम डिफ़ॉल्ट").assertCountEquals(2)
    }
}
