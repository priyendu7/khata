package com.openhand.khata.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.openhand.khata.core.database.dao.AccountDao
import com.openhand.khata.core.database.dao.BackupDao
import com.openhand.khata.core.database.dao.CategoryDao
import com.openhand.khata.core.database.dao.PayeeDao
import com.openhand.khata.core.database.dao.SmsImportDao
import com.openhand.khata.core.database.dao.TagDao
import com.openhand.khata.core.database.dao.TransactionDao
import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.database.entity.CategoryEntity
import com.openhand.khata.core.database.entity.EnumConverters
import com.openhand.khata.core.database.entity.PayeeDefaultTagEntity
import com.openhand.khata.core.database.entity.PayeeEntity
import com.openhand.khata.core.database.entity.TagEntity
import com.openhand.khata.core.database.entity.TransactionEntity
import com.openhand.khata.core.database.entity.TransactionTagEntity

/**
 * The first schema (PRD data model). Every change from now on bumps [version], adds a migration to
 * [KhataMigrations.ALL], commits the exported schema in `core/database/schemas/`, and adds a
 * migration test.
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        PayeeEntity::class,
        PayeeDefaultTagEntity::class,
        TransactionEntity::class,
        TransactionTagEntity::class,
        MetadataEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(EnumConverters::class)
abstract class KhataDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun categoryDao(): CategoryDao

    abstract fun tagDao(): TagDao

    abstract fun payeeDao(): PayeeDao

    abstract fun transactionDao(): TransactionDao

    abstract fun metadataDao(): MetadataDao

    abstract fun backupDao(): BackupDao

    abstract fun smsImportDao(): SmsImportDao

    companion object {
        const val NAME = "khata.db"
    }
}
