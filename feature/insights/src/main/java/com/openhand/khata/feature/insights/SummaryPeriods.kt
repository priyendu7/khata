package com.openhand.khata.feature.insights

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * This month and today in the phone's time zone, each as a `[from, until)` range of epoch
 * milliseconds, which is what the totals queries take.
 */
data class SummaryPeriods(
    val monthFrom: Long,
    val monthUntil: Long,
    val todayFrom: Long,
    val todayUntil: Long
) {
    companion object {
        fun of(today: LocalDate, zone: ZoneId): SummaryPeriods {
            val month = DateSpan.of(ChartPeriod.MONTH, today, DayOfWeek.MONDAY)
            val day = DateSpan(today, today)
            return SummaryPeriods(
                monthFrom = month.from(zone),
                monthUntil = month.until(zone),
                todayFrom = day.from(zone),
                todayUntil = day.until(zone)
            )
        }
    }
}

/**
 * Today's date, emitted again just after each midnight so "today" and "this month" roll over while
 * Home or Insights is open. (Screens stop collecting in the background and start afresh when
 * they're back.)
 */
internal fun currentDate(): Flow<LocalDate> = flow {
    while (true) {
        val now = ZonedDateTime.now()
        emit(now.toLocalDate())
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        delay(Duration.between(now, midnight).toMillis() + MIDNIGHT_MARGIN_MILLIS)
    }
}

private const val MIDNIGHT_MARGIN_MILLIS = 1_000L
