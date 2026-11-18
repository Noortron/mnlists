package com.example.mnlists.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mnlists.data.model.ParentType

/**
 * Child task of a To-Do or a Daily occurrence (SPEC §7). Polymorphic owner, so
 * no foreign key; indexes keep `(parentType, parentId)` lookups cheap.
 */
@Entity(
    tableName = "subtasks",
    indices = [
        Index(value = ["parentType", "parentId"]),
        Index("deadline"),
    ],
)
data class Subtask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentType: ParentType,
    val parentId: Long,
    val title: String,
    val note: String = "",
    /** Optional own deadline as epoch day; shown only inside the parent. */
    val deadline: Long? = null,
    val isCompleted: Boolean = false,
    val sortOrder: Double = 0.0,
)
