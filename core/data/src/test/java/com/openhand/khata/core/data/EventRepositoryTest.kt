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
    fun namesOnADayAreTheEventsThatIncludeIt() = runTest {
        save(Event(name = "Trip", start = oct(2), end = oct(4)))
        save(Event(name = "Diwali", start = oct(4), end = oct(4)))

        assertEquals(listOf("Diwali", "Trip"), events.namesOn(oct(4)))
        assertEquals(listOf("Trip"), events.namesOn(oct(2)))
        assertTrue(events.namesOn(oct(5)).isEmpty())
    }
}
