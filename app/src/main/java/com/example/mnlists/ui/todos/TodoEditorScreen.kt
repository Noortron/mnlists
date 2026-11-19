package com.example.mnlists.ui.todos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.mnlists.R

/**
 * Create/edit page for a to-do (SPEC §4.2).
 *
 * The due date is mandatory: quick chips cover the common cases and the card
 * opens a full date picker. Saving is blocked until both a title and a date
 * exist. Deletion warns that subtasks and reminders go with the parent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditorScreen(
    onDone: () -> Unit,
    viewModel: TodoEditorViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

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
                    isError = state.dueDateError,
                    onClick = { showDatePicker = true },
                )

                if (state.dueDateError) {
                    FieldError(stringResource(R.string.todo_due_error))
                } else {
                    FieldSupport(stringResource(R.string.todo_due_support))
                }

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
