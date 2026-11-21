package com.example.mnlists.data.repository

import com.example.mnlists.data.local.dao.HabitDao
import com.example.mnlists.data.local.dao.HabitLogDao
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.local.entity.HabitLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Habit reads/writes. Counter changes respect the enabled directions; reset
 * scheduling and history writes are added in Step 5.
 */
@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
) {

    fun observeHabits(): Flow<List<Habit>> = habitDao.observeAll()

    fun observeHabit(habitId: Long): Flow<Habit?> = habitDao.observeById(habitId)

    suspend fun getHabit(habitId: Long): Habit? = habitDao.getById(habitId)

    suspend fun createHabit(habit: Habit): Long = habitDao.insert(habit)

    suspend fun updateHabit(habit: Habit) = habitDao.update(habit.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteHabit(habitId: Long) {
        // History is intentionally preserved (SPEC §9 default).
        habitDao.deleteById(habitId)
    }

    /**
     * Applies a +/− tap. Returns the resulting count, or `null` when the habit
     * does not exist. Disabled directions are ignored (count unchanged).
     */
    suspend fun adjustCount(habitId: Long, delta: Int): Int? {
        val habit = habitDao.getById(habitId) ?: return null
        if (delta > 0 && !habit.allowUp) return habit.currentCount
        if (delta < 0 && !habit.allowDown) return habit.currentCount
        val newCount = (habit.currentCount + delta).coerceAtLeast(0)
        habitDao.updateCount(habitId, newCount, System.currentTimeMillis())
        return newCount
    }

    fun observeLogs(habitId: Long): Flow<List<HabitLog>> = habitLogDao.observeForHabit(habitId)

    /** Every log, used by the Calendar to resolve a past day's habit counts. */
    fun observeAllLogs(): Flow<List<HabitLog>> = habitLogDao.observeAll()

    suspend fun getLogs(habitId: Long): List<HabitLog> = habitLogDao.getForHabit(habitId)

    /**
     * Calendar +/- on a past cycle. The day belongs to a completed cycle, so
     * only that log changes; the live counter is untouched. Disabled directions
     * and a missing habit/log are no-ops.
     */
    suspend fun adjustLogCount(logId: Long, delta: Int): Int? {
        val log = habitLogDao.getById(logId) ?: return null
        val habit = habitDao.getById(log.habitId) ?: return null
        if (delta > 0 && !habit.allowUp) return log.countAtEndOfCycle
        if (delta < 0 && !habit.allowDown) return log.countAtEndOfCycle
        val newCount = (log.countAtEndOfCycle + delta).coerceAtLeast(0)
        habitLogDao.updateCount(logId, newCount)
        return newCount
    }

    suspend fun appendLog(log: HabitLog): Long = habitLogDao.insert(log)

    suspend fun nextSortOrder(): Double = habitDao.maxSortOrder() + 1.0

    /** Persists a drag-reorder: the given ids become `0..n` in list order (Step 10). */
    suspend fun reorderHabits(orderedIds: List<Long>) {
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id ->
            habitDao.updateSortOrder(id = id, sortOrder = index.toDouble(), updatedAt = now)
        }
    }
}
