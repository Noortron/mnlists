package com.example.mnlists.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mnlists.R
import com.example.mnlists.data.settings.ThemeMode
import com.example.mnlists.ui.MnlistsTopBar
import com.example.mnlists.ui.navigateToAbout
import com.example.mnlists.ui.navigateToSettings
import com.example.mnlists.ui.navigation.MnlistsRoutes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Navigation verification for the top-bar overflow menu: opening Settings or
 * About must land on the real destination composable, not just swallow the tap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class SettingsNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()

    private fun setShell() {
        compose.setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = HOME) {
                    composable(HOME) {
                        MnlistsTopBar(
                            title = context.getString(R.string.tab_habits),
                            showOverflowMenu = true,
                            containerColor = MaterialTheme.colorScheme.background,
                            onNavigateBack = {},
                            onOpenSettings = { navController.navigateToSettings() },
                            onOpenAbout = { navController.navigateToAbout() },
                        )
                    }
                    composable(MnlistsRoutes.SETTINGS) {
                        SettingsScreen(
                            themeMode = ThemeMode.SYSTEM,
                            onThemeModeChange = {},
                        )
                    }
                    composable(MnlistsRoutes.ABOUT) {
                        AboutScreen()
                    }
                }
            }
        }
    }

    @Test
    fun overflowSettings_opensTheSettingsPage() {
        setShell()

        compose.onNodeWithContentDescription(context.getString(R.string.action_more_options))
            .performClick()
        compose.onNodeWithText(context.getString(R.string.menu_settings)).performClick()

        compose.onNodeWithText(context.getString(R.string.settings_appearance)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_habit_defaults))
            .assertIsDisplayed()
    }

    @Test
    fun overflowAbout_opensTheAboutPage() {
        setShell()

        compose.onNodeWithContentDescription(context.getString(R.string.action_more_options))
            .performClick()
        compose.onNodeWithText(context.getString(R.string.menu_about)).performClick()

        compose.onNodeWithText(context.getString(R.string.about_credits_title))
            .assertIsDisplayed()
    }

    private companion object {
        const val HOME = "test_home"
    }
}
