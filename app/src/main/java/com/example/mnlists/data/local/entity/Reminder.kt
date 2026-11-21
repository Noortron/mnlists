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
    /**
     * Local wall-clock time as minutes after midnight (0..1439).
     *
     * Daily reminders recur on a date, so the wall-clock time must survive a
     * time-zone change (SPEC §8: recompute schedules after a zone change).
     * To-Do reminders keep the same value for display/editing, but their
     * `triggerAt` stays a fixed instant. `null` on pre-v4 rows; the scheduler
     * derives the time from `triggerAt` once and stores it.
     */
    val timeOfDayMinutes: Int? = null,
)
