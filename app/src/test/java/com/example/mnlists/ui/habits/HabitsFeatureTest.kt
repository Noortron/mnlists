package com.example.mnlists.ui.habits

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.local.entity.HabitLog
import com.example.mnlists.data.model.ResetSchedule
import com.example.mnlists.data.repository.HabitRepository
import com.example.mnlists.data.reset.HabitResetManager
import com.example.mnlists.ui.navigation.MnlistsRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Step 4 runtime verification without a device: real Room (in-memory and on-disk
 * via Robolectric), real repositories, real ViewModels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HabitsFeatureTest {

    private lateinit var context: Context
    private lateinit var database: MnlistsDatabase
    private lateinit var repository: HabitRepository
    private lateinit var resetManager: HabitResetManager

    @Before
    fun setUp() {
        // Unconfined main + direct Room executors make ViewModel launches and
        // Room's suspend DAO calls run inline, so assertions see final state.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, MnlistsDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(DIRECT_EXECUTOR)
            .setTransactionExecutor(DIRECT_EXECUTOR)
            .build()
        repository = HabitRepository(database.habitDao(), database.habitLogDao())
        resetManager = HabitResetManager(database)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun counter_respectsEnabledDirectionsAndFloorAtZero() = runBlocking {
        val viewModel = HabitsViewModel(repository, resetManager)

        val bothId = repository.createHabit(
            Habit(title = "Read", allowUp = true, allowDown = true),
        )
        viewModel.increment(bothId)
        assertEquals(1, repository.getHabit(bothId)!!.currentCount)
        viewModel.decrement(bothId)
        assertEquals(0, repository.getHabit(bothId)!!.currentCount)
        viewModel.decrement(bothId)
        assertEquals("counter never goes negative", 0, repository.getHabit(bothId)!!.currentCount)

        val upOnlyId = repository.createHabit(
            Habit(title = "Up only", allowUp = true, allowDown = false),
        )
        viewModel.decrement(upOnlyId)
        assertEquals(0, repository.getHabit(upOnlyId)!!.currentCount)
        viewModel.increment(upOnlyId)
        assertEquals(1, repository.getHabit(upOnlyId)!!.currentCount)

        val downOnlyId = repository.createHabit(
            Habit(title = "Down only", allowUp = false, allowDown = true, currentCount = 5),
        )
        viewModel.increment(downOnlyId)
        assertEquals("+ ignored when disabled", 5, repository.getHabit(downOnlyId)!!.currentCount)
        viewModel.decrement(downOnlyId)
        assertEquals(4, repository.getHabit(downOnlyId)!!.currentCount)

        // List state reflects the stored rows once collected.
        val rows = viewModel.habits.first { it.size == 3 }
        assertEquals(3, rows.size)
    }

    @Test
    fun editor_createsHabit_andRejectsInvalidInput() = runBlocking {
        val newVm = HabitEditorViewModel(repository, SavedStateHandle())
        newVm.setTitle("   ")
        var saved = false
        newVm.save { saved = true }
        assertFalse("empty title must block save", saved)
        assertTrue(newVm.uiState.titleError)

        newVm.setTitle("Meditate")
        newVm.setAllowUp(false)
        newVm.setAllowDown(false)
        newVm.save { saved = true }
        assertFalse("no enabled direction must block save", saved)
        assertTrue(newVm.uiState.directionsError)

        newVm.setAllowUp(true)
        newVm.setNote("10 minutes, morning")
        newVm.setSchedule(ResetSchedule.EVERY_N)
        newVm.setEveryN("2")
        newVm.save { saved = true }
        assertTrue(saved)

        val habits = repository.observeHabits().first()
        assertEquals(1, habits.size)
        val habit = habits.first()
        assertEquals("Meditate", habit.title)
        assertEquals("10 minutes, morning", habit.note)
        assertEquals(ResetSchedule.EVERY_N, habit.resetSchedule)
        assertEquals(2, habit.resetEveryN)
        assertEquals(0, habit.currentCount)
    }

    @Test
    fun editor_loadsExistingHabit_andSavesEdits() = runBlocking {
        val habitId = repository.createHabit(
            Habit(
                title = "Stretch",
                note = "before bed",
                resetSchedule = ResetSchedule.WEEKLY,
                allowUp = true,
                allowDown = true,
                currentCount = 3,
            ),
        )

        val editor = HabitEditorViewModel(
            repository,
            SavedStateHandle(mapOf(MnlistsRoutes.HABIT_ID_ARG to habitId)),
        )
        assertEquals("Stretch", editor.uiState.title)
        assertEquals("before bed", editor.uiState.note)
        assertEquals(ResetSchedule.WEEKLY, editor.uiState.schedule)
        assertEquals(3, repository.getHabit(habitId)!!.currentCount)

        editor.setNote("after waking up")
        editor.setSchedule(ResetSchedule.DAILY)
        var saved = false
        editor.save { saved = true }
        assertTrue(saved)

        val updated = repository.getHabit(habitId)!!
        assertEquals("Stretch", updated.title)
        assertEquals("after waking up", updated.note)
        assertEquals(ResetSchedule.DAILY, updated.resetSchedule)
        assertEquals("counter is preserved on edit", 3, updated.currentCount)
    }

    @Test
    fun delete_removesHabitButKeepsHistory() = runBlocking {
        val habitId = repository.createHabit(Habit(title = "Old habit"))
        repository.appendLog(
            HabitLog(
                habitId = habitId,
                date = 1L,
                countAtEndOfCycle = 3,
                cycleStart = 0L,
                cycleEnd = 1L,
            ),
        )

        val editor = HabitEditorViewModel(
            repository,
            SavedStateHandle(mapOf(MnlistsRoutes.HABIT_ID_ARG to habitId)),
        )
        var deleted = false
        editor.delete { deleted = true }

        assertTrue(deleted)
        assertNull(repository.getHabit(habitId))
        assertEquals("history survives deletion", 1, repository.getLogs(habitId).size)
        assertTrue(repository.observeHabits().first().isEmpty())
    }

    /** Proves persistence beyond the process: close the DB file and reopen it. */
    @Test
    fun habit_survivesDatabaseReopen() = runBlocking {
        context.deleteDatabase(REOPEN_DB)

        var fileDb = Room.databaseBuilder(context, MnlistsDatabase::class.java, REOPEN_DB)
            .allowMainThreadQueries()
            .build()
        val id = fileDb.habitDao().insert(Habit(title = "Persisted", currentCount = 2))
        fileDb.close()

        fileDb = Room.databaseBuilder(context, MnlistsDatabase::class.java, REOPEN_DB)
            .allowMainThreadQueries()
            .build()
        val reloaded = fileDb.habitDao().getById(id)
        assertNotNull(reloaded)
        assertEquals("Persisted", reloaded!!.title)
        assertEquals(2, reloaded.currentCount)
        fileDb.close()

        context.deleteDatabase(REOPEN_DB)
        Unit
    }

    private companion object {
        const val REOPEN_DB = "habit-persistence-test.db"

        /** Runs executor work on the calling thread so Room calls stay synchronous in tests. */
        val DIRECT_EXECUTOR = java.util.concurrent.Executor { it.run() }
    }
}
