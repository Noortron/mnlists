package com.example.mnlists.data.settings

/**
 * User-selectable theme preference. [SYSTEM] follows the device setting, the
 * other two force a palette regardless of the system value (SPEC §5).
 */
enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    /** Resolves the preference against the current system night-mode value. */
    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
