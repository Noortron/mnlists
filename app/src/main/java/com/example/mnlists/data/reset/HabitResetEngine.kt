package com.example.mnlists.data.reset

import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.model.ResetSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** One completed habit cycle that must be logged before the counter resets. */
data class DueHabitReset(
    /** Epoch millis when the cycle started (previous reset or habit creation). */
    val cycleStart: Long,
    /** Epoch millis of the boundary that ends the cycle. */
    val cycleEnd: Long,
    /** Cycle date as an epoch day in the calculation zone. */
    val logDate: Long,
    val countAtEndOfCycle: Int,
)

/**
 * Pure reset-schedule calculator (SPEC §4.1/§8, Step 5).
 *
 * Cycles are calendar based in the device time zone:
 * - [ResetSchedule.DAILY] — one cycle per local day.
 * - [ResetSchedule.WEEKLY] — ends at the start of [Habit.resetWeekday] when set,
 *   otherwise at the start of the next calendar week (Monday).
 * - [ResetSchedule.MONTHLY] — ends at the start of the next month.
 * - [ResetSchedule.EVERY_N] — ends at midnight N days after the cycle started.
 *
 * [dueResets] walks forward from `lastResetAt` and returns **every** cycle that
 * ended at or before `now`, so a habit that has not been opened for days is
 * caught up (one history entry per elapsed cycle, each carrying the counter
 * value that was live at the time).
 */
object HabitResetEngine {

    /** SPEC §9 default start value after a reset. */
    const val DEFAULT_START_VALUE = 0

    /** Defensive cap so a very old timestamp cannot loop forever. */
    const val MAX_CATCH_UP_CYCLES = 400

    fun dueResets(
        habit: Habit,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DueHabitReset> {
        // Clock moved backwards: never reset "into the future".
        if (now <= habit.lastResetAt) return emptyList()

        val resets = mutableListOf<DueHabitReset>()
        var cycleStart = habit.lastResetAt
        var guard = 0

        while (guard < MAX_CATCH_UP_CYCLES) {
            guard++
            val cycleEnd = nextBoundary(habit, cycleStart, zone)
            if (cycleEnd > now) break
            resets += DueHabitReset(
                cycleStart = cycleStart,
                cycleEnd = cycleEnd,
                logDate = epochDay(cycleStart, zone),
                countAtEndOfCycle = habit.currentCount,
            )
            cycleStart = cycleEnd
        }
        return resets
    }

    /** Clock time of the first boundary strictly after [cycleStart]. */
    fun nextBoundary(
        habit: Habit,
        cycleStart: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val startDate = Instant.ofEpochMilli(cycleStart).atZone(zone).toLocalDate()

        val boundaryDate = when (habit.resetSchedule) {
            ResetSchedule.DAILY -> startDate.plusDays(1)
            ResetSchedule.WEEKLY -> {
                val anchor = habit.resetWeekday ?: DayOfWeek.MONDAY
                startDate.with(TemporalAdjusters.next(anchor))
            }
            ResetSchedule.MONTHLY -> YearMonth.from(startDate).plusMonths(1).atDay(1)
            ResetSchedule.EVERY_N -> startDate.plusDays(habit.resetEveryN.coerceAtLeast(1).toLong())
        }

        return boundaryDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun epochDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    /** Local date for an epoch day; handy for callers showing history. */
    fun localDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
}
