package com.example.mnlists.ui.todos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.local.entity.Todo
import com.example.mnlists.data.local.entity.Reminder
import com.example.mnlists.data.model.ParentType
import com.example.mnlists.data.model.ReminderParentType
import com.example.mnlists.data.reminder.ReminderScheduler
import com.example.mnlists.data.repository.TodoRepository
import com.example.mnlists.ui.navigation.MnlistsRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Editable form state for creating or editing a single to-do. */
data class TodoEditorUiState(
    val title: String = "",
    val note: String = "",
    /** Epoch day, or `null` for an unplanned task (no deadline). */
    val dueDate: Long? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val titleError: Boolean = false,
    /** Completion of the parent to-do, kept in sync with the subtask cascade. */
    val isCompleted: Boolean = false,
)

/**
 * Create/edit form for a to-do. `todoId == 0` (or missing) means "new to-do";
 * any other value loads the existing row from Room.
 *
 * Subtasks are persisted live. Adding the first subtask to a brand-new to-do
 * commits the parent first (with the current title/note/deadline), because a
 * child needs a parent row to attach to.
 */
@HiltViewModel
class TodoEditorViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val reminderScheduler: ReminderScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var todoId: Long = savedStateHandle.get<Long>(MnlistsRoutes.TODO_ID_ARG) ?: 0L

    var uiState by mutableStateOf(TodoEditorUiState(isLoading = todoId > 0L))
        private set

    /** Live subtask list, in manual order (completed children stay in place). */
    var subtasks by mutableStateOf<List<Subtask>>(emptyList())
        private set

    /** Pending one-off reminders attached to this to-do (Step 11). */
    var reminders by mutableStateOf<List<Reminder>>(emptyList())
        private set

    /**
     * Set once when a brand-new to-do is committed on demand (first subtask or
     * reminder). The scaffold uses it to replace the "New to-do" route with the
     * edit route so the top-bar title matches the editor's edit-mode state.
     */
    var createdParentId by mutableStateOf<Long?>(null)
        private set

    val isEditing: Boolean get() = todoId > 0L

    init {
        if (todoId > 0L) load()
    }

    private fun load() {
        viewModelScope.launch {
            val todo = todoRepository.getTodo(todoId)
            uiState = if (todo == null) {
                uiState.copy(isLoading = false)
            } else {
                uiState.copy(
                    isLoading = false,
                    title = todo.title,
                    note = todo.note,
                    dueDate = todo.dueDate,
                    isCompleted = todo.isCompleted,
                )
            }
            refreshSubtasks()
            refreshReminders()
        }
    }

    fun setTitle(value: String) {
        uiState = uiState.copy(title = value, titleError = false)
    }

    fun setNote(value: String) {
        uiState = uiState.copy(note = value)
    }

    /** Pass `null` to remove the deadline; the date is optional. */
    fun setDueDate(epochDay: Long?) {
        uiState = uiState.copy(dueDate = epochDay)
    }

    fun save(onSaved: () -> Unit) {
        if (uiState.isSaving) return

        val state = uiState
        val title = state.title.trim()
        if (title.isEmpty()) {
            uiState = state.copy(titleError = true)
            return
        }

        uiState = state.copy(isSaving = true)
        viewModelScope.launch {
            if (todoId == 0L) {
                val now = System.currentTimeMillis()
                val createdId = todoRepository.createTodo(
                    Todo(
                        title = title,
                        note = state.note.trim(),
                        dueDate = state.dueDate,
                        createdAt = now,
                        updatedAt = now,
                        sortOrder = todoRepository.nextSortOrder(),
                    ),
                )
                todoId = createdId
            } else {
                // Re-read so a completion cascade that happened while the form
                // was open is not overwritten by a stale copy.
                val base = todoRepository.getTodo(todoId)
                if (base != null) {
                    todoRepository.updateTodo(
                        base.copy(
                            title = title,
                            note = state.note.trim(),
                            dueDate = state.dueDate,
                        ),
                    )
                }
            }
            uiState = uiState.copy(isSaving = false)
            onSaved()
        }
    }

    fun addSubtask(
        title: String,
        note: String,
        deadline: Long?,
        onResult: (Boolean) -> Unit,
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            onResult(false)
            return
        }

        viewModelScope.launch {
            val wasNew = todoId == 0L
            val parentId = ensureParent() ?: run {
                onResult(false)
                return@launch
            }
            todoRepository.addSubtask(
                Subtask(
                    parentType = ParentType.TODO,
                    parentId = parentId,
                    title = cleanTitle,
                    note = note.trim(),
                    deadline = deadline,
                ),
            )
            refreshSubtasks()
            if (wasNew) createdParentId = parentId
            onResult(true)
        }
    }

    fun updateSubtask(
        subtaskId: Long,
        title: String,
        note: String,
        deadline: Long?,
        onResult: (Boolean) -> Unit,
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            onResult(false)
            return
        }
        val current = subtasks.firstOrNull { it.id == subtaskId } ?: run {
            onResult(false)
            return
        }

        viewModelScope.launch {
            todoRepository.updateSubtask(
                current.copy(
                    title = cleanTitle,
                    note = note.trim(),
                    deadline = deadline,
                ),
            )
            refreshSubtasks()
            onResult(true)
        }
    }

    fun setSubtaskCompleted(subtaskId: Long, completed: Boolean) {
        viewModelScope.launch {
            todoRepository.setSubtaskCompleted(subtaskId, completed)
            refreshSubtasks()
        }
    }

    /**
     * Toggles the parent from the editor. The repository checks/unchecks every
     * subtask with it (SPEC §8), then the refreshed list reflects that.
     */
    fun setParentCompleted(completed: Boolean) {
        if (todoId <= 0L) return
        viewModelScope.launch {
            todoRepository.setCompleted(todoId, completed)
            uiState = uiState.copy(isCompleted = completed)
            refreshSubtasks()
        }
    }

    fun deleteSubtask(subtaskId: Long) {
        viewModelScope.launch {
            todoRepository.deleteSubtask(subtaskId)
            refreshSubtasks()
        }
    }

    /**
     * Reminders are persisted immediately, like subtasks. A brand-new to-do is
     * committed first so the reminder has a parent; a missing title blocks the
     * add and flags the title field.
     */
    fun addReminder(epochDay: Long, minutesOfDay: Int) {
        viewModelScope.launch {
            val wasNew = todoId == 0L
            val parentId = ensureParent() ?: return@launch
            reminderScheduler.create(
                parentType = ReminderParentType.TODO,
                parentId = parentId,
                epochDay = epochDay,
                minutesOfDay = minutesOfDay,
            )
            refreshReminders()
            if (wasNew) createdParentId = parentId
        }
    }

    fun updateReminder(reminderId: Long, epochDay: Long, minutesOfDay: Int) {
        viewModelScope.launch {
            reminderScheduler.update(reminderId, epochDay, minutesOfDay)
            refreshReminders()
        }
    }

    fun deleteReminder(reminderId: Long) {
        viewModelScope.launch {
            reminderScheduler.delete(reminderId)
            refreshReminders()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        if (todoId <= 0L) return
        viewModelScope.launch {
            // Cancel alarms before the repository cascade removes the rows.
            reminderScheduler.deleteForParent(ReminderParentType.TODO, todoId)
            todoRepository.deleteTodo(todoId)
            onDeleted()
        }
    }

    /**
     * Creates the parent row on demand for a new to-do. Returns `null` (and
     * flags the title field) when the parent has no title yet.
     */
    private suspend fun ensureParent(): Long? {
        if (todoId > 0L) return todoId

        val title = uiState.title.trim()
        if (title.isEmpty()) {
            uiState = uiState.copy(titleError = true)
            return null
        }

        val now = System.currentTimeMillis()
        todoId = todoRepository.createTodo(
            Todo(
                title = title,
                note = uiState.note.trim(),
                dueDate = uiState.dueDate,
                createdAt = now,
                updatedAt = now,
                sortOrder = todoRepository.nextSortOrder(),
            ),
        )
        uiState = uiState.copy(
            title = title,
            note = uiState.note.trim(),
            isLoading = false,
            titleError = false,
        )
        return todoId
    }

    /**
     * Re-reads children and the parent. The parent must be re-read because the
     * §8 cascade can flip its completion from either side.
     */
    private suspend fun refreshSubtasks() {
        if (todoId <= 0L) {
            subtasks = emptyList()
            return
        }
        subtasks = todoRepository.getSubtasks(todoId)
        val parent = todoRepository.getTodo(todoId)
        if (parent != null && parent.isCompleted != uiState.isCompleted) {
            uiState = uiState.copy(isCompleted = parent.isCompleted)
        }
    }

    private suspend fun refreshReminders() {
        reminders = if (todoId <= 0L) {
            emptyList()
        } else {
            reminderScheduler.getForParent(ReminderParentType.TODO, todoId)
        }
    }
}
