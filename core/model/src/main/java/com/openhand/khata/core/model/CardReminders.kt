package com.openhand.khata.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Credit card due date reminders (#156): on unless turned off, for every card except those in
 * [offCards], on each of [daysBefore] (0 is the due date itself).
 */
data class CardReminderSettings(
    val enabled: Boolean = true,
    val daysBefore: Set<Int> = DEFAULT_DAYS,
    val offCards: Set<Long> = emptySet()
) {
    fun isOn(cardId: Long): Boolean = enabled && cardId !in offCards

    companion object {
        /** The days a user can pick, furthest first. */
        val DAY_CHOICES = listOf(7, 3, 1, 0)
        val DEFAULT_DAYS = setOf(3, 1, 0)
    }
}

/** A card's current statement, as far as reminders need it. */
data class DueBill(
    val cardId: Long,
    val statementId: Long,
    val dueDate: LocalDate,
    val paidInFull: Boolean
)

/** One notification to post at [at] (epoch millis) for statement [statementId]. */
data class CardReminder(
    val cardId: Long,
    val statementId: Long,
    val daysBefore: Int,
    val at: Long
) {
    /** Remembered once it's posted, so it's never posted twice. */
    val key: String get() = key(statementId, daysBefore)

    companion object {
        fun key(statementId: Long, daysBefore: Int) = "$statementId:$daysBefore"
    }
}

object CardReminders {
    /** Reminders go out in the morning, where the phone is. */
    const val HOUR = 9

    /** When the reminder [daysBefore] days before [dueDate] goes out, in [zone]. */
    fun at(dueDate: LocalDate, daysBefore: Int, zone: ZoneId): Long =
        dueDate.minusDays(daysBefore.toLong()).atTime(HOUR, 0).atZone(zone).toInstant()
            .toEpochMilli()

    /**
     * The reminders still to post for [bills]: the ones not paid in full, on cards that have
     * reminders on. A reminder from an earlier day is skipped; one from earlier today goes out
     * now (at is in the past), as the statement may have only just come. [posted] keys are done.
     */
    fun plan(
        bills: List<DueBill>,
        settings: CardReminderSettings,
        posted: Set<String>,
        now: Long,
        zone: ZoneId
    ): List<CardReminder> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return bills.filter { !it.paidInFull && settings.isOn(it.cardId) }.flatMap { bill ->
            settings.daysBefore.sortedDescending()
                .filter { bill.dueDate.minusDays(it.toLong()) >= today }
                .map { CardReminder(bill.cardId, bill.statementId, it, at(bill.dueDate, it, zone)) }
                .filter { it.key !in posted }
        }
    }
}
