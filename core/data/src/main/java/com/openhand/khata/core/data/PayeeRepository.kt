package com.openhand.khata.core.data

import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.dao.PayeeRow
import com.openhand.khata.core.database.dao.TransactionRow
import com.openhand.khata.core.model.Payee
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PayeeRepository @Inject constructor(private val db: Lazy<KhataDatabase>) {
    /** Every saved payee with its defaults and transaction count, by display name. */
    fun observePayees(): Flow<List<Payee>> =
        db.observe { it.payeeDao().observeWithDetails() }.map { rows -> rows.map { it.toModel() } }

    /**
     * The saved payee a typed name refers to (by display name, else identifier), or null. An
     * archived default category is left out, since pickers no longer offer it.
     */
    suspend fun find(name: String): Payee? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        return db.io { database ->
            val dao = database.payeeDao()
            val entity = dao.findByName(clean) ?: return@io null
            val category = entity.defaultCategoryId?.let { database.categoryDao().getById(it) }
            Payee(
                id = entity.id,
                identifier = entity.identifier,
                displayName = entity.displayName,
                defaultCategoryId = category?.takeUnless { it.archived }?.id,
                defaultTags = dao.defaultTagNames(entity.id)
            )
        }
    }

    /**
     * Changes a payee's display name, default category and default tags. The identifier stays, so
     * the payee is still recognised by it. Tags are created as needed.
     */
    suspend fun save(payee: Payee) {
        val name = payee.displayName.trim()
        require(name.isNotEmpty()) { "Payee name is empty" }
        db.io { database ->
            val dao = database.payeeDao()
            val existing = requireNotNull(dao.getById(payee.id)) { "No payee ${payee.id}" }
            val tagIds = database.tagDao().getOrCreate(payee.defaultTags)
            dao.updateWithDefaultTags(
                existing.copy(displayName = name, defaultCategoryId = payee.defaultCategoryId),
                tagIds
            )
        }
    }

    /**
     * Merges two payees: every transaction of [fromPayeeId] moves to [intoPayeeId], which keeps
     * its own name and defaults, and [fromPayeeId] is deleted.
     */
    suspend fun merge(fromPayeeId: Long, intoPayeeId: Long) {
        db.io { it.payeeDao().merge(fromPayeeId, intoPayeeId) }
    }
}

private fun PayeeRow.toModel() = Payee(
    id = id,
    identifier = identifier,
    displayName = displayName,
    defaultCategoryId = defaultCategoryId,
    defaultTags = tags?.split(TransactionRow.TAG_SEPARATOR)?.sortedBy { it.lowercase() }.orEmpty(),
    transactionCount = usage
)
