package com.example.mnlists.ui.dailies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.mnlists.R
import com.example.mnlists.data.model.RepeatType
import java.time.DayOfWeek
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale

/**
 * Create/edit page for a Daily. In [DailyEditorMode.SERIES] it edits the
 * recurring series (title, note, start date, repeat schedule). In
 * [DailyEditorMode.OCCURRENCE] the series fields are read-only and the page
 * offers completion/skip plus subtasks for that one day — the rule the
 * Calendar tab will use in Step 12 (SPEC §9).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyEditorScreen(
    onDone: () -> Unit,
    viewModel: DailyEditorViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showStartPicker by rememberSaveable { mutableStateOf(false) }

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
                if (viewModel.mode == DailyEditorMode.SERIES) {
                    SeriesFields(
                        state = state,
                        viewModel = viewModel,
                        showStartPicker = showStartPicker,
                        onOpenStartPicker = { showStartPicker = true },
                        onCloseStartPicker = { showStartPicker = false },
                    )
                } else {
                    OccurrenceFields(state = state, viewModel = viewModel)
                }

                Spacer(Modifier.height(26.dp))

                if (viewModel.mode == DailyEditorMode.SERIES) {
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
                                text = stringResource(R.string.daily_save),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onDone,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.action_ok),
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
                            text = stringResource(
                                if (viewModel.mode == DailyEditorMode.SERIES) {
                                    R.string.daily_delete
                                } else {
                                    R.string.daily_delete_occurrence
                                },
                            ),
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
            title = {
                Text(
                    stringResource(
                        if (viewModel.mode == DailyEditorMode.SERIES) {
                            R.string.daily_delete_confirm_title
                        } else {
                            R.string.daily_delete_occurrence_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (viewModel.mode == DailyEditorMode.SERIES) {
                            R.string.daily_delete_confirm_body
                        } else {
                            R.string.daily_delete_occurrence_body
                        },
                    ),
                )
            },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesFields(
    state: DailyEditorUiState,
    viewModel: DailyEditorViewModel,
    showStartPicker: Boolean,
    onOpenStartPicker: () -> Unit,
    onCloseStartPicker: () -> Unit,
) {
    SectionHeader(stringResource(R.string.daily_section_details))

    InputCard {
        FieldLabel(stringResource(R.string.daily_title_label))
        DailyBorderlessTextField(
            value = state.title,
            onValueChange = viewModel::setTitle,
            placeholder = stringResource(R.string.daily_title_hint),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium,
            isError = state.titleError,
            modifier = Modifier.testTag(DAILY_TITLE_TAG),
        )
        if (state.titleError) {
            FieldError(stringResource(R.string.daily_title_error))
        }
    }

    Spacer(Modifier.height(10.dp))

    InputCard {
        FieldLabel(
            text = stringResource(R.string.daily_note_label),
            color = MaterialTheme.colorScheme.primary,
        )
        DailyBorderlessTextField(
            value = state.note,
            onValueChange = viewModel::setNote,
            placeholder = stringResource(R.string.daily_note_hint),
            minLines = 4,
            maxLines = 8,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(DAILY_NOTE_TAG),
        )
    }

    Spacer(Modifier.height(24.dp))

    SectionHeader(stringResource(R.string.daily_section_schedule))

    StartDateCard(
        value = DailyDates.format(state.startDate),
        onClick = onOpenStartPicker,
    )

    FieldSupport(stringResource(R.string.daily_start_support))

    Spacer(Modifier.height(8.dp))

    val today = DailyDates.today()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.startDate == today,
            onClick = { viewModel.setStartDate(today) },
            label = { Text(stringResource(R.string.daily_start_today)) },
            shape = MaterialTheme.shapes.large,
        )
        FilterChip(
            selected = state.startDate == today + 1,
            onClick = { viewModel.setStartDate(today + 1) },
            label = { Text(stringResource(R.string.daily_start_tomorrow)) },
            shape = MaterialTheme.shapes.large,
        )
    }

    Spacer(Modifier.height(10.dp))

    RepeatDropdown(state.repeatType, viewModel::setRepeatType)

    Spacer(Modifier.height(10.dp))

    InputCard {
        FieldLabel(stringResource(R.string.daily_every_n_label))
        DailyBorderlessTextField(
            value = state.everyN,
            onValueChange = viewModel::setEveryN,
            placeholder = stringResource(R.string.daily_every_n_hint),
            singleLine = true,
            isError = state.everyNError,
            modifier = Modifier.testTag(DAILY_EVERY_N_TAG),
        )
        if (state.everyNError) {
            FieldError(stringResource(R.string.daily_every_n_error))
        } else {
            FieldSupport(stringResource(R.string.daily_every_n_support))
        }
    }

    if (state.repeatType == RepeatType.WEEKLY) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.daily_weekdays_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
        ) {
            DayOfWeek.entries.forEach { day ->
                val selected = day in state.weekdays
                val fullName = day.getDisplayName(DayTextStyle.FULL, Locale.getDefault())
                Surface(
                    onClick = { viewModel.toggleWeekday(day) },
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .semantics { contentDescription = fullName },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = day.getDisplayName(DayTextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    SectionHeader(stringResource(R.string.todo_subtask_section))

    // Subtasks live on the occurrence, but are edited from this parent editor
    // (create/edit mode). In a brand-new series they are staged until Save.
    DailySubtaskSection(
        subtasks = viewModel.subtasks,
        onToggleCompleted = viewModel::setSubtaskCompleted,
        onAdd = { title, note, deadline -> viewModel.addSubtask(title, note, deadline) {} },
        onUpdate = { id, title, note, deadline ->
            viewModel.updateSubtask(id, title, note, deadline) {}
        },
        onDelete = viewModel::deleteSubtask,
    )

    if (showStartPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = DailyDates.toUtcMillis(state.startDate),
        )
        DatePickerDialog(
            onDismissRequest = onCloseStartPicker,
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            viewModel.setStartDate(DailyDates.fromUtcMillis(millis))
                        }
                        onCloseStartPicker()
                    },
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onCloseStartPicker) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun OccurrenceFields(
    state: DailyEditorUiState,
    viewModel: DailyEditorViewModel,
) {
    SectionHeader(stringResource(R.string.daily_section_details))

    InputCard {
        FieldLabel(stringResource(R.string.daily_title_label))
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        FieldSupport(stringResource(R.string.daily_occurrence_readonly))
    }

    Spacer(Modifier.height(10.dp))

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
            val doneDescription = stringResource(
                if (state.isCompleted) R.string.todo_mark_not_done else R.string.todo_mark_done,
                state.title,
            )
            val skipDescription = stringResource(
                if (state.isSkipped) R.string.daily_unskip_description
                else R.string.daily_skip_description,
                state.title,
            )
            Checkbox(
                checked = state.isCompleted,
                onCheckedChange = viewModel::setCompleted,
                modifier = Modifier.semantics { contentDescription = doneDescription },
            )
            Text(
                text = stringResource(R.string.daily_done_label),
                style = MaterialTheme.typography.bodyLarge,
                color = if (state.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            TextButton(
                onClick = { viewModel.setSkipped(!state.isSkipped) },
                modifier = Modifier.semantics { contentDescription = skipDescription },
            ) {
                Text(
                    stringResource(if (state.isSkipped) R.string.daily_unskip else R.string.daily_skip),
                )
            }
        }
    }

    if (state.note.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        InputCard {
            FieldLabel(stringResource(R.string.daily_note_label))
            Text(
                text = state.note,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    SectionHeader(stringResource(R.string.todo_subtask_section))

    DailySubtaskSection(
        subtasks = viewModel.subtasks,
        onToggleCompleted = viewModel::setSubtaskCompleted,
        onAdd = { title, note, deadline -> viewModel.addSubtask(title, note, deadline) {} },
        onUpdate = { id, title, note, deadline -> viewModel.updateSubtask(id, title, note, deadline) {} },
        onDelete = viewModel::deleteSubtask,
    )
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

@Composable
private fun InputCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp), content = content)
    }
}

@Composable
private fun FieldLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
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

@Composable
private fun DailyBorderlessTextField(
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

@Composable
private fun StartDateCard(value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.daily_start_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
private fun RepeatDropdown(value: RepeatType, onSelected: (RepeatType) -> Unit) {
    val options = RepeatType.entries
    var expanded by remember { mutableStateOf(false) }
    val label = when (value) {
        RepeatType.DAILY -> stringResource(R.string.daily_repeat_daily)
        RepeatType.WEEKLY -> stringResource(R.string.daily_repeat_weekly)
        RepeatType.MONTHLY -> stringResource(R.string.daily_repeat_monthly)
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.daily_repeat_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                RepeatType.DAILY -> stringResource(R.string.daily_repeat_daily)
                                RepeatType.WEEKLY -> stringResource(R.string.daily_repeat_weekly)
                                RepeatType.MONTHLY -> stringResource(R.string.daily_repeat_monthly)
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

/** Test tags for the series editor fields. */
internal const val DAILY_TITLE_TAG = "dailyTitle"
internal const val DAILY_NOTE_TAG = "dailyNote"
internal const val DAILY_EVERY_N_TAG = "dailyEveryN"
