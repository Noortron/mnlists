package com.example.mnlists.ui.components

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mnlists.R
import com.example.mnlists.data.local.entity.Reminder
import com.example.mnlists.data.reminder.ReminderTimeEngine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Add / edit / delete reminder rows shared by the To-Do and Daily series
 * editors (SPEC §4.5, Step 11).
 *
 * A To-Do reminder shows a date and a time; a Daily reminder repeats at the
 * chosen time on every scheduled occurrence, so only the time is editable.
 * The add/edit form is an inline card — the same reason as subtasks: text
 * fields in a dialog never settle under Robolectric.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderSection(
    reminders: List<Reminder>,
    allowDate: Boolean,
    onAdd: (epochDay: Long, minutesOfDay: Int) -> Unit,
    onUpdate: (reminderId: Long, epochDay: Long, minutesOfDay: Int) -> Unit,
    onDelete: (reminderId: Long) -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var pendingDelete by remember { mutableStateOf<Reminder?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Scheduling proceeds either way; Step 13 shows the status. */ }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Text(
        text = stringResource(R.string.reminder_section),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )

    if (reminders.isEmpty()) {
        Text(
            text = stringResource(R.string.reminder_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
    } else {
        reminders.forEach { reminder ->
            ReminderRow(
                reminder = reminder,
                allowDate = allowDate,
                zone = zone,
                onEdit = {
                    editing = reminder
                    showEditor = true
                },
                onDelete = { pendingDelete = reminder },
            )
            Spacer(Modifier.height(6.dp))
        }
    }

    if (showEditor) {
        ReminderEditorCard(
            reminder = editing,
            allowDate = allowDate,
            zone = zone,
            onCancel = {
                showEditor = false
                editing = null
            },
            onConfirm = { epochDay, minutes ->
                requestNotificationPermission()
                val target = editing
                if (target == null) {
                    onAdd(epochDay, minutes)
                } else {
                    onUpdate(target.id, epochDay, minutes)
                }
                showEditor = false
                editing = null
            },
        )
        Spacer(Modifier.height(6.dp))
    } else {
        OutlinedButton(
            onClick = {
                requestNotificationPermission()
                editing = null
                showEditor = true
            },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(REMINDER_ADD_TAG),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.reminder_add))
        }
    }

    pendingDelete?.let { reminder ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.reminder_delete_title)) },
            text = { Text(stringResource(R.string.reminder_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(reminder.id)
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
private fun ReminderRow(
    reminder: Reminder,
    allowDate: Boolean,
    zone: ZoneId,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val minutes = reminder.timeOfDayMinutes
        ?: ReminderTimeEngine.minutesOfDay(reminder.triggerAt, zone)
    val time = formatTime(minutes)
    val epochDay = ReminderTimeEngine.epochDayOf(reminder.triggerAt, zone)
    val deleteLabel = stringResource(R.string.reminder_delete_description)
    val value = if (allowDate) {
        "${formatDate(epochDay)} · $time"
    } else {
        stringResource(R.string.reminder_daily_value, time)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onEdit)
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.semantics {
                    contentDescription = deleteLabel
                },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditorCard(
    reminder: Reminder?,
    allowDate: Boolean,
    zone: ZoneId,
    onCancel: () -> Unit,
    onConfirm: (epochDay: Long, minutesOfDay: Int) -> Unit,
) {
    val today = LocalDate.now(zone).toEpochDay()
    val defaultMinutes = 9 * 60
    val defaultDay = if (ReminderTimeEngine.atLocalTime(today, defaultMinutes, zone) >
        System.currentTimeMillis()
    ) {
        today
    } else {
        today + 1
    }

    var epochDay by remember(reminder?.id) {
        mutableStateOf(
            reminder?.let { ReminderTimeEngine.epochDayOf(it.triggerAt, zone) } ?: defaultDay,
        )
    }
    var minutes by remember(reminder?.id) {
        mutableStateOf(
            reminder?.timeOfDayMinutes
                ?: reminder?.let { ReminderTimeEngine.minutesOfDay(it.triggerAt, zone) }
                ?: defaultMinutes,
        )
    }
    var showPicker by remember(reminder?.id) { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(
                text = stringResource(
                    if (reminder == null) R.string.reminder_new_title
                    else R.string.reminder_edit_title,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )

            if (allowDate) {
                Text(
                    text = stringResource(R.string.reminder_date_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp),
                ) {
                    FilterChip(
                        selected = epochDay == today,
                        onClick = { epochDay = today },
                        label = { Text(stringResource(R.string.reminder_today)) },
                        shape = MaterialTheme.shapes.large,
                    )
                    FilterChip(
                        selected = epochDay == today + 1,
                        onClick = { epochDay = today + 1 },
                        label = { Text(stringResource(R.string.reminder_tomorrow)) },
                        shape = MaterialTheme.shapes.large,
                    )
                }
                TextButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Text(formatDate(epochDay))
                }
                Spacer(Modifier.height(4.dp))
            }

            Text(
                text = stringResource(R.string.reminder_time_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            )
            TextButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> minutes = hour * 60 + minute },
                        minutes / 60,
                        minutes % 60,
                        true,
                    ).show()
                },
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text(formatTime(minutes))
            }

            Text(
                text = stringResource(
                    if (allowDate) R.string.reminder_todo_support
                    else R.string.reminder_daily_support,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
                TextButton(
                    onClick = { onConfirm(epochDay, minutes) },
                    modifier = Modifier.testTag(REMINDER_CONFIRM_TAG),
                ) {
                    Text(
                        stringResource(
                            if (reminder == null) R.string.reminder_set_confirm
                            else R.string.reminder_save_confirm,
                        ),
                    )
                }
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = epochDayToUtcMillis(epochDay),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            epochDay = utcMillisToEpochDay(it)
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

private fun formatTime(minutesOfDay: Int): String {
    val minutes = minutesOfDay.coerceIn(0, ReminderTimeEngine.MINUTES_PER_DAY - 1)
    return LocalTime.of(minutes / 60, minutes % 60)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))
}

private fun formatDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay)
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

private fun epochDayToUtcMillis(epochDay: Long): Long =
    LocalDate.ofEpochDay(epochDay)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

private fun utcMillisToEpochDay(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

/** Test tags used by the reminder Compose tests. */
internal const val REMINDER_ADD_TAG = "reminderAdd"
internal const val REMINDER_CONFIRM_TAG = "reminderConfirm"
