package com.openhand.khata.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The user's change to a built-in parser rule (Settings > Parsers, #111). Built-in rules ship
 * inside the app, so they can't change on the phone; this is applied on top. [editedCode] is the
 * edited rule's code, null when not edited. [baseHash] is the hash of the built-in rule when it
 * was edited, so an app update that changed the rule since can be noticed.
 */
@Entity(tableName = "builtin_rule_overrides")
data class BuiltInRuleOverrideEntity(
    @PrimaryKey @ColumnInfo(name = "rule_id") val ruleId: String,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    @ColumnInfo(name = "edited_code") val editedCode: String? = null,
    @ColumnInfo(name = "base_hash") val baseHash: String? = null
)

@Dao
interface BuiltInRuleOverrideDao {
    @Query("SELECT * FROM builtin_rule_overrides ORDER BY rule_id")
    fun observeAll(): Flow<List<BuiltInRuleOverrideEntity>>

    @Query("SELECT * FROM builtin_rule_overrides ORDER BY rule_id")
    suspend fun getAll(): List<BuiltInRuleOverrideEntity>

    @Query("SELECT * FROM builtin_rule_overrides WHERE rule_id = :ruleId")
    suspend fun get(ruleId: String): BuiltInRuleOverrideEntity?

    @Upsert suspend fun upsert(override: BuiltInRuleOverrideEntity)

    @Query("DELETE FROM builtin_rule_overrides WHERE rule_id = :ruleId")
    suspend fun delete(ruleId: String)
}
