package com.example.mnlists.data.repository

import androidx.room.withTransaction
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.dao.ReminderDao
import com.example.mnlists.data.local.dao.SubtaskDao
import com.example.mnlists.data.local.dao.TodoDao
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.local.entity.Todo
import com.example.mnlists.data.model.ParentType
import com.example.mnlists.data.model.ReminderParentType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * To-Do CRUD plus the two-way subtask cascade from SPEC §8:
 * all subtasks checked → parent checked; parent toggled → all subtasks follow.
 */
@Singleton
class TodoRepository @Inject constructor(
    private val database: MnlistsDatabase,
    private val todoDao: TodoDao,
    private val subtaskDao: SubtaskDao,
    private val reminderDao: ReminderDao,
) {

    fun observeTodos(): Flow<List<Todo>> = todoDao.observeAll()

    fun observeTodo(todoId: Long): Flow<Todo?> = todoDao.observeById(todoId)

    suspend fun getTodo(todoId: Long): Todo? = todoDao.getById(todoId)

    suspend fun createTodo(todo: Todo): Long = todoDao.insert(todo)

    suspend fun updateTodo(todo: Todo) =
        todoDao.update(todo.copy(updatedAt = System.currentTimeMillis()))

    /** Cascades to subtasks and pending reminders in one transaction (SPEC §4.7). */
    suspend fun deleteTodo(todoId: Long) {
        database.withTransaction {
            subtaskDao.deleteForParent(ParentType.TODO, todoId)
            reminderDao.deleteForParent(ReminderParentType.TODO, todoId)
            todoDao.deleteById(todoId)
        }
    }

    /** Inverse cascade: checking the parent checks every subtask, unchecking clears them. */
    suspend fun setCompleted(todoId: Long, completed: Boolean) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            todoDao.setCompleted(todoId, completed, completedAt = if (completed) now else null, updatedAt = now)
            subtaskDao.setCompletedForParent(ParentType.TODO, todoId, completed)
        }
    }

    fun observeSubtasks(todoId: Long): Flow<List<Subtask>> =
        subtaskDao.observeForParent(ParentType.TODO, todoId)

    suspend fun getSubtasks(todoId: Long): List<Subtask> =
        subtaskDao.getForParent(ParentType.TODO, todoId)

    suspend fun addSubtask(subtask: Subtask): Long {
        val ordered = if (subtask.sortOrder > 0.0) {
            subtask
        } else {
            subtask.copy(sortOrder = subtaskDao.maxSortOrder(ParentType.TODO, subtask.parentId) + 1.0)
        }
        return subtaskDao.insert(ordered)
    }

    suspend fun updateSubtask(subtask: Subtask) = subtaskDao.update(subtask)

    suspend fun deleteSubtask(subtaskId: Long) = subtaskDao.deleteById(subtaskId)

    /**
     * Completing the last subtask checks the parent; unchecking any subtask
     * unchecks the parent (SPEC §8).
     */
    suspend fun setSubtaskCompleted(subtaskId: Long, completed: Boolean) {
        database.withTransaction {
            val subtask = subtaskDao.getById(subtaskId) ?: return@withTransaction
            subtaskDao.setCompleted(subtaskId, completed)
            if (subtask.parentType != ParentType.TODO) return@withTransaction

            val total = subtaskDao.countForParent(ParentType.TODO, subtask.parentId)
            val remaining = subtaskDao.countIncompleteForParent(ParentType.TODO, subtask.parentId)
            val allDone = total > 0 && remaining == 0
            val now = System.currentTimeMillis()
            todoDao.setCompleted(
                id = subtask.parentId,
                completed = allDone,
                completedAt = if (allDone) now else null,
                updatedAt = now,
            )
        }
    }

    suspend fun nextSortOrder(): Double = todoDao.maxSortOrder() + 1.0
}
