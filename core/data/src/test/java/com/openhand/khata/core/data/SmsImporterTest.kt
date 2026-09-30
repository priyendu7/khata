package com.openhand.khata.core.data

import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.SmsTransaction
import com.openhand.khata.core.model.Totals
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransactionSource
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
class SmsImporterTest : RepositoryTest() {
    private val importer by lazy { SmsImporter(lazyDb) }
    private val transactions by lazy { TransactionRepository(lazyDb) }

    private fun sms(
        amount: Long = 36_600,
        at: Long = AT,
        ref: String? = "408756022625",
        payee: String? = "GENERAL STORE",
        last4: String? = "7391",
        direction: Direction = Direction.DEBIT,
        body: String = "Sent Rs.${amount / 100} to $payee ref $ref at $at"
    ) = SmsTransaction(
        amountPaise = amount,
        direction = direction,
        timestamp = at,
        bank = "Kotak",
        accountType = AccountType.BANK,
        accountLast4 = last4,
        payee = payee,
        referenceNo = ref,
        rawSms = body
    )

    private suspend fun saved(sms: SmsTransaction) = importer.import(sms) as SmsImportResult.Saved

    private suspend fun count() = db.backupDao().exportRows(null, null).size

    private suspend fun allIds() = db.transactionDao().observeAll().first().map { it.id }.sorted()

    @Test
    fun unknownPayeeIsSavedForReview() = runTest {
        val result = saved(sms())

        assertTrue(result.needsReview)
        val entity = db.transactionDao().getById(result.transactionId)!!
        assertEquals(TransactionSource.SMS, entity.source)
        assertEquals("408756022625", entity.referenceNo)
        assertEquals(sms().rawSms, entity.rawSms)
        assertEquals(uncategorizedId(), entity.categoryId)
        assertTrue(entity.needsReview)
        val payee = db.payeeDao().getById(entity.payeeId!!)!!
        assertEquals("GENERAL STORE", payee.identifier)
        assertEquals("GENERAL STORE", payee.displayName)
    }

    @Test
    fun knownPayeeIsFilledInFromPayeeMemory() = runTest {
        val food = db.categoryDao().getBySeedKey("food")!!.id
        val work = db.tagDao().getOrCreate(listOf("work"))
        val payeeId = db.payeeDao().insert(
            PayeeEntity(
                identifier = "mcdonalds",
                displayName = "McDonald's",
                defaultCategoryId = food
            )
        )
        db.payeeDao().setDefaultTags(payeeId, work)

        val result = saved(sms(payee = "MCDONALDS"))

        assertFalse(result.needsReview)
        val entity = db.transactionDao().getById(result.transactionId)!!
        assertEquals(payeeId, entity.payeeId)
        assertEquals(food, entity.categoryId)
        assertEquals(work, db.transactionDao().tagIds(result.transactionId))
        assertFalse(entity.needsReview)
    }

    @Test
    fun anSmsAfterRememberingACategoryUsesItWithoutReview() = runTest {
        val groceries = db.categoryDao().getBySeedKey("groceries")!!.id
        transactions.save(
            Transaction(
                amountPaise = 5_000,
                direction = Direction.DEBIT,
                timestamp = 0,
                payeeName = "SANTOSH GYANDEV MANM",
                categoryId = groceries
            ),
            rememberPayeeDefaults = true
        )

        val result = saved(sms(payee = "SANTOSH GYANDEV MANM"))

        assertFalse(result.needsReview)
        assertEquals(groceries, db.transactionDao().getById(result.transactionId)!!.categoryId)
    }

    @Test
    fun aPayeeTheUserNamedCountsAsKnown() = runTest {
        db.payeeDao().insert(
            PayeeEntity(identifier = "CRED", displayName = "Card bill", defaultCategoryId = null)
        )

        val result = saved(sms(payee = "CRED"))

        assertFalse(result.needsReview)
        assertEquals(
            uncategorizedId(),
            db.transactionDao().getById(result.transactionId)!!.categoryId
        )
    }

    @Test
    fun anArchivedDefaultCategoryIsNotUsed() = runTest {
        val food = db.categoryDao().getBySeedKey("food")!!
        db.categoryDao().update(food.copy(archived = true))
        db.payeeDao().insert(
            PayeeEntity(identifier = "CAFE", displayName = "CAFE", defaultCategoryId = food.id)
        )

        val result = saved(sms(payee = "CAFE"))

        assertTrue(result.needsReview)
        assertEquals(
            uncategorizedId(),
            db.transactionDao().getById(result.transactionId)!!.categoryId
        )
    }

    @Test
    fun accountIsCreatedOnceThenReused() = runTest {
        val first = saved(sms(ref = "1"))
        val second = saved(sms(ref = "2", at = AT + HOUR))

        val accounts = db.accountDao().getByLast4("7391")
        assertEquals(1, accounts.size)
        with(accounts.single()) {
            assertEquals("Kotak 7391", name)
            assertEquals("Kotak", bank)
            assertEquals(AccountType.BANK, type)
        }
        val accountIds = listOf(first, second).map {
            db.transactionDao().getById(it.transactionId)!!.accountId
        }
        assertEquals(listOf(accounts.single().id, accounts.single().id), accountIds)
    }

    @Test
    fun anAccountTheUserAddedIsFoundByLast4AndBank() = runTest {
        val mine = db.accountDao().insert(
            AccountEntity(
                name = "Salary",
                type = AccountType.BANK,
                bank = "Kotak Mahindra Bank",
                last4 = "7391"
            )
        )
        db.accountDao().insert(
            AccountEntity(name = "HDFC", type = AccountType.BANK, bank = "HDFC", last4 = "7391")
        )

        val result = saved(sms())

        assertEquals(mine, db.transactionDao().getById(result.transactionId)!!.accountId)
        // Neither a new account nor the other bank's account with the same last 4 digits.
        assertEquals(2, db.accountDao().getByLast4("7391").size)
    }

    @Test
    fun anAccountWithOnlyLast4IsUsedWhenItIsTheOnlyOne() = runTest {
        val mine = db.accountDao().insert(
            AccountEntity(name = "Main", type = AccountType.BANK, bank = null, last4 = "7391")
        )

        val result = saved(sms())

        assertEquals(mine, db.transactionDao().getById(result.transactionId)!!.accountId)
    }

    @Test
    fun sameReferenceTwiceIsOneTransaction() = runTest {
        val first = saved(sms())
        val again = importer.import(sms(at = AT + 3 * HOUR, body = "a different text"))

        assertEquals(
            SmsImportResult.Duplicate(first.transactionId, DuplicateMatch.REFERENCE),
            again
        )
        assertEquals(1, count())
    }

    @Test
    fun sameSmsTwiceIsOneTransaction() = runTest {
        val first = saved(sms(ref = null))
        // The inbox scan can time it a few seconds differently from the receiver.
        val again = importer.import(sms(ref = null, at = AT + 5_000, body = sms(ref = null).rawSms))

        assertEquals(SmsImportResult.Duplicate(first.transactionId, DuplicateMatch.SAME_SMS), again)
        assertEquals(1, count())
    }

    @Test
    fun withoutReferenceSameAmountAndAccountWithinTwoMinutesIsADuplicate() = runTest {
        val first = saved(sms(ref = null, body = "first"))

        val oneMinute = importer.import(sms(ref = null, at = AT + MINUTE, body = "second"))
        val tenMinutes = importer.import(sms(ref = null, at = AT + 10 * MINUTE, body = "third"))

        assertEquals(
            SmsImportResult.Duplicate(first.transactionId, DuplicateMatch.AMOUNT_AND_TIME),
            oneMinute
        )
        assertTrue(tenMinutes is SmsImportResult.Saved)
        assertEquals(2, count())
    }

    @Test
    fun differentReferencesAreDifferentPaymentsEvenAtTheSameTime() = runTest {
        saved(sms(ref = "111"))
        val second = importer.import(sms(ref = "222", body = "another"))

        assertTrue(second is SmsImportResult.Saved)
        assertEquals(2, count())
    }

    @Test
    fun aTransactionEnteredByHandIsNotAddedAgain() = runTest {
        // Manual entries usually have no account and no reference.
        val manual = transactions.save(
            Transaction(amountPaise = 36_600, direction = Direction.DEBIT, timestamp = AT - MINUTE)
        )
        val differentAmount = transactions.save(
            Transaction(amountPaise = 36_700, direction = Direction.DEBIT, timestamp = AT)
        )

        val result = importer.import(sms())

        assertEquals(SmsImportResult.Duplicate(manual, DuplicateMatch.AMOUNT_AND_TIME), result)
        assertEquals(listOf(manual, differentAmount).sorted(), allIds())
    }

    @Test
    fun totalsAreRightAfterImportingDebitsCreditsAndRefunds() = runTest {
        saved(sms(amount = 50_000, ref = "1"))
        saved(sms(amount = 100_000, ref = "2", direction = Direction.CREDIT, payee = "EMPLOYER"))
        saved(sms(amount = 10_000, ref = "3", direction = Direction.REFUND))

        val totals = transactions.observeTotals(AT - HOUR, AT + HOUR).first()

        assertEquals(Totals(spentPaise = 40_000, incomePaise = 100_000), totals)
    }

    private companion object {
        const val AT = 1_790_000_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
