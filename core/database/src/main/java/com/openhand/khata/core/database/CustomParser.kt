package com.openhand.khata.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A parser rule the user pasted in Settings > Parsers (PRD feature 8), kept as its rule code
 * (`khata1:…`). [ruleId] and [bank] are copies from the rule, for the list and so that pasting a
 * newer version of the same rule replaces it.
 */
@Entity(tableName = "custom_parsers", indices = [Index("rule_id", unique = true)])
data class CustomParserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "rule_id") val ruleId: String,
    val bank: String,
    val code: String,
    val enabled: Boolean = true,
    @ColumnInfo(name = "added_at") val addedAt: Long
)

@Dao
interface CustomParserDao {
    /** Newest first: the engine tries custom rules in this order. */
    @Query("SELECT * FROM custom_parsers ORDER BY added_at DESC, id DESC")
    fun observeAll(): Flow<List<CustomParserEntity>>

    @Query("SELECT code FROM custom_parsers WHERE enabled = 1 ORDER BY added_at DESC, id DESC")
    suspend fun enabledCodes(): List<String>

    @Query("SELECT id FROM custom_parsers WHERE rule_id = :ruleId")
    suspend fun idOf(ruleId: String): Long?

    @Upsert suspend fun upsert(parser: CustomParserEntity)

    /** Replaces the rule's code, keeping its place in the order and its switch. */
    @Query("UPDATE custom_parsers SET bank = :bank, code = :code WHERE rule_id = :ruleId")
    suspend fun updateCode(ruleId: String, bank: String, code: String): Int

    @Query("UPDATE custom_parsers SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM custom_parsers WHERE id = :id")
    suspend fun deleteById(id: Long)
}
