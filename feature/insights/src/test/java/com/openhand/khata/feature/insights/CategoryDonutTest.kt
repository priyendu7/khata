package com.openhand.khata.feature.insights

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tapping the donut: which slice is under the finger, found by its angle. */
class CategoryDonutTest {
    private val size = Size(200f, 200f)

    // Ring from radius 60 to 100: tap at radius 80.
    private fun at(degreesFromTop: Double): Offset {
        val radians = Math.toRadians(degreesFromTop - 90)
        return Offset(
            100f + 80f * Math.cos(radians).toFloat(),
            100f + 80f * Math.sin(radians).toFloat()
        )
    }

    @Test
    fun findsSlicesClockwiseFromTheTop() {
        val fractions = fractionsOf(listOf(50, 25, 25))

        assertEquals(0, sliceAt(at(10.0), size, fractions))
        assertEquals(0, sliceAt(at(170.0), size, fractions))
        assertEquals(1, sliceAt(at(200.0), size, fractions))
        assertEquals(2, sliceAt(at(300.0), size, fractions))
        assertEquals(2, sliceAt(at(359.0), size, fractions))
    }

    @Test
    fun theHoleAndTheCornersAreNotSlices() {
        val fractions = fractionsOf(listOf(1))

        assertNull(sliceAt(Offset(100f, 100f), size, fractions))
        assertNull(sliceAt(Offset(2f, 2f), size, fractions))
        assertEquals(0, sliceAt(Offset(100f, 25f), size, fractions))
    }

    @Test
    fun nothingToTapWhenEmpty() {
        assertEquals(emptyList<Float>(), fractionsOf(emptyList()))
        assertNull(sliceAt(at(0.0), size, emptyList()))
    }

    @Test
    fun sharesRoundToWholePercents() {
        assertEquals(33, shareOf(1, 3))
        assertEquals(67, shareOf(2, 3))
        assertEquals(100, shareOf(5_00, 5_00))
        assertEquals(0, shareOf(5_00, 0))
    }
}
