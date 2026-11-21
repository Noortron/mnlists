package com.example.mnlists.ui.calendar

import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.DailyOccurrence
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.local.entity.HabitLog
import com.example.mnlists.data.local.entity.Todo
import java.time.ZoneId

/** What kind of task a Calendar row represents. */
enum class CalendarTaskKind {
    TODO,
    DAILY,
}

/**
 * One task row in the Calendar day list. The Calendar is read/toggle only, so
 * the row carries exactly enough to render it and flip completion.
 */
data class CalendarTask(
    val kind: CalendarTaskKind,
    /** To-Do id or DailyOccurrence id, depending on [kind]. */
    val parentId: Long,
    val title: String,
    val note: String,
    val isCompleted: Boolean,
    val isSkipped: Boolean = false,
) {
    val key: String = "${kind.name.lowercase()}:$parentId"
}

/**
 * One habit row for the selected day.
 *
 * [logId] is set when the day belongs to a completed (logged) cycle; in that
 * case [adjustsCurrentCount] is false and +/- edits that history row. Otherwise
 * the day is inside the live cycle and +/- changes the live counter.
 */
data class CalendarHabit(
    val habit: Habit,
    val count: Int,
    val isAdjustable: Boolean,
    val logId: Long? = null,
    val adjustsCurrentCount: Boolean = false,
)

/**
 * Pure per-day filtering shared by the Calendar ViewModel and its tests.
 *
 * To-Do placement: a To-Do belongs to its due date. An **undated** To-Do (the
 * app allows unplanned tasks) belongs to the day it was completed, so completed
 * history is still reachable; an open undated task appears on no day.
 *
 * Daily placement: the occurrence row's own `date`.
 */
object CalendarDayModel {

    fun tasksForDate(
        todos: List<Todo>,
        occurrences: List<DailyOccurrence>,
        dailies: List<Daily>,
        date: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CalendarTask> {
        val todoTasks = todos
            .filter { todoDate(it, zone) == date }
            .map {
                CalendarTask(
                    kind = CalendarTaskKind.TODO,
                    parentId = it.id,
                    title = it.title,
                    note = it.note,
                    isCompleted = it.isCompleted,
                )
            }

        val seriesById = dailies.associateBy { it.id }
        val occurrenceTasks = occurrences
            .filter { it.date == date }
            .mapNotNull { occurrence ->
                seriesById[occurrence.dailyId]?.let { daily ->
                    CalendarTask(
                        kind = CalendarTaskKind.DAILY,
                        parentId = occurrence.id,
                        title = daily.title,
                        note = daily.note,
                        isCompleted = occurrence.isCompleted,
                        isSkipped = occurrence.isSkipped,
                    )
                }
            }

        return todoTasks + occurrenceTasks
    }

    /**
     * The day a To-Do appears on: its due date, or its completion date when it
     * has no deadline. Open undated tasks deliberately have no Calendar day.
     */
    fun todoDate(todo: Todo, zone: ZoneId = ZoneId.systemDefault()): Long? =
        todo.dueDate ?: todo.completedAt?.let { CalendarDates.epochDayOf(it, zone) }

    /**
     * Habits that existed on [date], with that day's count. Future dates have no
     * recorded count and therefore no habit rows (tasks can still be planned).
     */
    fun habitsForDate(
        habits: List<Habit>,
        logs: List<HabitLog>,
        date: Long,
        today: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CalendarHabit> {
        if (date > today) return emptyList()

        return habits
            .filter { CalendarDates.epochDayOf(it.createdAt, zone) <= date }
            .map { habit ->
                val currentCycleStart = CalendarDates.epochDayOf(habit.lastResetAt, zone)
                if (date >= currentCycleStart) {
                    CalendarHabit(
                        habit = habit,
                        count = habit.currentCount,
                        isAdjustable = true,
                        adjustsCurrentCount = true,
                    )
                } else {
                    val log = logForDate(habit, logs, date, zone)
                    CalendarHabit(
                        habit = habit,
                        count = log?.countAtEndOfCycle ?: 0,
                        isAdjustable = log != null,
                        logId = log?.id,
                        adjustsCurrentCount = false,
                    )
                }
            }
    }

    /** Latest logged cycle whose [start, end) range contains [date]. */
    fun logForDate(
        habit: Habit,
        logs: List<HabitLog>,
        date: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): HabitLog? = logs
        .asSequence()
        .filter { it.habitId == habit.id }
        .filter { log ->
            val start = CalendarDates.epochDayOf(log.cycleStart, zone)
            val endExclusive = CalendarDates.epochDayOf(log.cycleEnd, zone)
            date >= start && date < endExclusive
        }
        .maxByOrNull { it.cycleStart }
}
