package com.example.mnlists.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.mnlists.ui.calendar.CalendarRoute
import com.example.mnlists.ui.dailies.DailiesRoute
import com.example.mnlists.ui.dailies.DailyEditorScreen
import com.example.mnlists.ui.habits.HabitEditorScreen
import com.example.mnlists.ui.habits.HabitsRoute
import com.example.mnlists.ui.navigation.MnlistsRoutes
import com.example.mnlists.ui.navigation.TopLevelDestination
import com.example.mnlists.ui.screens.AboutScreen
import com.example.mnlists.ui.screens.SettingsScreen
import com.example.mnlists.ui.todos.TodoEditorScreen
import com.example.mnlists.ui.todos.TodosRoute

/** Duration of the NavHost slide overlap, in milliseconds. */
private const val NAV_TRANSITION_MS = 260

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
    val isTodoEditor = currentRoute == MnlistsRoutes.TODO_NEW ||
        currentRoute == MnlistsRoutes.TODO_EDITOR
    val isDailyEditor = currentRoute == MnlistsRoutes.DAILY_NEW ||
        currentRoute == MnlistsRoutes.DAILY_EDITOR ||
        currentRoute == MnlistsRoutes.DAILY_OCCURRENCE_EDITOR
    val isEditor = isHabitEditor || isTodoEditor || isDailyEditor

    // Keep the bar visible during its exit animation (route becomes null first).
    var lastTopLevel by remember { mutableStateOf<TopLevelDestination?>(null) }
    LaunchedEffect(topLevel) {
        if (topLevel != null) lastTopLevel = topLevel
    }
    val showBottomBar = topLevel != null || currentRoute == null
    val bottomBarSelection = topLevel ?: lastTopLevel ?: TopLevelDestination.HABITS

    val title = when {
        topLevel != null -> stringResource(topLevel.titleRes)
        currentRoute == MnlistsRoutes.HABIT_NEW -> stringResource(R.string.habit_new_title)
        currentRoute == MnlistsRoutes.HABIT_EDITOR -> stringResource(R.string.habit_edit_title)
        currentRoute == MnlistsRoutes.TODO_NEW -> stringResource(R.string.todo_new_title)
        currentRoute == MnlistsRoutes.TODO_EDITOR -> stringResource(R.string.todo_edit_title)
        currentRoute == MnlistsRoutes.DAILY_NEW -> stringResource(R.string.daily_new_title)
        currentRoute == MnlistsRoutes.DAILY_EDITOR -> stringResource(R.string.daily_edit_title)
        currentRoute == MnlistsRoutes.DAILY_OCCURRENCE_EDITOR ->
            stringResource(R.string.daily_occurrence_title)
        currentRoute == MnlistsRoutes.SETTINGS -> stringResource(R.string.menu_settings)
        currentRoute == MnlistsRoutes.ABOUT -> stringResource(R.string.menu_about)
        else -> stringResource(R.string.app_name)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MnlistsTopBar(
                title = title,
                showOverflowMenu = !isSubScreen && !isEditor,
                containerColor = if (isEditor) {
                    MaterialTheme.colorScheme.surfaceContainer
                } else {
                    MaterialTheme.colorScheme.background
                },
                onNavigateBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigateSingleTop(MnlistsRoutes.SETTINGS) },
                onOpenAbout = { navController.navigateSingleTop(MnlistsRoutes.ABOUT) },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(160)),
            ) {
                MnlistsBottomBar(
                    selected = bottomBarSelection,
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
                            TopLevelDestination.TODOS ->
                                navController.navigate(MnlistsRoutes.TODO_NEW)
                            TopLevelDestination.DAILIES ->
                                navController.navigate(MnlistsRoutes.DAILY_NEW)
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
            // Overlapping slide keeps the outgoing screen opaque underneath, so
            // the background never flashes through mid-navigation.
            enterTransition = {
                slideInHorizontally(animationSpec = tween(NAV_TRANSITION_MS)) { it }
            },
            exitTransition = {
                slideOutHorizontally(animationSpec = tween(NAV_TRANSITION_MS)) { -it / 6 }
            },
            popEnterTransition = {
                slideInHorizontally(animationSpec = tween(NAV_TRANSITION_MS)) { -it / 6 }
            },
            popExitTransition = {
                slideOutHorizontally(animationSpec = tween(NAV_TRANSITION_MS)) { it }
            },
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
            composable(MnlistsRoutes.DAILIES) {
                DailiesRoute(
                    onOpenSeries = { dailyId ->
                        navController.navigate(MnlistsRoutes.dailyEditorRoute(dailyId))
                    },
                )
            }
            composable(MnlistsRoutes.DAILY_NEW) {
                DailyEditorScreen(onDone = { navController.popBackStack() })
            }
            composable(
                route = MnlistsRoutes.DAILY_EDITOR,
                arguments = listOf(
                    navArgument(MnlistsRoutes.DAILY_ID_ARG) { type = NavType.LongType },
                ),
            ) {
                DailyEditorScreen(onDone = { navController.popBackStack() })
            }
            composable(
                route = MnlistsRoutes.DAILY_OCCURRENCE_EDITOR,
                arguments = listOf(
                    navArgument(MnlistsRoutes.DAILY_OCCURRENCE_ID_ARG) { type = NavType.LongType },
                ),
            ) {
                DailyEditorScreen(onDone = { navController.popBackStack() })
            }
            composable(MnlistsRoutes.TODOS) {
                TodosRoute(
                    onOpenTodo = { todoId ->
                        navController.navigate(MnlistsRoutes.todoEditorRoute(todoId))
                    },
                )
            }
            composable(MnlistsRoutes.TODO_NEW) {
                TodoEditorScreen(onDone = { navController.popBackStack() })
            }
            composable(
                route = MnlistsRoutes.TODO_EDITOR,
                arguments = listOf(
                    navArgument(MnlistsRoutes.TODO_ID_ARG) { type = NavType.LongType },
                ),
            ) {
                TodoEditorScreen(onDone = { navController.popBackStack() })
            }
            composable(MnlistsRoutes.CALENDAR) { CalendarRoute() }
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
    containerColor: Color,
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
            containerColor = containerColor,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
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
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) { launchSingleTop = true }
}
