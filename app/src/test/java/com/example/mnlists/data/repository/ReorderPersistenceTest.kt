package com.example.mnlists.data.repository

import android.content.Context
import androidx.room.Room
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.local.entity.Todo
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Step 10 proof that a drag-reorder is not just in-memory: the new `sortOrder`
 * values are written to Room, the database file is closed, reopened, and the
 * rows still come back in the dragged order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ReorderPersistenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TODOS_DB)
        context.deleteDatabase(HABITS_DB)
        context.deleteDatabase(OCCURRENCES_DB)
    }

    @Test
    fun todoReorder_persistsAcrossDatabaseReopen() = runBlocking {
        var database = open(TODOS_DB)
        var repository = todoRepository(database)
        val first = repository.createTodo(Todo(title = "First", sortOrder = 0.0))
        val second = repository.createTodo(Todo(title = "Second", sortOrder = 1.0))
        val third = repository.createTodo(Todo(title = "Third", sortOrder = 2.0))

        repository.reorderTodos(listOf(third, first, second))
        database.close()

        database = open(TODOS_DB)
        repository = todoRepository(database)
        val reloaded = repository.observeTodos().first()

        assertEquals(listOf(third, first, second), reloaded.map { it.id })
        assertEquals(listOf(0.0, 1.0, 2.0), reloaded.map { it.sortOrder })
        database.close()
        Unit
    }

    @Test
    fun habitReorder_persistsAcrossDatabaseReopen() = runBlocking {
        var database = open(HABITS_DB)
        var repository = habitRepository(database)
        val first = repository.createHabit(Habit(title = "First", sortOrder = 0.0))
        val second = repository.createHabit(Habit(title = "Second", sortOrder = 1.0))
        val third = repository.createHabit(Habit(title = "Third", sortOrder = 2.0))

        repository.reorderHabits(listOf(second, third, first))
        database.close()

        database = open(HABITS_DB)
        repository = habitRepository(database)
        val reloaded = repository.observeHabits().first()

        assertEquals(listOf(second, third, first), reloaded.map { it.id })
        assertEquals(listOf(0.0, 1.0, 2.0), reloaded.map { it.sortOrder })
        database.close()
        Unit
    }

    @Test
    fun dailyOccurrenceReorder_persistsAcrossDatabaseReopen() = runBlocking {
        val day = 20_000L
        var database = open(OCCURRENCES_DB)
        var repository = dailyRepository(database)
        val firstSeries = repository.createDaily(Daily(title = "First", startDate = day))
        val secondSeries = repository.createDaily(Daily(title = "Second", startDate = day))
        val thirdSeries = repository.createDaily(Daily(title = "Third", startDate = day))
        val first = repository.ensureOccurrence(firstSeries, day, sortOrder = 0.0)!!.id
        val second = repository.ensureOccurrence(secondSeries, day, sortOrder = 1.0)!!.id
        val third = repository.ensureOccurrence(thirdSeries, day, sortOrder = 2.0)!!.id

        repository.reorderOccurrences(listOf(second, first, third))
        database.close()

        database = open(OCCURRENCES_DB)
        repository = dailyRepository(database)
        val reloaded = repository.getOccurrencesForDate(day)

        assertEquals(listOf(second, first, third), reloaded.map { it.id })
        assertEquals(listOf(0.0, 1.0, 2.0), reloaded.map { it.sortOrder })
        database.close()
        Unit
    }

    private fun open(name: String): MnlistsDatabase =
        Room.databaseBuilder(context, MnlistsDatabase::class.java, name)
            .allowMainThreadQueries()
            .setQueryExecutor(DIRECT_EXECUTOR)
            .setTransactionExecutor(DIRECT_EXECUTOR)
            .build()

    private fun todoRepository(database: MnlistsDatabase) = TodoRepository(
        database = database,
        todoDao = database.todoDao(),
        subtaskDao = database.subtaskDao(),
        reminderDao = database.reminderDao(),
    )

    private fun habitRepository(database: MnlistsDatabase) =
        HabitRepository(database.habitDao(), database.habitLogDao())

    private fun dailyRepository(database: MnlistsDatabase) = DailyRepository(
        database = database,
        dailyDao = database.dailyDao(),
        occurrenceDao = database.dailyOccurrenceDao(),
        subtaskDao = database.subtaskDao(),
        reminderDao = database.reminderDao(),
    )

    private companion object {
        const val TODOS_DB = "reorder-todos-test.db"
        const val HABITS_DB = "reorder-habits-test.db"
        const val OCCURRENCES_DB = "reorder-occurrences-test.db"

        /** Runs executor work on the calling thread so Room calls stay synchronous. */
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
