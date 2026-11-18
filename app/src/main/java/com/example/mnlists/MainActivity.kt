package com.example.mnlists

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mnlists.ui.MnlistsApp
import com.example.mnlists.ui.settings.SettingsViewModel
import com.example.mnlists.ui.theme.MnlistsTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single Activity of the app. It owns the theme source of truth so the
 * palette can be applied above the navigation host and change instantly.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
