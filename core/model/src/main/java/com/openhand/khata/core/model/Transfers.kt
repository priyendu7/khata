package com.openhand.khata.core.model

/**
 * One saved transfer, as the Insights Transfers card reads it (#113). [pairId] is the other side
 * of the same move when that's saved too, and [pairAccount] its account.
 */
data class TransferEntry(
    val id: Long,
    val amountPaise: Long,
    val timestamp: Long,
    val side: TransferSide?,
    val kind: TransferKind?,
    val account: Account?,
    val payeeName: String?,
    val pairId: Long? = null,
    val pairAccount: Account? = null
)

/** One end of a move: one of the user's accounts, or the payee when only one side is saved. */
data class TransferEnd(val account: Account?, val payeeName: String? = null)

/**
 * Money moved [from] one end [to] the other, counted once even with both sides saved. When it
 * isn't known which way it went ([sideKnown] false), [from] is the transaction's own account.
 * [transactionId] is the transaction tapping it opens.
 */
data class TransferMove(
    val transactionId: Long,
    val amountPaise: Long,
    val timestamp: Long,
    val from: TransferEnd,
    val to: TransferEnd,
    val sideKnown: Boolean = true
)

/** The bills paid to one credit card over a period: [count] payments adding up to [paidPaise]. */
data class CardBill(val card: Account, val paidPaise: Long, val count: Int) {
    internal fun plus(paise: Long) = copy(paidPaise = paidPaise + paise, count = count + 1)
}

/**
 * Card bill payments whose card isn't known (CRED, or a biller with no card-side SMS), grouped by
 * who was paid; a null [payeeName] is a payment with no payee.
 */
data class UnknownCardBill(val payeeName: String?, val paidPaise: Long, val count: Int) {
    internal fun plus(paise: Long) = copy(paidPaise = paidPaise + paise, count = count + 1)
}

/**
 * What moved between accounts over a period (PRD features 1 and 5). None of it is spending or
 * income: card purchases were counted when they were made, so the bill paying them isn't.
 */
data class TransferSummary(
    val totalPaise: Long,
    val cards: List<CardBill>,
    val unknownCards: List<UnknownCardBill>,
    /** Newest first. */
    val moves: List<TransferMove>
) {
    val isEmpty: Boolean get() = moves.isEmpty()

    companion object {
        val EMPTY = TransferSummary(0, emptyList(), emptyList(), emptyList())

        /** [entries] newest first, as saved; a move with both sides saved counts once. */
        fun of(entries: List<TransferEntry>): TransferSummary {
            val byId = entries.associateBy { it.id }
            val shown = entries.filter { entry ->
                val pair = entry.pairId?.let(byId::get)
                pair == null || leads(entry, pair)
            }
            val moves = shown.map(::moveOf)
            val cards = mutableMapOf<Long, CardBill>()
            val unknown = mutableMapOf<String?, UnknownCardBill>()
            shown.zip(moves).forEach { (entry, move) ->
                val card = paidCard(entry, move)
                when {
                    card != null -> cards[card.id] = cards[card.id]?.plus(move.amountPaise)
                        ?: CardBill(card, move.amountPaise, 1)
                    entry.kind == TransferKind.CARD_PAYMENT && entry.pairId == null -> {
                        val name = entry.payeeName
                        unknown[name] = unknown[name]?.plus(move.amountPaise)
                            ?: UnknownCardBill(name, move.amountPaise, 1)
                    }
                }
            }
            return TransferSummary(
                totalPaise = moves.sumOf { it.amountPaise },
                cards = cards.values.sortedByDescending { it.paidPaise },
                unknownCards = unknown.values.sortedByDescending { it.paidPaise },
                moves = moves
            )
        }

        /**
         * Whether [entry] stands for the move rather than its [pair]: the money-out side, or the
         * older one when that doesn't decide it.
         */
        private fun leads(entry: TransferEntry, pair: TransferEntry): Boolean {
            val out = entry.side == TransferSide.OUT
            val pairOut = pair.side == TransferSide.OUT
            return if (out != pairOut) out else entry.id < pair.id
        }

        private fun moveOf(entry: TransferEntry): TransferMove {
            val own = TransferEnd(entry.account)
            val other = if (entry.pairId != null) {
                TransferEnd(entry.pairAccount)
            } else {
                TransferEnd(null, entry.payeeName)
            }
            val into = entry.side == TransferSide.IN
            return TransferMove(
                transactionId = entry.id,
                amountPaise = entry.amountPaise,
                timestamp = entry.timestamp,
                from = if (into) other else own,
                to = if (into) own else other,
                sideKnown = entry.side != null
            )
        }

        /**
         * The credit card [move] paid: the card it went into, or the card the transfer is on when
         * its side couldn't be read but it's a card payment (only a card's "payment received" is).
         */
        private fun paidCard(entry: TransferEntry, move: TransferMove): Account? {
            val to = move.to.account?.takeIf { move.sideKnown }
            return when {
                to?.type == AccountType.CREDIT_CARD -> to
                !move.sideKnown &&
                    entry.kind == TransferKind.CARD_PAYMENT &&
                    entry.account?.type == AccountType.CREDIT_CARD -> entry.account
                else -> null
            }
        }
    }
}
