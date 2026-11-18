package com.example.mnlists.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.mnlists.data.local.entity.HabitLog
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitLogDao {

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY cycleEnd DESC, id DESC")
    fun observeForHabit(habitId: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY cycleEnd DESC, id DESC")
    suspend fun getForHabit(habitId: Long): List<HabitLog>

    @Query(
        "SELECT * FROM habit_logs WHERE habitId = :habitId AND date BETWEEN :fromDate AND :toDate " +
            "ORDER BY date ASC, id ASC",
    )
    suspend fun getForHabitBetween(habitId: Long, fromDate: Long, toDate: Long): List<HabitLog>

    @Query("SELECT * FROM habit_logs WHERE date BETWEEN :fromDate AND :toDate ORDER BY date ASC")
    suspend fun getBetween(fromDate: Long, toDate: Long): List<HabitLog>

    @Insert
    suspend fun insert(log: HabitLog): Long

    @Insert
    suspend fun insertAll(logs: List<HabitLog>)
}
