package com.example.mnlists.ui.screens

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.mnlists.R
import com.example.mnlists.data.settings.ThemeMode

/**
 * Settings. Step 2 owns the appearance section (theme override); the rest of
 * the page (notification permission, habit defaults) arrives in Step 13.
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_appearance),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

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
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_appearance_footnote),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp),
        )
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

    Row(
        modifier = Modifier
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
