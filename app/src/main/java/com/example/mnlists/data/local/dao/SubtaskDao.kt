package com.example.mnlists.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.model.ParentType
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtaskDao {

    /** Manual order only: completed subtasks stay in place (SPEC §4.2). */
    @Query(
        "SELECT * FROM subtasks WHERE parentType = :parentType AND parentId = :parentId " +
            "ORDER BY sortOrder ASC, id ASC",
    )
    fun observeForParent(parentType: ParentType, parentId: Long): Flow<List<Subtask>>

    @Query(
        "SELECT * FROM subtasks WHERE parentType = :parentType AND parentId = :parentId " +
            "ORDER BY sortOrder ASC, id ASC",
    )
    suspend fun getForParent(parentType: ParentType, parentId: Long): List<Subtask>

    @Query("SELECT * FROM subtasks WHERE id = :id")
    suspend fun getById(id: Long): Subtask?

    @Insert
    suspend fun insert(subtask: Subtask): Long

    @Update
    suspend fun update(subtask: Subtask)

    @Delete
    suspend fun delete(subtask: Subtask)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM subtasks WHERE parentType = :parentType AND parentId = :parentId")
    suspend fun deleteForParent(parentType: ParentType, parentId: Long)

    @Query("UPDATE subtasks SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query(
        "UPDATE subtasks SET isCompleted = :completed " +
            "WHERE parentType = :parentType AND parentId = :parentId",
    )
    suspend fun setCompletedForParent(parentType: ParentType, parentId: Long, completed: Boolean)

    @Query("UPDATE subtasks SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Double)

    @Query("SELECT COUNT(*) FROM subtasks WHERE parentType = :parentType AND parentId = :parentId")
    suspend fun countForParent(parentType: ParentType, parentId: Long): Int

    @Query(
        "SELECT COUNT(*) FROM subtasks WHERE parentType = :parentType AND parentId = :parentId " +
            "AND isCompleted = 0",
    )
    suspend fun countIncompleteForParent(parentType: ParentType, parentId: Long): Int

    @Query(
        "SELECT COALESCE(MAX(sortOrder), 0.0) FROM subtasks " +
            "WHERE parentType = :parentType AND parentId = :parentId",
    )
    suspend fun maxSortOrder(parentType: ParentType, parentId: Long): Double
}
