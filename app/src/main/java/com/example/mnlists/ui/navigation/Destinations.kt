package com.example.mnlists.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.mnlists.R

/** Every route in the app. Kept in one place so type safety lives at call sites. */
object MnlistsRoutes {
    const val HABITS = "habits"
    const val DAILIES = "dailies"
    const val TODOS = "todos"
    const val CALENDAR = "calendar"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    /** Habit create page (FAB). */
    const val HABIT_NEW = "habits/new"

    /** Habit edit page; `{habitId}` is a Long nav argument. */
    const val HABIT_EDITOR = "habits/edit/{habitId}"
    const val HABIT_ID_ARG = "habitId"

    fun habitEditorRoute(habitId: Long): String = "habits/edit/$habitId"

    /** To-do create page (FAB). */
    const val TODO_NEW = "todos/new"

    /** To-do edit page; `{todoId}` is a Long nav argument. */
    const val TODO_EDITOR = "todos/edit/{todoId}"
    const val TODO_ID_ARG = "todoId"

    fun todoEditorRoute(todoId: Long): String = "todos/edit/$todoId"

    /** Daily series create page (FAB). */
    const val DAILY_NEW = "dailies/new"

    /** Whole-series editor opened from the Dailies list (SPEC §9). */
    const val DAILY_EDITOR = "dailies/edit/{dailyId}"
    const val DAILY_ID_ARG = "dailyId"

    /**
     * Occurrence-only editor (SPEC §9). Step 12's Calendar day list opens this
     * route; the Dailies list also exposes it via "Edit this day".
     */
    const val DAILY_OCCURRENCE_EDITOR = "dailies/occurrence/{occurrenceId}"
    const val DAILY_OCCURRENCE_ID_ARG = "occurrenceId"

    fun dailyEditorRoute(dailyId: Long): String = "dailies/edit/$dailyId"

    fun dailyOccurrenceEditorRoute(occurrenceId: Long): String = "dailies/occurrence/$occurrenceId"
}

/** The four bottom-bar destinations, in display order. */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    HABITS(MnlistsRoutes.HABITS, R.string.tab_habits, Icons.Filled.Favorite),
    DAILIES(MnlistsRoutes.DAILIES, R.string.tab_dailies, Icons.Filled.Refresh),
    TODOS(MnlistsRoutes.TODOS, R.string.tab_todos, Icons.Filled.CheckCircle),
    CALENDAR(MnlistsRoutes.CALENDAR, R.string.tab_calendar, Icons.Filled.DateRange),
}
