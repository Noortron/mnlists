package com.example.mnlists.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mnlists.data.daily.DailyOccurrenceManager
import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.DailyOccurrence
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.local.entity.HabitLog
import com.example.mnlists.data.local.entity.Todo
import com.example.mnlists.data.repository.DailyRepository
import com.example.mnlists.data.repository.HabitRepository
import com.example.mnlists.data.repository.TodoRepository
import com.example.mnlists.data.reset.HabitResetManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
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

/** Everything the Calendar screen renders for the selected day and month. */
data class CalendarUiState(
    val selectedDate: Long = CalendarDates.today(),
    val today: Long = CalendarDates.today(),
    val visibleMonth: YearMonth = CalendarDates.yearMonth(today),
    val habits: List<CalendarHabit> = emptyList(),
    val openTasks: List<CalendarTask> = emptyList(),
    val skippedTasks: List<CalendarTask> = emptyList(),
    val completedTasks: List<CalendarTask> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean
        get() = habits.isEmpty() &&
            openTasks.isEmpty() &&
            skippedTasks.isEmpty() &&
            completedTasks.isEmpty()
}

/**
 * Backs the Calendar tab (SPEC §4.4, Step 12).
 *
 * Defaults to today so the day list is populated before the user taps
 * anything. Selecting a date (grid or month paging) re-queries that day's
 * occurrences through Room; tasks and habit history are filtered by
 * [CalendarDayModel]. The only mutations exposed are task check/uncheck and
 * habit +/− — no editor, deletion or drag paths exist here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val dailyRepository: DailyRepository,
    private val habitRepository: HabitRepository,
    private val occurrenceManager: DailyOccurrenceManager,
    private val habitResetManager: HabitResetManager,
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(CalendarDates.today())
    val selectedDate: StateFlow<Long> = _selectedDate.asStateFlow()

    private val _visibleMonth = MutableStateFlow(CalendarDates.yearMonth(_selectedDate.value))
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    private data class DaySources(
        val todos: List<Todo>,
        val dailies: List<Daily>,
        val occurrences: List<DailyOccurrence>,
        val habits: List<Habit>,
        val logs: List<HabitLog>,
    )

    private val occurrencesForDay = _selectedDate.flatMapLatest { date ->
        dailyRepository.observeOccurrencesForDate(date)
    }

    private val daySources = combine(
        todoRepository.observeTodos(),
        dailyRepository.observeDailies(),
        occurrencesForDay,
        habitRepository.observeHabits(),
        habitRepository.observeAllLogs(),
    ) { todos, dailies, occurrences, habits, logs ->
        DaySources(todos, dailies, occurrences, habits, logs)
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        _selectedDate,
        _visibleMonth,
        daySources,
    ) { date, month, sources ->
        val today = CalendarDates.today()
        val tasks = CalendarDayModel.tasksForDate(
            todos = sources.todos,
            occurrences = sources.occurrences,
            dailies = sources.dailies,
            date = date,
        )
        CalendarUiState(
            selectedDate = date,
            today = today,
            visibleMonth = month,
            habits = CalendarDayModel.habitsForDate(
                habits = sources.habits,
                logs = sources.logs,
                date = date,
                today = today,
            ),
            openTasks = tasks.filter { !it.isCompleted && !it.isSkipped },
            skippedTasks = tasks.filter { !it.isCompleted && it.isSkipped },
            completedTasks = tasks.filter { it.isCompleted },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(
            selectedDate = _selectedDate.value,
            visibleMonth = _visibleMonth.value,
        ),
    )

    /**
     * Every entry (tab open, return from another tab) resets the Calendar to
     * today per SPEC §4.4 and runs the same idempotent repair as app start so a
     * midnight rollover still has today's occurrences and habit cycles.
     */
    fun onOpened() {
        val today = CalendarDates.today()
        _selectedDate.value = today
        _visibleMonth.value = CalendarDates.yearMonth(today)
        viewModelScope.launch {
            occurrenceManager.generateDueOccurrences(today = today)
            habitResetManager.applyDueResets()
        }
    }

    fun selectDate(date: Long) {
        _selectedDate.value = date
        _visibleMonth.value = CalendarDates.yearMonth(date)
    }

    fun showPreviousMonth() = moveMonth(-1)

    fun showNextMonth() = moveMonth(1)

    /** Back to today's highlight and day list in one tap. */
    fun goToToday() {
        selectDate(CalendarDates.today())
    }

    /** Task check/uncheck from the day list; reflects in every other screen. */
    fun setTaskCompleted(task: CalendarTask, completed: Boolean) {
        if (task.isSkipped) return
        viewModelScope.launch {
            when (task.kind) {
                CalendarTaskKind.TODO -> todoRepository.setCompleted(task.parentId, completed)
                CalendarTaskKind.DAILY ->
                    dailyRepository.setOccurrenceCompleted(task.parentId, completed)
            }
        }
    }

    fun incrementHabit(item: CalendarHabit) = adjustHabit(item, delta = +1)

    fun decrementHabit(item: CalendarHabit) = adjustHabit(item, delta = -1)

    private fun moveMonth(delta: Long) {
        val next = _visibleMonth.value.plusMonths(delta)
        _visibleMonth.value = next
        if (CalendarDates.yearMonth(_selectedDate.value) != next) {
            _selectedDate.value = CalendarDates.dayInMonth(_selectedDate.value, next)
        }
    }

    private fun adjustHabit(item: CalendarHabit, delta: Int) {
        if (!item.isAdjustable) return
        viewModelScope.launch {
            if (item.adjustsCurrentCount) {
                habitRepository.adjustCount(item.habit.id, delta)
            } else {
                item.logId?.let { habitRepository.adjustLogCount(it, delta) }
            }
        }
    }
}
