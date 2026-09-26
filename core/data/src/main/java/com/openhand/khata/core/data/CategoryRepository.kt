package com.openhand.khata.core.data

import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.Category
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
     * in the current language); any other category needs one.
     */
    suspend fun save(category: Category): Long {
        val clean = category.copy(name = category.name?.trim()?.ifEmpty { null })
        require(clean.name != null || clean.seedKey != null) { "Category name is empty" }
        require(!(clean.isUncategorized && clean.archived)) { "Uncategorized can't be archived" }
        return db.io { database ->
            val dao = database.categoryDao()
            if (clean.id == 0L) {
                dao.insert(clean.copy(seedKey = null).toEntity())
            } else {
                dao.update(clean.toEntity())
                clean.id
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
