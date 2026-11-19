package com.example.mnlists.ui.dailies

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.example.mnlists.data.daily.DailyOccurrenceManager
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.model.ParentType
import com.example.mnlists.data.model.RepeatType
import com.example.mnlists.data.repository.DailyRepository
import com.example.mnlists.ui.navigation.MnlistsRoutes
import java.time.DayOfWeek
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
 * Compose-level verification of the Dailies screens: real composables on the
 * JVM through Robolectric, backed by a real in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@OptIn(ExperimentalComposeUiApi::class)
class DailyUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: MnlistsDatabase
    private lateinit var repository: DailyRepository
    private lateinit var manager: DailyOccurrenceManager

    private val day: Long = DailyDates.today()

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, MnlistsDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(DIRECT_EXECUTOR)
            .setTransactionExecutor(DIRECT_EXECUTOR)
            .build()
        repository = DailyRepository(
            database = database,
            dailyDao = database.dailyDao(),
            occurrenceDao = database.dailyOccurrenceDao(),
            subtaskDao = database.subtaskDao(),
            reminderDao = database.reminderDao(),
        )
        manager = DailyOccurrenceManager(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun list_splitsOpenSkippedCompleted_andSkipMovesTheRow() = runBlocking {
        val openOccurrence = createDailyWithOccurrence("Morning pages")
        val skippedOccurrence = createDailyWithOccurrence("Skipped workout")
        repository.setOccurrenceSkipped(skippedOccurrence, true)
        val doneOccurrence = createDailyWithOccurrence("Finished reading")
        repository.setOccurrenceCompleted(doneOccurrence, true)

        val viewModel = DailiesViewModel(repository, manager)
        viewModel.setToday(day)
        var openedSeries: Long? = null
        var openedOccurrence: Long? = null

        compose.setContent {
            MaterialTheme {
                DailiesRoute(
                    onOpenSeries = { openedSeries = it },
                    onOpenOccurrence = { openedOccurrence = it },
                    viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("To do").assertIsDisplayed()
        compose.onNodeWithText("Skipped").assertIsDisplayed()
        compose.onNodeWithText("Completed").assertIsDisplayed()
        compose.onNodeWithText("Morning pages").assertIsDisplayed()
        compose.onNodeWithText("Skipped workout").assertIsDisplayed()
        compose.onNodeWithText("Finished reading").assertIsDisplayed()

        val openTop = compose.onNodeWithText("Morning pages").fetchSemanticsNode().boundsInRoot.top
        val skippedHeaderTop = compose.onNodeWithText("Skipped").fetchSemanticsNode().boundsInRoot.top
        val completedHeaderTop = compose.onNodeWithText("Completed").fetchSemanticsNode().boundsInRoot.top
        val doneTop = compose.onNodeWithText("Finished reading").fetchSemanticsNode().boundsInRoot.top
        assertTrue("open row must precede the Skipped section", openTop < skippedHeaderTop)
        assertTrue("completed row must sit below its header", completedHeaderTop < doneTop)
        assertStruckThrough("Finished reading")
        assertNotStruckThrough("Morning pages")

        // Skip is a live UI action on today's occurrence only.
        compose.onNodeWithContentDescription("Skip Morning pages").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(repository.getOccurrence(openOccurrence)!!.isSkipped)
        assertFalse(repository.getOccurrence(openOccurrence)!!.isCompleted)
        assertTrue(repository.getOccurrence(skippedOccurrence)!!.isSkipped)
        compose.onNodeWithContentDescription("Unskip Morning pages").assertExists()

        // Title and "Edit this day" lead to the two locked editors.
        compose.onNodeWithText("Morning pages").performScrollTo().performClick()
        assertEquals(repository.getOccurrence(openOccurrence)!!.dailyId, openedSeries)
        compose.onNodeWithContentDescription("Edit this day for Morning pages")
            .performScrollTo().performClick()
        assertEquals(openOccurrence, openedOccurrence)
        Unit
    }

    @Test
    fun list_subtaskAddAndCheck_cascadesToOccurrence_andDragHandleCollapses() = runBlocking {
        val occurrenceId = createDailyWithOccurrence("Morning pages")
        val viewModel = DailiesViewModel(repository, manager)
        viewModel.setToday(day)

        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                    DailiesRoute(onOpenSeries = {}, onOpenOccurrence = {}, viewModel = viewModel)
                }
            }
        }
        compose.waitForIdle()

        // Add a subtask with its own note from the list row (inline card).
        compose.onNodeWithText("Add subtask").performScrollTo().performClick()
        compose.onNodeWithText("New subtask").assertIsDisplayed()
        compose.onNodeWithTag(DAILY_SUBTASK_TITLE_TAG).performTextReplacement("Book flights")
        compose.onNodeWithTag(DAILY_SUBTASK_NOTE_TAG).performTextReplacement("window seat")
        compose.onNodeWithText("Add").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Book flights").performScrollTo().assertIsDisplayed()
        val stored = repository.getSubtasks(occurrenceId).single()
        assertEquals("Book flights", stored.title)
        assertEquals("window seat", stored.note)

        // Child check cascades to the parent and strikes the child in place.
        compose.onNodeWithContentDescription("Mark Book flights as done").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(repository.getOccurrence(occurrenceId)!!.isCompleted)
        assertStruckThrough("Book flights")

        // Drag-handle tap collapses details (expanded by default).
        compose.onNodeWithContentDescription("Collapse Morning pages").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("window seat").assertCountEquals(0)
        compose.onNodeWithContentDescription("Expand Morning pages").assertExists()
        // Collapsing the parent hides its whole subtask section (SPEC §4.2).
        compose.onAllNodesWithText("Book flights").assertCountEquals(0)
        Unit
    }

    @Test
    fun list_uncheckingParent_unchecksEverySubtask() = runBlocking {
        val occurrenceId = createDailyWithOccurrence("Morning pages")
        repository.addSubtask(
            Subtask(parentType = ParentType.DAILY_OCCURRENCE, parentId = occurrenceId, title = "First child"),
        )
        repository.addSubtask(
            Subtask(parentType = ParentType.DAILY_OCCURRENCE, parentId = occurrenceId, title = "Second child"),
        )
        repository.setOccurrenceCompleted(occurrenceId, true)

        val viewModel = DailiesViewModel(repository, manager)
        viewModel.setToday(day)
        compose.setContent {
            MaterialTheme {
                DailiesRoute(onOpenSeries = {}, onOpenOccurrence = {}, viewModel = viewModel)
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Mark Morning pages as not done")
            .performScrollTo().performClick()
        compose.waitForIdle()

        assertFalse(repository.getOccurrence(occurrenceId)!!.isCompleted)
        assertTrue(repository.getSubtasks(occurrenceId).none { it.isCompleted })
        assertNotStruckThrough("First child")
        Unit
    }

    @Test
    fun seriesEditor_createsWeeklySeries() = runBlocking {
        val editor = DailyEditorViewModel(repository, manager, SavedStateHandle())
        var done = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                    DailyEditorScreen(onDone = { done = true }, viewModel = editor)
                }
            }
        }

        editor.setTitle("Morning pages")
        editor.setStartDate(day)

        // Scheduling controls are reachable from the real composable.
        compose.onNodeWithText("Daily").performScrollTo().performClick()
        compose.onNodeWithText("Weekly").assertIsDisplayed()
        compose.onNodeWithText("Monthly").assertIsDisplayed()
        compose.onNodeWithText("Weekly").performClick()
        compose.onNodeWithText("Monday").performScrollTo().performClick()

        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitForIdle()

        assertTrue(done)
        val created = repository.observeDailies().first().single()
        assertEquals("Morning pages", created.title)
        assertEquals(RepeatType.WEEKLY, created.repeatType)
        assertTrue(DayOfWeek.MONDAY in created.weekdays)
    }

    @Test
    fun seriesEditor_editFromListChangesSeriesNotOccurrence() = runBlocking {
        val dailyId = repository.createDaily(
            Daily(
                title = "Morning pages",
                startDate = day,
                repeatType = RepeatType.WEEKLY,
                everyN = 2,
                weekdays = setOf(DayOfWeek.MONDAY),
            ),
        )
        val occurrenceId = repository.ensureOccurrence(dailyId, day)!!.id
        repository.setOccurrenceCompleted(occurrenceId, true)

        val editor = DailyEditorViewModel(
            repository,
            manager,
            SavedStateHandle(mapOf(MnlistsRoutes.DAILY_ID_ARG to dailyId)),
        )
        var done = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                    DailyEditorScreen(onDone = { done = true }, viewModel = editor)
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag(DAILY_TITLE_TAG).performScrollTo()
            .performTextReplacement("Evening pages")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitForIdle()

        assertTrue(done)
        assertEquals("Evening pages", repository.getDaily(dailyId)!!.title)
        assertTrue("series edits never rewrite an occurrence", repository.getOccurrence(occurrenceId)!!.isCompleted)
    }

    @Test
    fun seriesEditor_deleteAsksForConfirmation_andKeepsHistory() = runBlocking {
        val dailyId = repository.createDaily(Daily(title = "Morning pages", startDate = day))
        val past = repository.ensureOccurrence(dailyId, day - 1)!!.id

        val editor = DailyEditorViewModel(
            repository,
            manager,
            SavedStateHandle(mapOf(MnlistsRoutes.DAILY_ID_ARG to dailyId)),
        )
        var deletedDone = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                    DailyEditorScreen(onDone = { deletedDone = true }, viewModel = editor)
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Delete daily series").performScrollTo().performClick()
        compose.onNodeWithText("Delete daily series?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertNotNull(repository.getDaily(dailyId))
        assertFalse(deletedDone)

        compose.onNodeWithText("Delete daily series").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()
        assertNull(repository.getDaily(dailyId))
        assertNotNull("history is preserved", repository.getOccurrence(past))
        assertTrue(deletedDone)
    }

    @Test
    fun occurrenceEditor_fromCalendar_editsOnlyThatDay_andShowsItsSubtasks() = runBlocking {
        val occurrenceId = createDailyWithOccurrence("Morning pages")
        val dailyId = repository.getOccurrence(occurrenceId)!!.dailyId
        repository.addSubtask(
            Subtask(parentType = ParentType.DAILY_OCCURRENCE, parentId = occurrenceId, title = "Book flights"),
        )
        val editor = DailyEditorViewModel(
            repository,
            manager,
            SavedStateHandle(mapOf(MnlistsRoutes.DAILY_OCCURRENCE_ID_ARG to occurrenceId)),
        )
        assertEquals(DailyEditorMode.OCCURRENCE, editor.mode)

        var done = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                    DailyEditorScreen(onDone = { done = true }, viewModel = editor)
                }
            }
        }
        compose.waitForIdle()

        // The series title is visible but read-only here.
        compose.onNodeWithText("Morning pages").assertIsDisplayed()
        compose.onNodeWithContentDescription("Skip Morning pages").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(repository.getOccurrence(occurrenceId)!!.isSkipped)

        compose.onNodeWithContentDescription("Mark Book flights as done").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(repository.getOccurrence(occurrenceId)!!.isCompleted)
        assertEquals("Morning pages", repository.getDaily(dailyId)!!.title)

        compose.onNodeWithText("OK").performScrollTo().performClick()
        assertTrue(done)
        Unit
    }

    private suspend fun createDailyWithOccurrence(title: String): Long {
        val dailyId = repository.createDaily(Daily(title = title, startDate = day))
        return repository.ensureOccurrence(dailyId, day)!!.id
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
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
