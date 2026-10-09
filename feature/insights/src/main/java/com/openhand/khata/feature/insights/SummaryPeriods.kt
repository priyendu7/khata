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
 * This month, and last month up to the same day, in the phone's time zone; each as a
 * `[from, until)` range of epoch milliseconds, which is what the totals queries take. Comparing
 * with the same days keeps early-month figures fair: on the 3rd, three days against three days.
 */
data class SummaryPeriods(
    val monthFrom: Long,
    val monthUntil: Long,
    val lastMonthFrom: Long,
    val lastMonthUntil: Long
) {
    companion object {
        fun of(today: LocalDate, zone: ZoneId): SummaryPeriods {
            val month = DateSpan.of(ChartPeriod.MONTH, today, DayOfWeek.MONDAY)
            val lastMonthStart = today.minusMonths(1).withDayOfMonth(1)
            // On the 31st after a 30-day month, all of last month.
            val sameDay = minOf(today.dayOfMonth, lastMonthStart.lengthOfMonth())
            val lastMonth = DateSpan(lastMonthStart, lastMonthStart.withDayOfMonth(sameDay))
            return SummaryPeriods(
                monthFrom = month.from(zone),
                monthUntil = month.until(zone),
                lastMonthFrom = lastMonth.from(zone),
                lastMonthUntil = lastMonth.until(zone)
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
