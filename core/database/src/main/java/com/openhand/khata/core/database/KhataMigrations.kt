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

    /**
     * 2 → 3: transfers (#56). Payees can be marked as the user's own account, and a reference
     * number is no longer unique, since both sides of a move between own accounts can share one.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `payees` ADD COLUMN `own_account` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL("DROP INDEX IF EXISTS `index_transactions_reference_no`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_reference_no` " +
                    "ON `transactions` (`reference_no`)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
