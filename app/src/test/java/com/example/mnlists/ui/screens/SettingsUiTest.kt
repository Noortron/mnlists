package com.example.mnlists.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.mnlists.BuildConfig
import com.example.mnlists.R
import com.example.mnlists.data.model.ResetSchedule
import com.example.mnlists.data.settings.NotificationAccessState
import com.example.mnlists.data.settings.NotificationAccessStatus
import com.example.mnlists.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Compose-level verification of the Step 13 pages: the Settings theme and
 * default-schedule toggles, notification status actions and the About content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class SettingsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun themeSelection_reportsChanges_andHighlightsTheNewChoice() {
        var theme by mutableStateOf(ThemeMode.SYSTEM)
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = theme,
                    onThemeModeChange = { theme = it },
                )
            }
        }

        compose.onNodeWithTag(settingsThemeTag(ThemeMode.SYSTEM)).assertIsSelected()
        compose.onNodeWithTag(settingsThemeTag(ThemeMode.LIGHT)).performClick().assertIsSelected()
        assertEquals(ThemeMode.LIGHT, theme)
        compose.onNodeWithTag(settingsThemeTag(ThemeMode.DARK)).performClick().assertIsSelected()
        assertEquals(ThemeMode.DARK, theme)
    }

    @Test
    fun defaultScheduleSelection_reportsChanges_andHighlightsTheNewChoice() {
        var schedule by mutableStateOf(ResetSchedule.DAILY)
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    defaultSchedule = schedule,
                    onDefaultScheduleChange = { schedule = it },
                )
            }
        }

        compose.onNodeWithTag(settingsDefaultScheduleTag(ResetSchedule.DAILY)).assertIsSelected()

        compose.onNodeWithTag(settingsDefaultScheduleTag(ResetSchedule.WEEKLY))
            .performScrollTo()
            .performClick()
            .assertIsSelected()
        assertEquals(ResetSchedule.WEEKLY, schedule)

        compose.onNodeWithTag(settingsDefaultScheduleTag(ResetSchedule.EVERY_N))
            .performScrollTo()
            .performClick()
            .assertIsSelected()
        assertEquals(ResetSchedule.EVERY_N, schedule)
    }

    @Test
    fun notificationStatus_showsTheRightStateAndAction() {
        var actions = 0
        var access by mutableStateOf(
            NotificationAccessState(
                status = NotificationAccessStatus.NOT_GRANTED,
                canRequestPermission = true,
            ),
        )
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    notificationAccess = access,
                    onNotificationAction = { actions++ },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.settings_notification_status_not_allowed))
            .assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_NOTIFICATION_ACTION_TAG)
            .performScrollTo()
            .performClick()
        assertEquals(1, actions)

        compose.runOnUiThread {
            access = NotificationAccessState(status = NotificationAccessStatus.GRANTED)
        }
        compose.onNodeWithText(context.getString(R.string.settings_notification_status_allowed))
            .assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_NOTIFICATION_ACTION_TAG).assertDoesNotExist()

        compose.runOnUiThread {
            access = NotificationAccessState(status = NotificationAccessStatus.BLOCKED)
        }
        compose.onNodeWithText(context.getString(R.string.settings_notification_open_settings))
            .performScrollTo()
            .performClick()
        assertEquals(2, actions)
    }

    @Test
    fun backupRows_showBothActions_andReportClicks() {
        var exports = 0
        var imports = 0
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    onExportBackup = { exports++ },
                    onImportBackup = { imports++ },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.settings_backup))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_EXPORT_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(SETTINGS_IMPORT_TAG).performScrollTo().performClick()
        assertEquals(1, exports)
        assertEquals(1, imports)
    }

    @Test
    fun about_showsNameVersionDescriptionAndCredits() {
        compose.setContent {
            MaterialTheme { AboutScreen() }
        }

        compose.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
        compose.onNodeWithText(
            context.getString(R.string.about_version, BuildConfig.VERSION_NAME),
        ).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.about_description)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.about_credits_title))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.about_credits_body))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
