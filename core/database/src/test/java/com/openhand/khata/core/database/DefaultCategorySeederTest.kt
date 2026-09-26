package com.openhand.khata.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openhand.khata.core.model.DefaultCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultCategorySeederTest {
    private lateinit var db: KhataDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, KhataDatabase::class.java)
                .addCallback(DefaultCategorySeeder.callback)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun newDatabaseHasEveryDefaultCategory() = runTest {
        val categories = db.categoryDao().observeActive().first()

        assertEquals(DefaultCategory.entries.map { it.key }, categories.map { it.seedKey })
        categories.forEach { category ->
            val default = DefaultCategory.fromKey(category.seedKey)!!
            // No stored name: the UI shows the name in the current language.
            assertNull(category.name)
            assertEquals(default.color, category.color)
            assertEquals(default.icon, category.icon)
        }
    }

    @Test
    fun seedingAgainNeverDuplicates() = runTest {
        DefaultCategorySeeder.seed(db.openHelper.writableDatabase)
        DefaultCategorySeeder.seed(db.openHelper.writableDatabase)

        assertEquals(DefaultCategory.entries.size, db.categoryDao().observeActive().first().size)
    }

    @Test
    fun renamingKeepsTheSeedKey() = runTest {
        val dao = db.categoryDao()
        val food = dao.getBySeedKey("food")!!

        dao.update(food.copy(name = "Eating out"))
        DefaultCategorySeeder.seed(db.openHelper.writableDatabase)

        val renamed = dao.getById(food.id)!!
        assertEquals("Eating out", renamed.name)
        assertEquals("food", renamed.seedKey)
        assertEquals(DefaultCategory.entries.size, dao.observeActive().first().size)
    }
}
