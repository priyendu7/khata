package com.openhand.khata.core.data

import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.database.entity.CategoryEntity
import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.model.AccountType
import com.openhand.khata.core.model.DefaultCategory
import com.openhand.khata.core.model.TransactionRecord

/**
 * Turns the names in imported rows into ids: an existing account, category or payee with that
 * name (ignoring case), or a new one. Remembers what it found, so a file with thousands of rows
 * asks the database once per name. Used inside the import's database transaction.
 */
internal class ImportResolver(
    val database: KhataDatabase,
    /** Lowercase name → seed key, for default categories shown in any language. */
    private val categoryAliases: Map<String, String>,
    private val newCategoryColors: List<Int>
) {
    private val accounts = HashMap<String, Long>()
    private val payees = HashMap<String, PayeeEntity?>()
    private var categories: MutableMap<String, Long>? = null
    private var uncategorizedId = 0L
    private var newCategories = 0

    suspend fun accountId(name: String?): Long? {
        val clean = name?.trim()?.ifEmpty { null } ?: return null
        return accounts.getOrPut(clean.lowercase()) {
            val dao = database.accountDao()
            // The CSV only has the name, so a new account starts as a bank account.
            dao.getByName(clean)?.id ?: dao.insert(
                AccountEntity(name = clean, type = AccountType.BANK, bank = null, last4 = null)
            )
        }
    }

    /** No category, or a blank one, means Uncategorized. */
    suspend fun categoryId(name: String?): Long {
        val known = categories ?: loadCategories().also { categories = it }
        val clean = name?.trim()?.ifEmpty { null } ?: return uncategorizedId
        return known.getOrPut(clean.lowercase()) {
            val color = if (newCategoryColors.isEmpty()) {
                DefaultCategory.UNCATEGORIZED.color
            } else {
                newCategoryColors[newCategories++ % newCategoryColors.size]
            }
            val icon = DefaultCategory.UNCATEGORIZED.icon
            database.categoryDao().insert(CategoryEntity(name = clean, color = color, icon = icon))
        }
    }

    /** The saved payee a row names, by identifier and then by name, or null if there's none yet. */
    suspend fun existingPayee(record: TransactionRecord): PayeeEntity? {
        val identifier = record.payee?.trim()?.ifEmpty { null }
        val name = record.payeeName?.trim()?.ifEmpty { null } ?: identifier ?: return null
        return payees.getOrPut(payeeKey(identifier, name)) {
            val dao = database.payeeDao()
            identifier?.let { dao.getByIdentifier(it) } ?: dao.findByName(name)
        }
    }

    /** The row's payee, saved as a new one when it doesn't exist yet. */
    suspend fun payeeId(record: TransactionRecord): Long? {
        val identifier = record.payee?.trim()?.ifEmpty { null }
        val name = record.payeeName?.trim()?.ifEmpty { null } ?: identifier ?: return null
        val payee = existingPayee(record) ?: PayeeEntity(
            identifier = identifier ?: name,
            displayName = name,
            defaultCategoryId = null
        ).let { it.copy(id = database.payeeDao().insert(it)) }
            .also { payees[payeeKey(identifier, name)] = it }
        return payee.id
    }

    suspend fun tagIds(names: List<String>): List<Long> = database.tagDao().getOrCreate(names)

    private fun payeeKey(identifier: String?, name: String) =
        "${identifier.orEmpty().lowercase()}\u001F${name.lowercase()}"

    /** A user's own name wins over a default category's name in another language. */
    private suspend fun loadCategories(): MutableMap<String, Long> {
        val all = database.categoryDao().getAll()
        uncategorizedId = all.first { it.seedKey == DefaultCategory.UNCATEGORIZED.key }.id
        val bySeedKey = all.filter { it.seedKey != null }.associateBy { it.seedKey }
        val known = HashMap<String, Long>()
        all.forEach { category ->
            category.name?.let { known.putIfAbsent(it.lowercase(), category.id) }
        }
        categoryAliases.forEach { (alias, seedKey) ->
            bySeedKey[seedKey]?.let { known.putIfAbsent(alias.lowercase(), it.id) }
        }
        DefaultCategory.entries.forEach { default ->
            bySeedKey[default.key]?.let { known.putIfAbsent(default.key, it.id) }
        }
        return known
    }
}
