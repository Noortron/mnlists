package com.example.mnlists.data.settings

import com.example.mnlists.data.model.ResetSchedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Step 13 preference storage: defaults must match today's app behaviour, and
 * writes must be readable back as the UI expects. Kept in one test because the
 * DataStore instance is cached for the process and would leak state between
 * separate test methods.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SettingsRepositoryTest {

    @Test
    fun preferences_defaultsWritesAndFallbacks() = runBlocking {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())

        // Fresh installs (and unknown values) fall back to the original behaviour.
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
        assertEquals(ResetSchedule.DAILY, repository.defaultResetSchedule.first())
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("purple"))
        assertEquals(ResetSchedule.DAILY, ResetSchedule.fromStorage("hourly"))
        assertEquals(ResetSchedule.WEEKLY, ResetSchedule.fromStorage("WEEKLY"))

        repository.setThemeMode(ThemeMode.DARK)
        repository.setDefaultResetSchedule(ResetSchedule.EVERY_N)

        assertEquals(ThemeMode.DARK, repository.themeMode.first { it == ThemeMode.DARK })
        assertEquals(
            ResetSchedule.EVERY_N,
            repository.defaultResetSchedule.first { it == ResetSchedule.EVERY_N },
        )
    }
}
