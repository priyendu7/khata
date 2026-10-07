package com.openhand.khata.core.data

import androidx.room.withTransaction
import com.openhand.khata.core.database.EventEntity
import com.openhand.khata.core.database.EventRow
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.Event
import dagger.Lazy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface SaveEventResult {
    data class Saved(val id: Long) : SaveEventResult

    data object Blank : SaveEventResult

    /** Another event already has this name (ignoring case). */
    data object NameTaken : SaveEventResult
}

/**
 * Events (#73): named date ranges whose transactions all get the event's name as a tag. Saving one
 * tags what's already in its dates; SMS import and the editor tag what comes later.
 */
@Singleton
class EventRepository @Inject constructor(private val db: Lazy<KhataDatabase>) {
    /** Newest first. */
    fun observeEvents(): Flow<List<Event>> =
        db.observe { it.eventDao().observeAll() }.map { rows -> rows.map { it.toModel() } }

    /** Names of the events that include [date], by name. */
    suspend fun namesOn(date: LocalDate): List<String> =
        db.io { it.eventDao().namesOn(date.toEpochDay()) }

    /**
     * Adds (id 0) or changes an event and moves its tag: off transactions in the old dates, onto
     * ones in the new dates. A tag with [name] is reused if there is one. A tag left unused by a
     * change of name is deleted.
     */
    suspend fun save(event: Event, zone: ZoneId): SaveEventResult {
        val name = event.name.trim()
        if (name.isEmpty()) return SaveEventResult.Blank
        val start = minOf(event.start, event.end)
        val end = maxOf(event.start, event.end)
        return db.io { database ->
            database.withTransaction {
                val dao = database.eventDao()
                val tags = database.tagDao()
                val old = if (event.id == 0L) null else dao.getById(event.id)
                val tagId = tags.getOrCreate(listOf(name)).single()
                val owner = dao.getByTag(tagId)
                if (owner != null && owner.id != old?.id) {
                    return@withTransaction SaveEventResult.NameTaken
                }
                // The same tag with different capitals: the event's name is the new spelling.
                if (tags.getById(tagId)?.name != name) tags.rename(tagId, name)
                val entity = EventEntity(
                    id = old?.id ?: 0,
                    tagId = tagId,
                    startDay = start.toEpochDay(),
                    endDay = end.toEpochDay()
                )
                val id = if (old == null) {
                    dao.insert(entity)
                } else {
                    val oldUntil = (old.endDay + 1).millis(zone)
                    dao.untagRange(old.tagId, old.startDay.millis(zone), oldUntil)
                    dao.update(entity)
                    old.id
                }
                dao.tagRange(tagId, start.millis(zone), end.plusDays(1).millis(zone))
                if (old != null && old.tagId != tagId && !dao.tagInUse(old.tagId)) {
                    tags.deleteById(old.tagId)
                }
                SaveEventResult.Saved(id)
            }
        }
    }

    /** Deletes the event; transactions it tagged keep the tag, so the trip's history stays. */
    suspend fun delete(id: Long) {
        db.io { it.eventDao().deleteById(id) }
    }

    private fun EventRow.toModel() =
        Event(id, name, LocalDate.ofEpochDay(startDay), LocalDate.ofEpochDay(endDay))

    /** Local midnight at the start of this day. */
    private fun LocalDate.millis(zone: ZoneId) = atStartOfDay(zone).toInstant().toEpochMilli()

    private fun Long.millis(zone: ZoneId) = LocalDate.ofEpochDay(this).millis(zone)

    internal companion object {
        /** The local day of [timestamp] as an epoch day, as events store them. */
        fun dayOf(timestamp: Long, zone: ZoneId): Long =
            Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().toEpochDay()
    }
}
