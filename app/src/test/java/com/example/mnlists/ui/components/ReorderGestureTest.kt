package com.example.mnlists.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure checks for the Step 10.5 drag polish (no Compose host needed). */
class ReorderGestureTest {

    @Test
    fun longPressTimeout_isHalfTheSystemDefault() {
        assertEquals(250L, REORDER_LONG_PRESS_MS)
    }

    @Test
    fun gapShift_draggingDown_pullsRowsTowardTheOrigin() {
        val heights = listOf(100, 80, 120, 90)

        assertEquals(-100f, gapShift(draggedIndex = 0, targetIndex = 3, rowIndex = 1, rowSizes = heights))
        assertEquals(-180f, gapShift(draggedIndex = 0, targetIndex = 3, rowIndex = 2, rowSizes = heights))
        assertEquals(-300f, gapShift(draggedIndex = 0, targetIndex = 3, rowIndex = 3, rowSizes = heights))
    }

    @Test
    fun gapShift_draggingUp_pushesRowsTowardTheDropSlot() {
        val heights = listOf(100, 80, 120, 90)

        assertEquals(90f, gapShift(draggedIndex = 3, targetIndex = 0, rowIndex = 2, rowSizes = heights))
        assertEquals(210f, gapShift(draggedIndex = 3, targetIndex = 0, rowIndex = 1, rowSizes = heights))
        assertEquals(290f, gapShift(draggedIndex = 3, targetIndex = 0, rowIndex = 0, rowSizes = heights))
    }

    @Test
    fun gapShift_draggedRowAndRowsOutsideTheRangeStayStill() {
        val heights = listOf(100, 80, 120, 90)

        assertEquals(0f, gapShift(1, 3, 1, heights))
        assertEquals(0f, gapShift(1, 3, 0, heights))
        assertEquals(0f, gapShift(3, 1, 3, heights))
        assertEquals(0f, gapShift(2, 2, 0, heights))
        assertEquals(0f, gapShift(2, 2, 2, heights))
    }

    @Test
    fun gapShift_missingHeightsFallBackToZero() {
        assertEquals(-80f, gapShift(0, 2, 2, listOf(80)))
    }
}
