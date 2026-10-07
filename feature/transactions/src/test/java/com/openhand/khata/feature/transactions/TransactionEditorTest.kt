package com.openhand.khata.feature.transactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
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
import com.openhand.khata.core.model.Payee
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

    private val groceries =
        Category(id = 2, name = null, seedKey = "groceries", color = 0, icon = "groceries")
    private val swiggy = Payee(
        id = 3,
        identifier = "swiggy@icici",
        displayName = "Swiggy",
        defaultCategoryId = food.id,
        defaultTags = listOf("online")
    )

    private var saved: Transaction? = null
    private var remembered: Boolean? = null
    private var deleted = false

    /**
     * Hosts the editor like [TransactionEditorScreen] does, saving the way the ViewModel does and
     * looking payees up in [payees] (by display name or identifier) as they're typed.
     */
    private fun show(start: EditorForm, isNew: Boolean, payees: List<Payee> = emptyList()) {
        fun lookup(name: String) = payees.firstOrNull {
            it.displayName.equals(name.trim(), ignoreCase = true) ||
                it.identifier.equals(name.trim(), ignoreCase = true)
        }
        compose.setContent {
            var form by remember { mutableStateOf(start) }
            var showErrors by remember { mutableStateOf(false) }
            var categories by remember { mutableStateOf(listOf(food, groceries, uncategorized)) }
            TransactionEditorContent(
                isNew = isNew,
                form = form,
                showErrors = showErrors,
                categories = categories,
                accounts = listOf(cash),
                tagSuggestions = listOf("office"),
                onChange = { new ->
                    form = if (new.payee != form.payee) {
                        new.withKnownPayee(lookup(new.payee))
                    } else {
                        new
                    }
                },
                onTagQueryChange = {},
                // Like the ViewModel: a new name is added and selected (an existing one is the
                // repository's job, tested there).
                onAddCategory = { new ->
                    val added = new.copy(id = categories.maxOf { it.id } + 1)
                    categories = categories + added
                    form = form.copy(categoryId = added.id)
                },
                onSave = {
                    if (form.amountError != null) {
                        showErrors = true
                    } else {
                        saved = form.toTransaction(if (isNew) 0 else 42, zone)
                        remembered = form.rememberPayee
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

    @Test
    fun aKnownPayeeFillsInItsCategoryAndTags() {
        show(blank.copy(amount = "120"), isNew = true, payees = listOf(swiggy))

        compose.onNodeWithText("Paid to or received from (optional)").performTextInput("swiggy")

        compose.onNodeWithText("Food").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("online").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Category and tags filled in from Swiggy. Changes here apply to this transaction only."
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()

        assertEquals(food.id, saved!!.categoryId)
        assertEquals(listOf("online"), saved!!.tags)
    }

    @Test
    fun theCategoryCanBeOverriddenForOneTransaction() {
        show(blank.copy(amount = "120"), isNew = true, payees = listOf(swiggy))

        compose.onNodeWithText("Paid to or received from (optional)").performTextInput("Swiggy")
        compose.onNodeWithText("Category").performScrollTo().performClick()
        compose.onNodeWithText("Groceries").performClick()
        compose.onNodeWithContentDescription("Remove online").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()

        assertEquals(groceries.id, saved!!.categoryId)
        assertEquals(emptyList<String>(), saved!!.tags)
    }

    @Test
    fun offersToRememberANewPayee() {
        show(blank.copy(amount = "80"), isNew = true, payees = listOf(swiggy))
        val remember = "Remember this category and tags"

        compose.onNodeWithText(remember, substring = true).assertDoesNotExist()
        compose.onNodeWithText("Paid to or received from (optional)").performTextInput("Ramesh")
        compose.onNodeWithText(remember, substring = true)
            .performScrollTo().performClick()
        compose.onNodeWithText(remember, substring = true).assertIsOff()
        compose.onNodeWithText("Save").performClick()

        assertEquals("Ramesh", saved!!.payeeName)
        assertEquals(false, remembered)
    }

    @Test
    fun addsACategoryFromThePickerAndKeepsTheRestOfTheForm() {
        show(
            blank.copy(amount = "99", payee = "Vet", note = "Checkup", tags = listOf("dog")),
            isNew = true
        )

        compose.onNodeWithText("Category").performScrollTo().performClick()
        compose.onNodeWithText("New category").performClick()
        // Cancelling goes back to the picker with nothing added.
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText("New category").performClick()
        compose.onNodeWithText("Name").performTextInput("Pets")
        // The form's Save, not the editor's behind it.
        compose.onAllNodesWithText("Save").onLast().performScrollTo().performClick()

        compose.onNodeWithText("Pets").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        val transaction = saved!!
        assertEquals(11L, transaction.categoryId)
        assertEquals(9_900L, transaction.amountPaise)
        assertEquals("Vet", transaction.payeeName)
        assertEquals("Checkup", transaction.note)
        assertEquals(listOf("dog"), transaction.tags)
    }
}
