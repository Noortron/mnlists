package com.example.mnlists.data.reminder

import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.model.RepeatType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure schedule math for Step 11: no Android, no database. Weekly and monthly
 * behaviour must match [com.example.mnlists.data.daily.DailyOccurrenceEngine],
 * while Daily reminders add the wall-clock time on top of the occurrence date.
 */
class ReminderTimeEngineTest {

    private val utc = ZoneId.of("UTC")

    private fun at(date: String, hour: Int, minute: Int = 0): Long =
        LocalDate.parse(date)
            .atTime(LocalTime.of(hour, minute))
            .atZone(utc)
            .toInstant()
            .toEpochMilli()

    private fun daily(
        start: String,
        repeatType: RepeatType = RepeatType.DAILY,
        everyN: Int = 1,
        weekdays: Set<DayOfWeek> = emptySet(),
    ) = Daily(
        id = 1L,
        title = "Morning pages",
        startDate = LocalDate.parse(start).toEpochDay(),
        repeatType = repeatType,
        everyN = everyN,
        weekdays = weekdays,
    )

    @Test
    fun todoTrigger_isTheChosenLocalWallClock() {
        val day = LocalDate.parse("2026-11-21").toEpochDay()

        assertEquals(at("2026-11-21", 8, 30), ReminderTimeEngine.todoTrigger(day, 8 * 60 + 30, utc))
    }

    @Test
    fun dailyTrigger_usesTodayWhenTheTimeIsStillAhead_andTomorrowOtherwise() {
        val daily = daily("2026-11-20")
        val minutes = 9 * 60

        assertEquals(
            at("2026-11-20", 9),
            ReminderTimeEngine.nextDailyTrigger(daily, minutes, at("2026-11-20", 8), utc),
        )
        assertEquals(
            at("2026-11-21", 9),
            ReminderTimeEngine.nextDailyTrigger(daily, minutes, at("2026-11-20", 10), utc),
        )
        assertEquals(
            "exactly at the trigger is still upcoming",
            at("2026-11-20", 9),
            ReminderTimeEngine.nextDailyTrigger(daily, minutes, at("2026-11-20", 9), utc),
        )
    }

    @Test
    fun dailyTrigger_respectsEveryNDays() {
        val daily = daily("2026-11-20", everyN = 2)

        assertEquals(
            at("2026-11-22", 7),
            ReminderTimeEngine.nextDailyTrigger(daily, 7 * 60, at("2026-11-21", 12), utc),
        )
    }

    @Test
    fun dailyTrigger_neverStartsBeforeTheSeriesStart() {
        val daily = daily("2026-12-01")

        assertEquals(
            at("2026-12-01", 9),
            ReminderTimeEngine.nextDailyTrigger(daily, 9 * 60, at("2026-11-20", 8), utc),
        )
    }

    @Test
    fun weeklyTrigger_findsTheNextPickedWeekday() {
        val daily = daily(
            "2026-11-16", // Monday
            repeatType = RepeatType.WEEKLY,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )

        assertEquals(
            at("2026-11-18", 9),
            ReminderTimeEngine.nextDailyTrigger(daily, 9 * 60, at("2026-11-17", 12), utc),
        )
        assertEquals(
            "past Wednesday's time moves to Friday",
            at("2026-11-20", 9),
            ReminderTimeEngine.nextDailyTrigger(daily, 9 * 60, at("2026-11-18", 12), utc),
        )
    }

    @Test
    fun weeklyTrigger_honoursEveryNWeeks() {
        val daily = daily(
            "2026-11-16",
            repeatType = RepeatType.WEEKLY,
            everyN = 2,
            weekdays = setOf(DayOfWeek.MONDAY),
        )

        assertEquals(
            at("2026-11-30", 9),
            ReminderTimeEngine.nextDailyTrigger(daily, 9 * 60, at("2026-11-23", 12), utc),
        )
    }

    @Test
    fun monthlyTrigger_clampsToTheLastDayOfShortMonths() {
        val daily = daily(
            "2026-01-31",
            repeatType = RepeatType.MONTHLY,
        )

        assertEquals(
            at("2026-02-28", 9),
            ReminderTimeEngine.nextDailyTrigger(daily, 9 * 60, at("2026-02-01", 8), utc),
        )
        assertEquals(
            at("2026-03-31", 9),
            ReminderTimeEngine.nextDailyTrigger(daily, 9 * 60, at("2026-02-28", 12), utc),
        )
    }

    @Test
    fun monthlyTrigger_honoursEveryNMonths() {
        val daily = daily("2026-01-31", repeatType = RepeatType.MONTHLY, everyN = 2)

        assertEquals(
            at("2026-03-31", 9),
            ReminderTimeEngine.nextDailyTrigger(daily, 9 * 60, at("2026-02-01", 8), utc),
        )
    }

    @Test
    fun nextDailyTriggerAfter_isStrictlyLater() {
        val daily = daily("2026-11-20")
        val justFired = at("2026-11-20", 9)

        assertEquals(
            at("2026-11-21", 9),
            ReminderTimeEngine.nextDailyTriggerAfter(daily, 9 * 60, justFired, utc),
        )
    }

    @Test
    fun timezoneChange_keepsTheSameWallClockTime() {
        val day = LocalDate.parse("2026-11-20").toEpochDay()
        val dhaka = ZoneId.of("Asia/Dhaka")

        val utcNine = ReminderTimeEngine.atLocalTime(day, 9 * 60, utc)
        val dhakaNine = ReminderTimeEngine.atLocalTime(day, 9 * 60, dhaka)

        assertTrue("the same wall clock is a different instant", utcNine != dhakaNine)
        assertEquals(9 * 60, ReminderTimeEngine.minutesOfDay(dhakaNine, dhaka))
        assertEquals(day, ReminderTimeEngine.epochDayOf(dhakaNine, dhaka))
    }
}
