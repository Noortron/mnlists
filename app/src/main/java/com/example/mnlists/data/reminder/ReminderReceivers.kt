package com.example.mnlists.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Which broadcasts must rebuild every alarm after a clock/zone change. */
object ReminderRescheduleActions {

    /** HTC/older OEM quick-boot variants are safe to include. */
    const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"

    val actions: Set<String> = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        ACTION_QUICKBOOT_POWERON,
    )

    fun shouldReschedule(action: String?): Boolean =
        action != null && action in actions
}

/**
 * Applies [ReminderRescheduleActions] to the scheduler. Extracted from the
 * receiver so the boot/timezone behaviour is testable without Hilt.
 */
@Singleton
class ReminderRescheduleHandler @Inject constructor(
    private val scheduler: ReminderScheduler,
) {

    suspend fun handle(
        action: String?,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (!ReminderRescheduleActions.shouldReschedule(action)) return false
        scheduler.rescheduleAll(now, zone)
        return true
    }
}

/** Resolves a fired alarm to a notification, then advances the schedule. */
@Singleton
class ReminderFireHandler @Inject constructor(
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
) {

    suspend fun handle(
        notificationId: Int,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): FiredReminder? {
        val fired = scheduler.handleAlarm(notificationId, now, zone) ?: return null
        notifier.notify(fired)
        return fired
    }
}

/**
 * AlarmManager target. The notification is posted from a coroutine because
 * Room and alarm rescheduling must stay off the main thread.
 */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var fireHandler: ReminderFireHandler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE_REMINDER) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId < 0) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                fireHandler.handle(notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE_REMINDER = "com.example.mnlists.action.FIRE_REMINDER"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}

/** Rebuilds alarms after reboot, an app update, a clock change or a zone change. */
@AndroidEntryPoint
class ReminderRescheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var rescheduleHandler: ReminderRescheduleHandler

    override fun onReceive(context: Context, intent: Intent) {
        if (!ReminderRescheduleActions.shouldReschedule(intent.action)) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                rescheduleHandler.handle(intent.action)
            } finally {
                pending.finish()
            }
        }
    }
}
