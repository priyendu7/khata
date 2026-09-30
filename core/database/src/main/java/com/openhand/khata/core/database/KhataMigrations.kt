package com.openhand.khata.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema migration, in order. The app and the migration tests both use this list, so a
 * migration can't be registered in one and forgotten in the other.
 */
object KhataMigrations {
    /** 1 → 2: bank SMS that no parser rule could read, for the review inbox (#57). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `unparsed_sms` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sender` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                    "`received_at` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_unparsed_sms_received_at` " +
                    "ON `unparsed_sms` (`received_at`)"
            )
        }
    }

    /** 2 → 3: parser rules the user pasted in Settings > Parsers (#59). */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `custom_parsers` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`rule_id` TEXT NOT NULL, `bank` TEXT NOT NULL, `code` TEXT NOT NULL, " +
                    "`enabled` INTEGER NOT NULL, `added_at` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_parsers_rule_id` " +
                    "ON `custom_parsers` (`rule_id`)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
