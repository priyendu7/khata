package com.openhand.khata.core.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.openhand.khata.core.model.Category
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryEditorTest {
    @get:Rule val compose = createComposeRule()

    private val fuel = Category(id = 1, name = "Fuel", color = 0, icon = "fuel")

    // A default category with no name of its own: shown as "Food".
    private val food = Category(id = 2, name = null, seedKey = "food", color = 0, icon = "food")

    @Test
    fun aTakenNameIsRefused() {
        compose.setContent {
            CategoryEditor(
                category = Category(name = null, color = 0, icon = "label"),
                onSave = {},
                onDismiss = {},
                others = listOf(fuel, food)
            )
        }

        listOf(" fuel ", "FOOD").forEach { name ->
            compose.onNodeWithText("Name").performTextReplacement(name)
            compose.onNodeWithText("A category with this name already exists").assertExists()
            compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        }
        compose.onNodeWithText("Name").performTextReplacement("Pets")
        compose.onNodeWithText("A category with this name already exists").assertDoesNotExist()
        compose.onNodeWithText("Save").performScrollTo().assertIsEnabled()
    }
}
