package com.openhand.khata.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.openhand.khata.core.model.IgnoreKind
import kotlinx.coroutines.flow.Flow

/**
 * "Ignore this sender" or "ignore messages like this" (PRD feature 7). [pattern] is null for a
 * sender; [sample] is the SMS the rule was made from, kept so the list can show it.
 */
@Entity(tableName = "ignore_rules")
data class IgnoreRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: IgnoreKind,
    val header: String,
    val pattern: String?,
    val sample: String,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Dao
interface IgnoreRuleDao {
    /** Newest first. */
    @Query("SELECT * FROM ignore_rules ORDER BY created_at DESC, id DESC")
    fun observeAll(): Flow<List<IgnoreRuleEntity>>

    @Query("SELECT * FROM ignore_rules ORDER BY created_at DESC, id DESC")
    suspend fun getAll(): List<IgnoreRuleEntity>

    @Query("SELECT * FROM ignore_rules WHERE enabled = 1 ORDER BY created_at DESC, id DESC")
    suspend fun enabled(): List<IgnoreRuleEntity>

    @Insert suspend fun insert(rule: IgnoreRuleEntity): Long

    @Query("UPDATE ignore_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM ignore_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
