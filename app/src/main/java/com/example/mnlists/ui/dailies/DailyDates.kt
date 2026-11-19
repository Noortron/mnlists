package com.example.mnlists.ui.dailies

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Epoch-day helpers for the Dailies UI. Daily dates are stored as epoch days
 * (SPEC §7), so the picker conversions mirror the To-Do ones but live in this
 * package to keep the two features independent.
 */
object DailyDates {

    /** Today in the device zone, as an epoch day. */
    fun today(zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zone).toEpochDay()

    /** Human-readable date for cards and list rows. */
    fun format(epochDay: Long, locale: Locale = Locale.getDefault()): String =
        LocalDate.ofEpochDay(epochDay)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    /** Material date pickers work in UTC millis at the start of a day. */
    fun toUtcMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromUtcMillis(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
}
