package com.openhand.khata.core.database

import androidx.room.migration.Migration

/**
 * Every schema migration, in order. The app and the migration tests both use this list, so a
 * migration can't be registered in one and forgotten in the other.
 */
object KhataMigrations {
    val ALL: Array<Migration> = emptyArray()
}
