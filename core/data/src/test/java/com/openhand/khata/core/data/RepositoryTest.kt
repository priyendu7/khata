package com.openhand.khata.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openhand.khata.core.database.DefaultCategorySeeder
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.entity.TransactionEntity
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.TransactionSource
import dagger.Lazy
import org.junit.After
import org.junit.Before

/** In-memory database (plain SQLite, seeded like a real one) shared by the repository tests. */
abstract class RepositoryTest {
    protected lateinit var db: KhataDatabase
    protected val lazyDb = Lazy { db }

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room
            .inMemoryDatabaseBuilder(context, KhataDatabase::class.java)
            .addCallback(DefaultCategorySeeder.callback)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() = db.close()

    protected suspend fun uncategorizedId() = db.categoryDao().getBySeedKey("uncategorized")!!.id

    protected suspend fun addTransaction(
        accountId: Long? = null,
        tagIds: List<Long> = emptyList()
    ): Long {
        val id = db.transactionDao().insert(
            TransactionEntity(
                amountPaise = 10_000,
                direction = Direction.DEBIT,
                timestamp = 1_790_000_000_000,
                accountId = accountId,
                payeeId = null,
                categoryId = uncategorizedId(),
                note = null,
                referenceNo = null,
                source = TransactionSource.MANUAL,
                rawSms = null
            )
        )
        if (tagIds.isNotEmpty()) db.transactionDao().setTags(id, tagIds)
        return id
    }
}
