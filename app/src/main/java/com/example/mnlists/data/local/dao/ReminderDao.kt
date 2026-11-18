package com.example.mnlists.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.mnlists.data.local.entity.Reminder
import com.example.mnlists.data.model.ReminderParentType
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query(
        "SELECT * FROM reminders WHERE parentType = :parentType AND parentId = :parentId " +
            "ORDER BY triggerAt ASC",
    )
    fun observeForParent(parentType: ReminderParentType, parentId: Long): Flow<List<Reminder>>

    @Query(
        "SELECT * FROM reminders WHERE parentType = :parentType AND parentId = :parentId " +
            "ORDER BY triggerAt ASC",
    )
    suspend fun getForParent(parentType: ReminderParentType, parentId: Long): List<Reminder>

    @Query("SELECT * FROM reminders ORDER BY triggerAt ASC")
    suspend fun getAll(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE triggerAt <= :now ORDER BY triggerAt ASC")
    suspend fun getDue(now: Long): List<Reminder>

    @Query("SELECT * FROM reminders WHERE notificationId = :notificationId")
    suspend fun getByNotificationId(notificationId: Int): Reminder?

    @Query("SELECT COALESCE(MAX(notificationId), 1000) FROM reminders")
    suspend fun maxNotificationId(): Int

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Insert
    suspend fun insertAll(reminders: List<Reminder>)

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminders WHERE parentType = :parentType AND parentId = :parentId")
    suspend fun deleteForParent(parentType: ReminderParentType, parentId: Long)
}
