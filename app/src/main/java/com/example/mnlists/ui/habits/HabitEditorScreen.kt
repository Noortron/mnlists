package com.example.mnlists.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.mnlists.R
import com.example.mnlists.data.model.ResetSchedule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Create/edit page for a habit (SPEC §4.1): tap a title in the list to get here.
 * The note is visually emphasized and fully in view, and deletion asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditorScreen(
    onDone: () -> Unit,
    viewModel: HabitEditorViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(R.string.habit_title_label)) },
                singleLine = true,
                isError = state.titleError,
                supportingText = if (state.titleError) {
                    { Text(stringResource(R.string.habit_title_error)) }
                } else {
                    null
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            // The note is the emphasised field on this page (SPEC §4.1).
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.habit_note_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        placeholder = { Text(stringResource(R.string.habit_note_hint)) },
                        minLines = 4,
                        maxLines = 8,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.habit_schedule_section))
            ScheduleDropdown(
                schedule = state.schedule,
                onSelect = viewModel::setSchedule,
            )

            if (state.schedule == ResetSchedule.EVERY_N) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.everyN,
                    onValueChange = viewModel::setEveryN,
                    label = { Text(stringResource(R.string.habit_every_n_label)) },
                    singleLine = true,
                    isError = state.everyNError,
                    supportingText = {
                        Text(
                            if (state.everyNError) {
                                stringResource(R.string.habit_every_n_error)
                            } else {
                                stringResource(R.string.habit_every_n_support)
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.schedule == ResetSchedule.WEEKLY) {
                Spacer(Modifier.height(12.dp))
                WeekdayDropdown(
                    weekday = state.weekday,
                    onSelect = viewModel::setWeekday,
                )
            }

            Spacer(Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.habit_counter_section))

            DirectionRow(
                label = stringResource(R.string.habit_allow_up),
                description = stringResource(R.string.habit_allow_up_support),
                checked = state.allowUp,
                onCheckedChange = viewModel::setAllowUp,
            )
            DirectionRow(
                label = stringResource(R.string.habit_allow_down),
                description = stringResource(R.string.habit_allow_down_support),
                checked = state.allowDown,
                onCheckedChange = viewModel::setAllowDown,
            )
            if (state.directionsError) {
                Text(
                    text = stringResource(R.string.habit_directions_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                )
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = { viewModel.save(onDone) },
                enabled = !state.isSaving,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.habit_save))
                }
            }

            if (viewModel.isEditing) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.habit_delete))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.habit_delete_confirm_title)) },
            text = { Text(stringResource(R.string.habit_delete_confirm_body)) },
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
            shape = MaterialTheme.shapes.large,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDropdown(
    schedule: ResetSchedule,
    onSelect: (ResetSchedule) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = stringResource(schedule.labelRes()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.habit_schedule_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ResetSchedule.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekdayDropdown(
    weekday: DayOfWeek?,
    onSelect: (DayOfWeek?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val noneLabel = stringResource(R.string.habit_weekday_none)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = weekday?.displayName() ?: noneLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.habit_weekday_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(noneLabel) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            DayOfWeek.entries.forEach { day ->
                DropdownMenuItem(
                    text = { Text(day.displayName()) },
                    onClick = {
                        expanded = false
                        onSelect(day)
                    },
                )
            }
        }
    }
}

@Composable
private fun DirectionRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ResetSchedule.labelRes(): Int = when (this) {
    ResetSchedule.DAILY -> R.string.habit_schedule_daily
    ResetSchedule.WEEKLY -> R.string.habit_schedule_weekly
    ResetSchedule.MONTHLY -> R.string.habit_schedule_monthly
    ResetSchedule.EVERY_N -> R.string.habit_schedule_every_n
}

private fun DayOfWeek.displayName(): String =
    getDisplayName(TextStyle.FULL, Locale.getDefault())
