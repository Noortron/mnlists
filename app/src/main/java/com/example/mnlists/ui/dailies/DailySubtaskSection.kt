package com.example.mnlists.ui.dailies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mnlists.R
import com.example.mnlists.data.local.entity.Subtask

/**
 * Dailies' subtask section, intentionally the same behaviour as To-Do's
 * (SPEC §4.3/§8): children have a checkbox, note and own deadline; checking
 * them cascades to the occurrence and vice versa; completed children are
 * grayed + struck through in place. The add/edit form is an inline card —
 * a TextField inside a dialog never settles under Robolectric.
 *
 * Shared by the Dailies list (inside an expanded row) and the occurrence
 * editor, so the Calendar edit flow gets identical behaviour.
 */
@Composable
internal fun DailySubtaskSection(
    subtasks: List<Subtask>,
    onToggleCompleted: (Long, Boolean) -> Unit,
    onAdd: (String, String, Long?) -> Unit,
    onUpdate: (Long, String, String, Long?) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var collapsedIds by remember { mutableStateOf(setOf<Long>()) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Subtask?>(null) }
    var pendingDelete by remember { mutableStateOf<Subtask?>(null) }

    if (subtasks.isEmpty()) {
        Text(
            text = stringResource(R.string.todo_subtask_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
    } else {
        subtasks.forEach { subtask ->
            DailySubtaskRow(
                subtask = subtask,
                collapsed = subtask.id in collapsedIds,
                onToggleCollapsed = {
                    collapsedIds = if (subtask.id in collapsedIds) {
                        collapsedIds - subtask.id
                    } else {
                        collapsedIds + subtask.id
                    }
                },
                onToggleCompleted = { onToggleCompleted(subtask.id, it) },
                onEdit = {
                    editing = subtask
                    showEditor = true
                },
                onDelete = { pendingDelete = subtask },
            )
            Spacer(Modifier.height(6.dp))
        }
    }

    if (showEditor) {
        DailySubtaskEditorCard(
            subtask = editing,
            onCancel = {
                showEditor = false
                editing = null
            },
            onConfirm = { title, note, deadline ->
                val target = editing
                val close = { ok: Boolean ->
                    if (ok) {
                        showEditor = false
                        editing = null
                    }
                }
                if (target == null) {
                    onAdd(title, note, deadline)
                    close(true)
                } else {
                    onUpdate(target.id, title, note, deadline)
                    close(true)
                }
            },
        )
        Spacer(Modifier.height(6.dp))
    } else {
        OutlinedButton(
            onClick = {
                editing = null
                showEditor = true
            },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.todo_subtask_add))
        }
    }

    pendingDelete?.let { subtask ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.todo_subtask_delete_title)) },
            text = { Text(stringResource(R.string.todo_subtask_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(subtask.id)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun DailySubtaskRow(
    subtask: Subtask,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onToggleCompleted: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val completed = subtask.isCompleted
    val checkboxDescription = stringResource(
        if (completed) R.string.todo_mark_not_done else R.string.todo_mark_done,
        subtask.title,
    )
    val toggleDescription = stringResource(
        if (collapsed) R.string.todo_subtask_expand else R.string.todo_subtask_collapse,
        subtask.title,
    )
    val deleteDescription = stringResource(R.string.todo_subtask_delete, subtask.title)
    val contentColor = if (completed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = completed,
                onCheckedChange = onToggleCompleted,
                modifier = Modifier.semantics { contentDescription = checkboxDescription },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onEdit)
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = subtask.title.decorated(completed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!collapsed) {
                    if (subtask.note.isNotBlank()) {
                        Text(
                            text = subtask.note.decorated(completed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    SubtaskDeadlineLabel(subtask.deadline, completed, Modifier.padding(top = 3.dp))
                }
            }
            IconButton(
                onClick = onToggleCollapsed,
                modifier = Modifier.semantics { contentDescription = toggleDescription },
            ) {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.semantics { contentDescription = deleteDescription },
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SubtaskDeadlineLabel(deadline: Long?, completed: Boolean, modifier: Modifier = Modifier) {
    if (deadline == null) return
    val today = DailyDates.today()
    val text = when {
        deadline < today -> stringResource(R.string.todo_overdue_on, DailyDates.format(deadline))
        deadline == today -> stringResource(R.string.todo_due_today_label)
        deadline == today + 1 -> stringResource(R.string.todo_due_tomorrow_label)
        else -> stringResource(R.string.todo_due_on, DailyDates.format(deadline))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant
        else if (deadline < today) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Inline add/edit form for one subtask: title, note and an optional deadline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailySubtaskEditorCard(
    subtask: Subtask?,
    onCancel: () -> Unit,
    onConfirm: (title: String, note: String, deadline: Long?) -> Unit,
) {
    val today = DailyDates.today()
    var title by remember(subtask?.id) { mutableStateOf(subtask?.title.orEmpty()) }
    var note by remember(subtask?.id) { mutableStateOf(subtask?.note.orEmpty()) }
    var deadline by remember(subtask?.id) { mutableStateOf(subtask?.deadline) }
    var showTitleError by remember(subtask?.id) { mutableStateOf(false) }
    var showPicker by remember(subtask?.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(
                text = stringResource(
                    if (subtask == null) R.string.todo_subtask_new_title
                    else R.string.todo_subtask_edit_title,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )

            Text(
                text = stringResource(R.string.todo_subtask_title_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            )
            DailyBorderlessTextField(
                value = title,
                onValueChange = {
                    title = it
                    showTitleError = false
                },
                placeholder = stringResource(R.string.todo_subtask_title_hint),
                singleLine = true,
                isError = showTitleError,
                modifier = Modifier.testTag(DAILY_SUBTASK_TITLE_TAG),
            )
            if (showTitleError) {
                Text(
                    text = stringResource(R.string.todo_subtask_title_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.todo_subtask_note_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            )
            DailyBorderlessTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = stringResource(R.string.todo_subtask_note_hint),
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.testTag(DAILY_SUBTASK_NOTE_TAG),
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.todo_subtask_deadline_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp),
            ) {
                FilterChip(
                    selected = deadline == null,
                    onClick = { deadline = null },
                    label = { Text(stringResource(R.string.todo_subtask_deadline_none)) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.testTag(DAILY_SUBTASK_DEADLINE_NONE_TAG),
                )
                FilterChip(
                    selected = deadline == today,
                    onClick = { deadline = today },
                    label = { Text(stringResource(R.string.todo_subtask_due_today)) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.testTag(DAILY_SUBTASK_DEADLINE_TODAY_TAG),
                )
                FilterChip(
                    selected = deadline == today + 1,
                    onClick = { deadline = today + 1 },
                    label = { Text(stringResource(R.string.todo_subtask_due_tomorrow)) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.testTag(DAILY_SUBTASK_DEADLINE_TOMORROW_TAG),
                )
            }

            TextButton(
                onClick = { showPicker = true },
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text(
                    text = deadline?.let { DailyDates.format(it) }
                        ?: stringResource(R.string.todo_subtask_pick_date),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
                TextButton(
                    onClick = {
                        if (title.isBlank()) {
                            showTitleError = true
                        } else {
                            onConfirm(title, note, deadline)
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (subtask == null) R.string.todo_subtask_add_confirm
                            else R.string.todo_subtask_save_confirm,
                        ),
                    )
                }
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = deadline?.let(DailyDates::toUtcMillis)
                ?: DailyDates.toUtcMillis(today),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            deadline = DailyDates.fromUtcMillis(millis)
                        }
                        showPicker = false
                    },
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Text input with no outline or underline; the card is the field. */
@Composable
private fun DailyBorderlessTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            errorTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary,
            errorCursorColor = MaterialTheme.colorScheme.error,
        ),
    )
}

/** Test tags for the inline Dailies subtask editor. */
internal const val DAILY_SUBTASK_TITLE_TAG = "dailySubtaskTitle"
internal const val DAILY_SUBTASK_NOTE_TAG = "dailySubtaskNote"
internal const val DAILY_SUBTASK_DEADLINE_NONE_TAG = "dailySubtaskDeadlineNone"
internal const val DAILY_SUBTASK_DEADLINE_TODAY_TAG = "dailySubtaskDeadlineToday"
internal const val DAILY_SUBTASK_DEADLINE_TOMORROW_TAG = "dailySubtaskDeadlineTomorrow"
