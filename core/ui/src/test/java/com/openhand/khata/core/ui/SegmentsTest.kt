package com.openhand.khata.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentsTest {
    private val outer = Segments.OuterCorner
    private val inner = Segments.InnerCorner

    @Test
    fun firstItemHasLargeTopCorners() {
        assertEquals(RoundedCornerShape(outer, outer, inner, inner), segmentShape(0, 3))
    }

    @Test
    fun middleItemHasSmallCorners() {
        assertEquals(RoundedCornerShape(inner), segmentShape(1, 3))
    }

    @Test
    fun lastItemHasLargeBottomCorners() {
        assertEquals(RoundedCornerShape(inner, inner, outer, outer), segmentShape(2, 3))
    }

    @Test
    fun singleItemHasLargeCornersAllRound() {
        assertEquals(RoundedCornerShape(outer), segmentShape(0, 1))
        assertEquals(Segments.Single, segmentShape(0, 1))
    }
}
