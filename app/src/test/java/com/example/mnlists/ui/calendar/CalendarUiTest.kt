package com.example.mnlists.ui.calendar

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.example.mnlists.data.local.entity.Habit
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose-level proof for the Calendar tab's read/toggle-only day list. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class CalendarUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 21).toEpochDay()

    private fun habit(id: Long = 1, allowDown: Boolean = true) = Habit(
        id = id,
        title = "Read",
        note = "20 pages",
        currentCount = 3,
        allowUp = true,
        allowDown = allowDown,
    )

    private fun state(
        selectedDate: Long = today,
        openTasks: List<CalendarTask> = emptyList(),
        completedTasks: List<CalendarTask> = emptyList(),
        habits: List<CalendarHabit> = emptyList(),
    ) = CalendarUiState(
        selectedDate = selectedDate,
        today = today,
        visibleMonth = YearMonth.of(2026, 11),
        habits = habits,
        openTasks = openTasks,
        completedTasks = completedTasks,
        isLoading = false,
    )

    private fun todoTask(id: Long = 1, title: String = "File taxes", completed: Boolean = false) =
        CalendarTask(
            kind = CalendarTaskKind.TODO,
            parentId = id,
            title = title,
            note = "",
            isCompleted = completed,
        )

    @Test
    fun todayIsHighlighted_andItsDayListIsShownImmediately() {
        compose.setContent {
            MaterialTheme {
                CalendarScreen(state = state(openTasks = listOf(todoTask())))
            }
        }

        compose.onNodeWithTag(calendarDayTag(today)).assertIsSelected()
        compose.onNodeWithText("Today · ${CalendarDates.formatDay(today)}").assertIsDisplayed()
        compose.onNodeWithText("File taxes").assertIsDisplayed()
        // Read/toggle only: the day list has no editor/delete affordance.
        compose.onAllNodesWithText("Edit").assertCountEquals(0)
        compose.onAllNodesWithText("Delete").assertCountEquals(0)
    }

    @Test
    fun tappingAnotherDate_reportsTheSelection() {
        var selected: Long? = null
        compose.setContent {
            MaterialTheme {
                CalendarScreen(
                    state = state(),
                    onSelectDate = { selected = it },
                )
            }
        }

        val other = LocalDate.of(2026, 11, 20).toEpochDay()
        compose.onNodeWithTag(calendarDayTag(other)).performClick()

        assertEquals(other, selected)
    }

    @Test
    fun taskCheckbox_isTheOnlyTaskAction_andReportsTheNewState() {
        var toggledTask: CalendarTask? = null
        var toggledTo: Boolean? = null
        val task = todoTask()
        compose.setContent {
            MaterialTheme {
                CalendarScreen(
                    state = state(openTasks = listOf(task)),
                    onToggleTask = { item, completed ->
                        toggledTask = item
                        toggledTo = completed
                    },
                )
            }
        }

        compose.onNodeWithTag(calendarTaskTag(task)).performScrollTo().performClick()

        assertEquals(task, toggledTask)
        assertEquals(true, toggledTo)
    }

    @Test
    fun habitRow_showsTheDayCount_andPlusMinusAdjustIt() {
        val habit = habit()
        var incremented = 0
        var decremented = 0
        compose.setContent {
            MaterialTheme {
                CalendarScreen(
                    state = state(
                        habits = listOf(
                            CalendarHabit(
                                habit = habit,
                                count = 3,
                                isAdjustable = true,
                                adjustsCurrentCount = true,
                            ),
                        ),
                    ),
                    onIncrementHabit = { incremented++ },
                    onDecrementHabit = { decremented++ },
                )
            }
        }

        compose.onNodeWithTag(calendarHabitCountTag(habit.id)).assertTextEquals("3")
        compose.onNodeWithTag(calendarHabitIncreaseTag(habit.id)).performScrollTo().performClick()
        compose.onNodeWithTag(calendarHabitDecreaseTag(habit.id)).performScrollTo().performClick()

        assertEquals(1, incremented)
        assertEquals(1, decremented)
    }

    @Test
    fun habitRow_hidesDirectionsTheHabitDoesNotAllow() {
        val habit = habit(allowDown = false)
        compose.setContent {
            MaterialTheme {
                CalendarScreen(
                    state = state(
                        habits = listOf(
                            CalendarHabit(habit = habit, count = 2, isAdjustable = true),
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithTag(calendarHabitIncreaseTag(habit.id)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(calendarHabitDecreaseTag(habit.id)).assertDoesNotExist()
    }

    @Test
    fun completedTask_rendersStruckThrough_asHistory() {
        val done = todoTask(id = 2, title = "Old errand", completed = true)
        compose.setContent {
            MaterialTheme {
                CalendarScreen(state = state(completedTasks = listOf(done)))
            }
        }

        compose.onNodeWithText("Old errand").performScrollTo().assertIsDisplayed()
        val texts = compose.onNodeWithText("Old errand").fetchSemanticsNode().config[SemanticsProperties.Text]
        val annotated = texts.first() as AnnotatedString
        assertTrue(
            "completed Calendar rows keep the app-wide strikethrough",
            annotated.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough },
        )
    }

    @Test
    fun emptyDay_showsTheEmptyState() {
        compose.setContent {
            MaterialTheme { CalendarScreen(state = state()) }
        }

        compose.onNodeWithText("Nothing planned or recorded for this day.").assertIsDisplayed()
    }
}
