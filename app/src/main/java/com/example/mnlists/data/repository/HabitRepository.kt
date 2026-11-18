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

    suspend fun getLogs(habitId: Long): List<HabitLog> = habitLogDao.getForHabit(habitId)

    suspend fun appendLog(log: HabitLog): Long = habitLogDao.insert(log)
}
