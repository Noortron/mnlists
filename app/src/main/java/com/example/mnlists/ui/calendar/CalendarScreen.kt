package com.example.mnlists.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mnlists.R
import java.time.LocalDate
import java.time.YearMonth

/** Stateful entry point for the Calendar tab (SPEC §4.4, Step 12). */
@Composable
fun CalendarRoute(viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Default to today on every tab entry and repair a midnight rollover.
    LaunchedEffect(Unit) { viewModel.onOpened() }

    CalendarScreen(
        state = state,
        onSelectDate = viewModel::selectDate,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onGoToToday = viewModel::goToToday,
        onToggleTask = viewModel::setTaskCompleted,
        onIncrementHabit = viewModel::incrementHabit,
        onDecrementHabit = viewModel::decrementHabit,
    )
}

/**
 * Calendar UI: a month grid on the top half and the selected day's habits and
 * tasks on the bottom half.
 *
 * Deliberately read/toggle only — task checkboxes and habit +/- are the only
 * gestures. There is no title click, drag, delete or editor route, and the FAB
 * is hidden by the app scaffold on this destination.
 */
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onSelectDate: (Long) -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onGoToToday: () -> Unit = {},
    onToggleTask: (CalendarTask, Boolean) -> Unit = { _, _ -> },
    onIncrementHabit: (CalendarHabit) -> Unit = {},
    onDecrementHabit: (CalendarHabit) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MonthCalendar(
            month = state.visibleMonth,
            selectedDate = state.selectedDate,
            today = state.today,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onGoToToday = onGoToToday,
            onSelectDate = onSelectDate,
            modifier = Modifier.weight(1f),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        DayList(
            state = state,
            onToggleTask = onToggleTask,
            onIncrementHabit = onIncrementHabit,
            onDecrementHabit = onDecrementHabit,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selectedDate: Long,
    today: Long,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToToday: () -> Unit,
    onSelectDate: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.calendar_previous_month),
                    )
                }
                Text(
                    text = CalendarDates.formatMonth(month),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNextMonth) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.calendar_next_month),
                    )
                }
                TextButton(onClick = onGoToToday, modifier = Modifier.testTag(CALENDAR_TODAY_TAG)) {
                    Text(stringResource(R.string.calendar_today))
                }
            }

            WeekdayHeader()

            Column(modifier = Modifier.weight(1f)) {
                val cells = remember(month) { CalendarDates.monthCells(month) }
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        week.forEach { date ->
                            DayCell(
                                date = date,
                                isSelected = date == selectedDate,
                                isToday = date == today,
                                onSelectDate = onSelectDate,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val labels = remember { CalendarDates.weekdayLabels() }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayCell(
    date: Long?,
    isSelected: Boolean,
    isToday: Boolean,
    onSelectDate: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (date == null) {
        Spacer(modifier = modifier)
        return
    }

    val day = LocalDate.ofEpochDay(date).dayOfMonth.toString()
    val label = CalendarDates.formatDay(date)
    val background = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val foreground = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .selectable(
                selected = isSelected,
                role = Role.Button,
                onClick = { onSelectDate(date) },
            )
            .semantics { contentDescription = label }
            .testTag(calendarDayTag(date)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(background)
                .then(
                    if (isToday && !isSelected) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day,
                style = MaterialTheme.typography.bodyMedium,
                color = foreground,
            )
        }
    }
}

@Composable
private fun DayList(
    state: CalendarUiState,
    onToggleTask: (CalendarTask, Boolean) -> Unit,
    onIncrementHabit: (CalendarHabit) -> Unit,
    onDecrementHabit: (CalendarHabit) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = if (state.selectedDate == state.today) {
                    stringResource(R.string.calendar_selected_today, CalendarDates.formatDay(state.today))
                } else {
                    stringResource(R.string.calendar_selected_date, CalendarDates.formatDay(state.selectedDate))
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
        }

        if (state.isEmpty && !state.isLoading) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        text = stringResource(R.string.calendar_empty_day),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        if (state.habits.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.calendar_section_habits)) }
            items(items = state.habits, key = { "habit:${it.habit.id}" }) { item ->
                HabitDayRow(
                    item = item,
                    onIncrement = { onIncrementHabit(item) },
                    onDecrement = { onDecrementHabit(item) },
                )
            }
        }

        if (state.openTasks.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.calendar_section_tasks)) }
            items(items = state.openTasks, key = { it.key }) { task ->
                TaskDayRow(task = task, onToggle = onToggleTask)
            }
        }

        if (state.skippedTasks.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.daily_section_skipped)) }
            items(items = state.skippedTasks, key = { it.key }) { task ->
                TaskDayRow(task = task, onToggle = onToggleTask)
            }
        }

        if (state.completedTasks.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.todo_section_completed)) }
            items(items = state.completedTasks, key = { it.key }) { task ->
                TaskDayRow(task = task, onToggle = onToggleTask)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun TaskDayRow(
    task: CalendarTask,
    onToggle: (CalendarTask, Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle(task, it) },
                enabled = !task.isSkipped,
                modifier = Modifier.testTag(calendarTaskTag(task)),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                Text(
                    text = task.title.decorated(task.isCompleted),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = taskLabel(task),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun taskLabel(task: CalendarTask): String {
    val kind = stringResource(
        when (task.kind) {
            CalendarTaskKind.TODO -> R.string.calendar_task_todo
            CalendarTaskKind.DAILY -> R.string.calendar_task_daily
        },
    )
    return if (task.isSkipped) {
        stringResource(R.string.calendar_task_with_state, kind, stringResource(R.string.calendar_task_skipped))
    } else {
        kind
    }
}

@Composable
private fun HabitDayRow(
    item: CalendarHabit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val habit = item.habit
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(calendarHabitTag(habit.id)),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
                IconButton(
                    onClick = onDecrement,
                    enabled = item.isAdjustable,
                    modifier = Modifier.testTag(calendarHabitDecreaseTag(habit.id)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = stringResource(R.string.habit_decrease, habit.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = item.count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 28.dp)
                    .testTag(calendarHabitCountTag(habit.id)),
            )

            if (habit.allowUp) {
                IconButton(
                    onClick = onIncrement,
                    enabled = item.isAdjustable,
                    modifier = Modifier.testTag(calendarHabitIncreaseTag(habit.id)),
                ) {
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

private fun String.decorated(completed: Boolean): AnnotatedString =
    if (completed) {
        AnnotatedString(
            text = this,
            spanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
        )
    } else {
        AnnotatedString(this)
    }

/** Test tags shared with the Calendar Compose tests. */
internal fun calendarDayTag(epochDay: Long): String = "calendarDay_$epochDay"

internal fun calendarTaskTag(task: CalendarTask): String =
    "calendarTask_${task.kind.name.lowercase()}_${task.parentId}"

internal fun calendarHabitTag(habitId: Long): String = "calendarHabit_$habitId"

internal fun calendarHabitCountTag(habitId: Long): String = "calendarHabitCount_$habitId"

internal fun calendarHabitIncreaseTag(habitId: Long): String = "calendarHabitIncrease_$habitId"

internal fun calendarHabitDecreaseTag(habitId: Long): String = "calendarHabitDecrease_$habitId"

internal const val CALENDAR_TODAY_TAG = "calendarToday"
