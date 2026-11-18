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
