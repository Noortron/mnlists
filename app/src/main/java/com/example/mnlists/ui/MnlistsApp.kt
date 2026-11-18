package com.example.mnlists.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mnlists.R
import com.example.mnlists.data.settings.ThemeMode
import com.example.mnlists.ui.habits.HabitEditorScreen
import com.example.mnlists.ui.habits.HabitsRoute
import com.example.mnlists.ui.navigation.MnlistsRoutes
import com.example.mnlists.ui.navigation.TopLevelDestination
import com.example.mnlists.ui.screens.AboutScreen
import com.example.mnlists.ui.screens.CalendarScreen
import com.example.mnlists.ui.screens.DailiesScreen
import com.example.mnlists.ui.screens.SettingsScreen
import com.example.mnlists.ui.screens.TodosScreen

/**
 * Root composable: one Scaffold with the top bar, the four-tab bottom bar, the
 * context-aware FAB, and a single NavHost that also hosts Settings and About.
 */
@Composable
fun MnlistsApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = TopLevelDestination.entries.firstOrNull { it.route == currentRoute }
    val isSubScreen = currentRoute == MnlistsRoutes.SETTINGS || currentRoute == MnlistsRoutes.ABOUT
    val isHabitEditor = currentRoute == MnlistsRoutes.HABIT_NEW ||
        currentRoute == MnlistsRoutes.HABIT_EDITOR

    val title = when {
        topLevel != null -> stringResource(topLevel.titleRes)
        currentRoute == MnlistsRoutes.HABIT_NEW -> stringResource(R.string.habit_new_title)
        currentRoute == MnlistsRoutes.HABIT_EDITOR -> stringResource(R.string.habit_edit_title)
        currentRoute == MnlistsRoutes.SETTINGS -> stringResource(R.string.menu_settings)
        currentRoute == MnlistsRoutes.ABOUT -> stringResource(R.string.menu_about)
        else -> stringResource(R.string.app_name)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MnlistsTopBar(
                title = title,
                showOverflowMenu = !isSubScreen && !isHabitEditor,
                onNavigateBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigateSingleTop(MnlistsRoutes.SETTINGS) },
                onOpenAbout = { navController.navigateSingleTop(MnlistsRoutes.ABOUT) },
            )
        },
        bottomBar = {
            if (topLevel != null) {
                MnlistsBottomBar(
                    selected = topLevel,
                    onSelect = { destination -> navController.navigateToTopLevel(destination) },
                )
            }
        },
        floatingActionButton = {
            if (topLevel != null && topLevel != TopLevelDestination.CALENDAR) {
                AddFab(
                    destination = topLevel,
                    onClick = {
                        when (topLevel) {
                            TopLevelDestination.HABITS ->
                                navController.navigate(MnlistsRoutes.HABIT_NEW)
                            // Dailies/To-Do's create flows arrive in Steps 6 and 9.
                            else -> Unit
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MnlistsRoutes.HABITS,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(MnlistsRoutes.HABITS) {
                HabitsRoute(
                    onOpenHabit = { habitId ->
                        navController.navigate(MnlistsRoutes.habitEditorRoute(habitId))
                    },
                )
            }
            composable(MnlistsRoutes.HABIT_NEW) {
                HabitEditorScreen(onDone = { navController.popBackStack() })
            }
            composable(
                route = MnlistsRoutes.HABIT_EDITOR,
                arguments = listOf(
                    navArgument(MnlistsRoutes.HABIT_ID_ARG) { type = NavType.LongType },
                ),
            ) {
                HabitEditorScreen(onDone = { navController.popBackStack() })
            }
            composable(MnlistsRoutes.DAILIES) { DailiesScreen() }
            composable(MnlistsRoutes.TODOS) { TodosScreen() }
            composable(MnlistsRoutes.CALENDAR) { CalendarScreen() }
            composable(MnlistsRoutes.SETTINGS) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                )
            }
            composable(MnlistsRoutes.ABOUT) { AboutScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MnlistsTopBar(
    title: String,
    showOverflowMenu: Boolean,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (!showOverflowMenu) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        },
        actions = {
            if (showOverflowMenu) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.action_more_options),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_settings)) },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_about)) },
                            onClick = {
                                menuExpanded = false
                                onOpenAbout()
                            },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

@Composable
private fun MnlistsBottomBar(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.titleRes)) },
            )
        }
    }
}

@Composable
private fun AddFab(destination: TopLevelDestination, onClick: () -> Unit) {
    val contentDescription = when (destination) {
        TopLevelDestination.HABITS -> stringResource(R.string.fab_add_habit)
        TopLevelDestination.DAILIES -> stringResource(R.string.fab_add_daily)
        TopLevelDestination.TODOS -> stringResource(R.string.fab_add_todo)
        TopLevelDestination.CALENDAR -> stringResource(R.string.app_name)
    }
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = contentDescription,
        )
    }
}

/** Switches between the four top-level tabs without growing the back stack. */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) { launchSingleTop = true }
}
