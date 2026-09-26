package com.openhand.khata.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
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
import kotlinx.coroutines.flow.Flow

// Basic CRUD for the first schema. Feature issues add the queries their screens need.

@Dao
interface AccountDao {
    @Insert suspend fun insert(account: AccountEntity): Long

    @Update suspend fun update(account: AccountEntity)

    @Delete suspend fun delete(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAll(): Flow<List<AccountEntity>>
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
}

@Dao
interface PayeeDao {
    @Insert suspend fun insert(payee: PayeeEntity): Long

    @Update suspend fun update(payee: PayeeEntity)

    @Delete suspend fun delete(payee: PayeeEntity)

    @Query("SELECT * FROM payees WHERE identifier = :identifier")
    suspend fun getByIdentifier(identifier: String): PayeeEntity?

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
}
