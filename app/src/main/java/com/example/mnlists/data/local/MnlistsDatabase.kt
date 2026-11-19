package com.example.mnlists.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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

        /**
         * v2 makes `todos.dueDate` nullable (unplanned tasks). SQLite cannot
         * drop a NOT NULL constraint in place, so the table is recreated and
         * the rows are copied over; existing dates are preserved.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `todos_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`dueDate` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`completedAt` INTEGER, " +
                        "`sortOrder` REAL NOT NULL, " +
                        "`isCompleted` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO `todos_new` " +
                        "(`id`, `title`, `note`, `dueDate`, `createdAt`, `updatedAt`, " +
                        "`completedAt`, `sortOrder`, `isCompleted`) " +
                        "SELECT `id`, `title`, `note`, `dueDate`, `createdAt`, `updatedAt`, " +
                        "`completedAt`, `sortOrder`, `isCompleted` FROM `todos`",
                )
                db.execSQL("DROP TABLE `todos`")
                db.execSQL("ALTER TABLE `todos_new` RENAME TO `todos`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_isCompleted` ON `todos` (`isCompleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_dueDate` ON `todos` (`dueDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_sortOrder` ON `todos` (`sortOrder`)")
            }
        }
    }
}
