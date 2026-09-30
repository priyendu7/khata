package com.openhand.khata.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.database.entity.CategoryEntity
import com.openhand.khata.core.database.entity.PayeeDefaultTagEntity
import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.database.entity.TagEntity
import com.openhand.khata.core.database.entity.TransactionEntity
import com.openhand.khata.core.database.entity.TransactionTagEntity
import com.openhand.khata.core.model.Direction
import kotlinx.coroutines.flow.Flow

// Basic CRUD for the first schema. Feature issues add the queries their screens need.

/** A tag and how many transactions use it. */
data class TagUsage(val id: Long, val name: String, val usage: Int)

/** One row of the transactions list, joined with its payee, account, category and tags. */
data class TransactionRow(
    val id: Long,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val direction: Direction,
    val timestamp: Long,
    val note: String?,
    @ColumnInfo(name = "payee_name") val payeeName: String?,
    @ColumnInfo(name = "account_name") val accountName: String?,
    @Embedded(prefix = "category_") val category: CategoryEntity,
    /** Tag names joined with [TAG_SEPARATOR], or null when there are none. */
    val tags: String?
) {
    companion object {
        /** ASCII unit separator: can't be typed into a tag name, unlike a comma. */
        const val TAG_SEPARATOR = '\u001F'
    }
}

/**
 * Spending and income over a period. Spent is expenses minus refunds; transfers count as neither
 * (the same rules as `Totals.of` in :core:model).
 */
data class TotalsRow(
    @ColumnInfo(name = "spent_paise") val spentPaise: Long,
    @ColumnInfo(name = "income_paise") val incomePaise: Long
)

/** A category and its spending (expenses minus refunds) over a period. */
data class CategorySpendRow(
    @Embedded(prefix = "category_") val category: CategoryEntity,
    @ColumnInfo(name = "spent_paise") val spentPaise: Long
)

/** An expense, refund or income, with just what the charts need. */
data class AmountRow(
    val timestamp: Long,
    val direction: Direction,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long
)

/** One transaction with everything a CSV row needs, oldest first (`docs/csv-format.md`). */
data class ExportRow(
    val timestamp: Long,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val direction: Direction,
    @ColumnInfo(name = "account_name") val accountName: String?,
    @ColumnInfo(name = "payee_identifier") val payeeIdentifier: String?,
    @ColumnInfo(name = "payee_name") val payeeName: String?,
    @Embedded(prefix = "category_") val category: CategoryEntity,
    /** Tag names joined with [TransactionRow.TAG_SEPARATOR], or null when there are none. */
    val tags: String?,
    val note: String?,
    @ColumnInfo(name = "reference_no") val referenceNo: String?
)

/**
 * What import compares a new row with to spot a duplicate: the reference number, or else the
 * date, direction, amount and [party] (the payee's name, or the note when there's no payee).
 */
data class DuplicateKeyRow(
    val timestamp: Long,
    val direction: Direction,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "reference_no") val referenceNo: String?,
    val party: String?
)

/** A payee with its default tag names and how many transactions it has. */
data class PayeeRow(
    val id: Long,
    val identifier: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "default_category_id") val defaultCategoryId: Long?,
    /** Default tag names joined with [TransactionRow.TAG_SEPARATOR], or null when there are none. */
    val tags: String?,
    val usage: Int
)

@Dao
interface AccountDao {
    @Insert suspend fun insert(account: AccountEntity): Long

    @Update suspend fun update(account: AccountEntity)

    @Delete suspend fun delete(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE name = :name COLLATE NOCASE ORDER BY id LIMIT 1")
    suspend fun getByName(name: String): AccountEntity?

    /** Accounts with these last 4 digits (or with none, for null), oldest first. For SMS import. */
    @Query("SELECT * FROM accounts WHERE last4 IS :last4 ORDER BY id")
    suspend fun getByLast4(last4: String?): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE account_id = :accountId")
    suspend fun transactionCount(accountId: Long): Int

    @Query("UPDATE transactions SET account_id = :to WHERE account_id = :from")
    suspend fun moveTransactions(from: Long, to: Long?)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Moves the account's transactions to [moveTo] (null = no account), then deletes it. */
    @Transaction
    suspend fun deleteMovingTransactions(id: Long, moveTo: Long?) {
        moveTransactions(id, moveTo)
        deleteById(id)
    }
}

@Dao
interface CategoryDao {
    @Insert suspend fun insert(category: CategoryEntity): Long

    @Update suspend fun update(category: CategoryEntity)

    @Delete suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE seed_key = :seedKey")
    suspend fun getBySeedKey(seedKey: String): CategoryEntity?

    /** In creation order; the UI sorts by the displayed (possibly translated) name. */
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY id")
    fun observeActive(): Flow<List<CategoryEntity>>

    /** Every category, archived ones included, in creation order. */
    @Query("SELECT * FROM categories ORDER BY id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun getAll(): List<CategoryEntity>
}

@Dao
interface TagDao {
    /** @return the new id, or -1 if a tag with that name (ignoring case) already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Update suspend fun update(tag: TagEntity)

    @Delete suspend fun delete(tag: TagEntity)

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getByName(name: String): TagEntity?

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Long): TagEntity?

    @Query(
        "SELECT t.id AS id, t.name AS name, COUNT(tt.transaction_id) AS usage FROM tags t " +
            "LEFT JOIN transaction_tags tt ON tt.tag_id = t.id " +
            "GROUP BY t.id ORDER BY t.name COLLATE NOCASE"
    )
    fun observeWithUsage(): Flow<List<TagUsage>>

    /** Tags whose name starts with [prefix] (ignoring case), most used first. */
    @Query(
        "SELECT t.id AS id, t.name AS name, COUNT(tt.transaction_id) AS usage FROM tags t " +
            "LEFT JOIN transaction_tags tt ON tt.tag_id = t.id " +
            "WHERE t.name LIKE :prefix || '%' ESCAPE '\\' " +
            "GROUP BY t.id ORDER BY usage DESC, t.name COLLATE NOCASE LIMIT :limit"
    )
    suspend fun search(prefix: String, limit: Int): List<TagUsage>

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "INSERT OR IGNORE INTO transaction_tags (transaction_id, tag_id) " +
            "SELECT transaction_id, :into FROM transaction_tags WHERE tag_id = :from"
    )
    suspend fun copyTransactionLinks(from: Long, into: Long)

    @Query(
        "INSERT OR IGNORE INTO payee_default_tags (payee_id, tag_id) " +
            "SELECT payee_id, :into FROM payee_default_tags WHERE tag_id = :from"
    )
    suspend fun copyPayeeDefaultLinks(from: Long, into: Long)

    /** Moves every use of [from] to [into] and deletes [from]; its old links go with it (cascade). */
    @Transaction
    suspend fun merge(from: Long, into: Long) {
        if (from == into) return
        copyTransactionLinks(from, into)
        copyPayeeDefaultLinks(from, into)
        deleteById(from)
    }
}

@Dao
interface PayeeDao {
    @Insert suspend fun insert(payee: PayeeEntity): Long

    @Update suspend fun update(payee: PayeeEntity)

    @Delete suspend fun delete(payee: PayeeEntity)

    @Query("SELECT * FROM payees WHERE identifier = :identifier")
    suspend fun getByIdentifier(identifier: String): PayeeEntity?

    @Query("SELECT * FROM payees WHERE id = :id")
    suspend fun getById(id: Long): PayeeEntity?

    /**
     * The payee a typed name refers to, ignoring case: one with that display name first, else one
     * whose identifier (UPI ID, merchant) is exactly that text.
     */
    @Query(
        "SELECT * FROM payees WHERE display_name = :name COLLATE NOCASE " +
            "OR identifier = :name COLLATE NOCASE " +
            "ORDER BY (display_name = :name COLLATE NOCASE) DESC, id LIMIT 1"
    )
    suspend fun findByName(name: String): PayeeEntity?

    @Query("SELECT tag_id FROM payee_default_tags WHERE payee_id = :payeeId")
    suspend fun defaultTagIds(payeeId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addDefaultTags(links: List<PayeeDefaultTagEntity>)

    @Query("DELETE FROM payee_default_tags WHERE payee_id = :payeeId")
    suspend fun clearDefaultTags(payeeId: Long)

    @Transaction
    suspend fun setDefaultTags(payeeId: Long, tagIds: Collection<Long>) {
        clearDefaultTags(payeeId)
        addDefaultTags(tagIds.map { PayeeDefaultTagEntity(payeeId, it) })
    }

    @Query(
        "SELECT g.name FROM payee_default_tags pt JOIN tags g ON g.id = pt.tag_id " +
            "WHERE pt.payee_id = :payeeId ORDER BY g.name COLLATE NOCASE"
    )
    suspend fun defaultTagNames(payeeId: Long): List<String>

    @Query(
        "SELECT p.id, p.identifier, p.display_name, p.default_category_id, " +
            "(SELECT GROUP_CONCAT(g.name, char(31)) FROM payee_default_tags pt " +
            "JOIN tags g ON g.id = pt.tag_id WHERE pt.payee_id = p.id) AS tags, " +
            "(SELECT COUNT(*) FROM transactions t WHERE t.payee_id = p.id) AS usage " +
            "FROM payees p ORDER BY p.display_name COLLATE NOCASE, p.id"
    )
    fun observeWithDetails(): Flow<List<PayeeRow>>

    /** Updates the name and defaults together, so a half-saved edit can't happen. */
    @Transaction
    suspend fun updateWithDefaultTags(payee: PayeeEntity, tagIds: Collection<Long>) {
        update(payee)
        setDefaultTags(payee.id, tagIds)
    }

    @Query("UPDATE transactions SET payee_id = :into WHERE payee_id = :from")
    suspend fun moveTransactions(from: Long, into: Long)

    @Query("DELETE FROM payees WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Moves [from]'s transactions to [into] and deletes [from]; [into] keeps its own defaults. */
    @Transaction
    suspend fun merge(from: Long, into: Long) {
        if (from == into) return
        moveTransactions(from, into)
        deleteById(from)
    }
}

@Dao
interface TransactionDao {
    /** @throws android.database.sqlite.SQLiteConstraintException if [TransactionEntity.referenceNo] is already used. */
    @Insert suspend fun insert(transaction: TransactionEntity): Long

    @Update suspend fun update(transaction: TransactionEntity)

    @Delete suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE reference_no = :referenceNo")
    suspend fun getByReferenceNo(referenceNo: String): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT tag_id FROM transaction_tags WHERE transaction_id = :transactionId")
    suspend fun tagIds(transactionId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTags(links: List<TransactionTagEntity>)

    @Query("DELETE FROM transaction_tags WHERE transaction_id = :transactionId")
    suspend fun clearTags(transactionId: Long)

    @Transaction
    suspend fun setTags(transactionId: Long, tagIds: Collection<Long>) {
        clearTags(transactionId)
        addTags(tagIds.map { TransactionTagEntity(transactionId, it) })
    }

    @Query(
        "SELECT g.name FROM transaction_tags tt JOIN tags g ON g.id = tt.tag_id " +
            "WHERE tt.transaction_id = :transactionId ORDER BY g.name COLLATE NOCASE"
    )
    suspend fun tagNames(transactionId: Long): List<String>

    /** Adds (id 0) or updates [transaction] and sets its tags, all or nothing. Returns its id. */
    @Transaction
    suspend fun saveWithTags(transaction: TransactionEntity, tagIds: Collection<Long>): Long {
        val id = if (transaction.id == 0L) {
            insert(transaction)
        } else {
            update(transaction)
            transaction.id
        }
        setTags(id, tagIds)
        return id
    }

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT EXISTS (SELECT 1 FROM transactions)")
    fun observeAny(): Flow<Boolean>

    /** Totals for timestamps in [from, until). Transfers are left out; refunds reduce spending. */
    @Query(
        "SELECT COALESCE(SUM(CASE direction WHEN 'debit' THEN amount_paise " +
            "WHEN 'refund' THEN -amount_paise ELSE 0 END), 0) AS spent_paise, " +
            "COALESCE(SUM(CASE direction WHEN 'credit' THEN amount_paise ELSE 0 END), 0) " +
            "AS income_paise " +
            "FROM transactions WHERE timestamp >= :from AND timestamp < :until"
    )
    fun observeTotals(from: Long, until: Long): Flow<TotalsRow>

    /**
     * The category with the most spending (expenses minus refunds) for timestamps in
     * [from, until), or null when nothing was spent. Ties go to the older category.
     */
    @Query(
        "SELECT c.id AS category_id, c.name AS category_name, " +
            "c.seed_key AS category_seed_key, c.color AS category_color, " +
            "c.icon AS category_icon, c.archived AS category_archived, " +
            "SUM(CASE t.direction WHEN 'debit' THEN t.amount_paise ELSE -t.amount_paise END) " +
            "AS spent_paise " +
            "FROM transactions t JOIN categories c ON c.id = t.category_id " +
            "WHERE t.direction IN ('debit', 'refund') " +
            "AND t.timestamp >= :from AND t.timestamp < :until " +
            "GROUP BY c.id HAVING spent_paise > 0 ORDER BY spent_paise DESC, c.id LIMIT 1"
    )
    fun observeTopCategory(from: Long, until: Long): Flow<CategorySpendRow?>

    /**
     * Every category's spending (expenses minus refunds) for timestamps in [from, until), biggest
     * first, with the same rules as [observeTopCategory]. A category with more refunds than
     * spending comes last with a negative total; categories that net to zero are left out.
     */
    @Query(
        "SELECT c.id AS category_id, c.name AS category_name, " +
            "c.seed_key AS category_seed_key, c.color AS category_color, " +
            "c.icon AS category_icon, c.archived AS category_archived, " +
            "SUM(CASE t.direction WHEN 'debit' THEN t.amount_paise ELSE -t.amount_paise END) " +
            "AS spent_paise " +
            "FROM transactions t JOIN categories c ON c.id = t.category_id " +
            "WHERE t.direction IN ('debit', 'refund') " +
            "AND t.timestamp >= :from AND t.timestamp < :until " +
            "GROUP BY c.id HAVING spent_paise != 0 ORDER BY spent_paise DESC, c.id"
    )
    fun observeCategorySpending(from: Long, until: Long): Flow<List<CategorySpendRow>>

    /**
     * Every expense, refund and income for timestamps in [from, until), for charts that group by
     * local day or month in Kotlin (SQLite only knows UTC days). Transfers never count.
     */
    @Query(
        "SELECT timestamp, direction, amount_paise, category_id FROM transactions " +
            "WHERE direction != 'transfer' AND timestamp >= :from AND timestamp < :until"
    )
    fun observeAmounts(from: Long, until: Long): Flow<List<AmountRow>>

    /**
     * The transactions list, newest first. Each null argument means "any"; the rest combine.
     * [query] matches the payee name or note and must have `%`, `_` and `\` escaped with `\`.
     */
    @Query(
        "SELECT t.id, t.amount_paise, t.direction, t.timestamp, t.note, " +
            "p.display_name AS payee_name, a.name AS account_name, " +
            "c.id AS category_id, c.name AS category_name, c.seed_key AS category_seed_key, " +
            "c.color AS category_color, c.icon AS category_icon, " +
            "c.archived AS category_archived, " +
            "(SELECT GROUP_CONCAT(g.name, char(31)) FROM transaction_tags tt " +
            "JOIN tags g ON g.id = tt.tag_id WHERE tt.transaction_id = t.id) AS tags " +
            "FROM transactions t " +
            "JOIN categories c ON c.id = t.category_id " +
            "LEFT JOIN payees p ON p.id = t.payee_id " +
            "LEFT JOIN accounts a ON a.id = t.account_id " +
            "WHERE (:categoryId IS NULL OR t.category_id = :categoryId) " +
            "AND (:accountId IS NULL OR t.account_id = :accountId) " +
            "AND (:tagId IS NULL OR EXISTS (SELECT 1 FROM transaction_tags x " +
            "WHERE x.transaction_id = t.id AND x.tag_id = :tagId)) " +
            "AND (:from IS NULL OR t.timestamp >= :from) " +
            "AND (:until IS NULL OR t.timestamp < :until) " +
            "AND (:query IS NULL OR p.display_name LIKE '%' || :query || '%' ESCAPE '\\' " +
            "OR t.note LIKE '%' || :query || '%' ESCAPE '\\') " +
            "ORDER BY t.timestamp DESC, t.id DESC"
    )
    // Room binds query arguments only from parameters, so each filter needs its own.
    @Suppress("LongParameterList")
    fun observeList(
        query: String?,
        categoryId: Long?,
        tagId: Long?,
        accountId: Long?,
        from: Long?,
        until: Long?
    ): Flow<List<TransactionRow>>
}

/** The queries CSV export and import need (`docs/csv-format.md`). */
@Dao
interface BackupDao {
    /** Transactions in [from, until) for CSV export, oldest first; null bounds mean no limit. */
    @Query(
        "SELECT t.timestamp, t.amount_paise, t.direction, a.name AS account_name, " +
            "p.identifier AS payee_identifier, p.display_name AS payee_name, " +
            "c.id AS category_id, c.name AS category_name, c.seed_key AS category_seed_key, " +
            "c.color AS category_color, c.icon AS category_icon, " +
            "c.archived AS category_archived, " +
            "(SELECT GROUP_CONCAT(g.name, char(31)) FROM transaction_tags tt " +
            "JOIN tags g ON g.id = tt.tag_id WHERE tt.transaction_id = t.id) AS tags, " +
            "t.note, t.reference_no " +
            "FROM transactions t " +
            "JOIN categories c ON c.id = t.category_id " +
            "LEFT JOIN payees p ON p.id = t.payee_id " +
            "LEFT JOIN accounts a ON a.id = t.account_id " +
            "WHERE (:from IS NULL OR t.timestamp >= :from) " +
            "AND (:until IS NULL OR t.timestamp < :until) " +
            "ORDER BY t.timestamp, t.id"
    )
    suspend fun exportRows(from: Long?, until: Long?): List<ExportRow>

    /** Every transaction's duplicate-check fields, for CSV import. */
    @Query(
        "SELECT t.timestamp, t.direction, t.amount_paise, t.reference_no, " +
            "COALESCE(p.display_name, t.note) AS party " +
            "FROM transactions t LEFT JOIN payees p ON p.id = t.payee_id"
    )
    suspend fun duplicateKeys(): List<DuplicateKeyRow>
}

/** The duplicate checks SMS import runs before saving (sms/ingest via SmsImporter). */
@Dao
interface SmsImportDao {
    /** A transaction saved from exactly this SMS text within [from, until]. */
    @Query(
        "SELECT id FROM transactions WHERE raw_sms = :rawSms " +
            "AND timestamp BETWEEN :from AND :until LIMIT 1"
    )
    suspend fun findSameSms(rawSms: String, from: Long, until: Long): Long?

    /**
     * The transaction nearest [at] within [from, until] with this amount and direction, on this
     * account or on none (manual and CSV entries often have no account), whose reference number
     * doesn't contradict [referenceNo].
     */
    @Query(
        "SELECT id FROM transactions WHERE amount_paise = :amountPaise " +
            "AND direction = :direction " +
            "AND (account_id IS :accountId OR account_id IS NULL) " +
            "AND timestamp BETWEEN :from AND :until " +
            "AND (reference_no IS NULL OR :referenceNo IS NULL) " +
            "ORDER BY ABS(timestamp - :at), id LIMIT 1"
    )
    // Room binds query arguments only from parameters, so each one needs its own.
    @Suppress("LongParameterList")
    suspend fun findNear(
        amountPaise: Long,
        direction: Direction,
        accountId: Long?,
        referenceNo: String?,
        at: Long,
        from: Long,
        until: Long
    ): Long?
}

/** A waiting transaction with what its review card shows, joined in. */
data class ReviewRow(
    val id: Long,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val direction: Direction,
    val timestamp: Long,
    @ColumnInfo(name = "raw_sms") val rawSms: String?,
    @ColumnInfo(name = "account_name") val accountName: String?,
    @ColumnInfo(name = "payee_id") val payeeId: Long?,
    @ColumnInfo(name = "payee_identifier") val payeeIdentifier: String?,
    @ColumnInfo(name = "payee_name") val payeeName: String?,
    @ColumnInfo(name = "payee_pending") val payeePending: Int
)

/** The To review inbox (PRD feature 4): transactions with `needs_review` set. */
@Dao
interface ReviewDao {
    @Query("SELECT COUNT(*) FROM transactions WHERE needs_review = 1")
    fun observeCount(): Flow<Int>

    /** Waiting transactions, newest first. */
    @Query(
        "SELECT t.id, t.amount_paise, t.direction, t.timestamp, t.raw_sms, " +
            "a.name AS account_name, p.id AS payee_id, p.identifier AS payee_identifier, " +
            "p.display_name AS payee_name, " +
            "(SELECT COUNT(*) FROM transactions o WHERE o.needs_review = 1 " +
            "AND o.payee_id = t.payee_id) AS payee_pending " +
            "FROM transactions t " +
            "LEFT JOIN accounts a ON a.id = t.account_id " +
            "LEFT JOIN payees p ON p.id = t.payee_id " +
            "WHERE t.needs_review = 1 ORDER BY t.timestamp DESC, t.id DESC"
    )
    fun observeQueue(): Flow<List<ReviewRow>>

    @Query("SELECT id FROM transactions WHERE needs_review = 1 AND payee_id = :payeeId")
    suspend fun pendingIds(payeeId: Long): List<Long>

    /** Files [ids] under [categoryId] and takes them out of the inbox. */
    @Query("UPDATE transactions SET category_id = :categoryId, needs_review = 0 WHERE id IN (:ids)")
    suspend fun markReviewed(ids: List<Long>, categoryId: Long)

    /** Takes [id] out of the inbox as it is (Uncategorized, for an unknown payee). */
    @Query("UPDATE transactions SET needs_review = 0 WHERE id = :id")
    suspend fun skip(id: Long)
}
