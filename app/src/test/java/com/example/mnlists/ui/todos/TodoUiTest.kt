package com.example.mnlists.ui.todos

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.Todo
import com.example.mnlists.data.repository.TodoRepository
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
import org.robolectric.annotation.Config

/**
 * Compose-level verification of the Step 6 screens: the real composables are
 * rendered on the JVM through Robolectric, backed by a real Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class TodoUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: MnlistsDatabase
    private lateinit var repository: TodoRepository

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
    }

    @Test
    fun editor_blocksSaveWithoutDueDate_thenQuickPickCreatesTodo() = runBlocking {
        val viewModel = TodoEditorViewModel(repository, SavedStateHandle())
        var done = false

        compose.setContent {
            MaterialTheme {
                TodoEditorScreen(onDone = { done = true }, viewModel = viewModel)
            }
        }

        viewModel.setTitle("Buy milk")
        compose.onNodeWithText("Save").performScrollTo().performClick()

        compose.onNodeWithText("Pick a due date.").assertIsDisplayed()
        assertFalse("must not finish without a due date", done)
        assertTrue(repository.observeTodos().first().isEmpty())

        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()

        assertTrue(done)
        val todo = repository.observeTodos().first().single()
        assertEquals("Buy milk", todo.title)
        assertEquals(TodoDates.today(), todo.dueDate)
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
        val viewModel = TodoEditorViewModel(repository, SavedStateHandle())

        compose.setContent {
            MaterialTheme {
                TodoEditorScreen(onDone = {}, viewModel = viewModel)
            }
        }

        compose.onNodeWithText("Choose a date").performClick()
        compose.onNodeWithText("OK").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()

        assertEquals(TodoDates.today(), viewModel.uiState.dueDate)
        compose.onNodeWithText(TodoDates.format(TodoDates.today())).assertIsDisplayed()
        Unit
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
