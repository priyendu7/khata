package com.openhand.khata.feature.transactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.ReviewItem
import com.openhand.khata.core.model.SenderId
import com.openhand.khata.core.model.UnparsedSms
import com.openhand.khata.core.model.UnparsedSmsGroup
import com.openhand.khata.sms.parser.IgnoreTemplate
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
    private val ignored = mutableListOf<String>()
    private val sms = UnparsedSms(
        id = 3,
        sender = "JM-KOTAKB-S",
        body = "Rs.2,000 withdrawn at ATM using card XX5678.",
        receivedAt = 1_790_000_000_000
    )

    private fun show(queue: List<ReviewItem>?, unparsed: List<UnparsedSms> = emptyList()) {
        compose.setContent {
            ReviewContent(
                unparsed = grouped(unparsed),
                onAddByHand = { unparsedActions += "add ${it.id}" },
                onMakeParser = { unparsedActions += "parser ${it.id}" },
                onDismiss = { unparsedActions += "dismiss ${it.id}" },
                onDismissAll = { group -> unparsedActions += "dismiss all ${group.header}" },
                onCopy = { unparsedActions += "copy ${it.id}" },
                onOpenIssues = { unparsedActions += "github" },
                onIgnoreSender = { ignored += "sender ${it.id}" },
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

        compose.onNodeWithText("Khata found a transaction but couldn't read it").assertExists()
        compose.onNodeWithText("reads SMS like this automatically next time", substring = true)
            .assertExists()
        compose.onNodeWithText("if it isn't a transaction", substring = true).assertExists()
        compose.onNodeWithText(sms.body).assertExists()
        compose.onNodeWithText("Add by hand").performScrollTo().performClick()
        compose.onNodeWithText("Dismiss").performScrollTo().performClick()
        more().performClick()
        compose.onNodeWithText("Copy for a bug report").performClick()
        compose.onNodeWithText("remove names", substring = true).assertExists()
        compose.onNodeWithText("Open GitHub").performClick()

        assertEquals(listOf("add 3", "dismiss 3", "copy 3", "github"), unparsedActions)
        compose.onNodeWithText("SMS copied").assertDoesNotExist()
    }

    @Test
    fun ignoringASenderAsksFirst() {
        show(emptyList(), listOf(sms))

        more().performClick()
        compose.onNodeWithText("Ignore this sender").performClick()
        compose.onNodeWithText("Ignore all SMS from KOTAKB?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<String>(), ignored)

        more().performClick()
        compose.onNodeWithText("Ignore this sender").performClick()
        compose.onNodeWithText("Ignore").performClick()
        assertEquals(listOf("sender 3"), ignored)
    }

    /** The host saves the template as Ignore messages like this does with nothing tapped. */
    @Test
    fun ignoringLikeThisTakesTheCardAndItsMatchesOutOfToReview() {
        val sameKind = sms.copy(id = 4, body = "Rs.500 withdrawn at ATM using card XX1111.")
        val other = sms.copy(id = 5, body = "Rs.20 cashback credited to your account.")
        compose.setContent {
            var waiting by remember { mutableStateOf(listOf(sms, sameKind, other)) }
            ReviewContent(
                onBack = {},
                queue = emptyList(),
                categories = categories,
                tagSuggestions = emptyList(),
                onTagQueryChange = {},
                onSave = { _, _, _, _ -> },
                onSkip = {},
                unparsed = grouped(waiting),
                onIgnoreLikeThis = { from ->
                    val template = IgnoreTemplate.compile(
                        IgnoreTemplate.pattern(from.body, emptyList())
                    )!!
                    waiting = waiting.filterNot { template.matches(it.body) }
                }
            )
        }
        compose.onNodeWithText("3 left").assertExists()
        compose.onNodeWithText("Show all 3").performScrollTo().performClick()

        // The group's menu first, then each SMS's.
        compose.onAllNodesWithContentDescription("More actions")[1].performScrollTo().performClick()
        compose.onNodeWithText("Ignore messages like this").performClick()

        compose.onNodeWithText("1 left").assertExists()
        compose.onNodeWithText(sms.body).assertDoesNotExist()
        compose.onNodeWithText(other.body).assertExists()
    }

    /** On a tall screen, so the list composes every card. */
    @Test
    @Config(qualifiers = TALL)
    fun aSenderWithManySmsIsOneCardThatExpands() {
        val older = sms.copy(id = 4, sender = "AX-KOTAKB-S", body = "Rs.700 withdrawn at ATM.")
        val hdfc = sms.copy(id = 5, sender = "VM-HDFCBK-S", body = "Rs.99 paid to a shop.")
        show(emptyList(), listOf(sms, hdfc, older))

        compose.onNodeWithText("3 left").assertExists()
        compose.onNodeWithText("Khata found transactions but couldn't read them").assertExists()
        compose.onNodeWithText("KOTAKB · 2 messages").assertExists()
        compose.onNodeWithText(sms.body).assertExists()
        compose.onNodeWithText(older.body).assertDoesNotExist()
        // A sender with one SMS keeps its own actions.
        compose.onNodeWithText(hdfc.body).performScrollTo().assertExists()

        compose.onNodeWithText("Show all 2").performScrollTo().performClick()
        compose.onNodeWithText(older.body).performScrollTo().assertExists()
        compose.onAllNodesWithText("Add by hand")[1].performScrollTo().performClick()
        compose.onAllNodesWithText("Make a parser")[0].performScrollTo().performClick()

        compose.onNodeWithText("Hide the messages").performScrollTo().performClick()
        compose.onNodeWithText(older.body).assertDoesNotExist()
        assertEquals(listOf("add 4", "parser 3"), unparsedActions)
    }

    @Test
    fun dismissingAllOfASenderAsksFirst() {
        show(emptyList(), listOf(sms, sms.copy(id = 4, body = "Rs.700 withdrawn at ATM.")))

        compose.onNodeWithText("Dismiss all").performScrollTo().performClick()
        compose.onNodeWithText("Dismiss all 2 SMS from KOTAKB?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<String>(), unparsedActions)

        compose.onNodeWithText("Dismiss all").performScrollTo().performClick()
        compose.onNode(hasText("Dismiss all") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("dismiss all KOTAKB"), unparsedActions)
    }

    private fun more() = compose.onNodeWithContentDescription("More actions").performScrollTo()

    private companion object {
        const val TALL = "w400dp-h2000dp"
    }

    /** As [com.openhand.khata.core.data.UnparsedSmsRepository.observeGroups] groups them. */
    private fun grouped(newestFirst: List<UnparsedSms>) =
        newestFirst.groupBy { SenderId.parse(it.sender)!!.header }
            .map { (header, messages) -> UnparsedSmsGroup(header, messages) }
}
