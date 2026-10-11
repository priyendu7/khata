package com.openhand.khata.core.data

import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Event
import com.openhand.khata.core.model.SmsTransaction
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewRepositoryTest : RepositoryTest() {
    private val review by lazy { ReviewRepository(lazyDb) }
    private val importer by lazy { SmsImporter(lazyDb) }

    private suspend fun sms(payee: String?, ref: String, at: Long = AT, amount: Long = 5_000) = (
        importer.import(
            SmsTransaction(
                amountPaise = amount,
                direction = Direction.DEBIT,
                timestamp = at,
                bank = "Kotak",
                accountType = AccountType.BANK,
                accountLast4 = "1234",
                payee = payee,
                referenceNo = ref,
                rawSms = "Sent to $payee ref $ref"
            )
        ) as SmsImportResult.Saved
        ).transactionId

    /** A later SMS from [payee], a day after [AT]. */
    private suspend fun nextDay(payee: String) = importer.import(
        SmsTransaction(
            amountPaise = 5_000,
            direction = Direction.DEBIT,
            timestamp = AT + DAY,
            bank = "Kotak",
            accountType = AccountType.BANK,
            accountLast4 = "1234",
            payee = payee,
            referenceNo = "9",
            rawSms = "later"
        )
    ) as SmsImportResult.Saved

    private suspend fun food() = db.categoryDao().getBySeedKey("food")!!.id

    @Test
    fun theQueueShowsWaitingTransactionsNewestFirst() = runTest {
        val older = sms("GENERAL STORE", "1", at = AT)
        val newer = sms("CAFE", "2", at = AT + HOUR)

        val queue = review.observeQueue().first()

        assertEquals(listOf(newer, older), queue.map { it.transactionId })
        with(queue.first()) {
            assertEquals("CAFE", payeeIdentifier)
            assertEquals("Kotak 1234", accountName)
            assertEquals(1, payeePending)
            assertEquals("Sent to CAFE ref 2", rawSms)
        }
        assertEquals(2, review.observeCount().first())
    }

    @Test
    fun namingAPayeeFilesAllItsWaitingTransactions() = runTest {
        val first = sms("GENERAL STORE", "1")
        val second = sms("GENERAL STORE", "2", at = AT + HOUR)
        val other = sms("CAFE", "3", at = AT + 2 * HOUR)
        assertEquals(
            2,
            review.observeQueue().first().first {
                it.transactionId == first
            }.payeePending
        )

        val filed = review.review(first, " Corner shop ", food(), listOf("home"))

        assertEquals(2, filed)
        assertEquals(listOf(other), review.observeQueue().first().map { it.transactionId })
        val tag = db.tagDao().getByName("home")!!.id
        listOf(first, second).forEach { id ->
            val entity = db.transactionDao().getById(id)!!
            assertEquals(food(), entity.categoryId)
            assertFalse(entity.needsReview)
            assertEquals(listOf(tag), db.transactionDao().tagIds(id))
        }
        val payee = db.payeeDao().getByIdentifier("GENERAL STORE")!!
        assertEquals("Corner shop", payee.displayName)
        assertEquals(food(), payee.defaultCategoryId)
        assertEquals(listOf(tag), db.payeeDao().defaultTagIds(payee.id))
    }

    @Test
    fun theNextSmsFromANamedPayeeIsFilledInAutomatically() = runTest {
        review.review(sms("GENERAL STORE", "1"), "Corner shop", food(), emptyList())

        val next = nextDay("GENERAL STORE")

        assertFalse(next.needsReview)
        assertEquals(food(), db.transactionDao().getById(next.transactionId)!!.categoryId)
        assertEquals(0, review.observeCount().first())
    }

    @Test
    fun skipLeavesItUncategorizedAndThePayeeUnnamed() = runTest {
        val id = sms("GENERAL STORE", "1")

        review.skip(id)

        assertEquals(0, review.observeCount().first())
        assertEquals(uncategorizedId(), db.transactionDao().getById(id)!!.categoryId)
        // Not named, so its next SMS waits for review again.
        val next = nextDay("GENERAL STORE")
        assertTrue(next.needsReview)
    }

    @Test
    fun namingWithNoCategoryKeepsItUncategorizedButKnown() = runTest {
        val id = sms("CRED", "1")

        review.review(id, "Card bill", null, emptyList())

        assertEquals(uncategorizedId(), db.transactionDao().getById(id)!!.categoryId)
        assertEquals(null, db.payeeDao().getByIdentifier("CRED")!!.defaultCategoryId)
        val next = nextDay("CRED")
        assertFalse(next.needsReview)
    }

    @Test
    fun aTransactionWithNoPayeeGetsTheNameTyped() = runTest {
        val id = sms(payee = null, ref = "1")

        review.review(id, "Salary", null, emptyList())

        val entity = db.transactionDao().getById(id)!!
        assertEquals("Salary", db.payeeDao().getById(entity.payeeId!!)!!.displayName)
        assertFalse(entity.needsReview)
    }

    private companion object {
        const val AT = 1_790_000_000_000L
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }

    /** An event on the day of [at] in the device's time zone, as SMS import reads it. */
    private suspend fun event(name: String, at: Long) {
        val day = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
        EventRepository(
            lazyDb
        ).save(Event(name = name, start = day, end = day), ZoneId.systemDefault())
    }

    private suspend fun tagsOf(id: Long) = db.transactionDao().tagNames(id)

    @Test
    fun reviewingWithNoTagsKeepsTheEventTag() = runTest {
        event("Kalu waterfall trip", AT)
        val id = sms("WATERFALL CAFE", "1")
        assertEquals(listOf("Kalu waterfall trip"), review.observeQueue().first().single().tags)

        review.review(id, "Waterfall cafe", food(), emptyList())

        assertEquals(listOf("Kalu waterfall trip"), tagsOf(id))
    }

    @Test
    fun theCardsTagsAreAddedButOnlyTheyBecomeThePayeesDefaults() = runTest {
        event("trip", AT)
        val id = sms("WATERFALL CAFE", "1")

        review.review(id, "Waterfall cafe", food(), listOf("food"))

        assertEquals(listOf("food", "trip"), tagsOf(id))
        val payee = db.payeeDao().getByIdentifier("WATERFALL CAFE")!!
        assertEquals(
            listOf(db.tagDao().getByName("food")!!.id),
            db.payeeDao().defaultTagIds(payee.id)
        )
    }

    @Test
    fun otherWaitingTransactionsKeepTheirOwnEventTags() = runTest {
        event("first trip", AT)
        event("second trip", AT + 2 * DAY)
        val first = sms("CAB CO", "1", at = AT)
        val second = sms("CAB CO", "2", at = AT + 2 * DAY)
        val between = sms("CAB CO", "3", at = AT + DAY)

        review.review(first, "Cab co", null, listOf("travel"))

        assertEquals(listOf("first trip", "travel"), tagsOf(first))
        assertEquals(listOf("second trip", "travel"), tagsOf(second))
        assertEquals(listOf("travel"), tagsOf(between))
    }

    @Test
    fun aTagAddedByHandBeforeReviewingIsKept() = runTest {
        val id = sms("GENERAL STORE", "1")
        db.transactionDao().setTags(id, db.tagDao().getOrCreate(listOf("gift")))

        review.review(id, "General Store", food(), listOf("home"))

        assertEquals(listOf("gift", "home"), tagsOf(id))
    }

    @Test
    fun reviewingAlsoFilesASkippedTransactionFromThatPayee() = runTest {
        val skipped = sms("GENERAL STORE", "1", at = AT)
        review.skip(skipped)
        val pending = sms("GENERAL STORE", "2", at = AT + HOUR)

        val filed = review.review(pending, "General Store", food(), emptyList())

        assertEquals(2, filed)
        assertEquals(food(), db.transactionDao().getById(skipped)!!.categoryId)
    }
}
