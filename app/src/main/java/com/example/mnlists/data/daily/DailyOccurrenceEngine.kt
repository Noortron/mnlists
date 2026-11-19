package com.example.mnlists.data.daily

import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.model.RepeatType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Which dates a Daily series must have an occurrence for (SPEC §4.3/§8, Step 8).
 *
 * Every calculation is done on epoch **days**, so it is time-zone and DST safe;
 * the zone is only needed to turn "now" into today's date. Recurrence is
 * anchored on [Daily.startDate]:
 * - [RepeatType.DAILY] — every `everyN` days.
 * - [RepeatType.WEEKLY] — the picked weekdays (start date's weekday when none
 *   are picked), every `everyN` weeks.
 * - [RepeatType.MONTHLY] — the start date's day of every `everyN`-th month,
 *   clamped to the last day of short months.
 *
 * The engine is pure: it never touches the database and returns dates only,
 * leaving the repository to create the actual (independent) occurrence rows.
 */
object DailyOccurrenceEngine {

    /** Defensive cap so a very old start date cannot loop forever. */
    const val MAX_CATCH_UP_DATES = 365

    /** Today as an epoch day in [zone]; handy for callers of [datesToGenerate]. */
    fun today(zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zone).toEpochDay()

    /**
     * Dates that are still missing for [daily]: from the day after the last
     * generated occurrence (or from the series' start date when it has none)
     * through [today], inclusive. Returning only missing dates makes the
     * manager idempotent and backfills runs missed while the app was closed
     * (SPEC §9), without ever touching occurrences that already exist.
     */
    fun datesToGenerate(
        daily: Daily,
        lastGeneratedDate: Long?,
        today: Long,
    ): List<Long> {
        val from = if (lastGeneratedDate == null) daily.startDate else lastGeneratedDate + 1
        if (from > today) return emptyList()
        return scheduledDates(daily, from, today)
    }

    /** All scheduled dates of [daily] inside the inclusive range [fromDate, toDate]. */
    fun scheduledDates(
        daily: Daily,
        fromDate: Long,
        toDate: Long,
    ): List<Long> {
        val start = LocalDate.ofEpochDay(daily.startDate)
        val from = LocalDate.ofEpochDay(fromDate)
        val to = LocalDate.ofEpochDay(toDate)
        if (from > to || start > to) return emptyList()

        // Never generate before the series began, even if the caller asks to.
        val lower = if (from.isBefore(start)) start else from
        return when (daily.repeatType) {
            RepeatType.DAILY -> dailyDates(start, lower, to, daily.everyN)
            RepeatType.WEEKLY -> weeklyDates(start, lower, to, daily.everyN, daily.weekdays)
            RepeatType.MONTHLY -> monthlyDates(start, lower, to, daily.everyN)
        }
    }

    private fun dailyDates(start: LocalDate, from: LocalDate, to: LocalDate, everyN: Int): List<Long> {
        val step = everyN.coerceAtLeast(1).toLong()
        val elapsed = ChronoUnit.DAYS.between(start, from).coerceAtLeast(0)
        val periods = if (elapsed == 0L) 0L else (elapsed + step - 1) / step
        var date = start.plusDays(periods * step)
        val result = mutableListOf<Long>()
        while (!date.isAfter(to) && result.size < MAX_CATCH_UP_DATES) {
            result += date.toEpochDay()
            date = date.plusDays(step)
        }
        return result
    }

    private fun weeklyDates(
        start: LocalDate,
        from: LocalDate,
        to: LocalDate,
        everyN: Int,
        weekdays: Set<DayOfWeek>,
    ): List<Long> {
        val step = everyN.coerceAtLeast(1)
        val targets = weekdays.ifEmpty { setOf(start.dayOfWeek) }
        val startWeek = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val result = mutableListOf<Long>()
        var week = startWeek
        var period = 0
        while (!week.isAfter(to) && result.size < MAX_CATCH_UP_DATES) {
            if (period % step == 0) {
                targets.forEach { dayOfWeek ->
                    val date = week.with(dayOfWeek)
                    if (date >= start && date >= from && date <= to) {
                        result += date.toEpochDay()
                    }
                }
            }
            week = week.plusWeeks(1)
            period++
        }
        return result.sorted()
    }

    private fun monthlyDates(
        start: LocalDate,
        from: LocalDate,
        to: LocalDate,
        everyN: Int,
    ): List<Long> {
        val step = everyN.coerceAtLeast(1)
        val targetDay = start.dayOfMonth
        val startMonth = YearMonth.from(start)
        val toMonth = YearMonth.from(to)
        val result = mutableListOf<Long>()
        var month = startMonth
        var period = 0
        while (!month.isAfter(toMonth) && result.size < MAX_CATCH_UP_DATES) {
            if (period % step == 0) {
                val date = month.atDay(minOf(targetDay, month.lengthOfMonth()))
                if (date >= start && date >= from && date <= to) {
                    result += date.toEpochDay()
                }
            }
            month = month.plusMonths(1)
            period++
        }
        return result
    }
}
