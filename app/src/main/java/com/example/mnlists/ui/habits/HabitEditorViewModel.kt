package com.example.mnlists.ui.habits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.model.ResetSchedule
import com.example.mnlists.data.repository.HabitRepository
import com.example.mnlists.data.settings.HabitDefaults
import com.example.mnlists.ui.navigation.MnlistsRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Editable form state for creating or editing a single habit. */
data class HabitEditorUiState(
    val title: String = "",
    val note: String = "",
    val schedule: ResetSchedule = ResetSchedule.DAILY,
    val everyN: String = "1",
    val weekday: DayOfWeek? = null,
    val allowUp: Boolean = true,
    val allowDown: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val titleError: Boolean = false,
    val everyNError: Boolean = false,
    val directionsError: Boolean = false,
)

/**
 * Create/edit form for a habit. `habitId == 0` (or missing) means "new habit";
 * any other value loads the existing row from Room.
 */
@HiltViewModel
class HabitEditorViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    savedStateHandle: SavedStateHandle,
    private val habitDefaults: HabitDefaults,
) : ViewModel() {

    private val habitId: Long = savedStateHandle.get<Long>(MnlistsRoutes.HABIT_ID_ARG) ?: 0L
    private var existingHabit: Habit? = null

    /** Never let a slow preference read overwrite a schedule the user just picked. */
    private var scheduleTouched = false

    var uiState by mutableStateOf(HabitEditorUiState(isLoading = habitId > 0L))
        private set

    val isEditing: Boolean get() = habitId > 0L

    init {
        if (habitId > 0L) {
            load()
        } else {
            loadDefaultSchedule()
        }
    }

    private fun loadDefaultSchedule() {
        viewModelScope.launch {
            val default = habitDefaults.defaultResetSchedule.first()
            if (!scheduleTouched && existingHabit == null) {
                uiState = uiState.copy(schedule = default)
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val habit = habitRepository.getHabit(habitId)
            existingHabit = habit
            uiState = if (habit == null) {
                uiState.copy(isLoading = false)
            } else {
                uiState.copy(
                    isLoading = false,
                    title = habit.title,
                    note = habit.note,
                    schedule = habit.resetSchedule,
                    everyN = habit.resetEveryN.toString(),
                    weekday = habit.resetWeekday,
                    allowUp = habit.allowUp,
                    allowDown = habit.allowDown,
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

    fun setSchedule(schedule: ResetSchedule) {
        scheduleTouched = true
        uiState = uiState.copy(
            schedule = schedule,
            everyNError = false,
            // Weekday only applies to weekly resets.
            weekday = if (schedule == ResetSchedule.WEEKLY) uiState.weekday else null,
        )
    }

    fun setEveryN(value: String) {
        if (value.length > 3 || value.any { !it.isDigit() }) return
        uiState = uiState.copy(everyN = value, everyNError = false)
    }

    fun setWeekday(weekday: DayOfWeek?) {
        uiState = uiState.copy(weekday = weekday)
    }

    fun setAllowUp(value: Boolean) {
        uiState = uiState.copy(allowUp = value, directionsError = false)
    }

    fun setAllowDown(value: Boolean) {
        uiState = uiState.copy(allowDown = value, directionsError = false)
    }

    fun save(onSaved: () -> Unit) {
        if (uiState.isSaving) return

        val state = uiState
        val title = state.title.trim()
        val everyN = state.everyN.toIntOrNull()
        val titleError = title.isEmpty()
        val everyNError = state.schedule == ResetSchedule.EVERY_N && (everyN == null || everyN < 1)
        val directionsError = !state.allowUp && !state.allowDown

        if (titleError || everyNError || directionsError) {
            uiState = state.copy(
                titleError = titleError,
                everyNError = everyNError,
                directionsError = directionsError,
            )
            return
        }

        uiState = state.copy(isSaving = true)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val fields = { habit: Habit ->
                habit.copy(
                    title = title,
                    note = state.note.trim(),
                    resetSchedule = state.schedule,
                    resetEveryN = if (state.schedule == ResetSchedule.EVERY_N) everyN ?: 1 else 1,
                    resetWeekday = if (state.schedule == ResetSchedule.WEEKLY) state.weekday else null,
                    allowUp = state.allowUp,
                    allowDown = state.allowDown,
                )
            }

            val base = existingHabit
            if (base == null) {
                habitRepository.createHabit(
                    fields(
                        Habit(
                            title = title,
                            lastResetAt = now,
                            createdAt = now,
                            updatedAt = now,
                            sortOrder = habitRepository.nextSortOrder(),
                        ),
                    ),
                )
            } else {
                habitRepository.updateHabit(fields(base))
            }
            uiState = uiState.copy(isSaving = false)
            onSaved()
        }
    }

    /** Deleting a habit keeps its history (SPEC §9 default). */
    fun delete(onDeleted: () -> Unit) {
        if (habitId <= 0L) return
        viewModelScope.launch {
            habitRepository.deleteHabit(habitId)
            onDeleted()
        }
    }
}
