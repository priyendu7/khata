package com.openhand.khata.feature.payees

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Payee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Listing, editing and merging payees through the real screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PayeesScreenTest {
    @get:Rule val compose = createComposeRule()

    private val food = Category(id = 1, name = null, seedKey = "food", color = 0, icon = "food")
    private val groceries =
        Category(id = 2, name = null, seedKey = "groceries", color = 0, icon = "groceries")
    private val chai = Payee(
        id = 10,
        identifier = "paytmqr281005050101@paytm",
        displayName = "Chai stall",
        defaultCategoryId = food.id,
        defaultTags = listOf("office"),
        transactionCount = 3
    )
    private val ramesh = Payee(id = 11, identifier = "Ramesh", displayName = "Ramesh")

    private var saved: Payee? = null
    private var merged: Pair<Payee, Payee>? = null

    private fun show() {
        compose.setContent {
            PayeesContent(
                payees = listOf(chai, ramesh),
                categories = listOf(food, groceries),
                tagSuggestions = emptyList(),
                onTagQueryChange = {},
                onSave = { saved = it },
                onAddCategory = { _, _ -> },
                onMerge = { from, into -> merged = from to into },
                onBack = {}
            )
        }
    }

    @Test
    fun listsIdentifierAndDefaults() {
        show()

        compose.onNodeWithText("paytmqr281005050101@paytm").assertIsDisplayed()
        compose.onNodeWithText("Food · office").assertIsDisplayed()
        compose.onNodeWithText("3 transactions").assertIsDisplayed()
        compose.onNodeWithText("No default category").assertIsDisplayed()
    }

    @Test
    fun editsNameCategoryAndTags() {
        show()

        compose.onNodeWithText("Chai stall").performClick()
        compose.onNodeWithText("Edit payee").performClick()
        compose.onNodeWithText("Name").performTextReplacement("Sharma Tea")
        compose.onNodeWithText("Default category").performClick()
        compose.onNodeWithText("Groceries").performClick()
        compose.onNodeWithContentDescription("Remove office").performClick()
        compose.onNodeWithText("Default tags").performTextInput("snacks")
        compose.onNodeWithText("Default tags").performImeAction()
        compose.onNodeWithText("Save").performClick()

        assertEquals(
            chai.copy(
                displayName = "Sharma Tea",
                defaultCategoryId = groceries.id,
                defaultTags = listOf("snacks")
            ),
            saved
        )
    }

    @Test
    fun marksAPayeeAsOwnAccount() {
        show()

        compose.onNodeWithText("Ramesh").performClick()
        compose.onNodeWithText("Edit payee").performClick()
        compose.onNodeWithText("This is my own account").performClick()
        compose.onNodeWithText("Save").performClick()

        assertEquals(ramesh.copy(ownAccount = true), saved)
    }

    @Test
    fun listsOwnAccounts() {
        compose.setContent {
            PayeesContent(
                payees = listOf(ramesh.copy(ownAccount = true)),
                categories = listOf(food),
                tagSuggestions = emptyList(),
                onTagQueryChange = {},
                onSave = {},
                onAddCategory = { _, _ -> },
                onMerge = { _, _ -> },
                onBack = {}
            )
        }

        compose.onNodeWithText("Own account · No default category").assertIsDisplayed()
    }

    @Test
    fun mergesOnlyAfterConfirming() {
        show()

        compose.onNodeWithText("Ramesh").performClick()
        compose.onNodeWithText("Merge into another payee").performClick()
        // The dialog's entry, not the list row behind it.
        compose.onAllNodesWithText("Chai stall").onLast().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertNull(merged)

        compose.onNodeWithText("Ramesh").performClick()
        compose.onNodeWithText("Merge into another payee").performClick()
        // The dialog's entry, not the list row behind it.
        compose.onAllNodesWithText("Chai stall").onLast().performClick()
        compose.onNodeWithText("Merge Ramesh into Chai stall?").assertIsDisplayed()
        compose.onNodeWithText("Merge").performClick()
        assertEquals(ramesh to chai, merged)
    }
}
