package com.example.mnlists.ui.calendar

import android.content.Context
import androidx.room.Room
import com.example.mnlists.data.daily.DailyOccurrenceManager
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.local.entity.HabitLog
import com.example.mnlists.data.local.entity.Todo
import com.example.mnlists.data.repository.DailyRepository
import com.example.mnlists.data.repository.HabitRepository
import com.example.mnlists.data.repository.TodoRepository
import com.example.mnlists.data.reset.HabitResetManager
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Step 12 runtime proof without a device: real Room, real repositories and the
 * real Calendar ViewModel. Covers the "defaults to today" rule and the
 * per-day task/habit filtering including completed history.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CalendarFeatureTest {

    private lateinit var database: MnlistsDatabase
    private lateinit var todoRepository: TodoRepository
    private lateinit var dailyRepository: DailyRepository
    private lateinit var habitRepository: HabitRepository
    private lateinit var occurrenceManager: DailyOccurrenceManager
    private lateinit var resetManager: HabitResetManager

    private val today: Long = CalendarDates.today()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, MnlistsDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(DIRECT_EXECUTOR)
            .setTransactionExecutor(DIRECT_EXECUTOR)
            .build()
        todoRepository = TodoRepository(
            database = database,
            todoDao = database.todoDao(),
            subtaskDao = database.subtaskDao(),
            reminderDao = database.reminderDao(),
        )
        dailyRepository = DailyRepository(
            database = database,
            dailyDao = database.dailyDao(),
            occurrenceDao = database.dailyOccurrenceDao(),
            subtaskDao = database.subtaskDao(),
            reminderDao = database.reminderDao(),
        )
        habitRepository = HabitRepository(database.habitDao(), database.habitLogDao())
        occurrenceManager = DailyOccurrenceManager(database)
        resetManager = HabitResetManager(database)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = CalendarViewModel(
        todoRepository = todoRepository,
        dailyRepository = dailyRepository,
        habitRepository = habitRepository,
        occurrenceManager = occurrenceManager,
        habitResetManager = resetManager,
    )

    private fun startOfDayMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private suspend fun awaitState(
        viewModel: CalendarViewModel,
        predicate: (CalendarUiState) -> Boolean,
    ): CalendarUiState = withTimeout(5_000) {
        viewModel.uiState.first { !it.isLoading && predicate(it) }
    }

    @Test
    fun openingCalendar_defaultsToTodayAndShowsTodaysTasksAndHabits() = runBlocking {
        val todoId = todoRepository.createTodo(Todo(title = "File taxes", dueDate = today))
        val dailyId = dailyRepository.createDaily(
            Daily(title = "Morning pages", startDate = today),
        )
        habitRepository.createHabit(
            Habit(
                title = "Read",
                currentCount = 3,
                allowUp = true,
                allowDown = true,
                lastResetAt = startOfDayMillis(today),
                createdAt = startOfDayMillis(today - 30),
            ),
        )

        val viewModel = viewModel()
        viewModel.onOpened()

        val state = awaitState(viewModel) { it.openTasks.size == 2 }

        assertEquals("calendar defaults to today", today, state.selectedDate)
        assertEquals(today, state.today)
        assertEquals(CalendarDates.yearMonth(today), state.visibleMonth)
        assertEquals(
            setOf("File taxes", "Morning pages"),
            state.openTasks.map { it.title }.toSet(),
        )
        assertTrue(state.completedTasks.isEmpty())
        assertEquals(1, state.habits.size)
        assertEquals(3, state.habits.single().count)
        // Today's occurrence was generated by onOpened, not pre-seeded.
        assertEquals(1, dailyRepository.getOccurrencesForDate(today).size)
        assertNotNull(todoRepository.getTodo(todoId))
        assertNotNull(dailyId)
    }

    @Test
    fun selectingPastDate_showsCompletedHistoryAndLoggedHabitCounts() = runBlocking {
        val past = today - 3

        val todoId = todoRepository.createTodo(Todo(title = "Old errand", dueDate = past))
        todoRepository.setCompleted(todoId, true)

        val dailyId = dailyRepository.createDaily(Daily(title = "Old daily", startDate = past))
        val occurrenceId = dailyRepository.ensureOccurrence(dailyId, past)!!.id
        dailyRepository.setOccurrenceCompleted(occurrenceId, true)

        val habitId = habitRepository.createHabit(
            Habit(
                title = "Read",
                currentCount = 1,
                allowUp = true,
                allowDown = true,
                lastResetAt = startOfDayMillis(today),
                createdAt = startOfDayMillis(past - 10),
            ),
        )
        habitRepository.appendLog(
            HabitLog(
                habitId = habitId,
                date = past,
                countAtEndOfCycle = 4,
                cycleStart = startOfDayMillis(past),
                cycleEnd = startOfDayMillis(past + 1),
            ),
        )

        val viewModel = viewModel()
        viewModel.onOpened()
        viewModel.selectDate(past)

        val state = awaitState(viewModel) { it.selectedDate == past && it.completedTasks.size == 2 }

        assertEquals("history keeps both completed tasks", 2, state.completedTasks.size)
        assertEquals(setOf("Old errand", "Old daily"), state.completedTasks.map { it.title }.toSet())
        assertTrue(state.completedTasks.all { it.isCompleted })
        assertTrue(state.openTasks.isEmpty())
        assertEquals(4, state.habits.single().count)
        assertFalse("past counts come from the log, not the live counter", state.habits.single().adjustsCurrentCount)
    }

    @Test
    fun taskCheckbox_reflectsInTheSharedRepositories() = runBlocking {
        val todoId = todoRepository.createTodo(Todo(title = "Water plants", dueDate = today))
        val dailyId = dailyRepository.createDaily(Daily(title = "Stretch", startDate = today))

        val viewModel = viewModel()
        viewModel.onOpened()
        val before = awaitState(viewModel) { it.openTasks.size == 2 }

        val todoTask = before.openTasks.single { it.kind == CalendarTaskKind.TODO }
        viewModel.setTaskCompleted(todoTask, true)
        assertTrue(todoRepository.getTodo(todoId)!!.isCompleted)

        val dailyTask = before.openTasks.single { it.kind == CalendarTaskKind.DAILY }
        viewModel.setTaskCompleted(dailyTask, true)
        val occurrence = dailyRepository.getOccurrencesForDate(today).single()
        assertTrue(occurrence.isCompleted)

        viewModel.setTaskCompleted(todoTask, false)
        assertFalse(todoRepository.getTodo(todoId)!!.isCompleted)
        assertTrue("unchecking a to-do leaves the daily untouched", dailyRepository.getOccurrence(occurrence.id)!!.isCompleted)
        assertNotNull(dailyId)
    }

    @Test
    fun habitButtons_adjustTheLiveCycleToday_andTheLogOnPastDays() = runBlocking {
        val habitId = habitRepository.createHabit(
            Habit(
                title = "Read",
                currentCount = 2,
                allowUp = true,
                allowDown = true,
                lastResetAt = startOfDayMillis(today),
                createdAt = startOfDayMillis(today - 20),
            ),
        )
        val past = today - 2
        val logId = habitRepository.appendLog(
            HabitLog(
                habitId = habitId,
                date = past,
                countAtEndOfCycle = 5,
                cycleStart = startOfDayMillis(past),
                cycleEnd = startOfDayMillis(past + 1),
            ),
        )

        val viewModel = viewModel()
        viewModel.onOpened()
        val todayState = awaitState(viewModel) { it.selectedDate == today && it.habits.size == 1 }
        viewModel.incrementHabit(todayState.habits.single())
        assertEquals(3, habitRepository.getHabit(habitId)!!.currentCount)

        viewModel.selectDate(past)
        val pastState = awaitState(viewModel) {
            it.selectedDate == past && it.habits.singleOrNull()?.count == 5
        }
        viewModel.incrementHabit(pastState.habits.single())

        val log = database.habitLogDao().getById(logId)!!
        assertEquals("past-day +/- edits the logged cycle", 6, log.countAtEndOfCycle)
        assertEquals("the live counter is untouched", 3, habitRepository.getHabit(habitId)!!.currentCount)
    }

    @Test
    fun selectingADateAndReturning_resetsTheHighlightToToday() = runBlocking {
        val viewModel = viewModel()
        viewModel.onOpened()
        viewModel.selectDate(today - 10)
        awaitState(viewModel) { it.selectedDate == today - 10 }

        // Re-entering the tab runs onOpened() again; the Calendar must default to today.
        viewModel.onOpened()
        val afterTabEntry = awaitState(viewModel) { it.selectedDate == today }
        assertEquals(today, afterTabEntry.selectedDate)
        assertEquals(CalendarDates.yearMonth(today), afterTabEntry.visibleMonth)

        viewModel.selectDate(today - 10)
        awaitState(viewModel) { it.selectedDate == today - 10 }
        viewModel.goToToday()

        val state = awaitState(viewModel) { it.selectedDate == today }
        assertEquals(today, state.selectedDate)
        assertEquals(CalendarDates.yearMonth(today), state.visibleMonth)
    }

    @Test
    fun monthPaging_keepsTheSelectionInsideTheVisibleMonth() {
        val viewModel = viewModel()
        viewModel.selectDate(LocalDate.of(2026, 1, 31).toEpochDay())

        viewModel.showNextMonth()

        assertEquals(CalendarDates.yearMonth(LocalDate.of(2026, 2, 1).toEpochDay()), viewModel.visibleMonth.value)
        assertEquals(
            "Jan 31 clamps to the last day of February",
            LocalDate.of(2026, 2, 28).toEpochDay(),
            viewModel.selectedDate.value,
        )
    }

    private companion object {
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
