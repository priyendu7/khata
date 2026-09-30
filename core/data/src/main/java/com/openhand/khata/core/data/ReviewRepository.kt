package com.openhand.khata.core.data

import androidx.room.withTransaction
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.dao.ReviewRow
import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.model.DefaultCategory
import com.openhand.khata.core.model.ReviewItem
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The To review inbox (PRD feature 4): transactions from payees the user hasn't named yet, which
 * SMS import saves as Uncategorized with `needs_review` set.
 */
@Singleton
class ReviewRepository @Inject constructor(private val db: Lazy<KhataDatabase>) {
    /** How many transactions are waiting, for the badges. */
    fun observeCount(): Flow<Int> = db.observe { it.reviewDao().observeCount() }

    /** Waiting transactions, newest first. */
    fun observeQueue(): Flow<List<ReviewItem>> =
        db.observe { it.reviewDao().observeQueue() }.map { rows -> rows.map { it.toModel() } }

    /**
     * Names the payee of [transactionId] and saves its default category and tags (payee memory,
     * PRD feature 3), then files every waiting transaction from that payee the same way, so they
     * all leave the inbox and later SMS from it are filled in automatically. No category means
     * Uncategorized. Returns how many transactions were filed.
     */
    suspend fun review(
        transactionId: Long,
        payeeName: String,
        categoryId: Long?,
        tags: List<String>
    ): Int = db.io { database ->
        database.withTransaction {
            val transaction = database.transactionDao().getById(transactionId)
                ?: return@withTransaction 0
            val uncategorizedId =
                database.categoryDao().getBySeedKey(DefaultCategory.UNCATEGORIZED.key)!!.id
            val category = categoryId ?: uncategorizedId
            val tagIds = database.tagDao().getOrCreate(tags)
            val payeeDao = database.payeeDao()
            val name = payeeName.trim()
            val payee = transaction.payeeId?.let { payeeDao.getById(it) }
                ?: name.ifEmpty { null }?.let { newPayee(database, it) }
            if (payee != null) {
                val defaultCategory = category.takeUnless { it == uncategorizedId }
                payeeDao.updateWithDefaultTags(
                    payee.copy(
                        displayName = name.ifEmpty { payee.displayName },
                        defaultCategoryId = defaultCategory
                    ),
                    tagIds
                )
                if (transaction.payeeId == null) {
                    database.transactionDao().update(transaction.copy(payeeId = payee.id))
                }
            }
            val others = payee?.let { database.reviewDao().pendingIds(it.id) }.orEmpty()
            val ids = (others + transactionId).distinct()
            database.reviewDao().markReviewed(ids, category)
            ids.forEach { database.transactionDao().setTags(it, tagIds) }
            ids.size
        }
    }

    /** Takes one transaction out of the inbox as it is: Uncategorized, payee still unnamed. */
    suspend fun skip(transactionId: Long) {
        db.io { it.reviewDao().skip(transactionId) }
    }

    /** A payee named in review for a transaction whose SMS gave none. */
    private suspend fun newPayee(database: KhataDatabase, name: String): PayeeEntity {
        val dao = database.payeeDao()
        return dao.findByName(name)
            ?: PayeeEntity(identifier = name, displayName = name, defaultCategoryId = null)
                .let { it.copy(id = dao.insert(it)) }
    }
}

private fun ReviewRow.toModel() = ReviewItem(
    transactionId = id,
    amountPaise = amountPaise,
    direction = direction,
    timestamp = timestamp,
    accountName = accountName,
    payeeId = payeeId,
    payeeIdentifier = payeeIdentifier,
    payeeName = payeeName,
    payeePending = payeePending,
    rawSms = rawSms
)
