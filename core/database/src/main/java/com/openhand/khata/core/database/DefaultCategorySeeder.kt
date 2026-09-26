package com.openhand.khata.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.openhand.khata.core.model.DefaultCategory

/**
 * Adds the default categories when the database file is first created. Each row is keyed by its
 * unique `seed_key` and inserted with INSERT OR IGNORE, so running it again never duplicates.
 */
object DefaultCategorySeeder {
    val callback: RoomDatabase.Callback =
        object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) = seed(db)
        }

    fun seed(db: SupportSQLiteDatabase) {
        DefaultCategory.entries.forEach { category ->
            db.execSQL(
                "INSERT OR IGNORE INTO categories (name, seed_key, color, icon, archived) VALUES (NULL, ?, ?, ?, 0)",
                arrayOf<Any>(category.key, category.color, category.icon)
            )
        }
    }
}
