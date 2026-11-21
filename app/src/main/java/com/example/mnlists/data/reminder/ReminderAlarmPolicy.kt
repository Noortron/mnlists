package com.example.mnlists.data.reminder

import android.os.Build

/**
 * Exact alarms need the `SCHEDULE_EXACT_ALARM` app-op on Android 12+ and can be
 * revoked at any time, so the scheduler always has an inexact fallback.
 * Extracted as a pure function so both branches are unit tested.
 */
object ReminderAlarmPolicy {

    fun canUseExactAlarm(sdkInt: Int, exactAlarmsAllowed: Boolean): Boolean =
        sdkInt < Build.VERSION_CODES.S || exactAlarmsAllowed
}
