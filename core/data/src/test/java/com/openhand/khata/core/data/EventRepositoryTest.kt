package com.openhand.khata.core.data

import com.openhand.khata.core.database.entity.TransactionEntity
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Event
import com.openhand.khata.core.model.TransactionSource
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventRepositoryTest : RepositoryTest() {
    private val events by lazy { EventRepository(lazyDb) }
    private val tags by lazy { TagRepository(lazyDb) }
    private val zone = ZoneId.of("Asia/Kolkata")

    private val oct = { day: Int -> LocalDate.of(2026, 10, day) }

    /** A transaction at [time] on [date] in [zone]. */
    private suspend fun addOn(date: LocalDate, time: LocalTime = LocalTime.NOON): Long =
        db.transactionDao().insert(
            TransactionEntity(
                amountPaise = 10_000,
                direction = Direction.DEBIT,
                timestamp = date.atTime(time).atZone(zone).toInstant().toEpochMilli(),
                accountId = null,
                payeeId = null,
                categoryId = uncategorizedId(),
                note = null,
                referenceNo = null,
                source = TransactionSource.MANUAL,
                rawSms = null
            )
        )

    private suspend fun tagsOf(id: Long) = db.transactionDao().tagNames(id)

    private suspend fun save(event: Event) = (events.save(event, zone) as SaveEventResult.Saved).id

    @Test
    fun addingAnEventTagsItsFirstAndLastDayButNotTheDaysAround() = runTest {
        val before = addOn(oct(1), LocalTime.of(23, 59))
        val first = addOn(oct(2), LocalTime.MIDNIGHT)
        val middle = addOn(oct(3))
        val last = addOn(oct(4), LocalTime.of(23, 59))
        val after = addOn(oct(5), LocalTime.MIDNIGHT)

        save(Event(name = " Kalu waterfall trip ", start = oct(2), end = oct(4)))

        listOf(first, middle, last).forEach {
            assertEquals(listOf("Kalu waterfall trip"), tagsOf(it))
        }
        listOf(before, after).forEach { assertEquals(emptyList<String>(), tagsOf(it)) }
        assertEquals(
            listOf(Event(1, "Kalu waterfall trip", oct(2), oct(4))),
            events.observeEvents().first()
        )
    }

    @Test
    fun anExistingTagWithTheNameIsReusedAndOneDayIsFine() = runTest {
        val (goa) = tags.getOrCreate(listOf("goa"))
        val onDay = addOn(oct(2))

        save(Event(name = "Goa", start = oct(2), end = oct(2)))

        assertEquals(listOf(goa), db.transactionDao().tagIds(onDay))
        // The event's spelling wins.
        assertEquals(listOf("Goa"), tagsOf(onDay))
    }

    @Test
    fun changingTheDatesMovesTheTag() = runTest {
        val early = addOn(oct(2))
        val both = addOn(oct(3))
        val late = addOn(oct(5))
        val id = save(Event(name = "Trip", start = oct(2), end = oct(3)))

        save(Event(id = id, name = "Trip", start = oct(3), end = oct(5)))

        assertEquals(emptyList<String>(), tagsOf(early))
        assertEquals(listOf("Trip"), tagsOf(both))
        assertEquals(listOf("Trip"), tagsOf(late))
    }

    @Test
    fun renamingMovesTheTagAndDeletesTheOldOneWhenUnused() = runTest {
        val onDay = addOn(oct(2))
        val id = save(Event(name = "Trip", start = oct(2), end = oct(2)))

        save(Event(id = id, name = "Goa trip", start = oct(2), end = oct(2)))

        assertEquals(listOf("Goa trip"), tagsOf(onDay))
        assertNull(db.tagDao().getByName("Trip"))
    }

    @Test
    fun renamingKeepsTheOldTagWhereItIsStillUsed() = runTest {
        val (trip) = tags.getOrCreate(listOf("Trip"))
        val elsewhere = addOn(oct(20))
        db.transactionDao().setTags(elsewhere, listOf(trip))
        val id = save(Event(name = "Trip", start = oct(2), end = oct(2)))

        save(Event(id = id, name = "Goa trip", start = oct(2), end = oct(2)))

        assertEquals(listOf("Trip"), tagsOf(elsewhere))
    }

    @Test
    fun deletingAnEventKeepsItsTag() = runTest {
        val onDay = addOn(oct(2))
        val id = save(Event(name = "Trip", start = oct(2), end = oct(4)))

        events.delete(id)

        assertTrue(events.observeEvents().first().isEmpty())
        assertEquals(listOf("Trip"), tagsOf(onDay))
    }

    @Test
    fun twoEventsCantShareAName() = runTest {
        save(Event(name = "Trip", start = oct(2), end = oct(4)))

        assertEquals(
            SaveEventResult.NameTaken,
            events.save(Event(name = "trip", start = oct(10), end = oct(12)), zone)
        )
        assertEquals(
            SaveEventResult.Blank,
            events.save(Event(name = " ", start = oct(1), end = oct(1)), zone)
        )
    }

    @Test
    fun deletingTheTagDeletesTheEventAndMergingMovesIt() = runTest {
        save(Event(name = "Trip", start = oct(2), end = oct(4)))
        val (goa) = tags.getOrCreate(listOf("Goa"))

        tags.merge(db.tagDao().getByName("Trip")!!.id, goa)
        assertEquals(listOf("Goa"), events.observeEvents().first().map { it.name })

        tags.delete(goa)
        assertTrue(events.observeEvents().first().isEmpty())
    }

    @Test
    fun namesAtATimeAreTheEventsThatIncludeIt() = runTest {
        save(Event(name = "Trip", start = oct(2), end = oct(4)))
        save(Event(name = "Diwali", start = oct(4), end = oct(4)))
        save(
            Event(
                name = "Party",
                start = oct(4),
                end = oct(4),
                startTime = sixPm,
                endTime = elevenPm
            )
        )

        assertEquals(listOf("Diwali", "Trip"), events.namesAt(oct(4), LocalTime.NOON))
        assertEquals(listOf("Diwali", "Party", "Trip"), events.namesAt(oct(4), sixPm))
        assertEquals(listOf("Trip"), events.namesAt(oct(2), LocalTime.MIDNIGHT))
        assertTrue(events.namesAt(oct(5), LocalTime.MIDNIGHT).isEmpty())
    }

    @Test
    fun anEventWithTimesTagsFromItsStartMinuteToItsEndMinute() = runTest {
        val beforeStart = addOn(oct(2), LocalTime.of(17, 59))
        val atStart = addOn(oct(2), sixPm)
        val middle = addOn(oct(3), LocalTime.MIDNIGHT)
        val atEnd = addOn(oct(4), elevenPm)
        val withinEndMinute = addOn(oct(4), LocalTime.of(23, 0, 59))
        val afterEnd = addOn(oct(4), LocalTime.of(23, 1))

        save(
            Event(
                name = "Trip",
                start = oct(2),
                end = oct(4),
                startTime = sixPm,
                endTime = elevenPm
            )
        )

        listOf(atStart, middle, atEnd, withinEndMinute).forEach {
            assertEquals(listOf("Trip"), tagsOf(it))
        }
        listOf(beforeStart, afterEnd).forEach { assertEquals(emptyList<String>(), tagsOf(it)) }
        assertEquals(
            listOf(Event(1, "Trip", oct(2), oct(4), sixPm, elevenPm)),
            events.observeEvents().first()
        )
    }

    @Test
    fun changingTheTimesMovesTheTagAndTurningThemOffGoesBackToWholeDays() = runTest {
        val morning = addOn(oct(2), LocalTime.of(9, 0))
        val afternoon = addOn(oct(2), LocalTime.of(13, 0))
        val evening = addOn(oct(2), LocalTime.of(19, 0))
        val trip =
            Event(
                name = "Trip",
                start = oct(2),
                end = oct(4),
                startTime = sixPm,
                endTime = elevenPm
            )
        val id = save(trip)
        assertEquals(
            listOf(emptyList(), emptyList(), listOf("Trip")),
            tagsOf(morning, afternoon, evening)
        )

        save(trip.copy(id = id, startTime = LocalTime.NOON))
        assertEquals(
            listOf(emptyList(), listOf("Trip"), listOf("Trip")),
            tagsOf(morning, afternoon, evening)
        )

        save(trip.copy(id = id))
        assertEquals(
            listOf(emptyList(), emptyList(), listOf("Trip")),
            tagsOf(morning, afternoon, evening)
        )

        save(trip.copy(id = id, startTime = null, endTime = null))
        assertEquals(
            listOf(listOf("Trip"), listOf("Trip"), listOf("Trip")),
            tagsOf(morning, afternoon, evening)
        )
    }

    @Test
    fun aOneDayEventMustEndAfterItStarts() = runTest {
        val oneDay = Event(name = "Party", start = oct(2), end = oct(2), startTime = sixPm)

        assertEquals(
            SaveEventResult.EndBeforeStart,
            events.save(oneDay.copy(endTime = sixPm), zone)
        )
        assertTrue(events.observeEvents().first().isEmpty())
        // Over two days, an earlier end time is fine.
        save(oneDay.copy(end = oct(3), endTime = LocalTime.NOON))
    }

    private suspend fun tagsOf(vararg ids: Long) = ids.map { tagsOf(it) }

    private val sixPm = LocalTime.of(18, 0)
    private val elevenPm = LocalTime.of(23, 0)
}
