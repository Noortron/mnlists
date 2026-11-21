package com.example.mnlists.data.backup

import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.DailyOccurrence
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.local.entity.HabitLog
import com.example.mnlists.data.local.entity.Reminder
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.local.entity.Todo
import kotlinx.serialization.Serializable

/**
 * Wire format for a complete offline backup (SPEC §4.9). Enums and weekdays are
 * stored as plain strings/numbers so a backup stays readable and decoupled from
 * the Room type converters; [BackupValidator] checks every value before use.
 *
 * All fields have defaults so a structurally incomplete document decodes to a
 * payload the validator can reject with a friendly message instead of throwing
 * from the JSON library.
 */
@Serializable
data class BackupPayload(
    val format: String = "",
    val version: Int = 0,
    val exportedAt: Long = 0L,
    val habits: List<HabitBackup> = emptyList(),
    val habitLogs: List<HabitLogBackup> = emptyList(),
    val todos: List<TodoBackup> = emptyList(),
    val dailies: List<DailyBackup> = emptyList(),
    val dailyOccurrences: List<DailyOccurrenceBackup> = emptyList(),
    val subtasks: List<SubtaskBackup> = emptyList(),
    val reminders: List<ReminderBackup> = emptyList(),
)

@Serializable
data class HabitBackup(
    val id: Long = 0,
    val title: String = "",
    val note: String = "",
    val resetSchedule: String = "",
    val resetEveryN: Int = 0,
    val resetWeekday: Int? = null,
    val allowUp: Boolean = false,
    val allowDown: Boolean = false,
    val currentCount: Int = 0,
    val lastResetAt: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val sortOrder: Double = 0.0,
)

@Serializable
data class HabitLogBackup(
    val id: Long = 0,
    val habitId: Long = 0,
    val date: Long = 0,
    val countAtEndOfCycle: Int = 0,
    val cycleStart: Long = 0,
    val cycleEnd: Long = 0,
)

@Serializable
data class TodoBackup(
    val id: Long = 0,
    val title: String = "",
    val note: String = "",
    val dueDate: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val completedAt: Long? = null,
    val sortOrder: Double = 0.0,
    val isCompleted: Boolean = false,
)

@Serializable
data class DailyBackup(
    val id: Long = 0,
    val title: String = "",
    val note: String = "",
    val startDate: Long = 0,
    val repeatType: String = "",
    val everyN: Int = 0,
    val weekdays: List<Int> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class DailyOccurrenceBackup(
    val id: Long = 0,
    val dailyId: Long = 0,
    val date: Long = 0,
    val isCompleted: Boolean = false,
    val isSkipped: Boolean = false,
    val completedAt: Long? = null,
    val sortOrder: Double = 0.0,
)

@Serializable
data class SubtaskBackup(
    val id: Long = 0,
    val parentType: String = "",
    val parentId: Long = 0,
    val title: String = "",
    val note: String = "",
    val deadline: Long? = null,
    val isCompleted: Boolean = false,
    val sortOrder: Double = 0.0,
)

@Serializable
data class ReminderBackup(
    val id: Long = 0,
    val parentType: String = "",
    val parentId: Long = 0,
    val triggerAt: Long = 0,
    val notificationId: Int = 0,
    val timeOfDayMinutes: Int? = null,
)

/** Validated, type-safe content ready to be written to Room. */
data class ValidBackup(
    val habits: List<Habit> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
    val todos: List<Todo> = emptyList(),
    val dailies: List<Daily> = emptyList(),
    val dailyOccurrences: List<DailyOccurrence> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
) {
    val counts: BackupCounts
        get() = BackupCounts(
            habits = habits.size,
            habitLogs = habitLogs.size,
            todos = todos.size,
            dailies = dailies.size,
            occurrences = dailyOccurrences.size,
            subtasks = subtasks.size,
            reminders = reminders.size,
        )
}

data class BackupCounts(
    val habits: Int = 0,
    val habitLogs: Int = 0,
    val todos: Int = 0,
    val dailies: Int = 0,
    val occurrences: Int = 0,
    val subtasks: Int = 0,
    val reminders: Int = 0,
)

fun BackupPayload.counts(): BackupCounts = BackupCounts(
    habits = habits.size,
    habitLogs = habitLogs.size,
    todos = todos.size,
    dailies = dailies.size,
    occurrences = dailyOccurrences.size,
    subtasks = subtasks.size,
    reminders = reminders.size,
)

fun Habit.toBackup() = HabitBackup(
    id = id,
    title = title,
    note = note,
    resetSchedule = resetSchedule.name,
    resetEveryN = resetEveryN,
    resetWeekday = resetWeekday?.value,
    allowUp = allowUp,
    allowDown = allowDown,
    currentCount = currentCount,
    lastResetAt = lastResetAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sortOrder = sortOrder,
)

fun HabitLog.toBackup() = HabitLogBackup(
    id = id,
    habitId = habitId,
    date = date,
    countAtEndOfCycle = countAtEndOfCycle,
    cycleStart = cycleStart,
    cycleEnd = cycleEnd,
)

fun Todo.toBackup() = TodoBackup(
    id = id,
    title = title,
    note = note,
    dueDate = dueDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    sortOrder = sortOrder,
    isCompleted = isCompleted,
)

fun Daily.toBackup() = DailyBackup(
    id = id,
    title = title,
    note = note,
    startDate = startDate,
    repeatType = repeatType.name,
    everyN = everyN,
    weekdays = weekdays.map { it.value }.sorted(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun DailyOccurrence.toBackup() = DailyOccurrenceBackup(
    id = id,
    dailyId = dailyId,
    date = date,
    isCompleted = isCompleted,
    isSkipped = isSkipped,
    completedAt = completedAt,
    sortOrder = sortOrder,
)

fun Subtask.toBackup() = SubtaskBackup(
    id = id,
    parentType = parentType.name,
    parentId = parentId,
    title = title,
    note = note,
    deadline = deadline,
    isCompleted = isCompleted,
    sortOrder = sortOrder,
)

fun Reminder.toBackup() = ReminderBackup(
    id = id,
    parentType = parentType.name,
    parentId = parentId,
    triggerAt = triggerAt,
    notificationId = notificationId,
    timeOfDayMinutes = timeOfDayMinutes,
)

internal inline fun <reified T : Enum<T>> enumName(value: String): T? =
    runCatching { enumValueOf<T>(value) }.getOrNull()
