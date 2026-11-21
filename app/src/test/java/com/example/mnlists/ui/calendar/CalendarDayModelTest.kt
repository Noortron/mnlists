package com.example.mnlists.ui.calendar

import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.DailyOccurrence
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.local.entity.HabitLog
import com.example.mnlists.data.local.entity.Todo
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-day filtering rules for the Calendar (SPEC §4.4, Step 12). Pure JVM: no
 * Room, no Compose, so the placement rules are pinned down independently of UI.
 */
class CalendarDayModelTest {

    private val zone = ZoneOffset.UTC

    private fun day(date: String): Long = LocalDate.parse(date).toEpochDay()

    private fun millis(date: String): Long =
        LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()

    private val today = day("2026-11-21")

    private fun todo(
        id: Long,
        title: String,
        dueDate: Long? = null,
        completed: Boolean = false,
        completedAt: Long? = null,
    ) = Todo(
        id = id,
        title = title,
        dueDate = dueDate,
        isCompleted = completed,
        completedAt = completedAt,
    )

    private fun occurrence(
        id: Long,
        dailyId: Long,
        date: Long,
        completed: Boolean = false,
        skipped: Boolean = false,
    ) = DailyOccurrence(
        id = id,
        dailyId = dailyId,
        date = date,
        isCompleted = completed,
        isSkipped = skipped,
    )

    @Test
    fun tasksForDate_keepsOnlyTodosDueThatDayAndOccurrencesDatedThatDay() {
        val date = day("2026-11-20")
        val todos = listOf(
            todo(1, "Due today", dueDate = date),
            todo(2, "Due tomorrow", dueDate = date + 1),
        )
        val dailies = listOf(Daily(id = 10, title = "Morning pages", startDate = date - 5))
        val occurrences = listOf(
            occurrence(100, dailyId = 10, date = date, completed = true),
            occurrence(101, dailyId = 10, date = date + 1),
        )

        val tasks = CalendarDayModel.tasksForDate(todos, occurrences, dailies, date, zone)

        assertEquals(listOf("Due today", "Morning pages"), tasks.map { it.title })
        assertEquals(CalendarTaskKind.TODO, tasks[0].kind)
        assertEquals(1L, tasks[0].parentId)
        assertFalse(tasks[0].isCompleted)
        assertEquals(CalendarTaskKind.DAILY, tasks[1].kind)
        assertEquals(100L, tasks[1].parentId)
        assertTrue(tasks[1].isCompleted)
    }

    @Test
    fun tasksForDate_ignoresOrphanedOccurrencesWhoseSeriesIsGone() {
        val date = day("2026-11-20")
        val tasks = CalendarDayModel.tasksForDate(
            todos = emptyList(),
            occurrences = listOf(occurrence(100, dailyId = 99, date = date, completed = true)),
            dailies = emptyList(),
            date = date,
            zone = zone,
        )

        assertTrue("history of a deleted series is not rendered", tasks.isEmpty())
    }

    @Test
    fun tasksForDate_putsCompletedUndatedTodosOnTheirCompletionDay() {
        val completedOn = day("2026-11-18")
        val todos = listOf(
            todo(1, "Someday done", completed = true, completedAt = millis("2026-11-18") + 3_600_000),
            todo(2, "Still unplanned"),
        )
        val dailies = emptyList<Daily>()
        val occurrences = emptyList<DailyOccurrence>()

        val onCompletionDay = CalendarDayModel.tasksForDate(todos, occurrences, dailies, completedOn, zone)
        val onAnotherDay = CalendarDayModel.tasksForDate(todos, occurrences, dailies, completedOn + 1, zone)

        assertEquals(listOf("Someday done"), onCompletionDay.map { it.title })
        assertTrue(onCompletionDay.single().isCompleted)
        assertTrue("open undated tasks belong to no day", onAnotherDay.isEmpty())
        assertNull("no due date means no completion fallback", CalendarDayModel.todoDate(todos[1], zone))
    }

    @Test
    fun tasksForDate_carriesSkippedStateForSkippedOccurrences() {
        val date = day("2026-11-20")
        val tasks = CalendarDayModel.tasksForDate(
            todos = emptyList(),
            occurrences = listOf(occurrence(100, dailyId = 10, date = date, skipped = true)),
            dailies = listOf(Daily(id = 10, title = "Stretch", startDate = date)),
            date = date,
            zone = zone,
        )

        assertTrue(tasks.single().isSkipped)
        assertFalse(tasks.single().isCompleted)
    }

    @Test
    fun habitsForDate_usesTheLiveCounterInsideTheCurrentCycle() {
        val habit = Habit(
            id = 1,
            title = "Read",
            currentCount = 4,
            lastResetAt = millis("2026-11-21"),
            createdAt = millis("2026-11-01"),
        )

        val item = CalendarDayModel.habitsForDate(
            habits = listOf(habit),
            logs = emptyList(),
            date = today,
            today = today,
            zone = zone,
        ).single()

        assertEquals(4, item.count)
        assertTrue(item.isAdjustable)
        assertTrue(item.adjustsCurrentCount)
        assertNull(item.logId)
    }

    @Test
    fun habitsForDate_usesTheLoggedCountForPastCycles() {
        val habit = Habit(
            id = 1,
            title = "Read",
            currentCount = 1,
            lastResetAt = millis("2026-11-20"),
            createdAt = millis("2026-11-01"),
        )
        val oldLog = HabitLog(
            id = 50,
            habitId = 1,
            date = day("2026-11-18"),
            countAtEndOfCycle = 7,
            cycleStart = millis("2026-11-18"),
            cycleEnd = millis("2026-11-19"),
        )

        val item = CalendarDayModel.habitsForDate(
            habits = listOf(habit),
            logs = listOf(oldLog),
            date = day("2026-11-18"),
            today = today,
            zone = zone,
        ).single()

        assertEquals(7, item.count)
        assertEquals(50L, item.logId)
        assertFalse(item.adjustsCurrentCount)
        assertTrue(item.isAdjustable)
    }

    @Test
    fun habitsForDate_returnsNoRowsForFutureDatesOrBeforeCreation() {
        val habit = Habit(
            id = 1,
            title = "Read",
            currentCount = 2,
            lastResetAt = millis("2026-11-20"),
            createdAt = millis("2026-11-10"),
        )
        val logs = emptyList<HabitLog>()

        assertTrue(
            CalendarDayModel.habitsForDate(listOf(habit), logs, today + 1, today, zone).isEmpty(),
        )
        assertTrue(
            CalendarDayModel.habitsForDate(listOf(habit), logs, day("2026-11-09"), today, zone).isEmpty(),
        )
    }

    @Test
    fun logForDate_prefersTheLatestCycleContainingTheDay() {
        val habit = Habit(id = 1, title = "Read", createdAt = 0)
        val earlier = HabitLog(
            id = 1,
            habitId = 1,
            date = day("2026-11-10"),
            countAtEndOfCycle = 2,
            cycleStart = millis("2026-11-09"),
            cycleEnd = millis("2026-11-11"),
        )
        val later = earlier.copy(
            id = 2,
            countAtEndOfCycle = 5,
            cycleStart = millis("2026-11-10"),
            cycleEnd = millis("2026-11-12"),
        )

        val match = CalendarDayModel.logForDate(habit, listOf(earlier, later), day("2026-11-10"), zone)

        assertEquals(2L, match?.id)
    }
}
