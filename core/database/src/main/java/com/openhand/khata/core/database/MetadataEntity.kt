package com.openhand.khata.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Small key-value table for app bookkeeping (for example, when the last CSV export happened).
 */
@Entity(tableName = "app_metadata")
data class MetadataEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface MetadataDao {
    @Query("SELECT value FROM app_metadata WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entry: MetadataEntity)
}
