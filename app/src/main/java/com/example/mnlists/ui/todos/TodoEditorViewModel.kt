package com.example.mnlists.ui.todos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mnlists.data.local.entity.Todo
import com.example.mnlists.data.repository.TodoRepository
import com.example.mnlists.ui.navigation.MnlistsRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Editable form state for creating or editing a single to-do. */
data class TodoEditorUiState(
    val title: String = "",
    val note: String = "",
    /** Epoch day; `null` until the user picks one (mandatory per SPEC §4.2/§9). */
    val dueDate: Long? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val titleError: Boolean = false,
    val dueDateError: Boolean = false,
)

/**
 * Create/edit form for a to-do. `todoId == 0` (or missing) means "new to-do";
 * any other value loads the existing row from Room.
 */
@HiltViewModel
class TodoEditorViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val todoId: Long = savedStateHandle.get<Long>(MnlistsRoutes.TODO_ID_ARG) ?: 0L
    private var existingTodo: Todo? = null

    var uiState by mutableStateOf(TodoEditorUiState(isLoading = todoId > 0L))
        private set

    val isEditing: Boolean get() = todoId > 0L

    init {
        if (todoId > 0L) load()
    }

    private fun load() {
        viewModelScope.launch {
            val todo = todoRepository.getTodo(todoId)
            existingTodo = todo
            uiState = if (todo == null) {
                uiState.copy(isLoading = false)
            } else {
                uiState.copy(
                    isLoading = false,
                    title = todo.title,
                    note = todo.note,
                    dueDate = todo.dueDate,
                )
            }
        }
    }

    fun setTitle(value: String) {
        uiState = uiState.copy(title = value, titleError = false)
    }

    fun setNote(value: String) {
        uiState = uiState.copy(note = value)
    }

    fun setDueDate(epochDay: Long) {
        uiState = uiState.copy(dueDate = epochDay, dueDateError = false)
    }

    fun save(onSaved: () -> Unit) {
        if (uiState.isSaving) return

        val state = uiState
        val title = state.title.trim()
        val titleError = title.isEmpty()
        val dueDateError = state.dueDate == null

        if (titleError || dueDateError) {
            uiState = state.copy(titleError = titleError, dueDateError = dueDateError)
            return
        }

        val dueDate = state.dueDate ?: return
        uiState = state.copy(isSaving = true)
        viewModelScope.launch {
            val base = existingTodo
            if (base == null) {
                val now = System.currentTimeMillis()
                todoRepository.createTodo(
                    Todo(
                        title = title,
                        note = state.note.trim(),
                        dueDate = dueDate,
                        createdAt = now,
                        updatedAt = now,
                        sortOrder = todoRepository.nextSortOrder(),
                    ),
                )
            } else {
                // Completion state survives an edit; only the editable fields change.
                todoRepository.updateTodo(
                    base.copy(
                        title = title,
                        note = state.note.trim(),
                        dueDate = dueDate,
                    ),
                )
            }
            uiState = uiState.copy(isSaving = false)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        if (todoId <= 0L) return
        viewModelScope.launch {
            todoRepository.deleteTodo(todoId)
            onDeleted()
        }
    }
}
