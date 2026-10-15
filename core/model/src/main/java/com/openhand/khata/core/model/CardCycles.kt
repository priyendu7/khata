package com.openhand.khata.core.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** The days one credit card statement bills, [first] to [last], both included. */
data class CardCycle(val first: LocalDate, val last: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = date in first..last
}

/**
 * A credit card's billing cycles (#154). A cycle runs from the day after one statement's date to
 * the next statement's date. The card's billing day (1–31; a short month uses its last day) stands
 * in for a statement that hasn't come, before the first statement SMS or when one went missing.
 * With neither, the cycle is a month.
 */
object CardCycles {
    const val FIRST_BILLING_DAY = 1
    const val LAST_BILLING_DAY = 31

    /** A statement more than this many days after the one before means one in between is missing. */
    private const val MAX_CYCLE_DAYS = 45L

    /** Banks send a statement a few days either side of the billing day. */
    private const val SLACK_DAYS = 7L

    fun isValidBillingDay(day: Int?): Boolean =
        day == null || day in FIRST_BILLING_DAY..LAST_BILLING_DAY

    /** [billingDay] in [month], or the month's last day when it's shorter. */
    fun billingDate(month: YearMonth, billingDay: Int): LocalDate =
        month.atDay(minOf(billingDay, month.lengthOfMonth()))

    /** The last billing date on or before [date]. */
    fun billingDateOnOrBefore(date: LocalDate, billingDay: Int): LocalDate {
        val month = YearMonth.from(date)
        val inMonth = billingDate(month, billingDay)
        return if (inMonth <= date) inMonth else billingDate(month.minusMonths(1), billingDay)
    }

    /** The first billing date on or after [date]. */
    fun billingDateOnOrAfter(date: LocalDate, billingDay: Int): LocalDate {
        val month = YearMonth.from(date)
        val inMonth = billingDate(month, billingDay)
        return if (inMonth >= date) inMonth else billingDate(month.plusMonths(1), billingDay)
    }

    /**
     * The cycle that the statement dated [statementDate] bills. It starts after [previous], the
     * statement before it, unless that's missing or too long ago; then after the billing date
     * about a month earlier, or a month earlier without a billing day.
     */
    fun statementCycle(
        statementDate: LocalDate,
        previous: LocalDate?,
        billingDay: Int?
    ): CardCycle {
        val start = previous?.takeIf {
            it < statementDate && ChronoUnit.DAYS.between(it, statementDate) <= MAX_CYCLE_DAYS
        } ?: monthBefore(statementDate, billingDay)
        return CardCycle(start.plusDays(1), statementDate)
    }

    /**
     * The cycle [today] is in, which no statement has billed yet. It starts after the [latest]
     * statement, or after the last billing date when there's no statement or the latest is from
     * an earlier cycle (the one since then went missing), and ends on the next billing date.
     * With neither a statement nor a billing day, it's the calendar month.
     */
    fun currentCycle(latest: LocalDate?, billingDay: Int?, today: LocalDate): CardCycle {
        val billed = billingDay?.let { billingDateOnOrBefore(today.minusDays(1), it) }
        val boundary = when {
            billed == null -> latest?.let { monthsAfter(it, today) } ?: return calendarMonth(today)
            latest == null || latest < billed.minusDays(SLACK_DAYS) -> billed
            else -> latest
        }
        val end = when (billingDay) {
            null -> boundary.plusMonths(1)
            else -> billingDateOnOrAfter(boundary.plusDays(SLACK_DAYS), billingDay)
        }
        return CardCycle(boundary.plusDays(1), end)
    }

    /** The billing date nearest a month before [date], or just a month before without one. */
    private fun monthBefore(date: LocalDate, billingDay: Int?): LocalDate {
        val target = date.minusMonths(1)
        billingDay ?: return target
        val before = billingDateOnOrBefore(target, billingDay)
        val after = billingDateOnOrAfter(target, billingDay)
        val daysBefore = ChronoUnit.DAYS.between(before, target)
        val daysAfter = ChronoUnit.DAYS.between(target, after)
        return if (daysAfter < daysBefore) after else before
    }

    /** The last whole month after [latest] that starts before [today]. */
    private fun monthsAfter(latest: LocalDate, today: LocalDate): LocalDate {
        var boundary = latest
        while (boundary.plusMonths(1) < today) boundary = boundary.plusMonths(1)
        return boundary
    }

    private fun calendarMonth(today: LocalDate): CardCycle {
        val month = YearMonth.from(today)
        return CardCycle(month.atDay(1), month.atEndOfMonth())
    }
}
