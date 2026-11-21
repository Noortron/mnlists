package com.example.mnlists.data.model

/** How often a habit's counter auto-resets (SPEC §4.1). */
enum class ResetSchedule {
    DAILY,
    WEEKLY,
    MONTHLY,

    /** Every N days/weeks/months, using `Habit.resetEveryN`. */
    EVERY_N,
    ;

    companion object {
        /** Unknown/stale preference values fall back to the app's original daily default. */
        fun fromStorage(value: String?): ResetSchedule =
            entries.firstOrNull { it.name == value } ?: DAILY
    }
}

/** Recurrence pattern of a Daily series (SPEC §4.3). */
enum class RepeatType {
    DAILY,
    WEEKLY,
    MONTHLY,
}

/** Polymorphic owner of a [com.example.mnlists.data.local.entity.Subtask]. */
enum class ParentType {
    TODO,
    DAILY_OCCURRENCE,
}

/** Polymorphic owner of a [com.example.mnlists.data.local.entity.Reminder]. */
enum class ReminderParentType {
    TODO,
    DAILY,
}
