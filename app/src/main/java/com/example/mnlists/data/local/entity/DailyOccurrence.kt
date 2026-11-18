package com.example.mnlists.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single, fresh instance of a Daily for one date (SPEC §4.3/§7).
 *
 * No foreign key on purpose: deleting a Daily series must keep past completed
 * occurrences (SPEC §9), so the repository decides what to delete.
 */
@Entity(
    tableName = "daily_occurrences",
    indices = [
        Index(value = ["dailyId", "date"], unique = true),
        Index("date"),
        Index("isCompleted"),
    ],
)
data class DailyOccurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dailyId: Long,
    /** Occurrence date as epoch day — one row per series per date. */
    val date: Long,
    val isCompleted: Boolean = false,
    val isSkipped: Boolean = false,
    val completedAt: Long? = null,
    val sortOrder: Double = 0.0,
)
