package com.openhand.khata.feature.insights

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The periods the Insights charts can show. */
enum class ChartPeriod { WEEK, MONTH, YEAR, CUSTOM }

/** The days a chart covers, [first] and [last] both included. */
data class DateSpan(val first: LocalDate, val last: LocalDate) {
    /** Start of [first] in [zone], in epoch milliseconds: the `from` the queries take. */
    fun from(zone: ZoneId): Long = first.startMillis(zone)

    /** Start of the day after [last] in [zone]: the queries' exclusive `until`. */
    fun until(zone: ZoneId): Long = last.plusDays(1).startMillis(zone)

    companion object {
        /** The week, month or year (so far and to come) that [today] falls in. */
        fun of(period: ChartPeriod, today: LocalDate, firstDayOfWeek: DayOfWeek): DateSpan =
            when (period) {
                ChartPeriod.WEEK -> {
                    val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
                    DateSpan(start, start.plusDays(DAYS_IN_WEEK - 1))
                }
                ChartPeriod.MONTH -> DateSpan(
                    today.withDayOfMonth(1),
                    today.with(TemporalAdjusters.lastDayOfMonth())
                )
                ChartPeriod.YEAR -> DateSpan(
                    today.withDayOfYear(1),
                    today.with(TemporalAdjusters.lastDayOfYear())
                )
                ChartPeriod.CUSTOM -> error("A custom period has its own dates")
            }

        private const val DAYS_IN_WEEK = 7L
    }
}

internal fun LocalDate.startMillis(zone: ZoneId): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()
