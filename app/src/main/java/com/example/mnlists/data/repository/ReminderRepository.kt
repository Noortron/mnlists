package com.example.mnlists.data.repository

import com.example.mnlists.data.local.dao.ReminderDao
import com.example.mnlists.data.local.entity.Reminder
import com.example.mnlists.data.model.ReminderParentType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Stores reminder definitions. AlarmManager scheduling, permissions and
 * reschedule-on-boot arrive in Step 11; notification ids are allocated here so
 * they stay unique across both parents.
 */
@Singleton
class ReminderRepository @Inject constructor(
    private val reminderDao: ReminderDao,
) {

    fun observeForParent(parentType: ReminderParentType, parentId: Long): Flow<List<Reminder>> =
        reminderDao.observeForParent(parentType, parentId)

    suspend fun getForParent(parentType: ReminderParentType, parentId: Long): List<Reminder> =
        reminderDao.getForParent(parentType, parentId)

    suspend fun getAll(): List<Reminder> = reminderDao.getAll()

    suspend fun getDue(now: Long): List<Reminder> = reminderDao.getDue(now)

    suspend fun getByNotificationId(notificationId: Int): Reminder? =
        reminderDao.getByNotificationId(notificationId)

    suspend fun add(reminder: Reminder): Long = reminderDao.insert(reminder)

    suspend fun addAll(reminders: List<Reminder>) = reminderDao.insertAll(reminders)

    suspend fun update(reminder: Reminder) = reminderDao.update(reminder)

    suspend fun delete(reminder: Reminder) = reminderDao.delete(reminder)

    suspend fun deleteById(reminderId: Long) = reminderDao.deleteById(reminderId)

    suspend fun deleteForParent(parentType: ReminderParentType, parentId: Long) =
        reminderDao.deleteForParent(parentType, parentId)

    /** Allocates a fresh, stable notification id starting above 1000. */
    suspend fun nextNotificationId(): Int = reminderDao.maxNotificationId() + 1
}
