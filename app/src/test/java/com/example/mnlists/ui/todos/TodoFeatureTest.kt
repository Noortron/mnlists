package com.example.mnlists.ui.todos

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.Reminder
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.local.entity.Todo
import com.example.mnlists.data.model.ParentType
import com.example.mnlists.data.model.ReminderParentType
import com.example.mnlists.data.repository.TodoRepository
import com.example.mnlists.ui.navigation.MnlistsRoutes
import java.util.concurrent.Executor
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
 * Step 6 runtime proof without a device: real Room (in-memory), the real
 * repository and the real editor/list ViewModels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TodoFeatureTest {

    private lateinit var context: Context
    private lateinit var database: MnlistsDatabase
    private lateinit var repository: TodoRepository

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
        repository = TodoRepository(
            database = database,
            todoDao = database.todoDao(),
            subtaskDao = database.subtaskDao(),
            reminderDao = database.reminderDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun editor_requiresTitleAndDueDate_thenCreatesTodo() = runBlocking {
        val viewModel = TodoEditorViewModel(repository, SavedStateHandle())
        var saved = false

        // A title alone is not enough: the due date is mandatory (SPEC §4.2).
        viewModel.setTitle("Buy milk")
        viewModel.save { saved = true }
        assertTrue("due date must be flagged", viewModel.uiState.dueDateError)
        assertFalse(saved)
        assertTrue(repository.observeTodos().first().isEmpty())

        // A date alone is not enough either.
        viewModel.setTitle("   ")
        viewModel.setDueDate(TodoDates.today())
        viewModel.save { saved = true }
        assertTrue("title must be flagged", viewModel.uiState.titleError)
        assertFalse(viewModel.uiState.dueDateError)
        assertFalse(saved)
        assertTrue(repository.observeTodos().first().isEmpty())

        // Both together save; the title is trimmed and the row gets an order.
        viewModel.setTitle("  Buy milk  ")
        viewModel.save { saved = true }

        assertTrue(saved)
        assertFalse(viewModel.uiState.isSaving)
        val todo = repository.observeTodos().first().single()
        assertEquals("Buy milk", todo.title)
        assertEquals(TodoDates.today(), todo.dueDate)
        assertFalse(todo.isCompleted)
        assertTrue("new rows are appended", todo.sortOrder > 0.0)
    }

    @Test
    fun editor_loadsEdits_andDeletesWithCascade() = runBlocking {
        val creator = TodoEditorViewModel(repository, SavedStateHandle())
        creator.setTitle("Plan trip")
        creator.setDueDate(TodoDates.today())
        creator.save {}
        val id = repository.observeTodos().first().single().id

        // Children that must disappear with the parent (SPEC §4.7).
        database.subtaskDao().insert(
            Subtask(parentType = ParentType.TODO, parentId = id, title = "Book flights"),
        )
        database.reminderDao().insert(
            Reminder(
                parentType = ReminderParentType.TODO,
                parentId = id,
                triggerAt = System.currentTimeMillis() + 60_000,
                notificationId = 42,
            ),
        )
        // A completed to-do stays completed when its text is edited.
        repository.setCompleted(id, completed = true)

        val editor = TodoEditorViewModel(
            repository,
            SavedStateHandle(mapOf(MnlistsRoutes.TODO_ID_ARG to id)),
        )

        assertTrue(editor.isEditing)
        assertEquals("Plan trip", editor.uiState.title)
        assertEquals(TodoDates.today(), editor.uiState.dueDate)

        editor.setTitle("Plan Japan trip")
        editor.setNote("  Book with the points  ")
        editor.setDueDate(TodoDates.today() + 3)
        editor.save {}

        val updated = repository.getTodo(id)!!
        assertEquals("Plan Japan trip", updated.title)
        assertEquals("Book with the points", updated.note)
        assertEquals(TodoDates.today() + 3, updated.dueDate)
        assertTrue("completion survives editing", updated.isCompleted)

        var deleted = false
        editor.delete { deleted = true }

        assertTrue(deleted)
        assertNull(repository.getTodo(id))
        assertEquals(0, database.subtaskDao().countForParent(ParentType.TODO, id))
        assertTrue(database.reminderDao().getForParent(ReminderParentType.TODO, id).isEmpty())
    }

    @Test
    fun completedTodosSortBelowOpenOnes_andUncheckingRestoresOrder() = runBlocking {
        val first = createTodo("First", order = 1.0)
        val second = createTodo("Second", order = 2.0)
        val third = createTodo("Third", order = 3.0)

        val viewModel = TodosViewModel(repository)
        viewModel.setCompleted(first, completed = true)

        val completedRow = repository.getTodo(first)!!
        assertTrue(completedRow.isCompleted)
        assertNotNull("completedAt is stamped on check", completedRow.completedAt)

        // Completed items are moved below every open item (SPEC §4.6/§8).
        assertEquals(
            listOf(second, third, first),
            repository.observeTodos().first().map { it.id },
        )

        viewModel.setCompleted(first, completed = false)

        assertNull("completedAt is cleared on uncheck", repository.getTodo(first)!!.completedAt)
        assertEquals(
            listOf(first, second, third),
            repository.observeTodos().first().map { it.id },
        )
    }

    @Test
    fun datePickerConversions_roundTripAnEpochDay() {
        val day = TodoDates.today()
        assertEquals(day, TodoDates.fromUtcMillis(TodoDates.toUtcMillis(day)))
    }

    private suspend fun createTodo(title: String, order: Double): Long {
        val now = order.toLong() * 1_000L
        return repository.createTodo(
            Todo(
                title = title,
                dueDate = TodoDates.today(),
                sortOrder = order,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private companion object {
        /** Keeps Room's executor work on the calling thread so tests stay synchronous. */
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
