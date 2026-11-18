package com.example.mnlists.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.mnlists.data.local.entity.Habit
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeById(id: Long): Flow<Habit?>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): Habit?

    @Query("SELECT * FROM habits ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<Habit>

    @Insert
    suspend fun insert(habit: Habit): Long

    @Update
    suspend fun update(habit: Habit)

    @Delete
    suspend fun delete(habit: Habit)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE habits SET currentCount = :count, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCount(id: Long, count: Int, updatedAt: Long)

    /** Applies an auto-reset: zeroes the counter and moves the cycle start. */
    @Query(
        "UPDATE habits SET currentCount = :count, lastResetAt = :resetAt, updatedAt = :resetAt " +
            "WHERE id = :id",
    )
    suspend fun applyReset(id: Long, count: Int, resetAt: Long)
}
