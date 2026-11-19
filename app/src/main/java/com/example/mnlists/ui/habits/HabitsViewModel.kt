package com.example.mnlists.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.repository.HabitRepository
import com.example.mnlists.data.reset.HabitResetManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the Habits tab list: live rows plus counter taps and reset checks. */
@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val habitResetManager: HabitResetManager,
) : ViewModel() {

    val habits: StateFlow<List<Habit>> = habitRepository.observeHabits()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList(),
        )

    /**
     * Run every time the Habits route appears (app open, tab return, back from
     * the editor) so a long-running app still rolls over at the boundary.
     */
    fun checkResets() {
        viewModelScope.launch { habitResetManager.applyDueResets() }
    }

    fun increment(habitId: Long) {
        viewModelScope.launch { habitRepository.adjustCount(habitId, delta = +1) }
    }

    fun decrement(habitId: Long) {
        viewModelScope.launch { habitRepository.adjustCount(habitId, delta = -1) }
    }

    /** Long-press drag reorder (Step 10); the new order is persisted immediately. */
    fun moveHabit(fromId: Long, toId: Long) {
        if (fromId == toId) return
        val current = habits.value
        val from = current.indexOfFirst { it.id == fromId }
        val to = current.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0) return

        val moved = current.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { habitRepository.reorderHabits(moved.map { it.id }) }
    }
}
