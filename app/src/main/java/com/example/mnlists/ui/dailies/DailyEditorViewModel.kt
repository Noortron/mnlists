package com.example.mnlists.ui.dailies

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mnlists.data.daily.DailyOccurrenceManager
import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.model.ParentType
import com.example.mnlists.data.model.RepeatType
import com.example.mnlists.data.repository.DailyRepository
import com.example.mnlists.ui.navigation.MnlistsRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Which of the two locked editing rules this screen applies (SPEC §9). */
enum class DailyEditorMode {
    /** Opened from the Dailies list — changes the whole recurring series. */
    SERIES,

    /** Opened from a day (the Calendar in Step 12) — changes one occurrence. */
    OCCURRENCE,
}

/** Editable form state shared by both daily editor modes. */
data class DailyEditorUiState(
    val title: String = "",
    val note: String = "",
    val startDate: Long = DailyDates.today(),
    val repeatType: RepeatType = RepeatType.DAILY,
    val everyN: String = "1",
    val weekdays: Set<DayOfWeek> = emptySet(),
    val isCompleted: Boolean = false,
    val isSkipped: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val titleError: Boolean = false,
    val everyNError: Boolean = false,
)

/**
 * Create/edit form for a Daily. [DailyEditorMode.SERIES] edits title, note and
 * schedule on the [Daily] row; [DailyEditorMode.OCCURRENCE] cannot change those
 * fields — it only toggles completion/skip and edits that day's subtasks.
 * Occurrences and subtasks are never rewritten by a series edit.
 */
@HiltViewModel
class DailyEditorViewModel @Inject constructor(
    private val repository: DailyRepository,
    private val occurrenceManager: DailyOccurrenceManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val occurrenceId = savedStateHandle.get<Long>(MnlistsRoutes.DAILY_OCCURRENCE_ID_ARG) ?: 0L
    private var dailyId = savedStateHandle.get<Long>(MnlistsRoutes.DAILY_ID_ARG) ?: 0L

    val mode: DailyEditorMode =
        if (occurrenceId > 0L) DailyEditorMode.OCCURRENCE else DailyEditorMode.SERIES

    val isEditing: Boolean get() = mode == DailyEditorMode.OCCURRENCE || dailyId > 0L

    var uiState by mutableStateOf(
        DailyEditorUiState(isLoading = occurrenceId > 0L || dailyId > 0L),
    )
        private set

    var subtasks by mutableStateOf<List<Subtask>>(emptyList())
        private set

    init {
        when (mode) {
            DailyEditorMode.OCCURRENCE -> loadOccurrence()
            DailyEditorMode.SERIES -> if (dailyId > 0L) loadSeries()
        }
    }

    private fun loadSeries() {
        viewModelScope.launch {
            val daily = repository.getDaily(dailyId)
            uiState = if (daily == null) {
                uiState.copy(isLoading = false)
            } else {
                uiState.copy(
                    isLoading = false,
                    title = daily.title,
                    note = daily.note,
                    startDate = daily.startDate,
                    repeatType = daily.repeatType,
                    everyN = daily.everyN.toString(),
                    weekdays = daily.weekdays,
                )
            }
        }
    }

    private fun loadOccurrence() {
        viewModelScope.launch {
            val occurrence = repository.getOccurrence(occurrenceId)
            if (occurrence == null) {
                uiState = uiState.copy(isLoading = false)
                return@launch
            }
            dailyId = occurrence.dailyId
            val daily = repository.getDaily(dailyId)
            uiState = uiState.copy(
                isLoading = false,
                title = daily?.title.orEmpty(),
                note = daily?.note.orEmpty(),
                startDate = occurrence.date,
                repeatType = daily?.repeatType ?: RepeatType.DAILY,
                everyN = (daily?.everyN ?: 1).toString(),
                weekdays = daily?.weekdays ?: emptySet(),
                isCompleted = occurrence.isCompleted,
                isSkipped = occurrence.isSkipped,
            )
            refreshSubtasks()
        }
    }

    fun setTitle(value: String) {
        uiState = uiState.copy(title = value, titleError = false)
    }

    fun setNote(value: String) {
        uiState = uiState.copy(note = value)
    }

    fun setStartDate(epochDay: Long) {
        uiState = uiState.copy(startDate = epochDay)
    }

    fun setRepeatType(value: RepeatType) {
        uiState = uiState.copy(
            repeatType = value,
            // Weekdays only apply to weekly repeats.
            weekdays = if (value == RepeatType.WEEKLY) uiState.weekdays else emptySet(),
        )
    }

    fun setEveryN(value: String) {
        if (value.length > 3 || value.any { !it.isDigit() }) return
        uiState = uiState.copy(everyN = value, everyNError = false)
    }

    fun toggleWeekday(day: DayOfWeek) {
        if (uiState.repeatType != RepeatType.WEEKLY) return
        uiState = uiState.copy(
            weekdays = if (day in uiState.weekdays) uiState.weekdays - day else uiState.weekdays + day,
        )
    }

    /** Series only: validates and writes the [Daily] row, then backfills dates. */
    fun save(onSaved: () -> Unit) {
        if (mode != DailyEditorMode.SERIES || uiState.isSaving) return

        val state = uiState
        val title = state.title.trim()
        val everyN = state.everyN.toIntOrNull()
        val titleError = title.isEmpty()
        val everyNError = everyN == null || everyN < 1
        if (titleError || everyNError) {
            uiState = state.copy(titleError = titleError, everyNError = everyNError)
            return
        }

        uiState = state.copy(isSaving = true)
        viewModelScope.launch {
            val fields = { daily: Daily ->
                daily.copy(
                    title = title,
                    note = state.note.trim(),
                    startDate = state.startDate,
                    repeatType = state.repeatType,
                    everyN = everyN,
                    weekdays = if (state.repeatType == RepeatType.WEEKLY) state.weekdays else emptySet(),
                )
            }
            if (dailyId == 0L) {
                val now = System.currentTimeMillis()
                dailyId = repository.createDaily(
                    fields(Daily(title = title, startDate = state.startDate, createdAt = now, updatedAt = now)),
                )
            } else {
                val base = repository.getDaily(dailyId)
                if (base != null) repository.updateDaily(fields(base))
            }
            // The schedule may now cover dates that had no occurrence yet.
            occurrenceManager.generateDueOccurrences(today = DailyDates.today())
            uiState = uiState.copy(isSaving = false)
            onSaved()
        }
    }

    /** Occurrence only: completion cascades to this day's subtasks. */
    fun setCompleted(completed: Boolean) {
        if (mode != DailyEditorMode.OCCURRENCE) return
        viewModelScope.launch {
            repository.setOccurrenceCompleted(occurrenceId, completed)
            uiState = uiState.copy(isCompleted = completed)
            refreshSubtasks()
        }
    }

    /** Occurrence only: skip/unskip never touches the series or siblings. */
    fun setSkipped(skipped: Boolean) {
        if (mode != DailyEditorMode.OCCURRENCE) return
        viewModelScope.launch {
            repository.setOccurrenceSkipped(occurrenceId, skipped)
            uiState = uiState.copy(isSkipped = skipped)
            refreshSubtasks()
        }
    }

    fun setSubtaskCompleted(subtaskId: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.setSubtaskCompleted(subtaskId, completed)
            refreshSubtasks()
        }
    }

    fun addSubtask(title: String, note: String, deadline: Long?, onResult: (Boolean) -> Unit) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty() || mode != DailyEditorMode.OCCURRENCE) {
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

    /** Series delete keeps past occurrences; occurrence delete is one day only. */
    fun delete(onDeleted: () -> Unit) {
        if (!isEditing) return
        viewModelScope.launch {
            if (mode == DailyEditorMode.SERIES) {
                repository.deleteSeries(dailyId, DailyDates.today())
            } else {
                repository.deleteOccurrence(occurrenceId)
            }
            onDeleted()
        }
    }

    private suspend fun refreshSubtasks() {
        if (mode != DailyEditorMode.OCCURRENCE) {
            subtasks = emptyList()
            return
        }
        subtasks = repository.getSubtasks(occurrenceId)
        val occurrence = repository.getOccurrence(occurrenceId)
        if (occurrence != null) {
            uiState = uiState.copy(
                isCompleted = occurrence.isCompleted,
                isSkipped = occurrence.isSkipped,
            )
        }
    }
}
