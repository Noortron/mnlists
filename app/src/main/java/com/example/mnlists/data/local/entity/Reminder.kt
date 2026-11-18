package com.example.mnlists.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mnlists.data.model.ReminderParentType

/**
 * Local notification schedule attached to a To-Do or a Daily series (SPEC §7).
 * `notificationId` is the stable AlarmManager/NotificationManager id.
 */
@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["parentType", "parentId"]),
        Index("triggerAt"),
        Index(value = ["notificationId"], unique = true),
    ],
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentType: ReminderParentType,
    val parentId: Long,
    /** Epoch millis of the next fire time. */
    val triggerAt: Long,
    val notificationId: Int,
)
