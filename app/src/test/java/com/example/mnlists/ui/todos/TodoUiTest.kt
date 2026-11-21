package com.example.mnlists.ui.todos

import android.Manifest
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.local.entity.Todo
import com.example.mnlists.data.model.ParentType
import com.example.mnlists.data.reminder.ReminderScheduler
import com.example.mnlists.data.reminder.testReminderScheduler
import com.example.mnlists.data.repository.DailyRepository
import com.example.mnlists.data.repository.TodoRepository
import com.example.mnlists.ui.components.REMINDER_ADD_TAG
import com.example.mnlists.ui.components.REMINDER_CONFIRM_TAG
import com.example.mnlists.ui.navigation.MnlistsRoutes
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Compose-level verification of the To-Do screens: the real composables are
 * rendered on the JVM through Robolectric, backed by a real Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@OptIn(ExperimentalComposeUiApi::class)
class TodoUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: MnlistsDatabase
    private lateinit var repository: TodoRepository
    private lateinit var dailyRepository: DailyRepository
    private lateinit var reminderScheduler: ReminderScheduler

    @Before
    fun setUp() {
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
        reminderScheduler = testReminderScheduler(database, repository, dailyRepository)
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun list_splitsCompletedIntoItsOwnSection_andStrikesCompletedTitles() {
        var opened: Long? = null
        var toggled: Pair<Long, Boolean>? = null

        val today = TodoDates.today()
        val todos = listOf(
            Todo(
                id = 1L,
                title = "Buy milk",
                note = "2 litres",
                dueDate = today,
                sortOrder = 1.0,
            ),
            Todo(
                id = 2L,
                title = "Ship report",
                dueDate = today - 2,
                sortOrder = 2.0,
                isCompleted = true,
                completedAt = 1L,
            ),
        )

        compose.setContent {
            MaterialTheme {
                TodosScreen(
                    todos = todos,
                    onOpenTodo = { opened = it },
                    onToggleCompleted = { id, completed -> toggled = id to completed },
                )
            }
        }

        compose.onNodeWithText("To do").assertIsDisplayed()
        compose.onNodeWithText("Completed").assertIsDisplayed()
        compose.onNodeWithText("Buy milk").assertIsDisplayed()
        compose.onNodeWithText("2 litres").assertIsDisplayed()
        compose.onNodeWithText("Due today").assertIsDisplayed()

        // Geometry: open row above the "Completed" header, completed row below it.
        val openRowTop = compose.onNodeWithText("Buy milk").fetchSemanticsNode().boundsInRoot.top
        val headerTop = compose.onNodeWithText("Completed").fetchSemanticsNode().boundsInRoot.top
        val completedRowTop = compose.onNodeWithText("Ship report").fetchSemanticsNode().boundsInRoot.top
        assertTrue("open row must precede the Completed section", openRowTop < headerTop)
        assertTrue("completed row must sit below the header", headerTop < completedRowTop)

        // Completed rows are struck through, open rows are not (SPEC §4.6).
        assertStruckThrough("Ship report")
        assertNotStruckThrough("Buy milk")

        // Checkbox and title taps report the right row/state.
        compose.onNodeWithContentDescription("Mark Buy milk as done").performClick()
        assertEquals(1L to true, toggled)
        compose.onNodeWithContentDescription("Mark Ship report as not done").performClick()
        assertEquals(2L to false, toggled)
        compose.onNodeWithText("Buy milk").performClick()
        assertEquals(1L, opened)

        // The drag handle doubles as the collapse toggle (Step 10).
        compose.onNodeWithContentDescription("Collapse Buy milk").assertIsDisplayed()
    }

    @Test
    fun list_dragHandleCollapsesSubtasks() {
        val todo = Todo(id = 1L, title = "Plan trip", dueDate = TodoDates.today(), sortOrder = 0.0)
        val subtask = Subtask(
            id = 10L,
            parentType = ParentType.TODO,
            parentId = todo.id,
            title = "Book flights",
            sortOrder = 0.0,
        )

        compose.setContent {
            MaterialTheme {
                TodosScreen(
                    todos = listOf(todo),
                    subtasksByTodo = mapOf(todo.id to listOf(subtask)),
                    onOpenTodo = {},
                    onToggleCompleted = { _, _ -> },
                )
            }
        }

        // Expanded by default, then the same handle collapses and re-expands.
        compose.onNodeWithText("Book flights").assertIsDisplayed()
        compose.onNodeWithContentDescription("Collapse Plan trip").performClick()
        compose.onAllNodesWithText("Book flights").assertCountEquals(0)
        compose.onNodeWithContentDescription("Expand Plan trip").performClick()
        compose.onNodeWithText("Book flights").assertIsDisplayed()
    }

    @Test
    fun list_dragHandleStartsAfter250ms_andReportsTheDrop() {
        val today = TodoDates.today()
        val first = Todo(id = 11L, title = "Alpha", dueDate = today, sortOrder = 0.0)
        val second = Todo(id = 12L, title = "Beta", dueDate = today, sortOrder = 1.0)
        var moved: Pair<Long, Long>? = null

        compose.setContent {
            MaterialTheme {
                TodosScreen(
                    todos = listOf(first, second),
                    onOpenTodo = {},
                    onToggleCompleted = { _, _ -> },
                    onReorder = { from, to -> moved = from to to },
                )
            }
        }

        val rowHeight = compose.onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot.top -
            compose.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.top
        assertTrue("rows should be laid out", rowHeight > 0f)

        // Hold still past the 250ms timeout, then drag onto the next row. At
        // the old ~500ms platform default this would be treated as a plain tap.
        compose.onNodeWithContentDescription("Collapse Alpha").performTouchInput {
            down(center)
            move(delayMillis = 260)
            moveBy(Offset(0f, rowHeight), delayMillis = 16)
            up()
        }

        assertEquals(11L to 12L, moved)
    }

    @Test
    fun list_unplannedTodoShowsNoDate_andNeverLooksOverdue() {
        val today = TodoDates.today()
        val todos = listOf(
            Todo(id = 1L, title = "Unplanned task", dueDate = null, sortOrder = 1.0),
            Todo(id = 2L, title = "Late report", dueDate = today - 3, sortOrder = 2.0),
        )

        compose.setContent {
            MaterialTheme {
                TodosScreen(todos = todos, onOpenTodo = {}, onToggleCompleted = { _, _ -> })
            }
        }

        compose.onNodeWithText("Unplanned task").assertExists()
        compose.onNodeWithText("Late report").assertExists()

        // Only the dated row is labelled overdue; the unplanned row shows no date at all.
        compose.onAllNodesWithText("Overdue", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("Due", substring = true).assertCountEquals(0)
    }

    @Test
    fun editor_savesWithoutDueDate_andClearingADateStaysEmpty() = runBlocking {
        val viewModel = TodoEditorViewModel(repository, reminderScheduler, SavedStateHandle())
        var done = false

        compose.setContent {
            MaterialTheme {
                TodoEditorScreen(onDone = { done = true }, viewModel = viewModel)
            }
        }

        // A new to-do starts unplanned.
        compose.onNodeWithText("No deadline").assertIsDisplayed()

        viewModel.setTitle("Buy milk")

        // Picking a date and then clearing it must return to the empty state.
        compose.onNodeWithText("Today").performScrollTo().performClick()
        compose.onNodeWithText("Clear").performScrollTo().performClick()
        compose.onNodeWithText("No deadline").assertIsDisplayed()

        compose.onNodeWithText("Save").performScrollTo().performClick()

        assertTrue(done)
        val todo = repository.observeTodos().first().single()
        assertEquals("Buy milk", todo.title)
        assertNull("saving without a due date must be allowed", todo.dueDate)
    }

    @Test
    fun editor_loadsTodo_andDeletionAsksForCascadeConfirmation() = runBlocking {
        val dueDate = TodoDates.today() + 1
        val todoId = repository.createTodo(
            Todo(
                title = "Renew passport",
                note = "bring photos",
                dueDate = dueDate,
                sortOrder = 1.0,
            ),
        )
        val viewModel = TodoEditorViewModel(
            repository,
            reminderScheduler,
            SavedStateHandle(mapOf(MnlistsRoutes.TODO_ID_ARG to todoId)),
        )
        var done = false

        compose.setContent {
            MaterialTheme {
                TodoEditorScreen(onDone = { done = true }, viewModel = viewModel)
            }
        }

        compose.onNodeWithText("Renew passport").assertIsDisplayed()
        compose.onNodeWithText("bring photos").assertIsDisplayed()
        compose.onNodeWithText(TodoDates.format(dueDate)).assertIsDisplayed()

        // Cancel keeps the to-do; confirming removes it and finishes the screen.
        compose.onNodeWithText("Delete to-do").performScrollTo().performClick()
        compose.onNodeWithText("Delete to-do?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertNotNull(repository.getTodo(todoId))
        assertFalse(done)

        compose.onNodeWithText("Delete to-do").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        assertNull(repository.getTodo(todoId))
        assertTrue(done)
    }

    @Test
    fun editor_fullDatePicker_confirmingStoresThePickedDay() = runBlocking {
        val viewModel = TodoEditorViewModel(repository, reminderScheduler, SavedStateHandle())

        compose.setContent {
            MaterialTheme {
                TodoEditorScreen(onDone = {}, viewModel = viewModel)
            }
        }

        compose.onNodeWithText("No deadline").performClick()
        compose.onNodeWithText("OK").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()

        assertEquals(TodoDates.today(), viewModel.uiState.dueDate)
        compose.onNodeWithText(TodoDates.format(TodoDates.today())).assertIsDisplayed()
        Unit
    }

    @Test
    fun subtask_addEditDelete_withNoteAndDeadline() = runBlocking {
        val todoId = repository.createTodo(Todo(title = "Plan trip", sortOrder = 1.0))
        val viewModel = TodoEditorViewModel(
            repository,
            reminderScheduler,
            SavedStateHandle(mapOf(MnlistsRoutes.TODO_ID_ARG to todoId)),
        )
        applyEditor(viewModel)

        // Add: title + note + own deadline.
        compose.onNodeWithText("Add subtask").performScrollTo().performClick()
        compose.onNodeWithText("New subtask").assertIsDisplayed()
        compose.onNodeWithTag(SUBTASK_TITLE_TAG).performTextReplacement("Book flights")
        compose.onNodeWithTag(SUBTASK_NOTE_TAG).performTextReplacement("window seat")
        compose.onNodeWithTag(SUBTASK_DEADLINE_TOMORROW_TAG).performScrollTo().performClick()
        compose.onNodeWithText("Add").performScrollTo().performClick()

        // The inline form closes once the child is persisted.
        compose.onNodeWithText("Add subtask").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Book flights").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("window seat").assertExists()
        compose.onNodeWithText("Due tomorrow").assertExists()

        val stored = repository.getSubtasks(todoId).single()
        assertEquals("Book flights", stored.title)
        assertEquals("window seat", stored.note)
        assertEquals(TodoDates.today() + 1, stored.deadline)

        // Edit: rename and clear the deadline.
        compose.onNodeWithText("Book flights").performScrollTo().performClick()
        compose.onNodeWithText("Edit subtask").assertIsDisplayed()
        compose.onNodeWithTag(SUBTASK_TITLE_TAG).performTextReplacement("Book train")
        compose.onNodeWithTag(SUBTASK_DEADLINE_NONE_TAG).performScrollTo().performClick()
        compose.onNodeWithText("Save subtask").performScrollTo().performClick()

        compose.onNodeWithText("Book train").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Due tomorrow").assertCountEquals(0)
        val edited = repository.getSubtask(stored.id)!!
        assertEquals("Book train", edited.title)
        assertEquals("window seat", edited.note)
        assertNull(edited.deadline)

        // Delete with confirmation.
        compose.onNodeWithContentDescription("Delete Book train").performScrollTo().performClick()
        compose.onNodeWithText("Delete subtask?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()

        compose.onAllNodesWithText("Book train").assertCountEquals(0)
        assertTrue(repository.getSubtasks(todoId).isEmpty())
        Unit
    }

    @Test
    fun subtask_dragHandle_collapsesAndExpandsDetails() = runBlocking {
        val todoId = createTodoWithSubtask(
            title = "Book flights",
            note = "window seat",
            deadline = TodoDates.today() + 1,
        )
        applyEditor(editorFor(todoId))

        // Expanded by default.
        compose.onNodeWithText("Book flights").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("window seat").assertExists()
        compose.onNodeWithText("Due tomorrow").assertExists()

        compose.onNodeWithContentDescription("Collapse Book flights").performScrollTo().performClick()
        compose.onAllNodesWithText("window seat").assertCountEquals(0)
        compose.onAllNodesWithText("Due tomorrow").assertCountEquals(0)
        compose.onNodeWithContentDescription("Expand Book flights").assertExists()

        compose.onNodeWithContentDescription("Expand Book flights").performScrollTo().performClick()
        compose.onNodeWithText("window seat").assertExists()
        compose.onNodeWithText("Due tomorrow").assertExists()
        Unit
    }

    @Test
    fun completedSubtask_strikesThrough_andStaysInPlace() = runBlocking {
        val todoId = repository.createTodo(Todo(title = "Plan trip", sortOrder = 1.0))
        repository.addSubtask(Subtask(parentType = ParentType.TODO, parentId = todoId, title = "First child"))
        repository.addSubtask(Subtask(parentType = ParentType.TODO, parentId = todoId, title = "Second child"))
        applyEditor(editorFor(todoId))

        compose.onNodeWithContentDescription("Mark First child as done").performScrollTo().performClick()
        compose.waitForIdle()

        assertStruckThrough("First child")
        assertNotStruckThrough("Second child")
        assertTrue("one open child keeps the parent open", repository.getTodo(todoId)!!.isCompleted.not())

        val firstTop = compose.onNodeWithText("First child").fetchSemanticsNode().boundsInRoot.top
        val secondTop = compose.onNodeWithText("Second child").fetchSemanticsNode().boundsInRoot.top
        assertTrue("completed subtask must stay above the open one", firstTop < secondTop)
        Unit
    }

    @Test
    fun subtask_allChildrenDone_checksParent_andParentUncheckClearsThem() = runBlocking {
        val todoId = repository.createTodo(Todo(title = "Plan trip", sortOrder = 1.0))
        repository.addSubtask(Subtask(parentType = ParentType.TODO, parentId = todoId, title = "Book flights"))
        repository.addSubtask(Subtask(parentType = ParentType.TODO, parentId = todoId, title = "Pack bags"))
        applyEditor(editorFor(todoId))

        // Children → parent.
        compose.onNodeWithContentDescription("Mark Book flights as done").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Mark Pack bags as done").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Mark Plan trip as not done").assertExists()
        assertTrue("all children done must check the parent", repository.getTodo(todoId)!!.isCompleted)
        assertStruckThrough("Book flights")
        assertStruckThrough("Pack bags")

        // Parent unchecked → every child unchecked.
        compose.onNodeWithContentDescription("Mark Plan trip as not done").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Mark Plan trip as done").assertExists()
        compose.onNodeWithContentDescription("Mark Book flights as done").assertExists()
        compose.onNodeWithContentDescription("Mark Pack bags as done").assertExists()
        assertTrue(repository.getSubtasks(todoId).none { it.isCompleted })
        assertNotStruckThrough("Book flights")
        Unit
    }

    @Test
    fun parentToggle_cascadesToEverySubtask_inBothDirections() = runBlocking {
        val todoId = repository.createTodo(Todo(title = "Plan trip", sortOrder = 1.0))
        repository.addSubtask(Subtask(parentType = ParentType.TODO, parentId = todoId, title = "Book flights"))
        repository.addSubtask(Subtask(parentType = ParentType.TODO, parentId = todoId, title = "Pack bags"))
        applyEditor(editorFor(todoId))

        // Parent checked → all children checked.
        compose.onNodeWithContentDescription("Mark Plan trip as done").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Mark Book flights as not done").assertExists()
        compose.onNodeWithContentDescription("Mark Pack bags as not done").assertExists()
        assertTrue(repository.getSubtasks(todoId).all { it.isCompleted })
        assertStruckThrough("Book flights")
        assertStruckThrough("Pack bags")

        // Parent unchecked → all children unchecked.
        compose.onNodeWithContentDescription("Mark Plan trip as not done").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Mark Book flights as done").assertExists()
        compose.onNodeWithContentDescription("Mark Pack bags as done").assertExists()
        assertTrue(repository.getSubtasks(todoId).none { it.isCompleted })
        assertNotStruckThrough("Pack bags")
        Unit
    }

    @Test
    fun editor_addsAndDeletesAReminder() = runBlocking {
        val todoId = repository.createTodo(Todo(title = "Water plants", sortOrder = 1.0))
        val viewModel = editorFor(todoId)
        applyEditor(viewModel)

        compose.onNodeWithTag(REMINDER_ADD_TAG).performScrollTo().performClick()
        compose.onNodeWithText("New reminder").assertIsDisplayed()
        compose.onNodeWithTag(REMINDER_CONFIRM_TAG).performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(1, viewModel.reminders.size)
        compose.onNodeWithText("Reminders").performScrollTo().assertIsDisplayed()

        compose.onNodeWithContentDescription("Delete reminder").performScrollTo().performClick()
        compose.onNodeWithText("Delete reminder?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()

        assertTrue(viewModel.reminders.isEmpty())
    }

    private fun applyEditor(viewModel: TodoEditorViewModel) {
        compose.setContent {
            MaterialTheme {
                // A focused TextField in a Robolectric test blinks forever and
                // never lets the Compose test clock go idle.
                CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                    TodoEditorScreen(onDone = {}, viewModel = viewModel)
                }
            }
        }
    }

    private fun editorFor(todoId: Long): TodoEditorViewModel =
        TodoEditorViewModel(
            repository,
            reminderScheduler,
            SavedStateHandle(mapOf(MnlistsRoutes.TODO_ID_ARG to todoId)),
        )

    private suspend fun createTodoWithSubtask(
        title: String,
        note: String,
        deadline: Long?,
    ): Long {
        val todoId = repository.createTodo(Todo(title = "Plan trip", sortOrder = 1.0))
        repository.addSubtask(
            Subtask(
                parentType = ParentType.TODO,
                parentId = todoId,
                title = title,
                note = note,
                deadline = deadline,
            ),
        )
        return todoId
    }

    private fun assertStruckThrough(text: String) {
        val annotated = semanticsText(text)
        assertTrue(
            "\"$text\" should be struck through",
            annotated.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough },
        )
    }

    private fun assertNotStruckThrough(text: String) {
        val annotated = semanticsText(text)
        assertFalse(
            "\"$text\" should not be struck through",
            annotated.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough },
        )
    }

    private fun semanticsText(text: String): AnnotatedString {
        val node = compose.onNodeWithText(text).fetchSemanticsNode()
        val config = node.config
        val texts = if (config.contains(SemanticsProperties.Text)) {
            config[SemanticsProperties.Text]
        } else {
            emptyList()
        }
        return texts.firstOrNull() ?: error("No semantics text found for \"$text\"")
    }

    private companion object {
        /** Keeps Room's executor work on the calling thread so tests stay synchronous. */
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
