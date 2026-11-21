package com.example.mnlists.data.settings

/**
 * Result of checking whether this app may post reminder notifications.
 *
 * - [GRANTED]: runtime permission (when required) and app/channel notifications are on.
 * - [NOT_GRANTED]: Android 13+ runtime permission is missing and can still be requested.
 * - [BLOCKED]: permission exists but notifications/app channel are disabled in system settings.
 */
enum class NotificationAccessStatus {
    GRANTED,
    NOT_GRANTED,
    BLOCKED,
}

data class NotificationAccessState(
    val status: NotificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
    /** True when the Android 13+ permission dialog can still be shown. */
    val canRequestPermission: Boolean = false,
) {
    val isGranted: Boolean get() = status == NotificationAccessStatus.GRANTED
}

/** Abstraction so Settings UI/ViewModel tests do not need a real notification service. */
interface NotificationAccessChecker {
    fun currentStatus(): NotificationAccessState
}
