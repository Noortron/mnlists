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

    suspend fun getSubtask(subtaskId: Long): Subtask? = subtaskDao.getById(subtaskId)

    /**
     * Appends the subtask and, because a new open child means the parent can no
     * longer be "all done", unchecks the parent (SPEC §8).
     */
    suspend fun addSubtask(subtask: Subtask): Long = database.withTransaction {
        val ordered = if (subtask.sortOrder > 0.0) {
            subtask
        } else {
            subtask.copy(sortOrder = subtaskDao.maxSortOrder(ParentType.TODO, subtask.parentId) + 1.0)
        }
        val id = subtaskDao.insert(ordered)
        if (ordered.parentType == ParentType.TODO && !ordered.isCompleted) {
            todoDao.setCompleted(
                id = ordered.parentId,
                completed = false,
                completedAt = null,
                updatedAt = System.currentTimeMillis(),
            )
        }
        id
    }

    suspend fun updateSubtask(subtask: Subtask) = subtaskDao.update(subtask)

    /** Removing a child re-evaluates the parent: all remaining children done → checked. */
    suspend fun deleteSubtask(subtaskId: Long) = database.withTransaction {
        val subtask = subtaskDao.getById(subtaskId) ?: return@withTransaction
        subtaskDao.deleteById(subtaskId)
        if (subtask.parentType == ParentType.TODO) {
            syncTodoCompletion(subtask.parentId)
        }
    }

    /**
     * Completing the last subtask checks the parent; unchecking any subtask
     * unchecks the parent (SPEC §8).
     */
    suspend fun setSubtaskCompleted(subtaskId: Long, completed: Boolean) {
        database.withTransaction {
            val subtask = subtaskDao.getById(subtaskId) ?: return@withTransaction
            subtaskDao.setCompleted(subtaskId, completed)
            if (subtask.parentType == ParentType.TODO) {
                syncTodoCompletion(subtask.parentId)
            }
        }
    }

    /** Re-derives parent completion from its children; leaves childless todos alone. */
    private suspend fun syncTodoCompletion(todoId: Long) {
        val total = subtaskDao.countForParent(ParentType.TODO, todoId)
        if (total == 0) return

        val allDone = subtaskDao.countIncompleteForParent(ParentType.TODO, todoId) == 0
        val now = System.currentTimeMillis()
        todoDao.setCompleted(
            id = todoId,
            completed = allDone,
            completedAt = if (allDone) now else null,
            updatedAt = now,
        )
    }

    suspend fun nextSortOrder(): Double = todoDao.maxSortOrder() + 1.0

    /** Persists a drag-reorder: the given ids become `0..n` in list order (Step 10). */
    suspend fun reorderTodos(orderedIds: List<Long>) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            orderedIds.forEachIndexed { index, id ->
                todoDao.updateSortOrder(id = id, sortOrder = index.toDouble(), updatedAt = now)
            }
        }
    }
}
