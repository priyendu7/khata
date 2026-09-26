package com.openhand.khata.core.data

import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.database.entity.TagEntity
import com.openhand.khata.core.model.Tag
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface RenameResult {
    data object Renamed : RenameResult

    data object Blank : RenameResult

    /** Another tag already has this name (ignoring case); the UI can offer to merge into it. */
    data class NameTaken(val existing: Tag) : RenameResult
}

@Singleton
class TagRepository @Inject constructor(private val db: Lazy<KhataDatabase>) {
    /** Every tag with how many transactions use it, by name. */
    fun observeTags(): Flow<List<Tag>> =
        db.observe { it.tagDao().observeWithUsage() }.map { tags -> tags.map { it.toModel() } }

    /** Existing tags starting with [query], most used first: suggestions while typing. */
    suspend fun suggestions(query: String, limit: Int = SUGGESTIONS): List<Tag> {
        val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return db.io { database -> database.tagDao().search(escaped, limit).map { it.toModel() } }
    }

    /**
     * Ids for [names], creating tags that don't exist yet. Names are trimmed, blanks dropped, and
     * names that differ only in case count as the same tag.
     */
    suspend fun getOrCreate(names: Collection<String>): List<Long> {
        val clean = names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        return db.io { database ->
            val dao = database.tagDao()
            clean.map { name -> dao.getByName(name)?.id ?: dao.insert(TagEntity(name = name)) }
        }
    }

    suspend fun rename(tagId: Long, newName: String): RenameResult {
        val name = newName.trim()
        if (name.isEmpty()) return RenameResult.Blank
        return db.io { database ->
            val dao = database.tagDao()
            val existing = dao.getByName(name)
            if (existing != null && existing.id != tagId) {
                RenameResult.NameTaken(Tag(existing.id, existing.name))
            } else {
                dao.rename(tagId, name)
                RenameResult.Renamed
            }
        }
    }

    /** Moves every use of [fromTagId] (transactions and payee defaults) to [intoTagId], then deletes it. */
    suspend fun merge(fromTagId: Long, intoTagId: Long) {
        db.io { it.tagDao().merge(fromTagId, intoTagId) }
    }

    /** Deletes a tag; transactions keep everything else. */
    suspend fun delete(tagId: Long) {
        db.io { it.tagDao().deleteById(tagId) }
    }

    private companion object {
        const val SUGGESTIONS = 8
    }
}
