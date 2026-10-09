package com.openhand.khata.core.data

import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.hasNameOf
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class CategoryRepository @Inject constructor(private val db: Lazy<KhataDatabase>) {
    /** Every category, archived ones included, in creation order. */
    fun observeAll(): Flow<List<Category>> = db.observe {
        it.categoryDao().observeAll()
    }.map { categories -> categories.map { it.toModel() } }

    /** Categories that can be picked for a transaction (not archived). */
    fun observeActive(): Flow<List<Category>> = observeAll().map { all ->
        all.filterNot { it.archived }
    }

    /**
     * Adds a new category (id 0) or updates one. A default category may have no name (it's shown
     * in the current language); any other category needs one. A name another category is already
     * shown by is refused with [DuplicateCategoryNameException]. [defaultNames] maps each default
     * category's seed key to its name in the current language, for those without a name of their
     * own.
     */
    suspend fun save(category: Category, defaultNames: Map<String, String> = emptyMap()): Long {
        val clean = category.copy(name = category.name?.trim()?.ifEmpty { null })
        require(clean.name != null || clean.seedKey != null) { "Category name is empty" }
        require(!(clean.isUncategorized && clean.archived)) { "Uncategorized can't be archived" }
        return db.io { database ->
            val dao = database.categoryDao()
            // Only a new name is checked, so duplicates from before this check can still be edited.
            val before = dao.getById(clean.id)?.toModel()?.shownName(defaultNames)
            val renamed = !clean.shownName(defaultNames).equals(before, ignoreCase = true)
            if (renamed && dao.getAll().map { it.toModel() }.hasNameOf(clean, defaultNames)) {
                throw DuplicateCategoryNameException(clean.shownName(defaultNames).orEmpty())
            }
            if (clean.id == 0L) {
                dao.insert(clean.copy(seedKey = null).toEntity())
            } else {
                dao.update(clean.toEntity())
                clean.id
            }
        }
    }

    /**
     * Adds a new category from a picker, unless one is already called that (ignoring case): then
     * that one is returned instead, brought back if archived, so picking never makes duplicates.
     * [defaultNames] maps each default category's seed key to its name in the current language,
     * since a default category with no name of its own is shown by that name.
     */
    suspend fun addOrFind(category: Category, defaultNames: Map<String, String>): Category {
        val name = category.name?.trim().orEmpty()
        require(name.isNotEmpty()) { "Category name is empty" }
        return db.io { database ->
            val dao = database.categoryDao()
            val existing = dao.getAll().map { it.toModel() }.firstOrNull {
                (it.name ?: defaultNames[it.seedKey]).equals(name, ignoreCase = true)
            }
            when {
                existing == null -> {
                    val new = category.copy(id = 0, name = name, seedKey = null, archived = false)
                    new.copy(id = dao.insert(new.toEntity()))
                }
                existing.archived -> existing.copy(archived = false).also {
                    dao.update(it.toEntity())
                }
                else -> existing
            }
        }
    }

    /** Hides a category from pickers; transactions that use it keep it. */
    suspend fun setArchived(categoryId: Long, archived: Boolean) {
        db.io { database ->
            val dao = database.categoryDao()
            val category = dao.getById(categoryId)?.toModel() ?: return@io
            require(!(category.isUncategorized && archived)) { "Uncategorized can't be archived" }
            dao.update(category.copy(archived = archived).toEntity())
        }
    }
}

/** Another category is already called [name]. */
class DuplicateCategoryNameException(val name: String) :
    IllegalArgumentException("A category is already called $name")
