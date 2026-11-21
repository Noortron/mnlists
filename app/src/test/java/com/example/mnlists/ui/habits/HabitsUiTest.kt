package com.example.mnlists.ui.habits

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.model.ResetSchedule
import com.example.mnlists.data.repository.HabitRepository
import com.example.mnlists.data.settings.FakeHabitDefaults
import com.example.mnlists.ui.navigation.MnlistsRoutes
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Compose-level verification of the Step 4 screens: real composables rendered
 * on the JVM through Robolectric, backed by a real Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class HabitsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: MnlistsDatabase
    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, MnlistsDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(DIRECT_EXECUTOR)
            .setTransactionExecutor(DIRECT_EXECUTOR)
            .build()
        repository = HabitRepository(database.habitDao(), database.habitLogDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun listRow_showsTitleNoteCount_andOnlyEnabledDirectionButtons() {
        var opened: Long? = null
        var incremented: Long? = null
        var decremented: Long? = null

        compose.setContent {
            MaterialTheme {
                HabitsScreen(
                    habits = listOf(
                        Habit(
                            id = 1L,
                            title = "Read",
                            note = "20 pages before bed, every single day without fail",
                            currentCount = 3,
                            allowUp = true,
                            allowDown = true,
                        ),
                        Habit(
                            id = 2L,
                            title = "Water",
                            note = "",
                            currentCount = 5,
                            allowUp = true,
                            allowDown = false,
                        ),
                    ),
                    onOpenHabit = { opened = it },
                    onIncrement = { incremented = it },
                    onDecrement = { decremented = it },
                )
            }
        }

        // Title, note preview and current count are all rendered.
        compose.onNodeWithText("Read").assertIsDisplayed()
        compose.onNodeWithText("20 pages before bed, every single day without fail")
            .assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()

        // Both directions are enabled for "Read".
        compose.onNodeWithContentDescription("Increase Read").performClick()
        assertEquals(1L, incremented)
        compose.onNodeWithContentDescription("Decrease Read").performClick()
        assertEquals(1L, decremented)

        // "Water" only allows +, so no − button exists for it.
        compose.onNodeWithContentDescription("Increase Water").assertIsDisplayed()
        compose.onNodeWithContentDescription("Decrease Water").assertDoesNotExist()

        // Tapping the title opens the edit page.
        compose.onNodeWithText("Read").performClick()
        assertEquals(1L, opened)

        // Habits never collapse; the handle is present for long-press reordering.
        compose.onNodeWithContentDescription("Reorder Read").assertIsDisplayed()
    }

    @Test
    fun editor_loadsHabit_andDeleteAsksForConfirmation() = runBlocking {
        val habitId = repository.createHabit(
            Habit(
                title = "Stretch",
                note = "before bed",
                currentCount = 2,
                allowUp = true,
                allowDown = true,
            ),
        )
        val viewModel = HabitEditorViewModel(
            repository,
            SavedStateHandle(mapOf(MnlistsRoutes.HABIT_ID_ARG to habitId)),
            FakeHabitDefaults(),
        )
        var done = false

        compose.setContent {
            MaterialTheme {
                HabitEditorScreen(onDone = { done = true }, viewModel = viewModel)
            }
        }

        // Existing values load into the form; the note is fully visible.
        compose.onNodeWithText("Stretch").assertIsDisplayed()
        compose.onNodeWithText("before bed").assertIsDisplayed()

        // Deleting is a two-step action: the dialog appears and cancel is a no-op.
        compose.onNodeWithText("Delete habit").performScrollTo().performClick()
        compose.onNodeWithText("Delete habit?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete habit?").assertDoesNotExist()
        assertTrue(repository.getHabit(habitId) != null)

        // Confirming really deletes and finishes the screen.
        compose.onNodeWithText("Delete habit").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        assertNull(repository.getHabit(habitId))
        assertTrue("onDone must run after deletion", done)
    }

    @Test
    fun editor_usesRoundCounterToggles_andOffersNoMonthlyReset() = runBlocking {
        val viewModel = HabitEditorViewModel(repository, SavedStateHandle(), FakeHabitDefaults())

        compose.setContent {
            MaterialTheme {
                HabitEditorScreen(onDone = {}, viewModel = viewModel)
            }
        }

        // Round direction toggles start as "+ only"; flip them to "− only".
        compose.onNodeWithContentDescription("Increase").assertIsDisplayed()
        compose.onNodeWithContentDescription("Decrease").performClick()
        compose.onNodeWithContentDescription("Increase").performClick()

        // Full-width schedule dropdown: Daily / Weekly / Every N days, no Monthly.
        compose.onNodeWithText("Daily").performClick()
        compose.onNodeWithText("Weekly").assertIsDisplayed()
        compose.onNodeWithText("Every N days").assertIsDisplayed()
        compose.onNodeWithText("Monthly").assertDoesNotExist()
        compose.onNodeWithText("Every N days").performClick()

        viewModel.setTitle("Meditate")
        compose.onNodeWithText("Save").performScrollTo().performClick()

        val saved = repository.observeHabits().first().single()
        assertEquals("Meditate", saved.title)
        assertFalse("increase toggle was turned off", saved.allowUp)
        assertTrue("decrease toggle was turned on", saved.allowDown)
        assertEquals(ResetSchedule.EVERY_N, saved.resetSchedule)
    }

    private companion object {
        /** Keeps Room's executor work on the calling thread so tests stay synchronous. */
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
