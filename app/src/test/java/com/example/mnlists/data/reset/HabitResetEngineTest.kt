package com.example.mnlists.data.reset

import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.model.ResetSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure unit tests for the reset-schedule math (no Android/Room). */
class HabitResetEngineTest {

    private val zone = ZoneId.of("UTC")

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private fun day(iso: String): Long = LocalDate.parse(iso).toEpochDay()

    private fun habit(
        schedule: ResetSchedule,
        lastResetAt: Long,
        count: Int = 0,
        everyN: Int = 1,
        weekday: DayOfWeek? = null,
    ) = Habit(
        id = 1L,
        title = "Test",
        resetSchedule = schedule,
        resetEveryN = everyN,
        resetWeekday = weekday,
        currentCount = count,
        lastResetAt = lastResetAt,
        createdAt = lastResetAt,
    )

    @Test
    fun daily_logsEveryElapsedDay() {
        val lastReset = at("2026-11-10T08:00:00Z")
        val due = HabitResetEngine.dueResets(
            habit(ResetSchedule.DAILY, lastReset, count = 4),
            now = at("2026-11-12T09:00:00Z"),
            zone = zone,
        )

        assertEquals(2, due.size)
        assertEquals(at("2026-11-11T00:00:00Z"), due[0].cycleEnd)
        assertEquals(at("2026-11-11T00:00:00Z"), due[1].cycleStart)
        assertEquals(at("2026-11-12T00:00:00Z"), due[1].cycleEnd)
        assertTrue(due.all { it.countAtEndOfCycle == 4 })
        assertEquals(day("2026-11-10"), due[0].logDate)
        assertEquals(day("2026-11-11"), due[1].logDate)
    }

    @Test
    fun daily_isNotDueWithinTheSameDay() {
        val lastReset = at("2026-11-10T08:00:00Z")
        val due = HabitResetEngine.dueResets(
            habit(ResetSchedule.DAILY, lastReset),
            now = at("2026-11-10T23:59:00Z"),
            zone = zone,
        )
        assertTrue(due.isEmpty())
    }

    @Test
    fun weekly_usesConfiguredWeekdayAnchor() {
        // 2026-11-09 is a Monday; reset day is Wednesday.
        val due = HabitResetEngine.dueResets(
            habit(
                schedule = ResetSchedule.WEEKLY,
                lastResetAt = at("2026-11-09T10:00:00Z"),
                weekday = DayOfWeek.WEDNESDAY,
            ),
            now = at("2026-11-18T12:00:00Z"),
            zone = zone,
        )

        assertEquals(2, due.size)
        assertEquals(at("2026-11-11T00:00:00Z"), due[0].cycleEnd)
        assertEquals(at("2026-11-18T00:00:00Z"), due[1].cycleEnd)
    }

    @Test
    fun weekly_withoutWeekday_resetsOnCalendarWeekStart() {
        val due = HabitResetEngine.dueResets(
            habit(ResetSchedule.WEEKLY, at("2026-11-09T10:00:00Z")),
            now = at("2026-11-16T05:00:00Z"),
            zone = zone,
        )

        assertEquals(1, due.size)
        assertEquals(at("2026-11-16T00:00:00Z"), due.first().cycleEnd)
        assertEquals(day("2026-11-09"), due.first().logDate)
    }

    @Test
    fun monthly_resetsAtTheStartOfTheNextMonth() {
        val due = HabitResetEngine.dueResets(
            habit(ResetSchedule.MONTHLY, at("2026-01-15T10:00:00Z")),
            now = at("2026-03-02T00:00:00Z"),
            zone = zone,
        )

        assertEquals(2, due.size)
        assertEquals(at("2026-02-01T00:00:00Z"), due[0].cycleEnd)
        assertEquals(at("2026-03-01T00:00:00Z"), due[1].cycleEnd)
    }

    @Test
    fun everyN_resetsAtMidnightAfterNSpan() {
        val due = HabitResetEngine.dueResets(
            habit(
                schedule = ResetSchedule.EVERY_N,
                lastResetAt = at("2026-11-10T14:00:00Z"),
                everyN = 3,
            ),
            now = at("2026-11-17T00:30:00Z"),
            zone = zone,
        )

        assertEquals(2, due.size)
        assertEquals(at("2026-11-13T00:00:00Z"), due[0].cycleEnd)
        assertEquals(at("2026-11-16T00:00:00Z"), due[1].cycleEnd)
    }

    @Test
    fun everyN_invalidSpanFallsBackToOneDay() {
        val due = HabitResetEngine.dueResets(
            habit(
                schedule = ResetSchedule.EVERY_N,
                lastResetAt = at("2026-11-10T14:00:00Z"),
                everyN = 0,
            ),
            now = at("2026-11-11T01:00:00Z"),
            zone = zone,
        )

        assertEquals(1, due.size)
        assertEquals(at("2026-11-11T00:00:00Z"), due.first().cycleEnd)
    }

    @Test
    fun clockMovedBackwards_neverResets() {
        val due = HabitResetEngine.dueResets(
            habit(ResetSchedule.DAILY, at("2026-11-12T08:00:00Z")),
            now = at("2026-11-10T08:00:00Z"),
            zone = zone,
        )
        assertTrue(due.isEmpty())
    }
}
