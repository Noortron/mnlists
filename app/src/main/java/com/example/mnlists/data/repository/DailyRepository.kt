package com.example.mnlists.data.repository

import androidx.room.withTransaction
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.dao.DailyDao
import com.example.mnlists.data.local.dao.DailyOccurrenceDao
import com.example.mnlists.data.local.dao.ReminderDao
import com.example.mnlists.data.local.dao.SubtaskDao
import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.DailyOccurrence
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.model.ParentType
import com.example.mnlists.data.model.ReminderParentType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Daily series, their occurrences and occurrence subtasks.
 *
 * Occurrence *generation* (today + missed dates) lives in Step 8; this
 * repository exposes the primitives it needs.
 */
@Singleton
class DailyRepository @Inject constructor(
    private val database: MnlistsDatabase,
    private val dailyDao: DailyDao,
    private val occurrenceDao: DailyOccurrenceDao,
    private val subtaskDao: SubtaskDao,
    private val reminderDao: ReminderDao,
) {

    fun observeDailies(): Flow<List<Daily>> = dailyDao.observeAll()

    fun observeDaily(dailyId: Long): Flow<Daily?> = dailyDao.observeById(dailyId)

    suspend fun getDaily(dailyId: Long): Daily? = dailyDao.getById(dailyId)

    suspend fun createDaily(daily: Daily): Long = dailyDao.insert(daily)

    suspend fun updateDaily(daily: Daily) =
        dailyDao.update(daily.copy(updatedAt = System.currentTimeMillis()))

    /**
     * Deletes the series, its pending reminders and all future occurrences.
     * Past occurrences (and their subtasks) survive as history (SPEC §8/§9).
     */
    suspend fun deleteSeries(dailyId: Long, today: Long) {
        database.withTransaction {
            reminderDao.deleteForParent(ReminderParentType.DAILY, dailyId)
            occurrenceDao.deleteFutureForDaily(dailyId, afterDate = today)
            dailyDao.deleteById(dailyId)
        }
    }

    fun observeOccurrencesForDate(date: Long): Flow<List<DailyOccurrence>> =
        occurrenceDao.observeForDate(date)

    suspend fun getOccurrencesForDate(date: Long): List<DailyOccurrence> =
        occurrenceDao.getForDate(date)

    fun observeOccurrencesForDaily(dailyId: Long): Flow<List<DailyOccurrence>> =
        occurrenceDao.observeForDaily(dailyId)

    suspend fun getOccurrenceForDate(dailyId: Long, date: Long): DailyOccurrence? =
        occurrenceDao.getForDailyAndDate(dailyId, date)

    suspend fun latestOccurrenceDate(dailyId: Long): Long? = occurrenceDao.latestDateFor(dailyId)

    /** Idempotent: the unique (dailyId, date) index guarantees one row per day. */
    suspend fun addOccurrence(dailyId: Long, date: Long, sortOrder: Double = 0.0): Long =
        occurrenceDao.insert(
            DailyOccurrence(dailyId = dailyId, date = date, sortOrder = sortOrder),
        )

    /** Returns the existing occurrence for that date or creates it. */
    suspend fun ensureOccurrence(dailyId: Long, date: Long, sortOrder: Double = 0.0): DailyOccurrence? {
        val existing = occurrenceDao.getForDailyAndDate(dailyId, date)
        if (existing != null) return existing
        val id = occurrenceDao.insert(DailyOccurrence(dailyId = dailyId, date = date, sortOrder = sortOrder))
        return if (id > 0) occurrenceDao.getForDailyAndDate(dailyId, date) else null
    }

    /** Checking an occurrence cascades to its subtasks (SPEC §4.3 → §8). */
    suspend fun setOccurrenceCompleted(occurrenceId: Long, completed: Boolean) {
        database.withTransaction {
            occurrenceDao.setCompleted(
                id = occurrenceId,
                completed = completed,
                completedAt = if (completed) System.currentTimeMillis() else null,
            )
            subtaskDao.setCompletedForParent(ParentType.DAILY_OCCURRENCE, occurrenceId, completed)
        }
    }

    /** Skip affects this occurrence only; the series stays untouched. */
    suspend fun setOccurrenceSkipped(occurrenceId: Long, skipped: Boolean) =
        occurrenceDao.setSkipped(occurrenceId, skipped)

    fun observeSubtasks(occurrenceId: Long): Flow<List<Subtask>> =
        subtaskDao.observeForParent(ParentType.DAILY_OCCURRENCE, occurrenceId)

    suspend fun getSubtasks(occurrenceId: Long): List<Subtask> =
        subtaskDao.getForParent(ParentType.DAILY_OCCURRENCE, occurrenceId)

    suspend fun addSubtask(subtask: Subtask): Long {
        val ordered = if (subtask.sortOrder > 0.0) {
            subtask
        } else {
            subtask.copy(
                sortOrder = subtaskDao.maxSortOrder(ParentType.DAILY_OCCURRENCE, subtask.parentId) + 1.0,
            )
        }
        return subtaskDao.insert(ordered)
    }

    suspend fun updateSubtask(subtask: Subtask) = subtaskDao.update(subtask)

    suspend fun deleteSubtask(subtaskId: Long) = subtaskDao.deleteById(subtaskId)

    /** All subtasks checked → occurrence checked; unchecking one → unchecked. */
    suspend fun setSubtaskCompleted(subtaskId: Long, completed: Boolean) {
        database.withTransaction {
            val subtask = subtaskDao.getById(subtaskId) ?: return@withTransaction
            subtaskDao.setCompleted(subtaskId, completed)
            if (subtask.parentType != ParentType.DAILY_OCCURRENCE) return@withTransaction

            val total = subtaskDao.countForParent(ParentType.DAILY_OCCURRENCE, subtask.parentId)
            val remaining = subtaskDao.countIncompleteForParent(ParentType.DAILY_OCCURRENCE, subtask.parentId)
            val allDone = total > 0 && remaining == 0
            occurrenceDao.setCompleted(
                id = subtask.parentId,
                completed = allDone,
                completedAt = if (allDone) System.currentTimeMillis() else null,
            )
        }
    }
}
