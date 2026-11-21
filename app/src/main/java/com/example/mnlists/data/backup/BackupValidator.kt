package com.example.mnlists.data.backup

import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.DailyOccurrence
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.local.entity.HabitLog
import com.example.mnlists.data.local.entity.Reminder
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.local.entity.Todo
import com.example.mnlists.data.model.ParentType
import com.example.mnlists.data.model.ReminderParentType
import com.example.mnlists.data.model.RepeatType
import com.example.mnlists.data.model.ResetSchedule
import java.time.DayOfWeek
import kotlinx.serialization.SerializationException

/** Outcome of checking an untrusted backup file before touching the database. */
sealed interface BackupValidationResult {
    data class Valid(val backup: ValidBackup) : BackupValidationResult
    data class Invalid(val reason: String) : BackupValidationResult
}

/**
 * Every field of an imported document is checked here: structure (the codec),
 * file identity/version, value ranges, enum/weekday names, duplicate ids and
 * duplicate per-series dates. Nothing is written to Room until this succeeds.
 *
 * This is deliberately tolerant of orphan rows (habit history whose habit was
 * deleted, for example): the app's own data model keeps those on purpose.
 */
object BackupValidator {

    private const val MAX_FILE_CHARS = 16 * 1024 * 1024
    private const val MAX_ROWS = 500_000

    fun validate(raw: String): BackupValidationResult = try {
        checkValid(raw.isNotBlank()) { "The backup file is empty." }
        checkValid(raw.length <= MAX_FILE_CHARS) { "The backup file is too large." }

        val payload = try {
            BackupCodec.decode(raw)
        } catch (e: SerializationException) {
            fail("This file is not a valid MNlists backup.")
        } catch (e: IllegalArgumentException) {
            fail("This file is not a valid MNlists backup.")
        }

        checkValid(payload.format == BackupCodec.FORMAT) { "This file is not an MNlists backup." }
        checkValid(payload.version == BackupCodec.VERSION) {
            "This backup was made by a different version of MNlists (version ${payload.version})."
        }
        val counts = payload.counts()
        checkValid(
            counts.habits + counts.habitLogs + counts.todos + counts.dailies +
                counts.occurrences + counts.subtasks + counts.reminders <= MAX_ROWS,
        ) { "The backup contains too many items." }

        val habits = payload.habits.mapIndexed { index, item -> item.toEntity(index) }
        val habitLogs = payload.habitLogs.mapIndexed { index, item -> item.toEntity(index) }
        val todos = payload.todos.mapIndexed { index, item -> item.toEntity(index) }
        val dailies = payload.dailies.mapIndexed { index, item -> item.toEntity(index) }
        val occurrences = payload.dailyOccurrences.mapIndexed { index, item -> item.toEntity(index) }
        val subtasks = payload.subtasks.mapIndexed { index, item -> item.toEntity(index) }
        val reminders = payload.reminders.mapIndexed { index, item -> item.toEntity(index) }

        requireUniqueIds(habits.map { it.id }, "habit")
        requireUniqueIds(habitLogs.map { it.id }, "habit log")
        requireUniqueIds(todos.map { it.id }, "to-do")
        requireUniqueIds(dailies.map { it.id }, "daily")
        requireUniqueIds(occurrences.map { it.id }, "daily occurrence")
        requireUniqueIds(subtasks.map { it.id }, "subtask")
        requireUniqueIds(reminders.map { it.id }, "reminder")
        requireUniqueIds(reminders.map { it.notificationId.toLong() }, "notification")

        val seriesDays = HashSet<Pair<Long, Long>>()
        occurrences.forEach { occurrence ->
            checkValid(seriesDays.add(occurrence.dailyId to occurrence.date)) {
                "The backup has two occurrences of one daily on the same date."
            }
        }

        BackupValidationResult.Valid(
            ValidBackup(habits, habitLogs, todos, dailies, occurrences, subtasks, reminders),
        )
    } catch (e: InvalidBackupException) {
        BackupValidationResult.Invalid(e.reason)
    } catch (e: Exception) {
        BackupValidationResult.Invalid("This file could not be read as a backup.")
    }

    private class InvalidBackupException(val reason: String) : Exception(reason)

    private fun fail(reason: String): Nothing = throw InvalidBackupException(reason)

    private fun checkValid(condition: Boolean, message: () -> Any) {
        if (!condition) fail(message().toString())
    }

    private fun requireUniqueIds(ids: List<Long>, label: String) {
        val seen = HashSet<Long>()
        ids.forEach { id -> checkValid(seen.add(id)) { "The backup has two $label rows with id $id." } }
    }

    private fun checkId(id: Long, label: String, index: Int) {
        checkValid(id > 0) { "$label #${index + 1} has an invalid id." }
    }

    private fun checkTitle(title: String, label: String, index: Int) {
        checkValid(title.isNotBlank()) { "$label #${index + 1} needs a title." }
    }

    private fun checkOrder(value: Double, label: String, index: Int) {
        checkValid(value.isFinite()) { "$label #${index + 1} has an invalid order value." }
    }

    private fun checkTimestamp(value: Long, label: String, index: Int) {
        checkValid(value >= 0) { "$label #${index + 1} has an invalid date." }
    }

    private fun checkOptionalTimestamp(value: Long?, label: String, index: Int) {
        if (value != null) checkTimestamp(value, label, index)
    }

    private fun HabitBackup.toEntity(index: Int): Habit {
        checkId(id, "Habit", index)
        checkTitle(title, "Habit", index)
        checkOrder(sortOrder, "Habit", index)
        checkValid(resetEveryN >= 1) { "Habit #${index + 1} has an invalid reset interval." }
        checkValid(currentCount >= 0) { "Habit #${index + 1} has an invalid count." }
        checkValid(allowUp || allowDown) { "Habit #${index + 1} has no counter buttons." }
        checkValid(resetWeekday == null || resetWeekday in 1..7) {
            "Habit #${index + 1} has an invalid reset weekday."
        }
        checkTimestamp(lastResetAt, "Habit", index)
        checkTimestamp(createdAt, "Habit", index)
        checkTimestamp(updatedAt, "Habit", index)
        val schedule = enumName<ResetSchedule>(resetSchedule)
            ?: fail("Habit #${index + 1} has an unknown reset schedule.")
        return Habit(
            id = id,
            title = title,
            note = note,
            resetSchedule = schedule,
            resetEveryN = resetEveryN,
            resetWeekday = resetWeekday?.let(DayOfWeek::of),
            allowUp = allowUp,
            allowDown = allowDown,
            currentCount = currentCount,
            lastResetAt = lastResetAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            sortOrder = sortOrder,
        )
    }

    private fun HabitLogBackup.toEntity(index: Int): HabitLog {
        checkId(id, "Habit log", index)
        checkValid(habitId > 0) { "Habit log #${index + 1} has an invalid habit id." }
        checkValid(countAtEndOfCycle >= 0) { "Habit log #${index + 1} has an invalid count." }
        checkTimestamp(cycleStart, "Habit log", index)
        checkTimestamp(cycleEnd, "Habit log", index)
        checkValid(cycleStart <= cycleEnd) { "Habit log #${index + 1} has reversed cycle dates." }
        return HabitLog(id, habitId, date, countAtEndOfCycle, cycleStart, cycleEnd)
    }

    private fun TodoBackup.toEntity(index: Int): Todo {
        checkId(id, "To-do", index)
        checkTitle(title, "To-do", index)
        checkOrder(sortOrder, "To-do", index)
        checkTimestamp(createdAt, "To-do", index)
        checkTimestamp(updatedAt, "To-do", index)
        checkOptionalTimestamp(completedAt, "To-do completion", index)
        return Todo(id, title, note, dueDate, createdAt, updatedAt, completedAt, sortOrder, isCompleted)
    }

    private fun DailyBackup.toEntity(index: Int): Daily {
        checkId(id, "Daily", index)
        checkTitle(title, "Daily", index)
        checkValid(everyN >= 1) { "Daily #${index + 1} has an invalid interval." }
        checkValid(weekdays.all { it in 1..7 }) { "Daily #${index + 1} has an invalid weekday." }
        checkValid(weekdays.toSet().size == weekdays.size) { "Daily #${index + 1} repeats a weekday." }
        checkTimestamp(createdAt, "Daily", index)
        checkTimestamp(updatedAt, "Daily", index)
        val repeat = enumName<RepeatType>(repeatType)
            ?: fail("Daily #${index + 1} has an unknown repeat type.")
        return Daily(
            id = id,
            title = title,
            note = note,
            startDate = startDate,
            repeatType = repeat,
            everyN = everyN,
            weekdays = weekdays.map(DayOfWeek::of).toSet(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun DailyOccurrenceBackup.toEntity(index: Int): DailyOccurrence {
        checkId(id, "Daily occurrence", index)
        checkValid(dailyId > 0) { "Daily occurrence #${index + 1} has an invalid series id." }
        checkOrder(sortOrder, "Daily occurrence", index)
        checkOptionalTimestamp(completedAt, "Daily occurrence completion", index)
        return DailyOccurrence(id, dailyId, date, isCompleted, isSkipped, completedAt, sortOrder)
    }

    private fun SubtaskBackup.toEntity(index: Int): Subtask {
        checkId(id, "Subtask", index)
        checkValid(parentId > 0) { "Subtask #${index + 1} has an invalid parent id." }
        checkTitle(title, "Subtask", index)
        checkOrder(sortOrder, "Subtask", index)
        val type = enumName<ParentType>(parentType)
            ?: fail("Subtask #${index + 1} has an unknown parent type.")
        return Subtask(id, type, parentId, title, note, deadline, isCompleted, sortOrder)
    }

    private fun ReminderBackup.toEntity(index: Int): Reminder {
        checkId(id, "Reminder", index)
        checkValid(parentId > 0) { "Reminder #${index + 1} has an invalid parent id." }
        checkValid(notificationId > 0) { "Reminder #${index + 1} has an invalid notification id." }
        checkValid(timeOfDayMinutes == null || timeOfDayMinutes in 0..1439) {
            "Reminder #${index + 1} has an invalid time of day."
        }
        val type = enumName<ReminderParentType>(parentType)
            ?: fail("Reminder #${index + 1} has an unknown parent type.")
        return Reminder(id, type, parentId, triggerAt, notificationId, timeOfDayMinutes)
    }
}
