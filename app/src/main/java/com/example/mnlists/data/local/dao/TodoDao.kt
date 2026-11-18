package com.example.mnlists.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.mnlists.data.local.entity.Todo
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    /** Incomplete first, then manual order, then oldest first. */
    @Query("SELECT * FROM todos ORDER BY isCompleted ASC, sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<Todo>>

    @Query("SELECT * FROM todos WHERE id = :id")
    fun observeById(id: Long): Flow<Todo?>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: Long): Todo?

    @Query("SELECT * FROM todos WHERE dueDate = :date ORDER BY isCompleted ASC, sortOrder ASC")
    suspend fun getForDate(date: Long): List<Todo>

    @Insert
    suspend fun insert(todo: Todo): Long

    @Update
    suspend fun update(todo: Todo)

    @Delete
    suspend fun delete(todo: Todo)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "UPDATE todos SET isCompleted = :completed, completedAt = :completedAt, updatedAt = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long?, updatedAt: Long)

    @Query("UPDATE todos SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Double, updatedAt: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), 0.0) FROM todos")
    suspend fun maxSortOrder(): Double
}
