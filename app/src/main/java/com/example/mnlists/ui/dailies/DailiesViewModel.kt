package com.example.mnlists.ui.dailies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mnlists.data.daily.DailyOccurrenceManager
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.model.ParentType
import com.example.mnlists.data.repository.DailyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Dailies tab. It observes *today's* occurrences (joined with their
 * series for title/note) and generates missing ones every time the tab is
 * entered, so a long-running app crossing midnight still gets the new day
 * (SPEC §8/§9). Generation is idempotent — already-checked or skipped rows are
 * never rewritten.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DailiesViewModel @Inject constructor(
    private val repository: DailyRepository,
    private val occurrenceManager: DailyOccurrenceManager,
) : ViewModel() {

    /**
     * Fixed for this screen instance: generation and observation must agree
     * even if the clock rolls over while the tab is open. Tests set it to a
     * known epoch day.
     */
    private val _today = MutableStateFlow(DailyDates.today())
    val today: Long get() = _today.value
    val selectedDay: StateFlow<Long> = _today.asStateFlow()

    /** Today's occurrences with their series, in open → skipped → done order. */
    val entries: StateFlow<List<DailyEntry>> = combine(
        repository.observeDailies(),
        _today.flatMapLatest { day -> repository.observeOccurrencesForDate(day) },
    ) { dailies, occurrences ->
        val series = dailies.associateBy { it.id }
        occurrences
            .mapNotNull { occurrence -> series[occurrence.dailyId]?.let { DailyEntry(occurrence, it) } }
            .sortedWith(compareBy<DailyEntry> { it.statusRank() }.thenBy { it.occurrence.sortOrder })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** Subtasks are parented to occurrences, keyed here by occurrence id. */
    private val _subtasks = MutableStateFlow<Map<Long, List<Subtask>>>(emptyMap())
    val subtasks: StateFlow<Map<Long, List<Subtask>>> = _subtasks.asStateFlow()

    /** Overrides the observed day without generating (handy for tests). */
    fun setToday(value: Long) {
        _today.value = value
        viewModelScope.launch { refreshSubtasks() }
    }

    /** Tab-entry trigger. Uses the day this screen was opened on. */
    fun generateTodayOccurrences(today: Long = _today.value) {
        viewModelScope.launch {
            _today.value = today
            occurrenceManager.generateDueOccurrences(today = today)
            refreshSubtasks()
        }
    }

    fun setCompleted(occurrenceId: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.setOccurrenceCompleted(occurrenceId, completed)
            refreshSubtasks()
        }
    }

    fun setSkipped(occurrenceId: Long, skipped: Boolean) {
        viewModelScope.launch {
            repository.setOccurrenceSkipped(occurrenceId, skipped)
            refreshSubtasks()
        }
    }

    fun setSubtaskCompleted(subtaskId: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.setSubtaskCompleted(subtaskId, completed)
            refreshSubtasks()
        }
    }

    fun addSubtask(
        occurrenceId: Long,
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
            repository.addSubtask(
                Subtask(
                    parentType = ParentType.DAILY_OCCURRENCE,
                    parentId = occurrenceId,
                    title = cleanTitle,
                    note = note.trim(),
                    deadline = deadline,
                ),
            )
            refreshSubtasks()
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
        viewModelScope.launch {
            val current = repository.getSubtask(subtaskId)
            if (current != null) {
                repository.updateSubtask(
                    current.copy(title = cleanTitle, note = note.trim(), deadline = deadline),
                )
            }
            refreshSubtasks()
            onResult(current != null)
        }
    }

    fun deleteSubtask(subtaskId: Long) {
        viewModelScope.launch {
            repository.deleteSubtask(subtaskId)
            refreshSubtasks()
        }
    }

    /**
     * Long-press drag reorder (Step 10). Rows only move inside their own
     * open/Skipped/Completed section; the combined list order is persisted.
     */
    fun moveOccurrence(fromId: Long, toId: Long) {
        if (fromId == toId) return
        val current = entries.value
        val open = current.filter { it.statusRank() == 0 }
        val skipped = current.filter { it.statusRank() == 1 }
        val completed = current.filter { it.statusRank() == 2 }
        val section = when {
            open.any { it.occurrence.id == fromId } -> open
            skipped.any { it.occurrence.id == fromId } -> skipped
            completed.any { it.occurrence.id == fromId } -> completed
            else -> return
        }
        val from = section.indexOfFirst { it.occurrence.id == fromId }
        val to = section.indexOfFirst { it.occurrence.id == toId }
        if (from < 0 || to < 0) return

        val moved = section.toMutableList().apply { add(to, removeAt(from)) }
        val ordered = when (section) {
            open -> moved + skipped + completed
            skipped -> open + moved + completed
            else -> open + skipped + moved
        }.map { it.occurrence.id }
        viewModelScope.launch { repository.reorderOccurrences(ordered) }
    }

    /** Re-reads children directly so callers do not depend on Flow collection. */
    private suspend fun refreshSubtasks() {
        _subtasks.value = repository.getOccurrencesForDate(_today.value)
            .associate { occurrence -> occurrence.id to repository.getSubtasks(occurrence.id) }
    }
}

private fun DailyEntry.statusRank(): Int = when {
    occurrence.isCompleted -> 2
    occurrence.isSkipped -> 1
    else -> 0
}
