package com.example.mnlists.ui.todos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.mnlists.R
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.ui.components.ReminderSection

/**
 * Create/edit page for a to-do (SPEC §4.2/§4.3).
 *
 * The due date is optional: "No deadline" is a valid saved state. Subtasks are
 * edited live and follow the §8 cascade (all children done → parent done;
 * parent toggled → all children follow). Completed subtasks stay in place,
 * grayed out and struck through.
 *
 * The subtask form is an inline card rather than a dialog: a text field inside
 * a second window never settles under Robolectric (the Compose test clock keeps
 * waiting on it), which would make the required Compose tests impossible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditorScreen(
    onDone: () -> Unit,
    /**
     * Fired when a first subtask/reminder commits a brand-new parent. The
     * scaffold swaps the "New to-do" route for the edit route so the top-bar
     * title matches the editor's new edit-mode state.
     */
    onParentCreated: (Long) -> Unit = {},
    viewModel: TodoEditorViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    val createdParentId = viewModel.createdParentId
    LaunchedEffect(createdParentId) {
        if (createdParentId != null) onParentCreated(createdParentId)
    }
    val subtasks = viewModel.subtasks
    val reminders = viewModel.reminders
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showSubtaskEditor by remember { mutableStateOf(false) }
    var editingSubtask by remember { mutableStateOf<Subtask?>(null) }
    var pendingDelete by remember { mutableStateOf<Subtask?>(null) }
    // Collapsed children are tracked by id; everything is expanded by default.
    var collapsedIds by remember { mutableStateOf(setOf<Long>()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                SectionHeader(stringResource(R.string.todo_section_details))

                InputCard {
                    FieldLabel(stringResource(R.string.todo_title_label))
                    BorderlessTextField(
                        value = state.title,
                        onValueChange = viewModel::setTitle,
                        placeholder = stringResource(R.string.todo_title_hint),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium,
                        isError = state.titleError,
                        modifier = Modifier.testTag(TODO_TITLE_TAG),
                    )
                    if (state.titleError) {
                        FieldError(stringResource(R.string.todo_title_error))
                    }
                }

                Spacer(Modifier.height(10.dp))

                // The note is the emphasised field on this page, like habits.
                InputCard {
                    FieldLabel(
                        text = stringResource(R.string.todo_note_label),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    BorderlessTextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        placeholder = stringResource(R.string.todo_note_hint),
                        minLines = 4,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodyLarge,
                    )
                }

                Spacer(Modifier.height(24.dp))

                SectionHeader(stringResource(R.string.todo_section_due))

                DueDateCard(
                    label = stringResource(R.string.todo_due_label),
                    value = state.dueDate?.let { TodoDates.format(it) }
                        ?: stringResource(R.string.todo_due_unset),
                    isError = false,
                    onClick = { showDatePicker = true },
                )

                FieldSupport(stringResource(R.string.todo_due_support))

                Spacer(Modifier.height(8.dp))

                val today = TodoDates.today()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickDateChip(
                        label = stringResource(R.string.todo_due_today),
                        selected = state.dueDate == today,
                        onClick = { viewModel.setDueDate(today) },
                    )
                    QuickDateChip(
                        label = stringResource(R.string.todo_due_tomorrow),
                        selected = state.dueDate == today + 1,
                        onClick = { viewModel.setDueDate(today + 1) },
                    )
                    QuickDateChip(
                        label = stringResource(R.string.todo_due_next_week),
                        selected = state.dueDate == today + 7,
                        onClick = { viewModel.setDueDate(today + 7) },
                    )
                    QuickDateChip(
                        label = stringResource(R.string.todo_due_clear),
                        selected = state.dueDate == null,
                        onClick = { viewModel.setDueDate(null) },
                    )
                }

                Spacer(Modifier.height(24.dp))

                ReminderSection(
                    reminders = reminders,
                    allowDate = true,
                    onAdd = viewModel::addReminder,
                    onUpdate = viewModel::updateReminder,
                    onDelete = viewModel::deleteReminder,
                )

                Spacer(Modifier.height(24.dp))

                SectionHeader(stringResource(R.string.todo_subtask_section))

                if (subtasks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.todo_subtask_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                } else {
                    subtasks.forEach { subtask ->
                        SubtaskRow(
                            subtask = subtask,
                            collapsed = subtask.id in collapsedIds,
                            onToggleCollapsed = {
                                collapsedIds = if (subtask.id in collapsedIds) {
                                    collapsedIds - subtask.id
                                } else {
                                    collapsedIds + subtask.id
                                }
                            },
                            onToggleCompleted = { completed ->
                                viewModel.setSubtaskCompleted(subtask.id, completed)
                            },
                            onEdit = {
                                editingSubtask = subtask
                                showSubtaskEditor = true
                            },
                            onDelete = { pendingDelete = subtask },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }

                if (showSubtaskEditor) {
                    SubtaskEditorCard(
                        subtask = editingSubtask,
                        onCancel = {
                            showSubtaskEditor = false
                            editingSubtask = null
                        },
                        onConfirm = { title, note, deadline ->
                            val target = editingSubtask
                            val close = { ok: Boolean ->
                                if (ok) {
                                    showSubtaskEditor = false
                                    editingSubtask = null
                                }
                            }
                            if (target == null) {
                                viewModel.addSubtask(title, note, deadline, close)
                            } else {
                                viewModel.updateSubtask(target.id, title, note, deadline, close)
                            }
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                } else {
                    OutlinedButton(
                        onClick = {
                            editingSubtask = null
                            showSubtaskEditor = true
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

                if (viewModel.isEditing) {
                    Spacer(Modifier.height(10.dp))
                    // Parent completion lives here too so the cascade can be
                    // driven from the same page that shows the children.
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val parentDescription = stringResource(
                                if (state.isCompleted) R.string.todo_mark_not_done
                                else R.string.todo_mark_done,
                                state.title,
                            )
                            Checkbox(
                                checked = state.isCompleted,
                                onCheckedChange = viewModel::setParentCompleted,
                                modifier = Modifier.semantics {
                                    contentDescription = parentDescription
                                },
                            )
                            Text(
                                text = stringResource(R.string.todo_parent_done_label),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (state.isCompleted) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(26.dp))

                Button(
                    onClick = { viewModel.save(onDone) },
                    enabled = !state.isSaving,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.todo_save),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }

                if (viewModel.isEditing) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.todo_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.todo_delete_confirm_title)) },
            text = { Text(stringResource(R.string.todo_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete(onDone)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
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
                        viewModel.deleteSubtask(subtask.id)
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

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.dueDate?.let(TodoDates::toUtcMillis)
                ?: TodoDates.toUtcMillis(TodoDates.today()),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            viewModel.setDueDate(TodoDates.fromUtcMillis(millis))
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** One child row: checkbox, title (tap to edit), note/deadline, collapse, delete. */
@Composable
private fun SubtaskRow(
    subtask: Subtask,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onToggleCompleted: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val completed = subtask.isCompleted
    val titleText = subtask.title.decorated(completed)
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
                modifier = Modifier.semantics {
                    contentDescription = checkboxDescription
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onEdit)
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = titleText,
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
                    TodoDateLabel(
                        dueDate = subtask.deadline,
                        completed = completed,
                        modifier = Modifier.padding(top = 3.dp),
                    )
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

/** Inline add/edit form for one subtask: title, note and an optional deadline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtaskEditorCard(
    subtask: Subtask?,
    onCancel: () -> Unit,
    onConfirm: (title: String, note: String, deadline: Long?) -> Unit,
) {
    val today = TodoDates.today()
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

            FieldLabel(stringResource(R.string.todo_subtask_title_label))
            BorderlessTextField(
                value = title,
                onValueChange = {
                    title = it
                    showTitleError = false
                },
                placeholder = stringResource(R.string.todo_subtask_title_hint),
                singleLine = true,
                isError = showTitleError,
                modifier = Modifier.testTag(SUBTASK_TITLE_TAG),
            )
            if (showTitleError) {
                FieldError(stringResource(R.string.todo_subtask_title_error))
            }

            Spacer(Modifier.height(10.dp))

            FieldLabel(stringResource(R.string.todo_subtask_note_label))
            BorderlessTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = stringResource(R.string.todo_subtask_note_hint),
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.testTag(SUBTASK_NOTE_TAG),
            )

            Spacer(Modifier.height(14.dp))

            FieldLabel(stringResource(R.string.todo_subtask_deadline_label))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp),
            ) {
                FilterChip(
                    selected = deadline == null,
                    onClick = { deadline = null },
                    label = { Text(stringResource(R.string.todo_subtask_deadline_none)) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.testTag(SUBTASK_DEADLINE_NONE_TAG),
                )
                FilterChip(
                    selected = deadline == today,
                    onClick = { deadline = today },
                    label = { Text(stringResource(R.string.todo_subtask_due_today)) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.testTag(SUBTASK_DEADLINE_TODAY_TAG),
                )
                FilterChip(
                    selected = deadline == today + 1,
                    onClick = { deadline = today + 1 },
                    label = { Text(stringResource(R.string.todo_subtask_due_tomorrow)) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.testTag(SUBTASK_DEADLINE_TOMORROW_TAG),
                )
            }

            TextButton(
                onClick = { showPicker = true },
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text(
                    text = deadline?.let { TodoDates.format(it) }
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
            initialSelectedDateMillis = deadline?.let(TodoDates::toUtcMillis)
                ?: TodoDates.toUtcMillis(today),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            deadline = TodoDates.fromUtcMillis(millis)
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

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** Borderless, rounded surface that holds one or more inputs. */
@Composable
private fun InputCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            content = content,
        )
    }
}

@Composable
private fun FieldLabel(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
    )
}

@Composable
private fun FieldSupport(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
    )
}

@Composable
private fun FieldError(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
    )
}

/** Text input with no outline or underline; the card is the field. */
@Composable
private fun BorderlessTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    isError: Boolean = false,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = textStyle,
        placeholder = {
            Text(
                text = placeholder,
                style = textStyle,
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

/** Full-width date field: label + current value + calendar icon. */
@Composable
private fun DueDateCard(
    label: String,
    value: String,
    isError: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = if (isError) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickDateChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = MaterialTheme.shapes.large,
    )
}

/** Test tags for the parent title field and the subtask editor fields. */
internal const val TODO_TITLE_TAG = "todoTitle"
internal const val SUBTASK_TITLE_TAG = "subtaskTitle"
internal const val SUBTASK_NOTE_TAG = "subtaskNote"
internal const val SUBTASK_DEADLINE_NONE_TAG = "subtaskDeadlineNone"
internal const val SUBTASK_DEADLINE_TODAY_TAG = "subtaskDeadlineToday"
internal const val SUBTASK_DEADLINE_TOMORROW_TAG = "subtaskDeadlineTomorrow"
