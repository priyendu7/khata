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
    fun aNameAnotherCategoryHasIsRefused() = runTest {
        val hindi = mapOf("food" to "भोजन", "travel" to "यात्रा")
        val fuel = categories.save(Category(name = "Fuel", color = 0, icon = "fuel"))
        val all = categories.observeAll().first()
        val travel = all.first { it.seedKey == "travel" }
        val taken = listOf(
            // Another case and extra spaces.
            Category(name = "  fUEL ", color = 0, icon = "x"),
            // A default category's name in the current language.
            Category(name = "भोजन", color = 0, icon = "x"),
            // Renaming one to another's name.
            travel.copy(name = "Fuel")
        )

        taken.forEach { category ->
            assertThrows(DuplicateCategoryNameException::class.java) {
                kotlinx.coroutines.runBlocking { categories.save(category, hindi) }
            }
        }
        assertEquals(all, categories.observeAll().first())
        // Its own name, recoloured, is fine; so is a default name in another language.
        categories.save(Category(id = fuel, name = "FUEL", color = 1, icon = "fuel"), hindi)
        categories.save(travel.copy(name = "Food"), hindi)
        assertEquals("FUEL", categories.observeAll().first().first { it.id == fuel }.name)
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

    @Test
    fun addingANameThatExistsPicksTheExistingCategory() = runTest {
        val hindi = mapOf("food" to "भोजन", "groceries" to "किराना")
        val fuel = categories.addOrFind(
            Category(name = " Fuel ", color = 0, icon = "fuel"),
            hindi
        )
        val countAfterFuel = categories.observeAll().first().size

        // Any case, and a default category by its name in the current language.
        val upper = categories.addOrFind(Category(name = "FUEL", color = 1, icon = "x"), hindi)
        assertEquals(fuel.id, upper.id)
        val food = categories.addOrFind(Category(name = "भोजन", color = 1, icon = "x"), hindi)
        assertEquals("food", food.seedKey)
        assertEquals(countAfterFuel, categories.observeAll().first().size)

        // An archived match comes back rather than being duplicated.
        categories.setArchived(fuel.id, true)
        val again = categories.addOrFind(Category(name = "fuel", color = 1, icon = "x"), hindi)
        assertEquals(fuel.id, again.id)
        assertFalse(categories.observeAll().first().first { it.id == fuel.id }.archived)
        assertEquals(countAfterFuel, categories.observeAll().first().size)
    }
}
