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

    /** 3 → 4: "ignore this sender" and "ignore messages like this" rules (#88). */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `ignore_rules` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `header` TEXT NOT NULL, `pattern` TEXT, " +
                    "`sample` TEXT NOT NULL, `enabled` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL)"
            )
        }
    }

    /**
     * 4 → 5: transfers (#56). Payees can be marked as the user's own account, and a reference
     * number is no longer unique, since both sides of a move between own accounts can share one.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
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

    /** 5 → 6: the month a transaction counts in, when it isn't its date's month (#93). */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `counts_at` INTEGER")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_counts_at` " +
                    "ON `transactions` (`counts_at`)"
            )
        }
    }

    /** 6 → 7: events, named date ranges that tag their transactions (#73). */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `events` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`tag_id` INTEGER NOT NULL, `start_day` INTEGER NOT NULL, " +
                    "`end_day` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_events_tag_id` ON `events` (`tag_id`)"
            )
        }
    }

    /** 7 → 8: the user's changes to built-in parser rules, switched off or edited (#111). */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `builtin_rule_overrides` (" +
                    "`rule_id` TEXT NOT NULL, `enabled` INTEGER NOT NULL DEFAULT 1, " +
                    "`edited_code` TEXT, `base_hash` TEXT, PRIMARY KEY(`rule_id`))"
            )
        }
    }

    /**
     * 8 → 9: which side of a move a transfer is, its other side, and why it's a transfer (#113).
     * Null for every existing row; a one-time back-fill fills in what it can from the SMS.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `transfer_side` TEXT")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `transfer_pair_id` INTEGER")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `transfer_kind` TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9
    )
}
