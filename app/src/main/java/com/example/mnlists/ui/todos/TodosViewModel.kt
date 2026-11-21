package com.example.mnlists.ui.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.local.entity.Todo
import com.example.mnlists.data.repository.TodoRepository
import com.example.mnlists.data.settings.TodoSortMode
import com.example.mnlists.data.settings.TodoSortPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the To-Do's tab: live rows, completion toggles and sortable ordering. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodosViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    sortPreferences: TodoSortPreferences,
) : ViewModel() {

    /** The persisted choice from Settings; dragging is only offered in MANUAL. */
    val sortMode: StateFlow<TodoSortMode> = sortPreferences.todoSortMode
        .stateIn(
            scope = viewModelScope,
            // Eager so the current mode is available to the screen (and to
            // moveTodo) from the first frame without waiting for collection.
            started = SharingStarted.Eagerly,
            initialValue = TodoSortMode.MANUAL,
        )

    /**
     * The DAO returns `isCompleted ASC, sortOrder ASC`. MANUAL keeps that exact
     * order; DUE_DATE reorders within each completion section by deadline
     * (undated last), preserving the manual order as the tie-breaker.
     */
    val todos: StateFlow<List<Todo>> = combine(
        todoRepository.observeTodos(),
        sortPreferences.todoSortMode,
    ) { rows, mode -> rows.sortedFor(mode) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList(),
        )

    /** Children per to-do so list rows can expand their subtasks (Step 10). */
    val subtasks: StateFlow<Map<Long, List<Subtask>>> = todoRepository.observeTodos()
        .flatMapLatest { rows ->
            if (rows.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    rows.map { row ->
                        todoRepository.observeSubtasks(row.id).map { children -> row.id to children }
                    },
                ) { pairs -> pairs.toMap() }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyMap(),
        )

    /** Checks/unchecks a task, cascading to its subtasks (SPEC §8). */
    fun setCompleted(todoId: Long, completed: Boolean) {
        viewModelScope.launch { todoRepository.setCompleted(todoId, completed) }
    }

    /** A child checkbox in the expanded list row (cascade handled by the repository). */
    fun setSubtaskCompleted(subtaskId: Long, completed: Boolean) {
        viewModelScope.launch { todoRepository.setSubtaskCompleted(subtaskId, completed) }
    }

    /**
     * Long-press drag reorder (Step 10). Rows only move inside their own
     * open/Completed section, then the whole list is renumbered and persisted.
     */
    fun moveTodo(fromId: Long, toId: Long) {
        if (fromId == toId) return
        val current = todos.value
        val open = current.filterNot { it.isCompleted }
        val completed = current.filter { it.isCompleted }
        val inOpen = open.any { it.id == fromId }
        val section = if (inOpen) open else completed
        val from = section.indexOfFirst { it.id == fromId }
        val to = section.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0) return

        val moved = section.toMutableList().apply { add(to, removeAt(from)) }
        val ordered = (if (inOpen) moved + completed else open + moved).map { it.id }
        viewModelScope.launch { todoRepository.reorderTodos(ordered) }
    }
}

/** Pure ordering helper so the due-date rule can be unit-tested directly. */
internal fun List<Todo>.sortedFor(mode: TodoSortMode): List<Todo> = when (mode) {
    TodoSortMode.MANUAL -> this
    TodoSortMode.DUE_DATE -> sortedWith(
        compareBy<Todo> { it.isCompleted }
            .thenBy { it.dueDate == null }
            .thenBy { it.dueDate ?: Long.MAX_VALUE }
            .thenBy { it.sortOrder }
            .thenBy { it.id },
    )
}
