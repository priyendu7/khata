package com.openhand.khata.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * An SMS that got past the filters but no parser rule could read (PRD feature 7). It waits in
 * the review inbox with its raw text until the user adds it by hand or dismisses it; dismissed
 * ones are deleted. Only SMS from business senders are ever stored here, never from people.
 */
@Entity(tableName = "unparsed_sms", indices = [Index("received_at")])
data class UnparsedSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    @ColumnInfo(name = "received_at") val receivedAt: Long
)

@Dao
interface UnparsedSmsDao {
    @Insert suspend fun insert(sms: UnparsedSmsEntity): Long

    /** The same SMS text already stored within [from, until] (the receiver and the import). */
    @Query(
        "SELECT id FROM unparsed_sms WHERE body = :body " +
            "AND received_at BETWEEN :from AND :until LIMIT 1"
    )
    suspend fun findSame(body: String, from: Long, until: Long): Long?

    @Query("SELECT * FROM unparsed_sms ORDER BY received_at DESC, id DESC")
    fun observeAll(): Flow<List<UnparsedSmsEntity>>

    @Query("SELECT * FROM unparsed_sms ORDER BY received_at ASC, id ASC")
    suspend fun getAll(): List<UnparsedSmsEntity>

    @Query("SELECT * FROM unparsed_sms WHERE id = :id")
    suspend fun getById(id: Long): UnparsedSmsEntity?

    @Query("SELECT COUNT(*) FROM unparsed_sms")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM unparsed_sms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM unparsed_sms WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
