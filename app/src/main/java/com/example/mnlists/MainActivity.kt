package com.example.mnlists

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.mnlists.data.daily.DailyOccurrenceManager
import com.example.mnlists.data.reset.HabitResetManager
import com.example.mnlists.ui.MnlistsApp
import com.example.mnlists.ui.settings.SettingsViewModel
import com.example.mnlists.ui.theme.MnlistsTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The single Activity of the app. It owns the theme source of truth so the
 * palette can be applied above the navigation host and change instantly.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var habitResetManager: HabitResetManager

    @Inject
    lateinit var dailyOccurrenceManager: DailyOccurrenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // On-open checks (SPEC §8); both are idempotent. Daily generation also
        // backfills dates missed while the app was closed (SPEC §9, Step 8).
        lifecycleScope.launch { habitResetManager.applyDueResets() }
        lifecycleScope.launch { dailyOccurrenceManager.generateDueOccurrences() }

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()

            MnlistsTheme(themeMode = themeMode) {
                MnlistsApp(
                    themeMode = themeMode,
                    onThemeModeChange = settingsViewModel::setThemeMode,
                )
            }
        }
    }
}
