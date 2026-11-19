package com.example.mnlists.ui.todos

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Date helpers for To-Do's. Due dates are stored as epoch days, the same unit
 * the Room entity uses (SPEC §7), so these helpers are pure and testable.
 */
object TodoDates {

    /** Today in the device zone, as an epoch day. */
    fun today(zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zone).toEpochDay()

    /** Human-readable date for the editor and list rows. */
    fun format(epochDay: Long, locale: Locale = Locale.getDefault()): String =
        LocalDate.ofEpochDay(epochDay)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    /**
     * Material's date picker works in UTC millis at the start of a day, while
     * the rest of the app stores local epoch days. These two helpers convert
     * between the two representations.
     */
    fun toUtcMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromUtcMillis(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
}
