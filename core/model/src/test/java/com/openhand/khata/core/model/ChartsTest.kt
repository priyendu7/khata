package com.openhand.khata.core.model

import com.openhand.khata.core.model.Direction.CREDIT
import com.openhand.khata.core.model.Direction.DEBIT
import com.openhand.khata.core.model.Direction.REFUND
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartsTest {
    private val india = ZoneId.of("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(india).toInstant().toEpochMilli()

    private fun entry(time: Long, direction: Direction, paise: Long, category: Long = 1) =
        AmountEntry(time, direction, paise, category)

    private fun category(id: Long) =
        Category(id = id, name = "C$id", seedKey = null, color = 0, icon = "food")

    @Test
    fun daysSplitAtLocalMidnight() {
        val days = spendingByDay(
            listOf(
                entry(at(2026, 9, 26, 23, 59), DEBIT, 100_00),
                // 00:10 IST is still the 26th in UTC; it belongs to the 27th here.
                entry(at(2026, 9, 27, 0, 10), DEBIT, 200_00),
                entry(at(2026, 9, 27, 9), REFUND, 50_00),
                entry(at(2026, 9, 27, 10), CREDIT, 9_999_00)
            ),
            india
        )
        assertEquals(
            mapOf(LocalDate.of(2026, 9, 26) to 100_00L, LocalDate.of(2026, 9, 27) to 150_00L),
            days
        )
    }

    @Test
    fun aDayOfOnlyRefundsIsNegative() {
        val days = spendingByDay(listOf(entry(at(2026, 9, 1), REFUND, 70_00)), india)
        assertEquals(-70_00L, days.getValue(LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun percentChangeRoundsHalfAwayFromZero() {
        assertEquals(Change.Percent(18), Change.of(100_00, 118_00))
        assertEquals(Change.Percent(-18), Change.of(100_00, 82_00))
        assertEquals(Change.Percent(33), Change.of(3, 4))
        assertEquals(Change.Percent(1), Change.of(200, 201))
        assertEquals(Change.Percent(-1), Change.of(200, 199))
        assertEquals(Change.Percent(0), Change.of(500, 500))
        assertEquals(Change.Percent(-100), Change.of(500, 0))
        // More refunded than spent this month.
        assertEquals(Change.Percent(-150), Change.of(200, -100))
    }

    @Test
    fun nothingLastMonthIsNewOrNothingToSay() {
        assertEquals(Change.New, Change.of(0, 500))
        assertEquals(Change.New, Change.of(-200, 500))
        assertNull(Change.of(0, 0))
        assertNull(Change.of(-200, -100))
    }

    @Test
    fun monthlyBarsStackByTheTopCategoriesAndAddUp() {
        val august = YearMonth.of(2026, 8)
        val september = YearMonth.of(2026, 9)
        val entries = listOf(
            entry(at(2026, 8, 5), DEBIT, 1_000_00, category = 1),
            entry(at(2026, 8, 6), DEBIT, 500_00, category = 2),
            entry(at(2026, 8, 7), CREDIT, 50_000_00, category = 9),
            entry(at(2026, 9, 5), DEBIT, 1_180_00, category = 1),
            entry(at(2026, 9, 6), DEBIT, 300_00, category = 2),
            entry(at(2026, 9, 6), REFUND, 400_00, category = 2)
        ) + (3L..8L).map { entry(at(2026, 9, 10), DEBIT, it * 10_00, category = it) }

        val comparison = MonthlyComparison.of(
            entries,
            listOf(august, september),
            india,
            (1L..9L).map(::category)
        )

        // Categories 1, 2, 8, 7, 6 are the biggest over both months; 3, 4 and 5 are "Other".
        assertEquals(listOf(1L, 2L, 8L, 7L, 6L), comparison.stacked.map { it.id })
        val (aug, sep) = comparison.bars
        assertEquals(1_500_00L, aug.spentPaise)
        assertEquals(50_000_00L, aug.incomePaise)
        assertEquals(listOf(1_000_00L, 500_00L, 0L, 0L, 0L, 0L), aug.segments)
        // Category 2 refunded more than it spent in September: it adds nothing, "Other" nets it.
        assertEquals(1_180_00L - 100_00L + 330_00L, sep.spentPaise)
        assertEquals(listOf(1_180_00L, 0L, 80_00L, 70_00L, 60_00L, 20_00L), sep.segments)
        comparison.bars.forEach { assertEquals(it.spentPaise, it.segments.sum()) }

        assertEquals(Change.Percent(-6), comparison.change)
        assertEquals(
            listOf(Change.Percent(18), Change.Percent(-100), Change.New, Change.New, Change.New),
            comparison.categoryChanges.map { it.change }
        )
    }

    @Test
    fun oneMonthHasNoChange() {
        val comparison =
            MonthlyComparison.of(emptyList(), listOf(YearMonth.of(2026, 9)), india, emptyList())
        assertEquals(listOf(0L), comparison.bars.single().segments)
        assertNull(comparison.change)
        assertEquals(emptyList<CategoryChange>(), comparison.categoryChanges)
    }
}
