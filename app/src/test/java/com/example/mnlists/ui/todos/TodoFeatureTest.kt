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
import com.example.mnlists.data.reminder.FakeReminderAlarms
import com.example.mnlists.data.reminder.ReminderScheduler
import com.example.mnlists.data.reminder.testReminderScheduler
import com.example.mnlists.data.repository.DailyRepository
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
    private lateinit var dailyRepository: DailyRepository
    private lateinit var reminderScheduler: ReminderScheduler
    private lateinit var alarms: FakeReminderAlarms

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
        dailyRepository = DailyRepository(
            database = database,
            dailyDao = database.dailyDao(),
            occurrenceDao = database.dailyOccurrenceDao(),
            subtaskDao = database.subtaskDao(),
            reminderDao = database.reminderDao(),
        )
        alarms = FakeReminderAlarms()
        reminderScheduler = testReminderScheduler(database, repository, dailyRepository, alarms)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun editor_requiresOnlyTitle_thenCreatesTodoWithoutDueDate() = runBlocking {
        val viewModel = TodoEditorViewModel(repository, reminderScheduler, SavedStateHandle())
        var saved = false

        // A title is still required.
        viewModel.save { saved = true }
        assertTrue("title must be flagged", viewModel.uiState.titleError)
        assertFalse(saved)
        assertTrue(repository.observeTodos().first().isEmpty())

        // The due date is optional: a title alone now saves.
        viewModel.setTitle("  Buy milk  ")
        viewModel.save { saved = true }

        assertTrue(saved)
        assertFalse(viewModel.uiState.isSaving)
        val todo = repository.observeTodos().first().single()
        assertEquals("Buy milk", todo.title)
        assertNull("no due date is a valid saved state", todo.dueDate)
        assertFalse(todo.isCompleted)
        assertTrue("new rows are appended", todo.sortOrder > 0.0)
    }

    @Test
    fun editor_canClearAnExistingDueDate() = runBlocking {
        val id = repository.createTodo(
            Todo(title = "Buy milk", dueDate = TodoDates.today(), sortOrder = 1.0),
        )
        val editor = TodoEditorViewModel(
            repository,
            reminderScheduler,
            SavedStateHandle(mapOf(MnlistsRoutes.TODO_ID_ARG to id)),
        )

        assertEquals(TodoDates.today(), editor.uiState.dueDate)

        editor.setDueDate(null)
        editor.save {}

        assertNull(repository.getTodo(id)!!.dueDate)
    }

    @Test
    fun editor_loadsEdits_andDeletesWithCascade() = runBlocking {
        val creator = TodoEditorViewModel(repository, reminderScheduler, SavedStateHandle())
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
            reminderScheduler,
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
    fun subtaskCascade_allCheckedChecksParent_andParentToggleCascades() = runBlocking {
        val todoId = repository.createTodo(Todo(title = "Plan trip", sortOrder = 1.0))
        val first = repository.addSubtask(
            Subtask(parentType = ParentType.TODO, parentId = todoId, title = "Book flights"),
        )
        val second = repository.addSubtask(
            Subtask(parentType = ParentType.TODO, parentId = todoId, title = "Pack bags"),
        )

        // One open child keeps the parent open.
        repository.setSubtaskCompleted(first, completed = true)
        assertFalse(repository.getTodo(todoId)!!.isCompleted)

        // All children done → parent done (SPEC §8).
        repository.setSubtaskCompleted(second, completed = true)
        assertTrue(repository.getTodo(todoId)!!.isCompleted)
        assertNotNull(repository.getTodo(todoId)!!.completedAt)

        // Parent unchecked → every child unchecked.
        repository.setCompleted(todoId, completed = false)
        assertTrue(repository.getSubtasks(todoId).none { it.isCompleted })

        // Parent checked → every child checked.
        repository.setCompleted(todoId, completed = true)
        assertTrue(repository.getSubtasks(todoId).all { it.isCompleted })
    }

    @Test
    fun addingOpenSubtask_reopensCompletedParent_andDeletingReevaluates() = runBlocking {
        val todoId = repository.createTodo(Todo(title = "Plan trip", sortOrder = 1.0))
        val first = repository.addSubtask(
            Subtask(parentType = ParentType.TODO, parentId = todoId, title = "Book flights"),
        )
        repository.setSubtaskCompleted(first, completed = true)
        assertTrue(repository.getTodo(todoId)!!.isCompleted)

        // A new open child means "all done" no longer holds.
        val second = repository.addSubtask(
            Subtask(parentType = ParentType.TODO, parentId = todoId, title = "Pack bags"),
        )
        assertFalse(repository.getTodo(todoId)!!.isCompleted)

        repository.setSubtaskCompleted(second, completed = true)
        assertTrue(repository.getTodo(todoId)!!.isCompleted)

        // Removing a done child leaves only done children, so the parent stays done.
        repository.deleteSubtask(second)
        assertTrue(repository.getTodo(todoId)!!.isCompleted)
    }

    @Test
    fun editorParentToggle_updatesSubtaskList_andUiState() = runBlocking {
        val todoId = repository.createTodo(Todo(title = "Plan trip", sortOrder = 1.0))
        repository.addSubtask(
            Subtask(parentType = ParentType.TODO, parentId = todoId, title = "Book flights"),
        )
        val editor = TodoEditorViewModel(
            repository,
            reminderScheduler,
            SavedStateHandle(mapOf(MnlistsRoutes.TODO_ID_ARG to todoId)),
        )

        assertEquals(1, editor.subtasks.size)
        assertFalse(editor.uiState.isCompleted)

        editor.setParentCompleted(completed = true)
        assertTrue(editor.uiState.isCompleted)
        assertTrue("parent → children", editor.subtasks.all { it.isCompleted })

        editor.setSubtaskCompleted(editor.subtasks.first().id, completed = false)
        assertFalse("child unchecked → parent unchecked", editor.uiState.isCompleted)
    }

    @Test
    fun editor_addsEditsAndDeletesSubtasks() = runBlocking {
        val todoId = repository.createTodo(Todo(title = "Plan trip", sortOrder = 1.0))
        val editor = TodoEditorViewModel(
            repository,
            reminderScheduler,
            SavedStateHandle(mapOf(MnlistsRoutes.TODO_ID_ARG to todoId)),
        )

        var added = false
        editor.addSubtask("  Book flights ", "  window seat ", TodoDates.today() + 1) { added = it }
        assertTrue(added)
        val child = repository.getSubtasks(todoId).single()
        assertEquals("Book flights", child.title)
        assertEquals("window seat", child.note)
        assertEquals(TodoDates.today() + 1, child.deadline)
        assertEquals(1, editor.subtasks.size)

        var updated = false
        editor.updateSubtask(child.id, "Book train", "", null) { updated = it }
        assertTrue(updated)
        val changed = repository.getSubtask(child.id)!!
        assertEquals("Book train", changed.title)
        assertEquals("", changed.note)
        assertNull(changed.deadline)

        editor.deleteSubtask(child.id)
        assertTrue(repository.getSubtasks(todoId).isEmpty())
        assertTrue(editor.subtasks.isEmpty())
    }

    @Test
    fun editor_addingFirstSubtask_commitsNewParent() = runBlocking {
        val editor = TodoEditorViewModel(repository, reminderScheduler, SavedStateHandle())
        editor.setTitle("Plan trip")
        editor.setNote("  in spring ")

        var added = false
        editor.addSubtask("Book flights", "", null) { added = it }

        assertTrue(added)
        val parent = repository.observeTodos().first().single()
        assertEquals("Plan trip", parent.title)
        assertEquals("in spring", parent.note)
        assertNull(parent.dueDate)
        assertEquals(1, repository.getSubtasks(parent.id).size)
        assertTrue("the parent now exists, so the editor is in edit mode", editor.isEditing)
    }

    @Test
    fun editor_addsUpdatesAndDeletesTodoReminder() = runBlocking {
        val todoId = repository.createTodo(Todo(title = "Renew passport", sortOrder = 1.0))
        val editor = TodoEditorViewModel(
            repository,
            reminderScheduler,
            SavedStateHandle(mapOf(MnlistsRoutes.TODO_ID_ARG to todoId)),
        )
        val day = TodoDates.today()

        editor.addReminder(day + 1, 8 * 60)

        val reminder = editor.reminders.single()
        assertEquals(ReminderParentType.TODO, reminder.parentType)
        assertEquals(todoId, reminder.parentId)
        assertEquals(8 * 60, reminder.timeOfDayMinutes)
        assertEquals(reminder.triggerAt, alarms.scheduled[reminder.notificationId])

        editor.updateReminder(reminder.id, day + 2, 10 * 60)

        val updated = editor.reminders.single()
        assertEquals(10 * 60, updated.timeOfDayMinutes)
        assertTrue(updated.triggerAt > reminder.triggerAt)
        assertEquals(updated.triggerAt, alarms.scheduled[updated.notificationId])

        editor.deleteReminder(updated.id)

        assertTrue(editor.reminders.isEmpty())
        assertTrue(repository.observeTodos().first().isNotEmpty())
        assertTrue(alarms.cancelled.contains(updated.notificationId))
    }

    @Test
    fun editor_reminderNeedsATitleAndNeverLeaksIntoTheDatabase() = runBlocking {
        val editor = TodoEditorViewModel(repository, reminderScheduler, SavedStateHandle())

        editor.addReminder(TodoDates.today() + 1, 9 * 60)

        assertTrue(editor.uiState.titleError)
        assertTrue(editor.reminders.isEmpty())
        assertTrue(database.reminderDao().getAll().isEmpty())
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun viewModelMove_reordersOpenSectionOnly_andPersistsSortOrder() = runBlocking {
        val first = createTodo("First", 0.0)
        val second = createTodo("Second", 1.0)
        val third = createTodo("Third", 2.0)
        val done = repository.createTodo(
            Todo(
                title = "Done",
                dueDate = TodoDates.today(),
                sortOrder = 3.0,
                isCompleted = true,
            ),
        )
        val viewModel = TodosViewModel(repository)
        assertEquals(4, viewModel.todos.first { it.isNotEmpty() }.size)

        viewModel.moveTodo(first, third)

        assertEquals(
            listOf(second, third, first, done),
            repository.observeTodos().first().map { it.id },
        )

        // Completed rows keep their own section: cross-section moves are ignored.
        viewModel.moveTodo(done, second)
        assertEquals(
            listOf(second, third, first, done),
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
