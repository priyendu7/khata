package com.openhand.khata.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One expense, refund or income as the charts need it (transfers never count, so aren't here). */
data class AmountEntry(
    /** Milliseconds since the epoch. */
    val timestamp: Long,
    val direction: Direction,
    val amountPaise: Long,
    val categoryId: Long
)

/**
 * Spending on each day that has any, in [zone]: expenses minus refunds (PRD feature 1), so a day
 * can be negative. Days split at local midnight, like the Home totals.
 */
fun spendingByDay(entries: List<AmountEntry>, zone: ZoneId): Map<LocalDate, Long> =
    entries.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        .mapValues { (_, day) -> Totals.of(day.map { it.direction to it.amountPaise }).spentPaise }
        .filterValues { it != 0L }

/** How [current] compares with [previous]: a percent, or [New] when there was nothing before. */
sealed interface Change {
    /** Rounded to a whole percent, half away from zero. */
    data class Percent(val value: Long) : Change

    /** Spending this month after none (or only refunds) last month: no percent makes sense. */
    data object New : Change

    companion object {
        /** Null when neither month had spending, so there's nothing to say. */
        fun of(previous: Long, current: Long): Change? = when {
            previous > 0 -> Percent(roundedDiv((current - previous) * PERCENT, previous))
            current > 0 -> New
            else -> null
        }

        private const val PERCENT = 100L

        /** [numerator] / [denominator] (positive), rounded half away from zero; integer maths. */
        private fun roundedDiv(numerator: Long, denominator: Long): Long = if (numerator >= 0) {
            (numerator + denominator / 2) / denominator
        } else {
            -((-numerator + denominator / 2) / denominator)
        }
    }
}
