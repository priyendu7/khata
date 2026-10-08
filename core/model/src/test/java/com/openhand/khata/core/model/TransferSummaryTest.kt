package com.openhand.khata.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferSummaryTest {
    private val savings = Account(1, "Kotak Savings", AccountType.BANK)
    private val hdfcCard = Account(2, "HDFC Card", AccountType.CREDIT_CARD, last4 = "5678")
    private val axisCard = Account(3, "Axis Card", AccountType.CREDIT_CARD)
    private val salary = Account(4, "HDFC Salary", AccountType.BANK)

    private fun entry(
        id: Long,
        side: TransferSide?,
        account: Account?,
        amount: Long = 1_840_000,
        kind: TransferKind? = TransferKind.CARD_PAYMENT,
        payee: String? = null,
        pair: Pair<Long, Account>? = null
    ) = TransferEntry(
        id = id,
        amountPaise = amount,
        timestamp = 1_790_000_000_000L - id,
        side = side,
        kind = kind,
        account = account,
        payeeName = payee,
        pairId = pair?.first,
        pairAccount = pair?.second
    )

    @Test
    fun aPairedCardPaymentCountsOnceUnderItsCard() {
        val summary = TransferSummary.of(
            listOf(
                entry(2, TransferSide.IN, hdfcCard, pair = 1L to savings),
                entry(1, TransferSide.OUT, savings, payee = "CRED", pair = 2L to hdfcCard)
            )
        )

        assertEquals(1_840_000L, summary.totalPaise)
        assertEquals(listOf(CardBill(hdfcCard, 1_840_000, 1)), summary.cards)
        assertTrue(summary.unknownCards.isEmpty())
        val move = summary.moves.single()
        // The money-out side stands for the move.
        assertEquals(1L, move.transactionId)
        assertEquals(TransferEnd(savings), move.from)
        assertEquals(TransferEnd(hdfcCard), move.to)
    }

    @Test
    fun aCardsPaymentReceivedOnItsOwnCountsUnderTheCard() {
        val summary = TransferSummary.of(
            listOf(entry(5, TransferSide.IN, axisCard, amount = 900_000, payee = "Payment"))
        )

        assertEquals(listOf(CardBill(axisCard, 900_000, 1)), summary.cards)
        assertEquals(TransferEnd(null, "Payment"), summary.moves.single().from)
    }

    @Test
    fun unpairedCredPaymentsGoUnderCardNotKnownByPayee() {
        val summary = TransferSummary.of(
            listOf(
                entry(3, TransferSide.OUT, savings, amount = 100_000, payee = "CRED"),
                entry(2, TransferSide.OUT, savings, amount = 200_000, payee = "CRED"),
                entry(1, TransferSide.OUT, savings, amount = 50_000, payee = "BillDesk")
            )
        )

        assertTrue(summary.cards.isEmpty())
        assertEquals(
            listOf(UnknownCardBill("CRED", 300_000, 2), UnknownCardBill("BillDesk", 50_000, 1)),
            summary.unknownCards
        )
        assertEquals(TransferEnd(null, "CRED"), summary.moves.first().to)
    }

    @Test
    fun aMoveBetweenBankAccountsIsNoCardBill() {
        val summary = TransferSummary.of(
            listOf(
                entry(
                    2,
                    TransferSide.IN,
                    salary,
                    kind = TransferKind.OTHER_SIDE,
                    pair = 1L to savings
                ),
                entry(
                    1,
                    TransferSide.OUT,
                    savings,
                    kind = TransferKind.OTHER_SIDE,
                    pair = 2L to salary
                )
            )
        )

        assertEquals(1_840_000L, summary.totalPaise)
        assertTrue(summary.cards.isEmpty())
        assertTrue(summary.unknownCards.isEmpty())
    }

    @Test
    fun aPairWhoseOtherSideCountsInAnotherPeriodStillShowsBothEnds() {
        val summary = TransferSummary.of(
            listOf(entry(2, TransferSide.IN, hdfcCard, pair = 1L to savings))
        )

        val move = summary.moves.single()
        assertEquals(2L, move.transactionId)
        assertEquals(TransferEnd(savings), move.from)
        assertEquals(listOf(CardBill(hdfcCard, 1_840_000, 1)), summary.cards)
    }

    @Test
    fun aTransferWithAnUnknownSideIsAMoveOnItsOwn() {
        val summary = TransferSummary.of(
            listOf(entry(1, null, savings, kind = TransferKind.MANUAL, payee = "Mum"))
        )

        val move = summary.moves.single()
        assertFalse(move.sideKnown)
        assertEquals(TransferEnd(savings), move.from)
        assertEquals(TransferEnd(null, "Mum"), move.to)
        assertTrue(summary.cards.isEmpty())
    }

    @Test
    fun nothingMovedIsEmpty() {
        assertTrue(TransferSummary.of(emptyList()).isEmpty)
        assertEquals(TransferSummary.EMPTY, TransferSummary.of(emptyList()))
    }
}
