package com.openhand.khata.core.data

import androidx.room.withTransaction
import com.openhand.khata.core.database.EventEntity
import com.openhand.khata.core.database.EventRow
import com.openhand.khata.core.database.KhataDatabase
import com.openhand.khata.core.model.Event
import dagger.Lazy
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.LocalTime.MIDNIGHT
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

    /** A one-day event whose end time isn't after its start time (#141). */
    data object EndBeforeStart : SaveEventResult
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

    /** Names of the events that include [date] at [time], by name. */
    suspend fun namesAt(date: LocalDate, time: LocalTime): List<String> =
        db.io { it.eventDao().namesAt(date.toEpochDay(), time.minuteOfDay()) }

    /**
     * Adds (id 0) or changes an event and moves its tag: off transactions in the old dates and
     * times, onto ones in the new. A tag with [name] is reused if there is one. A tag left unused
     * by a change of name is deleted.
     */
    suspend fun save(event: Event, zone: ZoneId): SaveEventResult {
        val name = event.name.trim()
        return when {
            name.isEmpty() -> SaveEventResult.Blank
            !event.timesInOrder -> SaveEventResult.EndBeforeStart
            else -> db.io { database ->
                database.withTransaction { write(database, event.copy(name = name), zone) }
            }
        }
    }

    private suspend fun write(
        database: KhataDatabase,
        event: Event,
        zone: ZoneId
    ): SaveEventResult {
        val dao = database.eventDao()
        val tags = database.tagDao()
        val old = if (event.id == 0L) null else dao.getById(event.id)
        val tagId = tags.getOrCreate(listOf(event.name)).single()
        val owner = dao.getByTag(tagId)
        if (owner != null && owner.id != old?.id) return SaveEventResult.NameTaken
        // The same tag with different capitals: the event's name is the new spelling.
        if (tags.getById(tagId)?.name != event.name) tags.rename(tagId, event.name)
        val entity = event.toEntity(id = old?.id ?: 0, tagId = tagId)
        val id = if (old == null) {
            dao.insert(entity)
        } else {
            val (from, until) = old.span(zone)
            dao.untagRange(old.tagId, from, until)
            dao.update(entity)
            old.id
        }
        val (from, until) = entity.span(zone)
        dao.tagRange(tagId, from, until)
        if (old != null && old.tagId != tagId && !dao.tagInUse(old.tagId)) {
            tags.deleteById(old.tagId)
        }
        return SaveEventResult.Saved(id)
    }

    /** Deletes the event; transactions it tagged keep the tag, so the trip's history stays. */
    suspend fun delete(id: Long) {
        db.io { it.eventDao().deleteById(id) }
    }

    /** Dates in order; times only when both are set. */
    private fun Event.toEntity(id: Long, tagId: Long) = EventEntity(
        id = id,
        tagId = tagId,
        startDay = minOf(start, end).toEpochDay(),
        endDay = maxOf(start, end).toEpochDay(),
        startMinute = startTime?.takeIf { hasTimes }?.minuteOfDay(),
        endMinute = endTime?.takeIf { hasTimes }?.minuteOfDay()
    )

    private fun EventRow.toModel() = Event(
        id,
        name,
        LocalDate.ofEpochDay(startDay),
        LocalDate.ofEpochDay(endDay),
        startMinute?.let(::timeOf),
        endMinute?.let(::timeOf)
    )

    internal companion object {
        private const val MINUTES_PER_HOUR = 60

        /** The local day and minute of the day of [timestamp], as events store them. */
        fun dayAndMinuteOf(timestamp: Long, zone: ZoneId): Pair<Long, Int> {
            val at = Instant.ofEpochMilli(timestamp).atZone(zone)
            return at.toLocalDate().toEpochDay() to at.toLocalTime().minuteOfDay()
        }

        /**
         * The timestamps the event tags, as [from, until): from its first day's start time (or
         * midnight) to just after its last day's end minute (or the next midnight), in [zone].
         */
        fun EventEntity.span(zone: ZoneId): Pair<Long, Long> {
            val first = LocalDate.ofEpochDay(
                startDay
            ).atTime(startMinute?.let(::timeOf) ?: MIDNIGHT)
            val last =
                endMinute?.let { LocalDate.ofEpochDay(endDay).atTime(timeOf(it)).plusMinutes(1) }
                    ?: LocalDate.ofEpochDay(endDay + 1).atTime(MIDNIGHT)
            return first.millis(zone) to last.millis(zone)
        }

        private fun LocalDateTime.millis(zone: ZoneId) = atZone(zone).toInstant().toEpochMilli()

        fun LocalTime.minuteOfDay() = hour * MINUTES_PER_HOUR + minute

        fun timeOf(minute: Int): LocalTime =
            LocalTime.of(minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR)
    }
}
