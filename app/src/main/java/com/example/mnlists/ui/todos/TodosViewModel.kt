package com.example.mnlists.ui.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mnlists.data.local.entity.Todo
import com.example.mnlists.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the To-Do's tab: live rows, completion toggles and reset-safe ordering. */
@HiltViewModel
class TodosViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
) : ViewModel() {

    /**
     * The DAO already orders incomplete rows first (`isCompleted ASC,
     * sortOrder ASC`); the screen splits the list into the open and Completed
     * sections without re-sorting.
     */
    val todos: StateFlow<List<Todo>> = todoRepository.observeTodos()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList(),
        )

    /** Checks/unchecks a task, cascading to its subtasks (SPEC §8). */
    fun setCompleted(todoId: Long, completed: Boolean) {
        viewModelScope.launch { todoRepository.setCompleted(todoId, completed) }
    }
}
