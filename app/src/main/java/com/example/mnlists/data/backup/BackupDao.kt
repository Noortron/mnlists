package com.example.mnlists.data.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.DailyOccurrence
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.local.entity.HabitLog
import com.example.mnlists.data.local.entity.Reminder
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.local.entity.Todo

/**
 * Bulk access used only by backup & restore. Keeping it in its own DAO avoids
 * widening the feature DAOs with methods the rest of the app never calls.
 */
@Dao
interface BackupDao {

    @Query("SELECT * FROM habits ORDER BY id ASC")
    suspend fun habits(): List<Habit>

    @Query("SELECT * FROM habit_logs ORDER BY id ASC")
    suspend fun habitLogs(): List<HabitLog>

    @Query("SELECT * FROM todos ORDER BY id ASC")
    suspend fun todos(): List<Todo>

    @Query("SELECT * FROM dailies ORDER BY id ASC")
    suspend fun dailies(): List<Daily>

    @Query("SELECT * FROM daily_occurrences ORDER BY id ASC")
    suspend fun dailyOccurrences(): List<DailyOccurrence>

    @Query("SELECT * FROM subtasks ORDER BY id ASC")
    suspend fun subtasks(): List<Subtask>

    @Query("SELECT * FROM reminders ORDER BY id ASC")
    suspend fun reminders(): List<Reminder>

    @Query("DELETE FROM habits")
    suspend fun deleteHabits()

    @Query("DELETE FROM habit_logs")
    suspend fun deleteHabitLogs()

    @Query("DELETE FROM todos")
    suspend fun deleteTodos()

    @Query("DELETE FROM dailies")
    suspend fun deleteDailies()

    @Query("DELETE FROM daily_occurrences")
    suspend fun deleteOccurrences()

    @Query("DELETE FROM subtasks")
    suspend fun deleteSubtasks()

    @Query("DELETE FROM reminders")
    suspend fun deleteReminders()

    @Insert
    suspend fun insertHabits(items: List<Habit>)

    @Insert
    suspend fun insertHabitLogs(items: List<HabitLog>)

    @Insert
    suspend fun insertTodos(items: List<Todo>)

    @Insert
    suspend fun insertDailies(items: List<Daily>)

    @Insert
    suspend fun insertOccurrences(items: List<DailyOccurrence>)

    @Insert
    suspend fun insertSubtasks(items: List<Subtask>)

    @Insert
    suspend fun insertReminders(items: List<Reminder>)
}
