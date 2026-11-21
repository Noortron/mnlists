package com.example.mnlists.data.settings

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mnlists.data.reminder.ReminderNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the real Android notification state for the Settings status row.
 * Mirrors the gating in `AndroidReminderNotifier`: the runtime permission is
 * checked first, then app-wide notifications and the `reminders` channel.
 */
@Singleton
class AndroidNotificationAccessChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NotificationAccessChecker {

    override fun currentStatus(): NotificationAccessState {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        if (!runtimeGranted) {
            return NotificationAccessState(
                status = NotificationAccessStatus.NOT_GRANTED,
                canRequestPermission = true,
            )
        }

        val appEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val channelEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = manager?.getNotificationChannel(ReminderNotifications.CHANNEL_ID)
            channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        } else {
            true
        }

        return if (appEnabled && channelEnabled) {
            NotificationAccessState(status = NotificationAccessStatus.GRANTED)
        } else {
            NotificationAccessState(status = NotificationAccessStatus.BLOCKED)
        }
    }
}
