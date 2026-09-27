package com.openhand.khata.core.data

import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.dao.TransactionRow
import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.database.entity.TagEntity
import com.openhand.khata.core.database.entity.TransactionEntity
import com.openhand.khata.core.model.DefaultCategory
import com.openhand.khata.core.model.Transaction
import com.openhand.khata.core.model.TransactionFilter
import com.openhand.khata.core.model.TransactionListItem
import com.openhand.khata.core.model.TransactionSource
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TransactionRepository @Inject constructor(private val db: Lazy<KhataDatabase>) {
    /** The transactions matching [filter], newest first, updated whenever the data changes. */
    fun observe(filter: TransactionFilter = TransactionFilter()): Flow<List<TransactionListItem>> {
        val query = filter.query.trim().ifEmpty { null }?.let(::escapeLike)
        return db.observe {
            it.transactionDao().observeList(
                query = query,
                categoryId = filter.categoryId,
                tagId = filter.tagId,
                accountId = filter.accountId,
                from = filter.from,
                until = filter.until
            )
        }.map { rows -> rows.map { it.toModel() } }
    }

    /** The transaction with [id] as the edit screen shows it, or null if it's gone. */
    suspend fun get(id: Long): Transaction? = db.io { database ->
        val dao = database.transactionDao()
        val entity = dao.getById(id) ?: return@io null
        Transaction(
            id = entity.id,
            amountPaise = entity.amountPaise,
            direction = entity.direction,
            timestamp = entity.timestamp,
            accountId = entity.accountId,
            payeeName = entity.payeeId?.let { database.payeeDao().getById(it)?.displayName },
            categoryId = entity.categoryId,
            tags = dao.tagNames(id),
            note = entity.note
        )
    }

    /**
     * Adds a new transaction (id 0) or updates one, and returns its id. The payee is matched by
     * name or saved as a new one, tags are created as needed, and no category means Uncategorized.
     * Fields the screen doesn't show (source, reference number, SMS text) are kept on update.
     */
    suspend fun save(transaction: Transaction): Long {
        require(transaction.amountPaise > 0) { "Amount must be more than zero" }
        val payeeName = transaction.payeeName?.trim()?.ifEmpty { null }
        val note = transaction.note?.trim()?.ifEmpty { null }
        val tagNames = transaction.tags.map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        return db.io { database ->
            val payeeDao = database.payeeDao()
            val payeeId = payeeName?.let { name ->
                payeeDao.findByName(name)?.id
                    ?: payeeDao.insert(
                        PayeeEntity(identifier = name, displayName = name, defaultCategoryId = null)
                    )
            }
            val tagDao = database.tagDao()
            val tagIds = tagNames.map { name ->
                tagDao.getByName(name)?.id ?: tagDao.insert(TagEntity(name = name))
            }
            val categoryId = transaction.categoryId
                ?: database.categoryDao().getBySeedKey(DefaultCategory.UNCATEGORIZED.key)!!.id
            val dao = database.transactionDao()
            val existing = if (transaction.id == 0L) null else dao.getById(transaction.id)
            val entity = TransactionEntity(
                id = existing?.id ?: 0,
                amountPaise = transaction.amountPaise,
                direction = transaction.direction,
                timestamp = transaction.timestamp,
                accountId = transaction.accountId,
                payeeId = payeeId,
                categoryId = categoryId,
                note = note,
                referenceNo = existing?.referenceNo,
                source = existing?.source ?: TransactionSource.MANUAL,
                rawSms = existing?.rawSms,
                // Saving from the edit screen means the user has looked at it.
                needsReview = false
            )
            dao.saveWithTags(entity, tagIds)
        }
    }

    suspend fun delete(id: Long) {
        db.io { it.transactionDao().deleteById(id) }
    }
}

private fun TransactionRow.toModel() = TransactionListItem(
    id = id,
    amountPaise = amountPaise,
    direction = direction,
    timestamp = timestamp,
    payeeName = payeeName,
    note = note,
    accountName = accountName,
    category = category.toModel(),
    tags = tags?.split(TransactionRow.TAG_SEPARATOR)?.sortedBy { it.lowercase() }.orEmpty()
)
