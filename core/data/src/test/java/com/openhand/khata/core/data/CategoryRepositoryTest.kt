package com.openhand.khata.core.data

import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.DefaultCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryRepositoryTest : RepositoryTest() {
    private val categories by lazy { CategoryRepository(lazyDb) }

    @Test
    fun addsRenamesRecoloursAndResetsCategories() = runTest {
        val id = categories.save(
            Category(name = " Fuel ", color = 0xFF1565C0.toInt(), icon = "fuel")
        )
        val food = categories.observeAll().first().first { it.seedKey == "food" }

        categories.save(food.copy(name = "Eating out", color = 0xFF000000.toInt(), icon = "pizza"))
        val renamed = categories.observeAll().first().first { it.id == food.id }
        assertEquals("Eating out", renamed.name)
        assertEquals("food", renamed.seedKey)

        // Clearing the name of a default category goes back to its translated default name.
        categories.save(renamed.copy(name = "  "))
        assertNull(categories.observeAll().first().first { it.id == food.id }.name)

        assertEquals("Fuel", categories.observeAll().first().first { it.id == id }.name)
    }

    @Test
    fun newCategoriesNeedANameAndNeverGetASeedKey() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                categories.save(Category(name = " ", color = 0, icon = "x"))
            }
        }
        val id = categories.save(
            Category(name = "Pets", seedKey = "food", color = 0, icon = "pets")
        )
        assertNull(categories.observeAll().first().first { it.id == id }.seedKey)
    }

    @Test
    fun archivedCategoriesLeaveThePickersButStay() = runTest {
        val travel = categories.observeAll().first().first { it.seedKey == "travel" }

        categories.setArchived(travel.id, true)

        assertFalse(categories.observeActive().first().any { it.id == travel.id })
        assertTrue(categories.observeAll().first().first { it.id == travel.id }.archived)

        categories.setArchived(travel.id, false)
        assertTrue(categories.observeActive().first().any { it.id == travel.id })
    }

    @Test
    fun uncategorizedCantBeArchived() = runTest {
        val id = uncategorizedId()
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { categories.setArchived(id, true) }
        }
        val uncategorized = categories.observeAll().first().first { it.id == id }
        assertTrue(uncategorized.isUncategorized)
        assertEquals(DefaultCategory.entries.size, categories.observeActive().first().size)
    }
}
