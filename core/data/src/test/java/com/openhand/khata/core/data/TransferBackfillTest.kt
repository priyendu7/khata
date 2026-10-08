package com.openhand.khata.core.data

import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.database.entity.TransactionEntity
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.SmsTransaction
import com.openhand.khata.core.model.TransactionSource
import com.openhand.khata.core.model.TransferKind
import com.openhand.khata.core.model.TransferSide
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The one-time back-fill of #113, for transfers saved before their details were kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransferBackfillTest : RepositoryTest() {
    private val backfill by lazy { TransferBackfill(lazyDb) }

    /** What the rules read from each SMS text, standing in for the parser. */
    private val readable = mutableMapOf<String, SmsTransaction>()

    private suspend fun run() = backfill.run { body, _, _ -> readable[body] }

    private suspend fun entity(id: Long) = db.transactionDao().getById(id)!!

    private suspend fun account(name: String, type: AccountType, bank: String) =
        db.accountDao().insert(AccountEntity(name = name, type = type, bank = bank, last4 = null))

    /** A transfer as saved before #113: no side, kind or pair. */
    private suspend fun oldTransfer(
        accountId: Long?,
        at: Long,
        sms: SmsTransaction?,
        source: TransactionSource = TransactionSource.SMS
    ): Long {
        sms?.let { readable[it.rawSms] = it }
        return db.transactionDao().insert(
            TransactionEntity(
                amountPaise = AMOUNT,
                direction = Direction.TRANSFER,
                timestamp = at,
                accountId = accountId,
                payeeId = null,
                categoryId = uncategorizedId(),
                note = null,
                referenceNo = null,
                source = source,
                rawSms = sms?.rawSms
            )
        )
    }

    private fun sms(direction: Direction, type: AccountType, payee: String?, body: String) =
        SmsTransaction(
            amountPaise = AMOUNT,
            direction = direction,
            timestamp = AT,
            bank = "Kotak",
            accountType = type,
            accountLast4 = null,
            payee = payee,
            referenceNo = null,
            rawSms = body
        )

    private val cred = sms(Direction.DEBIT, AccountType.BANK, "CRED", "Sent Rs 5000 to CRED")
    private val received = sms(
        Direction.CREDIT,
        AccountType.CREDIT_CARD,
        null,
        "Payment of Rs 5000 received on your HDFC Credit Card"
    )

    @Test
    fun anExistingCredTransferGetsSideOutFromItsSms() = runTest {
        val bank = account("Kotak Savings", AccountType.BANK, "Kotak")
        val id = oldTransfer(bank, AT, cred)

        run()

        assertEquals(TransferSide.OUT, entity(id).transferSide)
        assertEquals(TransferKind.CARD_PAYMENT, entity(id).transferKind)
        assertNull(entity(id).transferPairId)
    }

    @Test
    fun existingMatchingSidesGetLinked() = runTest {
        val bank = account("Kotak Savings", AccountType.BANK, "Kotak")
        val card = account("HDFC Card", AccountType.CREDIT_CARD, "HDFC")
        val out = oldTransfer(bank, AT, cred)
        val into = oldTransfer(card, AT + 20 * MINUTE, received)

        run()

        assertEquals(TransferSide.IN, entity(into).transferSide)
        assertEquals(TransferKind.CARD_PAYMENT, entity(into).transferKind)
        assertEquals(into, entity(out).transferPairId)
        assertEquals(out, entity(into).transferPairId)
    }

    @Test
    fun sidesFurtherApartThanHalfAnHourStayUnlinked() = runTest {
        val bank = account("Kotak Savings", AccountType.BANK, "Kotak")
        val card = account("HDFC Card", AccountType.CREDIT_CARD, "HDFC")
        val out = oldTransfer(bank, AT, cred)
        oldTransfer(card, AT + 2 * HOUR, received)

        run()

        assertNull(entity(out).transferPairId)
    }

    @Test
    fun aManualTransferWithNoSmsKeepsAnEmptySide() = runTest {
        val id = oldTransfer(null, AT, sms = null, source = TransactionSource.MANUAL)

        run()

        assertNull(entity(id).transferSide)
        assertEquals(TransferKind.MANUAL, entity(id).transferKind)
    }

    @Test
    fun anSmsNoRuleReadsNowKeepsAnEmptySide() = runTest {
        val id = oldTransfer(null, AT, sms = null)
        db.transactionDao().update(entity(id).copy(rawSms = "An SMS no rule reads"))

        run()

        assertNull(entity(id).transferSide)
        assertNull(entity(id).transferKind)
    }

    @Test
    fun itRunsOnlyOnce() = runTest {
        val bank = account("Kotak Savings", AccountType.BANK, "Kotak")
        run()
        val id = oldTransfer(bank, AT, cred)

        run()

        assertNull(entity(id).transferSide)
    }

    private companion object {
        const val AT = 1_790_000_000_000L
        const val AMOUNT = 500_000L
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
