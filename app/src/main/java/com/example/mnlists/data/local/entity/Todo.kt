package com.example.mnlists.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A one-off task with a mandatory due date (SPEC §7). */
@Entity(
    tableName = "todos",
    indices = [
        Index("isCompleted"),
        Index("dueDate"),
        Index("sortOrder"),
    ],
)
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    /** Mandatory due date as epoch day (SPEC §4.2). */
    val dueDate: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    /** Manual ordering inside the list; lower comes first. */
    val sortOrder: Double = 0.0,
    val isCompleted: Boolean = false,
)
