package com.openhand.khata.core.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * A named date range, such as a trip (#73). Every transaction from [start] to [end] (inclusive,
 * local days) is tagged with [name], so the whole trip can be found and totalled through the tag.
 * With times (#141) it starts at [startTime] on the first day and ends at [endTime] (that minute
 * included) on the last, so a trip leaving at 6 PM doesn't tag the morning's groceries.
 */
data class Event(
    val id: Long = 0,
    val name: String,
    val start: LocalDate,
    val end: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null
) {
    /** Times count only when both are set; otherwise the event is whole days. */
    val hasTimes: Boolean get() = startTime != null && endTime != null

    /** A one-day event must end after it starts; over several days any times are in order. */
    val timesInOrder: Boolean
        get() = when {
            startTime == null || endTime == null || start != end -> true
            else -> endTime > startTime
        }
}
