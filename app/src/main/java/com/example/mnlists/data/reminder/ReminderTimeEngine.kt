package com.example.mnlists.data.reminder

import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.model.RepeatType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Pure reminder-time calculator (SPEC §4.5/§8/§9, Step 11).
 *
 * A To-Do reminder is one fixed instant. A Daily reminder is a **local time of
 * day** attached to every scheduled occurrence date, so it must be recomputed
 * for each fire and again after a time-zone change — never treated as a fixed
 * millisecond offset. Recurrence comes from [Daily] and mirrors
 * `DailyOccurrenceEngine` (daily/weekly/monthly + every N).
 */
object ReminderTimeEngine {

    const val MINUTES_PER_DAY = 24 * 60

    /** Defensive bound for the "next date" walk (a schedule can be sparse). */
    private const val MAX_WALK_STEPS = 1_000

    /** Today as an epoch day in [zone]. */
    fun today(zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.now(zone).toEpochDay()

    /** Local date of an instant, as an epoch day. */
    fun epochDayOf(triggerAt: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(triggerAt).atZone(zone).toLocalDate().toEpochDay()

    /** Local wall-clock time of an instant as minutes after midnight. */
    fun minutesOfDay(triggerAt: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val time = Instant.ofEpochMilli(triggerAt).atZone(zone).toLocalTime()
        return time.hour * 60 + time.minute
    }

    /** Wall-clock [minutesOfDay] on [epochDay] in [zone] as epoch millis. */
    fun atLocalTime(
        epochDay: Long,
        minutesOfDay: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val minutes = minutesOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        return LocalDate.ofEpochDay(epochDay)
            .atTime(minutes / 60, minutes % 60)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * First trigger at or after [now]: today's occurrence when its time has not
     * passed yet, otherwise the next scheduled occurrence.
     */
    fun nextDailyTrigger(
        daily: Daily,
        minutesOfDay: Int,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val date = nextScheduledDate(daily, today) ?: return null
        val candidate = atLocalTime(date.toEpochDay(), minutesOfDay, zone)
        if (candidate >= now) return candidate

        val next = nextScheduledDate(daily, date.plusDays(1)) ?: return null
        return atLocalTime(next.toEpochDay(), minutesOfDay, zone)
    }

    /** Strictly-after variant used when advancing a just-fired Daily reminder. */
    fun nextDailyTriggerAfter(
        daily: Daily,
        minutesOfDay: Int,
        after: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val afterDate = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        val date = nextScheduledDate(daily, afterDate) ?: return null
        val candidate = atLocalTime(date.toEpochDay(), minutesOfDay, zone)
        if (candidate > after) return candidate

        val next = nextScheduledDate(daily, date.plusDays(1)) ?: return null
        return atLocalTime(next.toEpochDay(), minutesOfDay, zone)
    }

    /** First fixed instant for a To-Do reminder at a local date/time. */
    fun todoTrigger(
        epochDay: Long,
        minutesOfDay: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = atLocalTime(epochDay, minutesOfDay, zone)

    /**
     * First scheduled date of [daily] on or after [onOrAfter], or `null` when
     * the series starts after the searched range (never, unless inputs are
     * corrupt). Dates before [Daily.startDate] are never returned.
     */
    fun nextScheduledDate(daily: Daily, onOrAfter: LocalDate): LocalDate? {
        val start = LocalDate.ofEpochDay(daily.startDate)
        val lower = if (onOrAfter.isBefore(start)) start else onOrAfter
        return when (daily.repeatType) {
            RepeatType.DAILY -> nextDailyDate(start, lower, daily.everyN)
            RepeatType.WEEKLY -> nextWeeklyDate(start, lower, daily.everyN, daily.weekdays)
            RepeatType.MONTHLY -> nextMonthlyDate(start, lower, daily.everyN)
        }
    }

    private fun nextDailyDate(start: LocalDate, lower: LocalDate, everyN: Int): LocalDate {
        val step = everyN.coerceAtLeast(1).toLong()
        if (lower <= start) return start
        val elapsed = ChronoUnit.DAYS.between(start, lower)
        val periods = (elapsed + step - 1) / step
        return start.plusDays(periods * step)
    }

    private fun nextWeeklyDate(
        start: LocalDate,
        lower: LocalDate,
        everyN: Int,
        weekdays: Set<DayOfWeek>,
    ): LocalDate? {
        val step = everyN.coerceAtLeast(1).toLong()
        val targets = (weekdays.ifEmpty { setOf(start.dayOfWeek) }).sortedBy { it.value }
        val startWeek = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lowerWeek = lower.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekDistance = ChronoUnit.WEEKS.between(startWeek, lowerWeek).coerceAtLeast(0)
        var period = weekDistance / step

        var guard = 0
        while (guard++ < MAX_WALK_STEPS) {
            val week = startWeek.plusWeeks(period * step)
            targets.forEach { dayOfWeek ->
                val date = week.with(dayOfWeek)
                if (!date.isBefore(start) && !date.isBefore(lower)) return date
            }
            period++
        }
        return null
    }

    private fun nextMonthlyDate(start: LocalDate, lower: LocalDate, everyN: Int): LocalDate? {
        val step = everyN.coerceAtLeast(1).toLong()
        val startMonth = YearMonth.from(start)
        val lowerMonth = YearMonth.from(lower)
        val monthDistance = ChronoUnit.MONTHS.between(startMonth, lowerMonth).coerceAtLeast(0)
        var period = monthDistance / step
        val targetDay = start.dayOfMonth

        var guard = 0
        while (guard++ < MAX_WALK_STEPS) {
            val month = startMonth.plusMonths(period * step)
            val date = month.atDay(minOf(targetDay, month.lengthOfMonth()))
            if (!date.isBefore(start) && !date.isBefore(lower)) return date
            period++
        }
        return null
    }
}
