package com.example.mnlists.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mnlists.data.model.ResetSchedule
import com.example.mnlists.data.settings.NotificationAccessChecker
import com.example.mnlists.data.settings.NotificationAccessState
import com.example.mnlists.data.settings.SettingsRepository
import com.example.mnlists.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs Settings: persisted theme/habit defaults plus live notification status. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val notificationAccessChecker: NotificationAccessChecker,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = ThemeMode.SYSTEM,
        )

    val defaultResetSchedule: StateFlow<ResetSchedule> = settingsRepository.defaultResetSchedule
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = ResetSchedule.DAILY,
        )

    private val _notificationAccess = MutableStateFlow(notificationAccessChecker.currentStatus())
    val notificationAccess: StateFlow<NotificationAccessState> = _notificationAccess.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDefaultResetSchedule(schedule: ResetSchedule) {
        viewModelScope.launch { settingsRepository.setDefaultResetSchedule(schedule) }
    }

    /** Re-reads permission/app/channel state after a permission prompt or system settings. */
    fun refreshNotificationAccess() {
        _notificationAccess.value = notificationAccessChecker.currentStatus()
    }
}
