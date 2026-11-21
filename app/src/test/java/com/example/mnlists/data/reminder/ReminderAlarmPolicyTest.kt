package com.example.mnlists.data.reminder

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exact-alarm capability policy (Android 12+ app-op, inexact fallback). */
class ReminderAlarmPolicyTest {

    @Test
    fun beforeAndroid12ExactAlarmsAreAlwaysAvailable() {
        assertTrue(ReminderAlarmPolicy.canUseExactAlarm(Build.VERSION_CODES.R, exactAlarmsAllowed = false))
    }

    @Test
    fun fromAndroid12TheAppOpDecides() {
        assertTrue(ReminderAlarmPolicy.canUseExactAlarm(Build.VERSION_CODES.S, exactAlarmsAllowed = true))
        assertFalse(ReminderAlarmPolicy.canUseExactAlarm(Build.VERSION_CODES.S, exactAlarmsAllowed = false))
        assertFalse(
            ReminderAlarmPolicy.canUseExactAlarm(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, false),
        )
    }
}
