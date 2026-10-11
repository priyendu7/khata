package com.openhand.khata.core.model

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventTest {
    private val day = LocalDate.of(2026, 10, 2)
    private val noon = Event(name = "Party", start = day, end = day, startTime = LocalTime.NOON)

    @Test
    fun aOneDayEventMustEndAfterItStarts() {
        assertFalse(noon.copy(endTime = LocalTime.NOON).timesInOrder)
        assertFalse(noon.copy(endTime = LocalTime.of(9, 0)).timesInOrder)
        assertTrue(noon.copy(endTime = LocalTime.of(12, 1)).timesInOrder)
    }

    @Test
    fun overSeveralDaysOrWithoutTimesAnyEndIsFine() {
        assertTrue(noon.copy(end = day.plusDays(1), endTime = LocalTime.of(9, 0)).timesInOrder)
        assertTrue(noon.timesInOrder)
        assertFalse(noon.hasTimes)
    }
}
