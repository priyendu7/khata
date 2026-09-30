package com.openhand.khata.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PRD feature 5: the donut shows the top 5–6 categories plus "Other". */
class CategoryBreakdownTest {
    private fun spend(id: Long, paise: Long) = CategorySpend(
        Category(id = id, name = "C$id", seedKey = null, color = 0, icon = "food"),
        paise
    )

    @Test
    fun nothingSpentIsEmpty() {
        val breakdown = CategoryBreakdown.of(emptyList())
        assertTrue(breakdown.isEmpty)
        assertEquals(0L, breakdown.totalPaise)
    }

    @Test
    fun sixCategoriesAreAllShown() {
        val breakdown = CategoryBreakdown.of((1L..6L).map { spend(it, it * 100) })

        assertEquals(listOf(6L, 5L, 4L, 3L, 2L, 1L), breakdown.slices.map { it.category.id })
        assertEquals(0L, breakdown.otherPaise)
        assertEquals(2_100L, breakdown.totalPaise)
    }

    @Test
    fun moreThanSixKeepsTheBiggestFiveAndGroupsTheRest() {
        val breakdown = CategoryBreakdown.of((1L..8L).map { spend(it, it * 100) })

        assertEquals(listOf(8L, 7L, 6L, 5L, 4L), breakdown.slices.map { it.category.id })
        assertEquals(300L + 200L + 100L, breakdown.otherPaise)
        assertEquals(3_600L, breakdown.chartedPaise)
    }

    @Test
    fun tiesGoToTheOlderCategory() {
        val breakdown = CategoryBreakdown.of(listOf(spend(2, 500), spend(1, 500)))
        assertEquals(listOf(1L, 2L), breakdown.slices.map { it.category.id })
    }

    @Test
    fun refundsBeyondSpendingStayOutOfTheDonutButCountInTheTotal() {
        val breakdown = CategoryBreakdown.of(listOf(spend(1, 1_000), spend(2, -300)))

        assertEquals(listOf(1L), breakdown.slices.map { it.category.id })
        assertEquals(listOf(2L), breakdown.refunded.map { it.category.id })
        assertEquals(1_000L, breakdown.chartedPaise)
        assertEquals(700L, breakdown.totalPaise)
    }
}
