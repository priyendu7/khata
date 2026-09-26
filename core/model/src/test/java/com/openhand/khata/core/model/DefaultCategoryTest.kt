package com.openhand.khata.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultCategoryTest {
    @Test
    fun keysAreStable() {
        // These are stored in users' databases. Changing one needs a migration.
        assertEquals(
            listOf(
                "food",
                "groceries",
                "travel",
                "rent",
                "work",
                "bills_utilities",
                "shopping",
                "health",
                "entertainment",
                "uncategorized"
            ),
            DefaultCategory.entries.map { it.key }
        )
    }

    @Test
    fun keysLookUpTheirCategory() {
        DefaultCategory.entries.forEach { assertEquals(it, DefaultCategory.fromKey(it.key)) }
        assertNull(DefaultCategory.fromKey("renamed-by-user"))
        assertNull(DefaultCategory.fromKey(null))
    }

    @Test
    fun coloursAreOpaqueAndDistinct() {
        val colours = DefaultCategory.entries.map { it.color }
        colours.forEach { assertEquals(0xFF, it ushr 24) }
        assertEquals(colours.size, colours.toSet().size)
    }
}
