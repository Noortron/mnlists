package com.example.mnlists.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mnlists.MainActivity
import com.example.mnlists.R
import com.example.mnlists.data.model.ReminderParentType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A reminder that has just fired, already resolved to its parent's title. */
data class FiredReminder(
    val notificationId: Int,
    val title: String,
    val parentType: ReminderParentType,
)

/**
 * Posts the local notification for a fired reminder. Kept behind an interface
 * so the schedule/fire tests do not depend on a real notification service.
 */
interface ReminderNotifier {
    fun notify(fired: FiredReminder)
}

@Singleton
class AndroidReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReminderNotifier {

    override fun notify(fired: FiredReminder) {
        ReminderNotifications.ensureChannel(context)

        // POST_NOTIFICATIONS is a runtime permission on Android 13+. The alarm
        // still advances the schedule when it is denied; it simply cannot show
        // anything until the user grants it (SPEC §4.5).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val openApp = PendingIntent.getActivity(
            context,
            fired.notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NOTIFICATION_ID, fired.notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(
            context,
            ReminderNotifications.CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(fired.title)
            .setContentText(context.getString(R.string.notification_reminder_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        // SecurityException would only be thrown if the permission was revoked
        // between the check above and this call.
        runCatching { manager.notify(fired.notificationId, notification) }
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}

/** Channel setup shared by [AndroidReminderNotifier] and the Application. */
object ReminderNotifications {

    const val CHANNEL_ID = "reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_reminders_description)
        }
        manager.createNotificationChannel(channel)
    }
}
