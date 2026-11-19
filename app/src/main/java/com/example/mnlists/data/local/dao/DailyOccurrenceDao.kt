package com.example.mnlists.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mnlists.data.local.entity.DailyOccurrence
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyOccurrenceDao {

    @Query(
        "SELECT * FROM daily_occurrences WHERE date = :date " +
            "ORDER BY isSkipped ASC, isCompleted ASC, sortOrder ASC",
    )
    fun observeForDate(date: Long): Flow<List<DailyOccurrence>>

    @Query(
        "SELECT * FROM daily_occurrences WHERE date = :date " +
            "ORDER BY isSkipped ASC, isCompleted ASC, sortOrder ASC",
    )
    suspend fun getForDate(date: Long): List<DailyOccurrence>

    @Query("SELECT * FROM daily_occurrences WHERE dailyId = :dailyId AND date = :date")
    suspend fun getForDailyAndDate(dailyId: Long, date: Long): DailyOccurrence?

    @Query("SELECT * FROM daily_occurrences WHERE id = :id")
    suspend fun getById(id: Long): DailyOccurrence?

    @Query("SELECT * FROM daily_occurrences WHERE dailyId = :dailyId ORDER BY date DESC")
    fun observeForDaily(dailyId: Long): Flow<List<DailyOccurrence>>

    @Query("SELECT * FROM daily_occurrences WHERE dailyId = :dailyId ORDER BY date DESC")
    suspend fun getForDaily(dailyId: Long): List<DailyOccurrence>

    @Query("SELECT * FROM daily_occurrences WHERE date BETWEEN :fromDate AND :toDate ORDER BY date ASC")
    suspend fun getBetween(fromDate: Long, toDate: Long): List<DailyOccurrence>

    @Query("SELECT MAX(date) FROM daily_occurrences WHERE dailyId = :dailyId")
    suspend fun latestDateFor(dailyId: Long): Long?

    /** Safe to call repeatedly: the unique (dailyId, date) index wins. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(occurrence: DailyOccurrence): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(occurrences: List<DailyOccurrence>): List<Long>

    @Update
    suspend fun update(occurrence: DailyOccurrence)

    @Delete
    suspend fun delete(occurrence: DailyOccurrence)

    @Query("DELETE FROM daily_occurrences WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "UPDATE daily_occurrences SET isCompleted = :completed, completedAt = :completedAt " +
            "WHERE id = :id",
    )
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long?)

    @Query("UPDATE daily_occurrences SET isSkipped = :skipped WHERE id = :id")
    suspend fun setSkipped(id: Long, skipped: Boolean)

    @Query("UPDATE daily_occurrences SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Double)

    /** Used when deleting a series: history (<= [afterDate]) stays. */
    @Query("DELETE FROM daily_occurrences WHERE dailyId = :dailyId AND date > :afterDate")
    suspend fun deleteFutureForDaily(dailyId: Long, afterDate: Long)
}
