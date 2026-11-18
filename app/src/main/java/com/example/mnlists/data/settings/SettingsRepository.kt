package com.example.mnlists.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** One DataStore file for all user preferences (theme today, more in Step 13). */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Local-only preference storage. Reads are exposed as a cold [Flow] so the UI
 * reacts the moment a value is written — this is what makes theme switches
 * instant.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences -> ThemeMode.fromStorage(preferences[THEME_MODE_KEY]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.storageValue
        }
    }

    private companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }
}
