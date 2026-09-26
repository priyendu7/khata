package com.openhand.khata.core.database

import android.content.Context
import com.openhand.khata.core.database.dao.AccountDao
import com.openhand.khata.core.database.dao.CategoryDao
import com.openhand.khata.core.database.dao.PayeeDao
import com.openhand.khata.core.database.dao.TagDao
import com.openhand.khata.core.database.dao.TransactionDao
import com.openhand.khata.core.security.DatabaseKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The database is opened the first time something injects it (not at app start), and that first
 * injection does disk and Keystore work, so inject it off the main thread (e.g. in a repository).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideOpenedDatabase(@ApplicationContext context: Context): OpenedDatabase =
        KhataDatabaseFactory.open(context, DatabaseKeyManager.create(context))

    @Provides
    fun provideDatabase(opened: OpenedDatabase): KhataDatabase = opened.database

    @Provides
    fun provideMetadataDao(database: KhataDatabase): MetadataDao = database.metadataDao()

    @Provides
    fun provideAccountDao(database: KhataDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideCategoryDao(database: KhataDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTagDao(database: KhataDatabase): TagDao = database.tagDao()

    @Provides
    fun providePayeeDao(database: KhataDatabase): PayeeDao = database.payeeDao()

    @Provides
    fun provideTransactionDao(database: KhataDatabase): TransactionDao = database.transactionDao()
}
