package com.example.mnlists.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mnlists.R
import com.example.mnlists.data.model.ResetSchedule
import com.example.mnlists.data.settings.NotificationAccessState
import com.example.mnlists.data.settings.NotificationAccessStatus
import com.example.mnlists.data.settings.ThemeMode
import com.example.mnlists.ui.backup.BackupFileIo
import com.example.mnlists.ui.backup.BackupViewModel
import com.example.mnlists.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

/** Stateful Settings destination: persisted preferences plus live permission state. */
@Composable
fun SettingsRoute(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val defaultSchedule by viewModel.defaultResetSchedule.collectAsStateWithLifecycle()
    val notificationAccess by viewModel.notificationAccess.collectAsStateWithLifecycle()
    val pendingBackup by backupViewModel.pending.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(backupViewModel) {
        backupViewModel.events.collect { event ->
            snackbarHostState.showSnackbar(event.message)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshNotificationAccess() }

    // SAF gives temporary read/write access to the chosen document; no storage
    // permission is needed. CreateDocument defaults to the public Downloads area.
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = try {
                backupViewModel.exportJson()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.backup_export_failed, e.message ?: ""),
                )
                return@launch
            }
            val saved = BackupFileIo.write(context, uri, json)
            snackbarHostState.showSnackbar(
                if (saved) {
                    context.getString(R.string.backup_export_success)
                } else {
                    context.getString(R.string.backup_file_unreadable)
                },
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = BackupFileIo.read(context, uri)
            if (raw == null) {
                snackbarHostState.showSnackbar(context.getString(R.string.backup_file_unreadable))
            } else {
                backupViewModel.prepareImport(raw)
            }
        }
    }

    // Coming back from the system notification page must update the status row.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshNotificationAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        SettingsScreen(
            modifier = Modifier.padding(padding),
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            defaultSchedule = defaultSchedule,
            onDefaultScheduleChange = viewModel::setDefaultResetSchedule,
            notificationAccess = notificationAccess,
            onNotificationAction = {
                if (notificationAccess.canRequestPermission) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.openNotificationSettings()
                }
            },
            onExportBackup = { exportLauncher.launch(BackupFileIo.suggestedFileName()) },
            onImportBackup = {
                importLauncher.launch(arrayOf("application/json", "*/*"))
            },
        )

        pendingBackup?.let { pending ->
            val counts = pending.counts
            AlertDialog(
                onDismissRequest = backupViewModel::cancelImport,
                title = { Text(stringResource(R.string.backup_import_confirm_title)) },
                text = {
                    Text(
                        stringResource(R.string.backup_import_confirm_body) + "\n\n" +
                            stringResource(
                                R.string.backup_import_summary,
                                counts.habits,
                                counts.todos,
                                counts.dailies,
                                counts.reminders,
                            ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = backupViewModel::confirmImport) {
                        Text(stringResource(R.string.backup_import_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = backupViewModel::cancelImport) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

/**
 * Full Settings page (SPEC §4.8, Step 13): appearance, reminder notification
 * status and the default reset schedule for new habits.
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    defaultSchedule: ResetSchedule = ResetSchedule.DAILY,
    onDefaultScheduleChange: (ResetSchedule) -> Unit = {},
    notificationAccess: NotificationAccessState = NotificationAccessState(),
    onNotificationAction: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        SectionHeader(stringResource(R.string.settings_appearance))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    ThemeOptionRow(
                        mode = mode,
                        selected = mode == themeMode,
                        onClick = { onThemeModeChange(mode) },
                    )
                    if (index != ThemeMode.entries.lastIndex) {
                        SettingsDivider()
                    }
                }
            }
        }

        Footnote(stringResource(R.string.settings_appearance_footnote))

        SectionHeader(
            text = stringResource(R.string.settings_notifications),
            modifier = Modifier.padding(top = 22.dp),
        )
        NotificationStatusCard(
            access = notificationAccess,
            onAction = onNotificationAction,
        )

        SectionHeader(
            text = stringResource(R.string.settings_habit_defaults),
            modifier = Modifier.padding(top = 22.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.selectableGroup()) {
                val options = settingsScheduleOptions()
                options.forEachIndexed { index, schedule ->
                    ScheduleOptionRow(
                        schedule = schedule,
                        selected = schedule == defaultSchedule,
                        onClick = { onDefaultScheduleChange(schedule) },
                    )
                    if (index != options.lastIndex) {
                        SettingsDivider()
                    }
                }
            }
        }
        Footnote(stringResource(R.string.settings_habit_defaults_footnote))

        SectionHeader(
            text = stringResource(R.string.settings_backup),
            modifier = Modifier.padding(top = 22.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column {
                BackupActionRow(
                    description = stringResource(R.string.settings_export_support),
                    label = stringResource(R.string.settings_export),
                    tag = SETTINGS_EXPORT_TAG,
                    onClick = onExportBackup,
                )
                SettingsDivider()
                BackupActionRow(
                    description = stringResource(R.string.settings_import_support),
                    label = stringResource(R.string.settings_import),
                    tag = SETTINGS_IMPORT_TAG,
                    onClick = onImportBackup,
                )
            }
        }
        Footnote(stringResource(R.string.settings_backup_footnote))
    }
}

@Composable
private fun BackupActionRow(
    description: String,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Button(onClick = onClick, modifier = Modifier.testTag(tag)) {
            Text(label)
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun Footnote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp),
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun NotificationStatusCard(
    access: NotificationAccessState,
    onAction: () -> Unit,
) {
    val (status, support) = when (access.status) {
        NotificationAccessStatus.GRANTED ->
            stringResource(R.string.settings_notification_status_allowed) to
                stringResource(R.string.settings_notification_support_allowed)
        NotificationAccessStatus.NOT_GRANTED ->
            stringResource(R.string.settings_notification_status_not_allowed) to
                stringResource(R.string.settings_notification_support_not_allowed)
        NotificationAccessStatus.BLOCKED ->
            stringResource(R.string.settings_notification_status_blocked) to
                stringResource(R.string.settings_notification_support_blocked)
    }
    val actionLabel = when (access.status) {
        NotificationAccessStatus.GRANTED -> null
        NotificationAccessStatus.NOT_GRANTED -> R.string.settings_notification_allow
        NotificationAccessStatus.BLOCKED -> R.string.settings_notification_open_settings
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_notification_status_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (access.isGranted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = support,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (actionLabel != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.testTag(SETTINGS_NOTIFICATION_ACTION_TAG),
                ) {
                    Text(stringResource(actionLabel))
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val (title, description) = when (mode) {
        ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system) to
            stringResource(R.string.theme_mode_system_description)
        ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light) to
            stringResource(R.string.theme_mode_light_description)
        ThemeMode.DARK -> stringResource(R.string.theme_mode_dark) to
            stringResource(R.string.theme_mode_dark_description)
    }

    OptionRow(
        title = title,
        description = description,
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag(settingsThemeTag(mode)),
    )
}

@Composable
private fun ScheduleOptionRow(
    schedule: ResetSchedule,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val (title, description) = when (schedule) {
        ResetSchedule.DAILY -> stringResource(R.string.habit_schedule_daily) to
            stringResource(R.string.settings_habit_default_daily)
        ResetSchedule.WEEKLY -> stringResource(R.string.habit_schedule_weekly) to
            stringResource(R.string.settings_habit_default_weekly)
        ResetSchedule.MONTHLY -> stringResource(R.string.habit_schedule_monthly) to
            stringResource(R.string.settings_habit_default_monthly)
        ResetSchedule.EVERY_N -> stringResource(R.string.habit_schedule_every_n) to
            stringResource(R.string.settings_habit_default_every_n)
    }

    OptionRow(
        title = title,
        description = description,
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag(settingsDefaultScheduleTag(schedule)),
    )
}

@Composable
private fun OptionRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Same schedules the habit editor offers (Monthly is intentionally UI-removed). */
internal fun settingsScheduleOptions(): List<ResetSchedule> =
    ResetSchedule.entries.filter { it != ResetSchedule.MONTHLY }

internal fun settingsThemeTag(mode: ThemeMode): String = "settingsTheme_${mode.name.lowercase()}"

internal fun settingsDefaultScheduleTag(schedule: ResetSchedule): String =
    "settingsDefaultSchedule_${schedule.name.lowercase()}"

internal const val SETTINGS_NOTIFICATION_ACTION_TAG = "settingsNotificationAction"
internal const val SETTINGS_EXPORT_TAG = "settingsExportBackup"
internal const val SETTINGS_IMPORT_TAG = "settingsImportBackup"

private fun Context.openNotificationSettings() {
    val appSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    val fallback = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    runCatching { startActivity(appSettings) }
        .onFailure { startActivity(fallback) }
}
