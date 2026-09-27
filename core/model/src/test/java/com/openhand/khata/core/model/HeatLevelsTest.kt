package com.openhand.khata.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HeatLevelsTest {
    @Test
    fun nothingSpentIsAllEmpty() {
        val levels = HeatLevels.of(listOf(0L, -500L))
        assertEquals(0, levels.level(0))
        assertEquals(0, levels.level(-500))
        assertEquals(0, levels.level(100))
    }

    @Test
    fun oneDayIsTheDarkest() {
        assertEquals(HeatLevels.MAX, HeatLevels.of(listOf(250_00L)).level(250_00))
    }

    @Test
    fun evenlySpreadDaysUseEveryLevel() {
        val levels = HeatLevels.of(listOf(100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L))
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4), (1..8).map { levels.level(it * 100L) })
    }

    @Test
    fun oneVeryLargeDayDoesNotWashOutTheRest() {
        val days = listOf(100L, 150L, 200L, 250L, 300L, 350L, 400L, 1_000_000L)
        val levels = HeatLevels.of(days)
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4), days.map { levels.level(it) })
    }

    @Test
    fun negativeDaysCountAsZero() {
        val levels = HeatLevels.of(listOf(-300L, 100L, 200L))
        assertEquals(0, levels.level(-300))
        assertEquals(2, levels.level(100))
        assertEquals(4, levels.level(200))
    }
}
