package com.openhand.khata.core.data

import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.SmsTransaction
import com.openhand.khata.core.model.Totals
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransferKind
import com.openhand.khata.core.model.TransferSide
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Transfers saved and edited through [TransactionRepository], and read for Insights (#113). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransferRepositoryTest : RepositoryTest() {
    private val transactions by lazy { TransactionRepository(lazyDb) }
    private val importer by lazy { SmsImporter(lazyDb) }

    private suspend fun entity(id: Long) = db.transactionDao().getById(id)!!

    /** Both sides of a ₹10,000 move from Kotak to HDFC, saved from SMS; returns (out, in). */
    private suspend fun move(): Pair<Long, Long> {
        val out = importer.import(sms(Direction.DEBIT, "Kotak", "7391", AT))
        val into = importer.import(sms(Direction.CREDIT, "HDFC", "5678", AT + MINUTE))
        return (out as SmsImportResult.Saved).transactionId to
            (into as SmsImportResult.Saved).transactionId
    }

    private fun sms(direction: Direction, bank: String, last4: String, at: Long) = SmsTransaction(
        amountPaise = 1_000_000,
        direction = direction,
        timestamp = at,
        bank = bank,
        accountType = AccountType.BANK,
        accountLast4 = last4,
        payee = null,
        referenceNo = null,
        rawSms = "$bank $direction Rs 10000 at $at"
    )

    @Test
    fun choosingTransferWithMoneyOutSavesSideOutAndKindManual() = runTest {
        val account = db.accountDao().insert(
            AccountEntity(
                name = "Kotak Savings",
                type = AccountType.BANK,
                bank = null,
                last4 = null
            )
        )
        val id = transactions.save(
            Transaction(
                amountPaise = 50_000,
                direction = Direction.TRANSFER,
                timestamp = AT,
                accountId = account,
                transferSide = TransferSide.OUT
            )
        )

        assertEquals(TransferSide.OUT, entity(id).transferSide)
        assertEquals(TransferKind.MANUAL, entity(id).transferKind)
        assertEquals(TransferSide.OUT, transactions.get(id)!!.transferSide)
    }

    @Test
    fun changingATransferBackToAnExpenseClearsItAndUnlinksItsPair() = runTest {
        val (out, into) = move()

        transactions.save(transactions.get(out)!!.copy(direction = Direction.DEBIT))

        val expense = entity(out)
        assertEquals(Direction.DEBIT, expense.direction)
        assertNull(expense.transferSide)
        assertNull(expense.transferKind)
        assertNull(expense.transferPairId)
        // The other side stays a transfer, now on its own.
        assertEquals(Direction.TRANSFER, entity(into).direction)
        assertEquals(TransferSide.IN, entity(into).transferSide)
        assertNull(entity(into).transferPairId)
    }

    @Test
    fun editingATransferWithoutChangingItsSideKeepsItsKindAndPair() = runTest {
        val (out, into) = move()

        transactions.save(transactions.get(out)!!.copy(note = "Rent money"))

        assertEquals(TransferKind.OTHER_SIDE, entity(out).transferKind)
        assertEquals(into, entity(out).transferPairId)
        assertEquals(out, entity(into).transferPairId)
    }

    @Test
    fun deletingOneSideUnlinksTheOther() = runTest {
        val (out, into) = move()

        transactions.delete(out)

        assertNull(entity(into).transferPairId)
        assertEquals(Direction.TRANSFER, entity(into).direction)
    }

    @Test
    fun transfersNeverCountInTotalsChartsOrCsv() = runTest {
        move()
        importer.import(
            sms(Direction.DEBIT, "Kotak", "7391", AT + HOUR).copy(
                amountPaise = 36_600,
                payee = "GENERAL STORE",
                rawSms = "Sent Rs 366 to GENERAL STORE"
            )
        )
        val from = AT - DAY
        val until = AT + DAY

        assertEquals(Totals(36_600, 0), transactions.observeTotals(from, until).first())
        assertEquals(
            36_600L,
            transactions.observeCategorySpending(from, until).first().sumOf { it.spentPaise }
        )
        assertEquals(
            36_600L,
            transactions.observeAccountSpending(from, until).first().sumOf { it.spentPaise }
        )
        assertEquals(
            36_600L,
            transactions.observeTagSpending(from, until).first().sumOf { it.spentPaise }
        )
        assertTrue(
            transactions.observeAmounts(from, until).first()
                .none { it.direction == Direction.TRANSFER }
        )
        val csv = BackupRepository(lazyDb).export { it.name.orEmpty() }
        assertEquals(2, csv.count { it.direction == Direction.TRANSFER })
        assertEquals(Totals(36_600, 0), Totals.of(csv.map { it.direction to it.amountPaise }))
        // The Transfers card counts the move, once.
        assertEquals(1_000_000L, transactions.observeTransfers(from, until).first().totalPaise)
    }

    private companion object {
        const val AT = 1_790_000_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
