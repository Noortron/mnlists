package com.example.mnlists.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Immutable history entry written when a habit cycle ends (SPEC §7/§8).
 * Deliberately has no foreign key: deleting a habit keeps its history
 * (default from SPEC §9 — orphan-safe).
 */
@Entity(
    tableName = "habit_logs",
    indices = [
        Index("habitId"),
        Index("date"),
        Index("cycleStart"),
    ],
)
data class HabitLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    /** Cycle date as epoch day (local zone at write time). */
    val date: Long,
    val countAtEndOfCycle: Int,
    /** Epoch millis. */
    val cycleStart: Long,
    /** Epoch millis. */
    val cycleEnd: Long,
)
