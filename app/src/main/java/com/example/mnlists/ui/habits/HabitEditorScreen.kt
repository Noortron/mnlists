package com.example.mnlists.ui.habits

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.mnlists.R
import com.example.mnlists.data.model.ResetSchedule
import java.time.DayOfWeek
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale

/**
 * Create/edit page for a habit (SPEC §4.1).
 *
 * Layout: borderless input cards on a slightly darker canvas, grouped under
 * "Details" / "Scheduling" / "Counter" headers. The note is the emphasised
 * field, and deletion asks first.
 */
@Composable
fun HabitEditorScreen(
    onDone: () -> Unit,
    viewModel: HabitEditorViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

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
                SectionHeader(stringResource(R.string.habit_section_details))

                InputCard {
                    FieldLabel(stringResource(R.string.habit_title_label))
                    BorderlessTextField(
                        value = state.title,
                        onValueChange = viewModel::setTitle,
                        placeholder = stringResource(R.string.habit_title_hint),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium,
                        isError = state.titleError,
                        modifier = Modifier.testTag(HABIT_TITLE_TAG),
                    )
                    if (state.titleError) {
                        FieldError(stringResource(R.string.habit_title_error))
                    }
                }

                Spacer(Modifier.height(10.dp))

                // The note is the emphasised field on this page (SPEC §4.1).
                InputCard {
                    FieldLabel(
                        text = stringResource(R.string.habit_note_label),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    BorderlessTextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        placeholder = stringResource(R.string.habit_note_hint),
                        minLines = 4,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodyLarge,
                    )
                }

                Spacer(Modifier.height(24.dp))

                SectionHeader(stringResource(R.string.habit_section_scheduling))

                val selectableSchedules = scheduleOptions()

                DropdownCard(
                    label = stringResource(R.string.habit_schedule_label),
                    value = stringResource(state.schedule.labelRes()),
                    options = selectableSchedules.map { stringResource(it.labelRes()) },
                    onSelected = { index ->
                        viewModel.setSchedule(selectableSchedules[index])
                    },
                )

                if (state.schedule == ResetSchedule.EVERY_N) {
                    Spacer(Modifier.height(10.dp))
                    InputCard {
                        FieldLabel(stringResource(R.string.habit_every_n_label))
                        BorderlessTextField(
                            value = state.everyN,
                            onValueChange = viewModel::setEveryN,
                            placeholder = "1",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = state.everyNError,
                        )
                        FieldSupport(
                            text = stringResource(
                                if (state.everyNError) {
                                    R.string.habit_every_n_error
                                } else {
                                    R.string.habit_every_n_support
                                },
                            ),
                            isError = state.everyNError,
                        )
                    }
                }

                if (state.schedule == ResetSchedule.WEEKLY) {
                    Spacer(Modifier.height(10.dp))
                    val weekdays = DayOfWeek.entries
                    DropdownCard(
                        label = stringResource(R.string.habit_weekday_label),
                        value = state.weekday?.displayName()
                            ?: stringResource(R.string.habit_weekday_none),
                        options = listOf(stringResource(R.string.habit_weekday_none)) +
                            weekdays.map { it.displayName() },
                        onSelected = { index ->
                            viewModel.setWeekday(
                                if (index == 0) null else weekdays[index - 1],
                            )
                        },
                    )
                }

                Spacer(Modifier.height(24.dp))

                SectionHeader(stringResource(R.string.habit_section_counter))

                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    DirectionToggle(
                        icon = Icons.Filled.Remove,
                        label = stringResource(R.string.habit_counter_decrease),
                        allowed = state.allowDown,
                        onToggle = { viewModel.setAllowDown(!state.allowDown) },
                    )
                    DirectionToggle(
                        icon = Icons.Filled.Add,
                        label = stringResource(R.string.habit_counter_increase),
                        allowed = state.allowUp,
                        onToggle = { viewModel.setAllowUp(!state.allowUp) },
                    )
                }

                FieldSupport(
                    text = stringResource(R.string.habit_counter_hint),
                    isError = state.directionsError,
                    errorText = stringResource(R.string.habit_directions_error),
                )

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
                            text = stringResource(R.string.habit_save),
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
                            text = stringResource(R.string.habit_delete),
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

/** Reset schedules offered in the editor; Monthly is intentionally UI-removed. */
private fun scheduleOptions(): List<ResetSchedule> =
    ResetSchedule.entries.filter { it != ResetSchedule.MONTHLY }

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
private fun FieldSupport(
    text: String,
    isError: Boolean = false,
    errorText: String? = null,
) {
    Text(
        text = if (isError && errorText != null) errorText else text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
    )
}

@Composable
private fun FieldError(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
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
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
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
        keyboardOptions = keyboardOptions,
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

/**
 * Full-width dropdown styled as a field card: label + current value + chevron.
 * Used for both the reset schedule and the optional reset day.
 */
@Composable
private fun DropdownCard(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
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

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelected(index)
                    },
                )
            }
        }
    }
}

/**
 * Round toggle for one counter direction. Filled when enabled, outlined when
 * hidden, so the state is obvious at a glance.
 */
@Composable
private fun DirectionToggle(
    icon: ImageVector,
    label: String,
    allowed: Boolean,
    onToggle: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(60.dp)
                .toggleable(
                    value = allowed,
                    role = Role.Switch,
                    onValueChange = { onToggle() },
                )
                .semantics { contentDescription = label },
            shape = CircleShape,
            color = if (allowed) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            },
            contentColor = if (allowed) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            border = if (allowed) {
                null
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (allowed) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun ResetSchedule.labelRes(): Int = when (this) {
    ResetSchedule.DAILY -> R.string.habit_schedule_daily
    ResetSchedule.WEEKLY -> R.string.habit_schedule_weekly
    ResetSchedule.MONTHLY -> R.string.habit_schedule_monthly
    ResetSchedule.EVERY_N -> R.string.habit_schedule_every_n
}

private fun DayOfWeek.displayName(): String =
    getDisplayName(DayTextStyle.FULL, Locale.getDefault())

/** Test tag for the parent title field. */
internal const val HABIT_TITLE_TAG = "habitTitle"
