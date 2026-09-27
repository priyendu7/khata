package com.openhand.khata.feature.insights

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
            fun LocalDate.start() = atStartOfDay(zone).toInstant().toEpochMilli()
            val firstOfMonth = today.withDayOfMonth(1)
            return SummaryPeriods(
                monthFrom = firstOfMonth.start(),
                monthUntil = firstOfMonth.plusMonths(1).start(),
                todayFrom = today.start(),
                todayUntil = today.plusDays(1).start()
            )
        }
    }
}

/**
 * Today's date, emitted again just after each midnight so "today" and "this month" roll over while
 * Home is open. (Home stops collecting in the background and starts afresh when it's back.)
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
