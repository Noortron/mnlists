package com.example.mnlists.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mnlists.data.settings.SettingsRepository
import com.example.mnlists.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Exposes the persisted theme preference to the UI and writes user changes. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = ThemeMode.SYSTEM,
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }
}
