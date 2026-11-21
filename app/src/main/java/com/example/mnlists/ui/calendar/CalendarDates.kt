package com.example.mnlists.ui.calendar

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure calendar date math for the Calendar tab (SPEC §4.4, Step 12).
 *
 * Dates are epoch days everywhere else in the app, so the grid is expressed in
 * epoch days too. Weeks are **Monday-first** to match the Daily weekday picker.
 */
object CalendarDates {

    /** Number of cells in the fixed six-week grid. */
    const val CELL_COUNT = 42

    fun today(zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zone).toEpochDay()

    fun yearMonth(epochDay: Long): YearMonth = YearMonth.from(LocalDate.ofEpochDay(epochDay))

    fun epochDayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun firstDayOfMonth(month: YearMonth): Long = month.atDay(1).toEpochDay()

    /** Leading blank cells before day 1; `0` when the month starts on Monday. */
    fun leadingBlanks(month: YearMonth): Int =
        (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7

    /**
     * Six fixed weeks of epoch days for [month]; `null` cells are the leading
     * and trailing blanks that keep the grid height stable while paging.
     */
    fun monthCells(month: YearMonth): List<Long?> {
        val first = firstDayOfMonth(month)
        val blanks = leadingBlanks(month)
        val length = month.lengthOfMonth()
        return List(CELL_COUNT) { index ->
            val day = index - blanks
            if (day in 0 until length) first + day else null
        }
    }

    /** Same day-of-month as [epochDay], clamped to the target month's length. */
    fun dayInMonth(epochDay: Long, month: YearMonth): Long {
        val day = LocalDate.ofEpochDay(epochDay).dayOfMonth
        return month.atDay(minOf(day, month.lengthOfMonth())).toEpochDay()
    }

    fun formatMonth(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))

    fun formatDay(epochDay: Long, locale: Locale = Locale.getDefault()): String =
        LocalDate.ofEpochDay(epochDay)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

    fun weekdayLabels(locale: Locale = Locale.getDefault()): List<String> =
        DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, locale) }

    /** Material date pickers work in UTC millis at the start of a day. */
    fun toUtcMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromUtcMillis(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
}
