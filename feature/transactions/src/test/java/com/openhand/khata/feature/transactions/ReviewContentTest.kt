package com.openhand.khata.feature.transactions

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.ReviewItem
import com.openhand.khata.core.model.UnparsedSms
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewContentTest {
    @get:Rule val compose = createComposeRule()

    private val saved = mutableListOf<Triple<String, Long?, List<String>>>()
    private val skipped = mutableListOf<Long>()

    private val categories = listOf(
        Category(id = 1, name = null, seedKey = "uncategorized", color = 0, icon = "category"),
        Category(id = 2, name = "Food", color = 0, icon = "restaurant")
    )

    private fun item(id: Long, payee: String = "MCDONALDS", pending: Int = 1) = ReviewItem(
        transactionId = id,
        amountPaise = 6_090,
        direction = Direction.DEBIT,
        timestamp = 1_790_000_000_000,
        accountName = "Kotak 1234",
        payeeId = id,
        payeeIdentifier = payee,
        payeeName = payee,
        payeePending = pending,
        rawSms = "Sent Rs.60.90 from Kotak Bank A/c X1234 to $payee"
    )

    private val unparsedActions = mutableListOf<String>()
    private val sms = UnparsedSms(
        id = 3,
        sender = "JM-KOTAKB-S",
        body = "Rs.2,000 withdrawn at ATM using card XX5678.",
        receivedAt = 1_790_000_000_000
    )

    private fun show(queue: List<ReviewItem>?, unparsed: List<UnparsedSms> = emptyList()) {
        compose.setContent {
            ReviewContent(
                unparsed = unparsed,
                onAddByHand = { unparsedActions += "add ${it.id}" },
                onDismiss = { unparsedActions += "dismiss ${it.id}" },
                onCopy = { unparsedActions += "copy ${it.id}" },
                onOpenIssues = { unparsedActions += "github" },
                onBack = {},
                queue = queue,
                categories = categories,
                tagSuggestions = emptyList(),
                onTagQueryChange = {},
                onSave = { item, name, categoryId, tags ->
                    assertEquals(queue!!.first(), item)
                    saved += Triple(name, categoryId, tags)
                },
                onSkip = { skipped += it.transactionId }
            )
        }
    }

    @Test
    fun showsTheFirstCardWithTheSmsOnRequest() {
        show(listOf(item(1), item(2, payee = "CAFE")))

        compose.onNodeWithText("2 left").assertExists()
        compose.onNodeWithText("₹60.90").assertExists()
        compose.onNodeWithText("In the SMS: MCDONALDS").assertExists()
        compose.onNodeWithText("Sent Rs.60.90", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Show the SMS").performClick()
        compose.onNodeWithText("Sent Rs.60.90", substring = true).assertExists()
    }

    @Test
    fun savesTheNameAndCategory() {
        show(listOf(item(1, pending = 3)))

        compose.onNodeWithText(
            "Saving also fills in 2 more from this payee."
        ).performScrollTo().assertExists()
        compose.onNodeWithText("MCDONALDS").performTextReplacement("McDonald's")
        compose.onNodeWithText("Uncategorized").performScrollTo().performClick()
        compose.onNodeWithText("Food").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()

        assertEquals(listOf(Triple("McDonald's", 2L, emptyList<String>())), saved)
    }

    @Test
    fun skips() {
        show(listOf(item(7)))

        compose.onNodeWithText("Skip").performScrollTo().performClick()

        assertEquals(listOf(7L), skipped)
    }

    @Test
    fun saysAllCaughtUpWhenEmpty() {
        show(emptyList())

        compose.onNodeWithText("All caught up").assertExists()
    }

    @Test
    fun unreadableSmsComeAfterThePayeesAndCountInWhatsLeft() {
        show(listOf(item(1)), listOf(sms))

        compose.onNodeWithText("2 left").assertExists()
        compose.onNodeWithText("In the SMS: MCDONALDS").assertExists()
        compose.onNodeWithText(sms.body).assertDoesNotExist()
    }

    @Test
    fun anUnreadableSmsCanBeAddedDismissedOrCopied() {
        show(emptyList(), listOf(sms))

        compose.onNodeWithText("A bank SMS Khata couldn't read").assertExists()
        compose.onNodeWithText(sms.body).assertExists()
        compose.onNodeWithText("Add by hand").performScrollTo().performClick()
        compose.onNodeWithText("Dismiss").performScrollTo().performClick()
        compose.onNodeWithText("Copy for a bug report").performScrollTo().performClick()
        compose.onNodeWithText("remove names", substring = true).assertExists()
        compose.onNodeWithText("Open GitHub").performClick()

        assertEquals(listOf("add 3", "dismiss 3", "copy 3", "github"), unparsedActions)
        compose.onNodeWithText("SMS copied").assertDoesNotExist()
    }
}
