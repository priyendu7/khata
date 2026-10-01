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
        bank: String = "Kotak",
        type: AccountType = AccountType.BANK,
        body: String = "Sent Rs.${amount / 100} to $payee ref $ref at $at"
    ) = SmsTransaction(
        amountPaise = amount,
        direction = direction,
        timestamp = at,
        bank = bank,
        accountType = type,
        accountLast4 = last4,
        payee = payee,
        referenceNo = ref,
        rawSms = body
    )

    private suspend fun saved(sms: SmsTransaction) = importer.import(sms) as SmsImportResult.Saved

    private suspend fun count() = db.backupDao().exportRows(null, null).size

    private suspend fun allIds() = db.transactionDao().observeAll().first().map { it.id }.sorted()

    private suspend fun directionOf(id: Long) = db.transactionDao().getById(id)!!.direction

    /** The totals Home shows, after checking they follow the same rules as `Totals.of`. */
    private suspend fun totals(): Totals {
        val home = transactions.observeTotals(AT - DAY, AT + DAY).first()
        val rows = db.transactionDao().observeAll().first()
        assertEquals(Totals.of(rows.map { it.direction to it.amountPaise }), home)
        return home
    }

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
            PayeeEntity(identifier = "RAVI", displayName = "Ravi Kumar", defaultCategoryId = null)
        )

        val result = saved(sms(payee = "RAVI"))

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

    @Test
    fun previewSavesNothing() = runTest {
        val rows = count()
        val payees = db.payeeDao().observeWithDetails().first().size
        val accounts = db.accountDao().getByLast4("7391").size

        val preview = importer.preview(sms())

        assertEquals(rows, count())
        assertEquals(payees, db.payeeDao().observeWithDetails().first().size)
        assertEquals(accounts, db.accountDao().getByLast4("7391").size)
        assertEquals(null, preview.duplicate)
        assertEquals(null, preview.account)
        assertEquals(null, preview.payeeName)
        assertTrue(preview.needsReview)
    }

    @Test
    fun previewFindsTheAccountAKnownPayeeAndItsCategory() = runTest {
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
        saved(sms(ref = "1", payee = "MCDONALDS"))

        val preview = importer.preview(sms(ref = "2", at = AT + HOUR, payee = "MCDONALDS"))

        assertEquals("Kotak 7391", preview.account?.name)
        assertEquals("McDonald's", preview.payeeName)
        assertEquals("food", preview.category?.seedKey)
        assertEquals(listOf("work"), preview.tags)
        assertFalse(preview.needsReview)
        assertEquals(null, preview.duplicate)
    }

    @Test
    fun previewReportsADuplicate() = runTest {
        val first = saved(sms())

        val preview = importer.preview(sms(body = "the same payment, another SMS"))

        assertEquals(
            SmsImportResult.Duplicate(first.transactionId, DuplicateMatch.REFERENCE),
            preview.duplicate
        )
    }

    @Test
    fun previewSaysACredPaymentWouldBeATransferWithoutReview() = runTest {
        val preview = importer.preview(sms(payee = "CRED"))

        assertEquals(TransferMatch.CARD_PAYMENT, preview.transfer)
        assertFalse(preview.needsReview)
    }

    @Test
    fun previewFindsTheOtherSideOfAMoveAndChangesNothing() = runTest {
        val debit = saved(sms(ref = null, payee = "SELF"))

        val preview = importer.preview(
            sms(ref = null, last4 = "4455", direction = Direction.CREDIT, at = AT + 5 * MINUTE)
        )

        assertEquals(TransferMatch.OTHER_SIDE, preview.transfer)
        assertEquals(Direction.DEBIT, directionOf(debit.transactionId))
    }

    @Test
    fun previewOfADuplicateIsNotATransfer() = runTest {
        saved(sms(payee = "CRED"))

        val preview = importer.preview(sms(payee = "CRED", body = "the same payment, again"))

        assertEquals(DuplicateMatch.REFERENCE, preview.duplicate?.match)
        assertEquals(null, preview.transfer)
    }

    @Test
    fun aCredPaymentAndTheCardsPaymentReceivedAreTransfersAndSpendingIsUnchanged() = runTest {
        val purchase = saved(card(amount = 500_000, direction = Direction.DEBIT, payee = "AMAZON"))
        assertEquals(Totals(spentPaise = 500_000, incomePaise = 0), totals())

        val cred = saved(sms(amount = 500_000, payee = "CRED", ref = "212129343896"))
        val received = saved(
            card(
                amount = 500_000,
                direction = Direction.CREDIT,
                payee = null,
                at = AT + 10 * MINUTE,
                body = "Payment of Rs 5000 received on your HDFC Bank Credit Card XX1234"
            )
        )

        assertEquals(Direction.DEBIT, directionOf(purchase.transactionId))
        assertEquals(Direction.TRANSFER, directionOf(cred.transactionId))
        assertEquals(Direction.TRANSFER, directionOf(received.transactionId))
        // Nothing to ask about a transfer: no category.
        assertFalse(cred.needsReview)
        assertFalse(received.needsReview)
        assertEquals(3, count())
        assertEquals(Totals(spentPaise = 500_000, incomePaise = 0), totals())
    }

    @Test
    fun aCardBillPaidThroughBbpsIsATransfer() = runTest {
        val result = saved(
            sms(
                payee = "BillDesk",
                body = "Rs 4,500 debited from A/c X7391 towards BBPS payment for Axis Credit Card"
            )
        )

        assertEquals(Direction.TRANSFER, directionOf(result.transactionId))
        assertEquals(Totals.ZERO, totals())
    }

    @Test
    fun aMoveBetweenOwnAccountsMakesBothSidesTransfersWithoutAddingAnything() = runTest {
        val out = saved(sms(amount = 1_000_000, ref = "1", payee = "PRIYENDU S"))
        assertEquals(Direction.DEBIT, directionOf(out.transactionId))

        val into = saved(
            sms(
                amount = 1_000_000,
                ref = "2",
                payee = "PRIYENDU SINGH",
                direction = Direction.CREDIT,
                bank = "HDFC",
                last4 = "5678",
                at = AT + 20 * MINUTE
            )
        )

        // The saved debit is updated, not saved again.
        assertEquals(listOf(out.transactionId, into.transactionId), allIds())
        assertEquals(Direction.TRANSFER, directionOf(out.transactionId))
        assertEquals(Direction.TRANSFER, directionOf(into.transactionId))
        assertFalse(db.transactionDao().getById(out.transactionId)!!.needsReview)
        assertEquals(Totals.ZERO, totals())
    }

    @Test
    fun theSameAmountTwoHoursApartIsNotAMove() = runTest {
        val out = saved(sms(amount = 1_000_000, ref = "1"))
        val into = saved(
            sms(
                amount = 1_000_000,
                ref = "2",
                payee = "EMPLOYER",
                direction = Direction.CREDIT,
                bank = "HDFC",
                last4 = "5678",
                at = AT + 2 * HOUR
            )
        )

        assertEquals(Direction.DEBIT, directionOf(out.transactionId))
        assertEquals(Direction.CREDIT, directionOf(into.transactionId))
        assertEquals(Totals(spentPaise = 1_000_000, incomePaise = 1_000_000), totals())
    }

    @Test
    fun theSameAmountOnTheSameAccountIsNotAMove() = runTest {
        saved(sms(amount = 1_000_000, ref = "1"))
        val back = saved(
            sms(amount = 1_000_000, ref = "2", direction = Direction.CREDIT, at = AT + MINUTE)
        )

        assertEquals(Direction.CREDIT, directionOf(back.transactionId))
    }

    @Test
    fun theSameReferenceOnAnotherAccountIsTheOtherSideNotADuplicate() = runTest {
        val out = saved(sms(amount = 250_000, ref = "IMPS42"))
        val into = importer.import(
            sms(
                amount = 250_000,
                ref = "IMPS42",
                direction = Direction.CREDIT,
                bank = "HDFC",
                last4 = "5678",
                // Later than the time window: the reference alone links them.
                at = AT + 3 * HOUR,
                body = "Rs 2500 credited to HDFC A/c 5678 IMPS Ref IMPS42"
            )
        ) as SmsImportResult.Saved

        assertEquals(Direction.TRANSFER, directionOf(out.transactionId))
        assertEquals(Direction.TRANSFER, directionOf(into.transactionId))
        // Seeing either SMS again adds nothing.
        assertEquals(
            SmsImportResult.Duplicate(out.transactionId, DuplicateMatch.REFERENCE),
            importer.import(sms(amount = 250_000, ref = "IMPS42", body = "again"))
        )
        assertEquals(2, count())
        assertEquals(Totals.ZERO, totals())
    }

    @Test
    fun aPayeeMarkedAsOwnAccountMakesATransfer() = runTest {
        db.payeeDao().insert(
            PayeeEntity(
                identifier = "PRIYENDU SINGH",
                displayName = "My HDFC account",
                defaultCategoryId = null,
                ownAccount = true
            )
        )

        val result = saved(sms(payee = "PRIYENDU SINGH"))

        assertEquals(Direction.TRANSFER, directionOf(result.transactionId))
        assertFalse(result.needsReview)
        assertEquals(Totals.ZERO, totals())
    }

    @Test
    fun aTransferChangedBackToAnExpenseStaysAnExpense() = runTest {
        val cred = saved(sms(payee = "CRED"))
        val edited = transactions.get(cred.transactionId)!!.copy(direction = Direction.DEBIT)
        transactions.save(edited)

        // The inbox scan seeing the SMS again doesn't undo the change.
        val again = importer.import(sms(payee = "CRED"))

        assertEquals(
            SmsImportResult.Duplicate(cred.transactionId, DuplicateMatch.REFERENCE),
            again
        )
        assertEquals(Direction.DEBIT, directionOf(cred.transactionId))
        assertEquals(Totals(spentPaise = 36_600, incomePaise = 0), totals())
    }

    private fun card(
        amount: Long,
        direction: Direction,
        payee: String?,
        at: Long = AT - HOUR,
        body: String = "Rs ${amount / 100} spent on HDFC Credit Card XX1234 at $payee"
    ) = sms(
        amount = amount,
        direction = direction,
        payee = payee,
        ref = null,
        bank = "HDFC",
        type = AccountType.CREDIT_CARD,
        last4 = "1234",
        at = at,
        body = body
    )

    private companion object {
        const val AT = 1_790_000_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
