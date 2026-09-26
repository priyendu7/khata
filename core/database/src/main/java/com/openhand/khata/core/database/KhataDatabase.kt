package com.openhand.khata.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

// Version 1 isn't final until a release opens the database; see issue #9 before changing it later.
@Database(entities = [MetadataEntity::class], version = 1, exportSchema = true)
abstract class KhataDatabase : RoomDatabase() {
    abstract fun metadataDao(): MetadataDao

    companion object {
        const val NAME = "khata.db"
    }
}
