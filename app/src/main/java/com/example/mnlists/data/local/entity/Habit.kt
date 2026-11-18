package com.example.mnlists.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mnlists.data.model.ResetSchedule
import java.time.DayOfWeek

/**
 * A habit counter with a user-defined auto-reset schedule (SPEC §7).
 * `currentCount` is the count inside the current reset cycle; history lives in
 * [HabitLogEntity] and is never wiped by a reset.
 */
@Entity(
    tableName = "habits",
    indices = [
        Index("lastResetAt"),
        Index("createdAt"),
    ],
)
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val resetSchedule: ResetSchedule = ResetSchedule.DAILY,
    /** N for [ResetSchedule.EVERY_N]; 1 otherwise. */
    val resetEveryN: Int = 1,
    /** Optional weekday anchor for weekly resets. */
    val resetWeekday: DayOfWeek? = null,
    val allowUp: Boolean = true,
    val allowDown: Boolean = false,
    val currentCount: Int = 0,
    /** Epoch millis of the last reset (also the current cycle start). */
    val lastResetAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
