package com.example.mnlists.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mnlists.data.model.ResetSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** One DataStore file for all user preferences (theme + Step 13 habit defaults). */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Local-only preference storage. Reads are exposed as cold [Flow]s so the UI
 * reacts the moment a value is written — this is what makes theme switches
 * instant and keeps new-habit defaults in sync.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HabitDefaults {

    private val preferencesFlow: Flow<Preferences> = context.settingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }

    val themeMode: Flow<ThemeMode> = preferencesFlow
        .map { preferences -> ThemeMode.fromStorage(preferences[THEME_MODE_KEY]) }
        .distinctUntilChanged()

    override val defaultResetSchedule: Flow<ResetSchedule> = preferencesFlow
        .map { preferences -> ResetSchedule.fromStorage(preferences[DEFAULT_RESET_SCHEDULE_KEY]) }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.storageValue
        }
    }

    suspend fun setDefaultResetSchedule(schedule: ResetSchedule) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEFAULT_RESET_SCHEDULE_KEY] = schedule.name
        }
    }

    private companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val DEFAULT_RESET_SCHEDULE_KEY = stringPreferencesKey("default_reset_schedule")
    }
}
