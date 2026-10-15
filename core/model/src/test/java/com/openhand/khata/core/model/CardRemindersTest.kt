package com.openhand.khata.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRemindersTest {
    private val india = ZoneId.of("Asia/Kolkata")
    private val due = LocalDate.parse("2026-11-02")
    private val bill = DueBill(cardId = 1, statementId = 10, dueDate = due, paidInFull = false)

    private fun millis(text: String, zone: ZoneId = india) =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun plan(
        bills: List<DueBill> = listOf(bill),
        settings: CardReminderSettings = CardReminderSettings(),
        posted: Set<String> = emptySet(),
        now: String = "2026-10-15T12:00",
        zone: ZoneId = india
    ) = CardReminders.plan(bills, settings, posted, millis(now, zone), zone)

    @Test
    fun byDefaultThreeDaysOneDayAndOnTheDayAtNineWhereThePhoneIs() {
        assertEquals(
            listOf(
                CardReminder(1, 10, 3, millis("2026-10-30T09:00")),
                CardReminder(1, 10, 1, millis("2026-11-01T09:00")),
                CardReminder(1, 10, 0, millis("2026-11-02T09:00"))
            ),
            plan()
        )
    }

    @Test
    fun theTimeFollowsThePhonesTimeZone() {
        val london = ZoneId.of("Europe/London")
        assertEquals(
            millis("2026-11-02T09:00", london),
            plan(zone = london).single { it.daysBefore == 0 }.at
        )
        // 9:00 in London is 14:30 in India.
        assertEquals(millis("2026-11-02T14:30"), CardReminders.at(due, 0, london))
    }

    @Test
    fun noRemindersOncePaidInFull() {
        assertTrue(plan(listOf(bill.copy(paidInFull = true))).isEmpty())
    }

    @Test
    fun noRemindersWhenTurnedOffForAllOrForTheCard() {
        assertTrue(plan(settings = CardReminderSettings(enabled = false)).isEmpty())
        assertTrue(plan(settings = CardReminderSettings(offCards = setOf(1))).isEmpty())
        assertEquals(3, plan(settings = CardReminderSettings(offCards = setOf(2))).size)
    }

    @Test
    fun thePickedDaysAreUsed() {
        val settings = CardReminderSettings(daysBefore = setOf(7, 0))
        assertEquals(listOf(7, 0), plan(settings = settings).map { it.daysBefore })
    }

    @Test
    fun earlierDaysAreSkippedButEarlierTodayGoesOutNow() {
        // 1 Nov at 10:00: the 3-day reminder was on 30 Oct, the 1-day one at 9:00 today.
        val reminders = plan(now = "2026-11-01T10:00")
        assertEquals(listOf(1, 0), reminders.map { it.daysBefore })
        assertTrue(reminders.first().at < millis("2026-11-01T10:00"))
    }

    @Test
    fun postedRemindersAreNotPlannedAgain() {
        val posted = setOf(CardReminder.key(10, 3))
        assertEquals(listOf(1, 0), plan(posted = posted).map { it.daysBefore })
    }

    @Test
    fun nothingAfterTheDueDate() {
        assertTrue(plan(now = "2026-11-03T08:00").isEmpty())
    }
}
