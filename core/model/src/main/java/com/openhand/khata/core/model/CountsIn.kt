package com.openhand.khata.core.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * The month a transaction counts in, when that isn't its date's month (#93): October's salary paid
 * on 30 September counts in October. It's stored as local midnight on the 1st of that month, so
 * period totals treat the transaction as happening then and a month still adds up to its days.
 * Null always means "same as date".
 */
object CountsIn {
    /** [timestamp]'s own month in [zone]. */
    fun monthOf(timestamp: Long, zone: ZoneId): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(timestamp).atZone(zone))

    /** What's stored for [month]: null when it's [timestamp]'s own month, else its 1st at 00:00. */
    fun toMillis(month: YearMonth?, timestamp: Long, zone: ZoneId): Long? =
        month?.takeIf { it != monthOf(timestamp, zone) }
            ?.atDay(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()

    fun fromMillis(millis: Long?, zone: ZoneId): YearMonth? = millis?.let { monthOf(it, zone) }

    /** The editor's choices for a date in [dateMonth]: the month before, its own, the next. */
    fun choices(dateMonth: YearMonth): List<YearMonth> =
        listOf(dateMonth.minusMonths(1), dateMonth, dateMonth.plusMonths(1))

    /**
     * [month] after the date moved to [dateMonth]: kept while it's still the month before or after,
     * otherwise back to "same as date" (null).
     */
    fun keepFor(month: YearMonth?, dateMonth: YearMonth): YearMonth? =
        month?.takeIf { it != dateMonth && it in choices(dateMonth) }
}
