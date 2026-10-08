package com.openhand.khata.core.data

import androidx.room.withTransaction
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.MetadataEntity
import com.openhand.khata.core.database.entity.TransactionEntity
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.SmsTransaction
import com.openhand.khata.core.model.TransactionSource
import com.openhand.khata.core.model.TransferKind
import com.openhand.khata.core.model.TransferSide
import dagger.Lazy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Fills in which side of a move each transfer is, why it's a transfer and its other side (#113)
 * for transfers saved before those were kept. Each SMS is read again with the current rules for
 * its side, and the sides are paired by [SmsImporter]'s rules: the same reference number, or else
 * the same amount within 30 minutes. A transfer it can't work out keeps an empty side and shows
 * as a move on its own. Runs once: after that, [run] does nothing.
 */
@Singleton
class TransferBackfill @Inject constructor(private val db: Lazy<KhataDatabase>) {
    /**
     * [read] reads a saved SMS again: its text, its account's bank and its time → what it says,
     * or null when no rule reads it.
     */
    suspend fun run(read: suspend (rawSms: String, bank: String?, at: Long) -> SmsTransaction?) {
        db.io { database ->
            if (database.metadataDao().get(DONE_KEY) != null) return@io
            // Ones saved since the update already have their details.
            val old = database.transferDao().getTransfers().filter {
                it.transferSide == null && it.transferKind == null && it.transferPairId == null
            }
            val filled = old.map { it.withDetails(database, read) }
            database.withTransaction {
                val dao = database.transferDao()
                filled.forEach { dao.setTransferDetails(it.id, it.transferSide, it.transferKind) }
                pairs(dao.getTransfers()).forEach { (a, b) -> dao.linkTransfers(a, b) }
                database.metadataDao().put(MetadataEntity(DONE_KEY, DONE))
            }
        }
    }

    private suspend fun TransactionEntity.withDetails(
        database: KhataDatabase,
        read: suspend (String, String?, Long) -> SmsTransaction?
    ): TransactionEntity {
        val sms = rawSms ?: return copy(
            transferKind = TransferKind.MANUAL.takeIf { source == TransactionSource.MANUAL }
        )
        val account = accountId?.let { database.accountDao().getById(it) }
        return read(sms, account?.bank, timestamp)?.let { withDetails(database, it) } ?: this
    }

    /** The side and kind [parsed], this transfer's SMS read again, shows. */
    private suspend fun TransactionEntity.withDetails(
        database: KhataDatabase,
        parsed: SmsTransaction
    ): TransactionEntity {
        val ownAccount = payeeId?.let { database.payeeDao().getById(it) }?.ownAccount == true
        return copy(
            transferSide = when (parsed.direction) {
                Direction.DEBIT -> TransferSide.OUT
                Direction.CREDIT, Direction.REFUND -> TransferSide.IN
                Direction.TRANSFER -> null
            },
            transferKind = when {
                CardBillPayment.matches(parsed) -> TransferKind.CARD_PAYMENT
                ownAccount -> TransferKind.OWN_ACCOUNT
                // Neither: it became a transfer when its other side was saved.
                else -> TransferKind.OTHER_SIDE
            }
        )
    }

    /**
     * Money-out and money-in sides to link, oldest first: the same reference number on another
     * account, or else the nearest one with the same amount within [PAIR_WINDOW].
     */
    private fun pairs(transfers: List<TransactionEntity>): List<Pair<Long, Long>> {
        val open = transfers.filter { it.transferPairId == null && it.accountId != null }
        val ins = open.filter { it.transferSide == TransferSide.IN }.toMutableList()
        return open.filter { it.transferSide == TransferSide.OUT }.mapNotNull { out ->
            val others = ins.filter { it.accountId != out.accountId }
            val match = others.firstOrNull {
                out.referenceNo != null && it.referenceNo == out.referenceNo
            } ?: others
                .filter {
                    it.amountPaise == out.amountPaise &&
                        abs(it.timestamp - out.timestamp) <= PAIR_WINDOW
                }
                .minByOrNull { abs(it.timestamp - out.timestamp) }
            match?.let {
                ins.remove(it)
                out.id to it.id
            }
        }
    }

    private companion object {
        const val DONE_KEY = "transfer_backfill_done"
        const val DONE = "1"
        val PAIR_WINDOW = TimeUnit.MINUTES.toMillis(30)
    }
}
