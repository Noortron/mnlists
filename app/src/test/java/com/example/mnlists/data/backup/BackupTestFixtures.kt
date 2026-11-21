package com.example.mnlists.data.backup

/** One complete backup covering all seven Room tables. */
internal fun samplePayload(): BackupPayload = BackupPayload(
    format = BackupCodec.FORMAT,
    version = BackupCodec.VERSION,
    exportedAt = 1_700_000_000_000L,
    habits = listOf(
        HabitBackup(
            id = 1,
            title = "Read",
            note = "20 pages",
            resetSchedule = "EVERY_N",
            resetEveryN = 3,
            resetWeekday = 1,
            allowUp = true,
            currentCount = 4,
            lastResetAt = 1_000L,
            createdAt = 900L,
            updatedAt = 1_100L,
            sortOrder = 1.5,
        ),
    ),
    habitLogs = listOf(
        HabitLogBackup(
            id = 1,
            habitId = 1,
            date = 20_000L,
            countAtEndOfCycle = 7,
            cycleStart = 100L,
            cycleEnd = 1_000L,
        ),
    ),
    todos = listOf(
        TodoBackup(
            id = 1,
            title = "File taxes",
            note = "receipts",
            dueDate = 20_100L,
            createdAt = 900L,
            updatedAt = 1_100L,
            sortOrder = 0.0,
        ),
    ),
    dailies = listOf(
        DailyBackup(
            id = 1,
            title = "Water plants",
            startDate = 20_000L,
            repeatType = "WEEKLY",
            everyN = 2,
            weekdays = listOf(1, 4),
            createdAt = 900L,
            updatedAt = 1_100L,
        ),
    ),
    dailyOccurrences = listOf(
        DailyOccurrenceBackup(id = 1, dailyId = 1, date = 20_000L, sortOrder = 0.0),
    ),
    subtasks = listOf(
        SubtaskBackup(
            id = 1,
            parentType = "TODO",
            parentId = 1,
            title = "Collect receipts",
            deadline = 20_100L,
        ),
    ),
    reminders = listOf(
        ReminderBackup(
            id = 1,
            parentType = "DAILY",
            parentId = 1,
            triggerAt = 123_456L,
            notificationId = 1_001,
            timeOfDayMinutes = 480,
        ),
    ),
)

internal fun sampleJson(): String = BackupCodec.encode(samplePayload())

/** Applies a simple text substitution to the sample JSON for negative tests. */
internal fun mutatedJson(old: String, new: String): String =
    sampleJson().replace(old, new, ignoreCase = false)
