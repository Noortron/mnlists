package com.example.mnlists.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mnlists.R
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.ui.components.PlaceholderContent

/** Stateful entry point for the Habits tab. */
@Composable
fun HabitsRoute(
    onOpenHabit: (Long) -> Unit,
    viewModel: HabitsViewModel = hiltViewModel(),
) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()

    // On-open check: roll over any habit cycles that ended while the app was
    // closed (Step 5). Runs again when returning to this tab.
    LaunchedEffect(Unit) { viewModel.checkResets() }

    HabitsScreen(
        habits = habits,
        onOpenHabit = onOpenHabit,
        onIncrement = viewModel::increment,
        onDecrement = viewModel::decrement,
    )
}

/**
 * Habits list (SPEC §4.1). Rows never collapse: the truncated note preview is
 * always visible, and only the enabled directions show a −/+ button.
 */
@Composable
fun HabitsScreen(
    habits: List<Habit>,
    onOpenHabit: (Long) -> Unit,
    onIncrement: (Long) -> Unit,
    onDecrement: (Long) -> Unit,
) {
    if (habits.isEmpty()) {
        PlaceholderContent(
            icon = Icons.Filled.Favorite,
            title = stringResource(R.string.tab_habits),
            description = stringResource(R.string.empty_habits),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = habits, key = { it.id }) { habit ->
            HabitRow(
                habit = habit,
                onOpen = { onOpenHabit(habit.id) },
                onIncrement = { onIncrement(habit.id) },
                onDecrement = { onDecrement(habit.id) },
            )
        }
    }
}

@Composable
private fun HabitRow(
    habit: Habit,
    onOpen: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onOpen)
                    .padding(vertical = 4.dp, horizontal = 0.dp),
            ) {
                Text(
                    text = habit.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (habit.note.isNotBlank()) {
                    Text(
                        text = habit.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            if (habit.allowDown) {
                IconButton(onClick = onDecrement) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = stringResource(R.string.habit_decrease, habit.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = habit.currentCount.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 28.dp),
            )

            if (habit.allowUp) {
                IconButton(onClick = onIncrement) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.habit_increase, habit.title),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
