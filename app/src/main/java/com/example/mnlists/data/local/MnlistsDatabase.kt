package com.example.mnlists.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.mnlists.data.local.dao.DailyDao
import com.example.mnlists.data.local.dao.DailyOccurrenceDao
import com.example.mnlists.data.local.dao.HabitDao
import com.example.mnlists.data.local.dao.HabitLogDao
import com.example.mnlists.data.local.dao.ReminderDao
import com.example.mnlists.data.local.dao.SubtaskDao
import com.example.mnlists.data.local.dao.TodoDao
import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.DailyOccurrence
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.local.entity.HabitLog
import com.example.mnlists.data.local.entity.Reminder
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.local.entity.Todo

/**
 * The single local database (SPEC §7). No destructive fallbacks: on-device data
 * is the only copy, so schema changes must ship real migrations.
 */
@Database(
    entities = [
        Habit::class,
        HabitLog::class,
        Todo::class,
        Daily::class,
        DailyOccurrence::class,
        Subtask::class,
        Reminder::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MnlistsDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao

    abstract fun habitLogDao(): HabitLogDao

    abstract fun todoDao(): TodoDao

    abstract fun dailyDao(): DailyDao

    abstract fun dailyOccurrenceDao(): DailyOccurrenceDao

    abstract fun subtaskDao(): SubtaskDao

    abstract fun reminderDao(): ReminderDao

    companion object {
        const val DATABASE_NAME = "mnlists.db"
    }
}
