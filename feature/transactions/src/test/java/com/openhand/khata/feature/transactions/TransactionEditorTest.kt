package com.openhand.khata.feature.transactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Transaction
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Adding and editing a transaction through the real editor UI, with the ViewModel's rules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionEditorTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val food = Category(id = 1, name = null, seedKey = "food", color = 0, icon = "food")
    private val uncategorized =
        Category(id = 10, name = null, seedKey = "uncategorized", color = 0, icon = "uncategorized")
    private val cash = Account(id = 5, name = "Cash", type = AccountType.WALLET)

    private var saved: Transaction? = null
    private var deleted = false

    /** Hosts the editor like [TransactionEditorScreen] does, saving the way the ViewModel does. */
    private fun show(start: EditorForm, isNew: Boolean) {
        compose.setContent {
            var form by remember { mutableStateOf(start) }
            var showErrors by remember { mutableStateOf(false) }
            TransactionEditorContent(
                isNew = isNew,
                form = form,
                showErrors = showErrors,
                categories = listOf(food, uncategorized),
                accounts = listOf(cash),
                tagSuggestions = listOf("office"),
                onChange = { form = it },
                onTagQueryChange = {},
                onSave = {
                    if (form.amountError != null) {
                        showErrors = true
                    } else {
                        saved = form.toTransaction(if (isNew) 0 else 42, zone)
                    }
                },
                onDelete = { deleted = true },
                onBack = {}
            )
        }
    }

    private val blank = EditorForm(date = LocalDate.of(2026, 9, 26), time = LocalTime.of(9, 30))

    @Test
    fun addsATransaction() {
        show(blank, isNew = true)

        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Enter an amount").assertIsDisplayed()
        assertEquals(null, saved)

        compose.onNodeWithTag(AMOUNT_FIELD_TAG).performTextInput("250.5")
        compose.onNodeWithText("Income").performClick()
        compose.onNodeWithText("Paid to or received from (optional)").performTextInput("Acme Ltd")
        compose.onNodeWithText("Category").performScrollTo().performClick()
        compose.onNodeWithText("Food").performClick()
        compose.onNodeWithText("Account").performScrollTo().performClick()
        compose.onNodeWithText("Cash").performClick()
        compose.onNodeWithText("Tags (optional)").performScrollTo().performTextInput("salary")
        compose.onNodeWithText("Tags (optional)").performImeAction()
        compose.onNodeWithText("office").performScrollTo().performClick()
        compose.onNodeWithText("Note (optional)").performScrollTo().performTextInput("September")
        compose.onNodeWithText("Save").performClick()

        val transaction = saved!!
        assertEquals(25_050L, transaction.amountPaise)
        assertEquals(Direction.CREDIT, transaction.direction)
        assertEquals("Acme Ltd", transaction.payeeName)
        assertEquals(food.id, transaction.categoryId)
        assertEquals(cash.id, transaction.accountId)
        assertEquals(listOf("salary", "office"), transaction.tags)
        assertEquals("September", transaction.note)
    }

    @Test
    fun rejectsLettersAndAZeroAmount() {
        show(blank, isNew = true)

        compose.onNodeWithTag(AMOUNT_FIELD_TAG).performTextInput("abc")
        compose.onNodeWithTag(AMOUNT_FIELD_TAG).performTextInput("0")
        compose.onNodeWithText("Save").performClick()

        compose.onNodeWithText("The amount must be more than ₹0").assertIsDisplayed()
        assertEquals(null, saved)
    }

    @Test
    fun editsATransaction() {
        val existing = Transaction(
            id = 42,
            amountPaise = 12_340,
            direction = Direction.DEBIT,
            timestamp = blank.copy(amount = "1").toTransaction(0, zone).timestamp,
            accountId = cash.id,
            payeeName = "Dhaba",
            categoryId = food.id,
            tags = listOf("family"),
            note = "Dinner"
        )
        show(EditorForm.from(existing, zone), isNew = false)

        compose.onNodeWithText("Edit transaction").assertIsDisplayed()
        compose.onNodeWithTag(AMOUNT_FIELD_TAG).performTextReplacement("150")
        compose.onNodeWithContentDescription("Remove family").performScrollTo().performClick()
        compose.onNodeWithText("Category").performScrollTo().performClick()
        compose.onNodeWithText("Uncategorized").performClick()
        compose.onNodeWithText("Save").performClick()

        val transaction = saved!!
        assertEquals(42L, transaction.id)
        assertEquals(15_000L, transaction.amountPaise)
        assertEquals(emptyList<String>(), transaction.tags)
        assertEquals(null, transaction.categoryId)
        assertEquals("Dhaba", transaction.payeeName)
        assertEquals(existing.timestamp, transaction.timestamp)
    }

    @Test
    fun deletesOnlyAfterConfirming() {
        show(blank.copy(amount = "10"), isNew = false)

        compose.onNodeWithText("Delete transaction").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(false, deleted)

        compose.onNodeWithText("Delete transaction").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        assertTrue(deleted)
    }
}
