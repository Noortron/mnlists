package com.example.mnlists.ui.calendar

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure month-grid math for the Calendar tab (SPEC §4.4, Step 12). */
class CalendarDatesTest {

    private fun epochDay(date: String): Long = LocalDate.parse(date).toEpochDay()

    @Test
    fun monthCells_isAlwaysSixMondayFirstWeeks() {
        val november = YearMonth.of(2026, 11)
        val cells = CalendarDates.monthCells(november)

        assertEquals(42, cells.size)
        // 2026-11-01 is a Sunday, so Monday-first leaves six leading blanks.
        assertEquals(6, CalendarDates.leadingBlanks(november))
        assertEquals(listOf(null, null, null, null, null, null), cells.take(6))
        assertEquals(epochDay("2026-11-01"), cells[6])
        assertEquals(epochDay("2026-11-30"), cells[35])
        assertTrue("trailing cells are blanks", cells.drop(36).all { it == null })
    }

    @Test
    fun monthCells_startsWithDayOneWhenTheMonthStartsOnMonday() {
        val june = YearMonth.of(2026, 6)
        val cells = CalendarDates.monthCells(june)

        assertEquals(0, CalendarDates.leadingBlanks(june))
        assertEquals(epochDay("2026-06-01"), cells.first())
        assertEquals(30, cells.count { it != null })
    }

    @Test
    fun dayInMonth_keepsTheDayAndClampsToShorterMonths() {
        val january31 = epochDay("2026-01-31")
        val february = YearMonth.of(2026, 2)

        assertEquals(epochDay("2026-02-28"), CalendarDates.dayInMonth(january31, february))
        assertEquals(epochDay("2026-03-31"), CalendarDates.dayInMonth(january31, YearMonth.of(2026, 3)))
        assertEquals(epochDay("2026-03-15"), CalendarDates.dayInMonth(epochDay("2026-03-15"), YearMonth.of(2026, 3)))
    }

    @Test
    fun monthCells_useNullsRatherThanNeighbouringMonths() {
        val cells = CalendarDates.monthCells(YearMonth.of(2026, 11))

        assertNull(cells.first())
        assertNull(cells.last())
        assertEquals(30, cells.filterNotNull().size)
        assertTrue(cells.filterNotNull().all { CalendarDates.yearMonth(it) == YearMonth.of(2026, 11) })
    }

    @Test
    fun utcPickerRoundTrip_isStable() {
        val day = epochDay("2026-11-21")

        assertEquals(day, CalendarDates.fromUtcMillis(CalendarDates.toUtcMillis(day)))
    }
}
