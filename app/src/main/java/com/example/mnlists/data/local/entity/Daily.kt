package com.example.mnlists.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mnlists.data.model.RepeatType
import java.time.DayOfWeek

/**
 * Recurring to-do series (SPEC §7). Occurrences are generated separately in
 * [DailyOccurrence]; editing the series never rewrites already-generated ones.
 */
@Entity(
    tableName = "dailies",
    indices = [
        Index("startDate"),
        Index("createdAt"),
    ],
)
data class Daily(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    /** First occurrence date as epoch day. */
    val startDate: Long,
    val repeatType: RepeatType = RepeatType.DAILY,
    /** "Every N units" — bounded to >= 1 by the repository/UI. */
    val everyN: Int = 1,
    /** Only used for [RepeatType.WEEKLY]; empty otherwise. */
    val weekdays: Set<DayOfWeek> = emptySet(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
