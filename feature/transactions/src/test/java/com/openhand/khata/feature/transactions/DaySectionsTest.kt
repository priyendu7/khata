package com.openhand.khata.feature.transactions

import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Direction
import com.openhand.khata.core.model.Totals
import com.openhand.khata.core.model.TransactionListItem
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DaySectionsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val category = Category(id = 1, name = "Food", color = 0, icon = "food")

    private fun item(id: Long, day: Int, hour: Int, direction: Direction, amount: Long) =
        TransactionListItem(
            id = id,
            amountPaise = amount,
            direction = direction,
            timestamp = ZonedDateTime.of(
                2026,
                9,
                day,
                hour,
                0,
                0,
                0,
                zone
            ).toInstant().toEpochMilli(),
            payeeName = null,
            note = null,
            accountName = null,
            category = category,
            tags = emptyList()
        )

    @Test
    fun groupsByLocalDayWithTotals() {
        val sections = groupByDay(
            listOf(
                item(5, day = 26, hour = 23, Direction.DEBIT, 10_000),
                item(4, day = 26, hour = 1, Direction.TRANSFER, 99_000),
                item(3, day = 26, hour = 0, Direction.CREDIT, 50_000),
                item(2, day = 25, hour = 20, Direction.DEBIT, 30_000),
                item(1, day = 25, hour = 9, Direction.REFUND, 5_000)
            ),
            zone
        )

        assertEquals(
            listOf(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 25)),
            sections.map {
                it.date
            }
        )
        assertEquals(
            listOf(listOf(5L, 4L, 3L), listOf(2L, 1L)),
            sections.map { s ->
                s.items.map { it.id }
            }
        )
        assertEquals(Totals(spentPaise = 10_000, incomePaise = 50_000), sections[0].totals)
        assertEquals(Totals(spentPaise = 25_000, incomePaise = 0), sections[1].totals)
    }

    @Test
    fun emptyListHasNoDays() {
        assertEquals(emptyList<DaySection>(), groupByDay(emptyList(), zone))
    }
}
