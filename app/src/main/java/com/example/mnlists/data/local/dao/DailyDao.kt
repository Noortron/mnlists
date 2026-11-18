package com.example.mnlists.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.mnlists.data.local.entity.Daily
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyDao {

    @Query("SELECT * FROM dailies ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<Daily>>

    @Query("SELECT * FROM dailies WHERE id = :id")
    fun observeById(id: Long): Flow<Daily?>

    @Query("SELECT * FROM dailies WHERE id = :id")
    suspend fun getById(id: Long): Daily?

    @Query("SELECT * FROM dailies ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<Daily>

    @Insert
    suspend fun insert(daily: Daily): Long

    @Update
    suspend fun update(daily: Daily)

    @Delete
    suspend fun delete(daily: Daily)

    @Query("DELETE FROM dailies WHERE id = :id")
    suspend fun deleteById(id: Long)
}
