package com.example.mnlists.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one side effect the reminder scheduler needs from Android: posting and
 * cancelling the alarm that wakes [ReminderAlarmReceiver].
 *
 * Kept behind an interface so schedule computation, boot handling and the
 * fire path can be unit tested without touching the real AlarmManager.
 */
interface ReminderAlarms {

    fun schedule(notificationId: Int, triggerAt: Long)

    fun cancel(notificationId: Int)
}

@Singleton
class AndroidReminderAlarms @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReminderAlarms {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun schedule(notificationId: Int, triggerAt: Long) {
        val manager = alarmManager ?: return
        val pendingIntent = fireIntent(notificationId)
        val exact = ReminderAlarmPolicy.canUseExactAlarm(
            sdkInt = Build.VERSION.SDK_INT,
            exactAlarmsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                manager.canScheduleExactAlarms(),
        )
        // A revoked exact-alarm grant falls back to an inexact alarm rather
        // than dropping the reminder entirely (SPEC §4.5).
        if (exact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    override fun cancel(notificationId: Int) {
        val manager = alarmManager ?: return
        manager.cancel(fireIntent(notificationId))
    }

    private fun fireIntent(notificationId: Int): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ACTION_FIRE_REMINDER)
            .putExtra(ReminderAlarmReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        return PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
