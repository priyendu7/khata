package com.openhand.khata.core.data

import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.PaymentStatus
import com.openhand.khata.core.model.SmsStatement
import com.openhand.khata.core.model.SmsTransaction
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransferSide
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Card bill payments (#166): what counts, and linking, recording and fixing them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardPaymentLinkTest : RepositoryTest() {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val transactions by lazy { TransactionRepository(lazyDb) }
    private val cards by lazy { CardRepository(lazyDb, transactions) }
    private val importer by lazy { SmsImporter(lazyDb) }
    private val today = LocalDate.parse("2026-10-15")

    private fun at(date: String) =
        LocalDate.parse(date).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()

    /** ICICI ··1234's statement of 9 Oct, ₹35,299.72 due 23 Oct, dated by its SMS. */
    private suspend fun statement(): Long {
        val sms = SmsStatement(
            bank = "ICICI",
            cardLast4 = "1234",
            totalPaise = TOTAL,
            minDuePaise = 3_450_00,
            dueDate = LocalDate.parse("2026-10-23"),
            statementDate = LocalDate.parse("2026-10-09"),
            rawSms = "ICICI Bank Credit Card XX1234 statement",
            dateFromSms = true
        )
        return (cards.save(sms) as StatementSaveResult.Saved).cardId
    }

    /** A CRED payment from a bank account, which no card SMS confirms: "card not known". */
    private suspend fun credPayment(amount: Long = TOTAL, date: String = "2026-10-10"): Long {
        val saved = importer.import(
            SmsTransaction(
                amountPaise = amount,
                direction = Direction.DEBIT,
                timestamp = at(date),
                bank = "HDFC",
                accountType = AccountType.BANK,
                accountLast4 = "9999",
                payee = "cred.club@axisb",
                referenceNo = null,
                rawSms = "Rs.$amount debited from A/c XX9999 to cred.club@axisb on $date"
            )
        )
        return (saved as SmsImportResult.Saved).transactionId
    }

    /** ₹163 into Axis ··5678 on 11 Oct, [hour] hours after noon so none is a duplicate. */
    private suspend fun cardCredit(body: String, hour: Int = 0) = importer.import(
        SmsTransaction(
            amountPaise = 163_00,
            direction = Direction.CREDIT,
            timestamp = at("2026-10-11") + hour * HOUR,
            bank = "Axis",
            accountType = AccountType.CREDIT_CARD,
            accountLast4 = "5678",
            payee = null,
            referenceNo = null,
            rawSms = body
        )
    ) as SmsImportResult.Saved

    private suspend fun latest(card: Long) = cards.observeCard(card, today, zone).first()!!.latest!!

    private suspend fun transfers() =
        transactions.observeTransfers(at("2026-10-01"), at("2026-11-01")).first()

    @Test
    fun linkingACardNotKnownPaymentMarksTheStatementPaidAndUnlinkingUndoesIt() = runTest {
        val card = statement()
        // A second card, so the bank side alone isn't taken as this card's.
        db.accountDao().insert(
            AccountEntity(
                name = "Axis 5678",
                type = AccountType.CREDIT_CARD,
                bank = "Axis",
                last4 = "5678"
            )
        )
        val cred = credPayment()
        assertEquals(PaymentStatus.NOT_PAID, latest(card).status)
        assertEquals(listOf(cred), cards.observeUnlinkedPayments().first().map { it.transactionId })
        assertEquals(TOTAL, transfers().unknownCards.single().paidPaise)

        cards.linkPayment(cred, card)

        val paid = latest(card)
        assertEquals(PaymentStatus.PAID_IN_FULL, paid.status)
        assertTrue(paid.payments.single().linked)
        assertTrue(cards.observeUnlinkedPayments().first().isEmpty())
        assertEquals(TOTAL, transfers().cards.single().paidPaise)
        assertTrue(transfers().unknownCards.isEmpty())

        cards.unlinkPayment(paid.payments.single().transactionId)

        assertEquals(PaymentStatus.NOT_PAID, latest(card).status)
        assertEquals(listOf(cred), cards.observeUnlinkedPayments().first().map { it.transactionId })
    }

    @Test
    fun recordingANewPaymentMarksTheStatementPaid() = runTest {
        val card = statement()
        transactions.save(
            Transaction(
                amountPaise = TOTAL,
                direction = Direction.TRANSFER,
                timestamp = at("2026-10-12"),
                accountId = card,
                transferSide = TransferSide.IN
            )
        )

        val paid = latest(card)
        assertEquals(PaymentStatus.PAID_IN_FULL, paid.status)
        // Made by hand, not linked: unlinking leaves it alone.
        cards.unlinkPayment(paid.payments.single().transactionId)
        assertEquals(PaymentStatus.PAID_IN_FULL, latest(card).status)
    }

    @Test
    fun insightsAndTheCardPageAgree() = runTest {
        val card = statement()
        // A card's "payment received", and one whose side couldn't be read.
        cardCredit("Payment of Rs 163 has been received on your Axis Credit Card XX5678")
        val icici = importer.import(
            SmsTransaction(
                amountPaise = 20_000_00,
                direction = Direction.CREDIT,
                timestamp = at("2026-10-12"),
                bank = "ICICI",
                accountType = AccountType.CREDIT_CARD,
                accountLast4 = "1234",
                payee = null,
                referenceNo = null,
                rawSms = "Payment of Rs 20000 received towards your ICICI Credit Card XX1234"
            )
        ) as SmsImportResult.Saved
        transactions.save(transactions.get(icici.transactionId)!!.copy(transferSide = null))

        val bill = transfers().cards.single { it.card.id == card }
        assertEquals(latest(card).paidPaise, bill.paidPaise)
        assertEquals(
            latest(card).payments.map { it.transactionId },
            transfers().paymentsTo(card).map { it.transactionId }
        )
    }

    @Test
    fun cardCreditsArePaymentsOnlyWhenTheSmsSaysSo() = runTest {
        val payment = cardCredit("Payment of Rs 163 received on your Axis Credit Card XX5678")
        val cashback = cardCredit("Cashback of Rs 163 credited to your Axis Bank card XX5678", 1)
        val refund = cardCredit("Refund of Rs 163 from Flipkart credited to card XX5678", 2)
        val reversal = cardCredit("Reversal of Rs 163 on your Axis card XX5678 processed", 3)
        val other = cardCredit("Rs 163 credited to your Axis Bank card XX5678 by Flipkart", 4)

        suspend fun direction(saved: SmsImportResult.Saved) =
            db.transactionDao().getById(saved.transactionId)!!.direction

        assertEquals(Direction.TRANSFER, direction(payment))
        listOf(cashback, refund, reversal).forEach { assertEquals(Direction.REFUND, direction(it)) }
        assertEquals(Direction.CREDIT, direction(other))
        assertTrue(other.needsReview)
        assertEquals(1, transfers().cards.single().count)
    }

    @Test
    fun aPaymentChangedToARefundLeavesCardBillsPaid() = runTest {
        val card = statement()
        val received = importer.import(
            SmsTransaction(
                amountPaise = TOTAL,
                direction = Direction.CREDIT,
                timestamp = at("2026-10-12"),
                bank = "ICICI",
                accountType = AccountType.CREDIT_CARD,
                accountLast4 = "1234",
                payee = null,
                referenceNo = null,
                rawSms = "Payment of Rs 35299.72 received on your ICICI Credit Card XX1234"
            )
        ) as SmsImportResult.Saved
        assertEquals(PaymentStatus.PAID_IN_FULL, latest(card).status)

        val saved = transactions.get(received.transactionId)!!
        transactions.save(saved.copy(direction = Direction.REFUND, transferSide = null))

        assertEquals(PaymentStatus.NOT_PAID, latest(card).status)
        assertTrue(transfers().cards.isEmpty())
    }

    private companion object {
        const val TOTAL = 35_299_72L
        const val HOUR = 3_600_000L
    }
}
