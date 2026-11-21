package com.example.mnlists.data.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.example.mnlists.data.model.ReminderParentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Notification channel and POST_NOTIFICATIONS gating for Step 11. Robolectric
 * records what NotificationManagerCompat actually posted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderNotifierTest {

    private lateinit var context: Context
    private lateinit var notifier: AndroidReminderNotifier

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        notifier = AndroidReminderNotifier(context)
    }

    @Test
    fun notificationCreatesTheChannel_andPostsTheParentTitle() {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.notify(FiredReminder(7, "Morning pages", ReminderParentType.DAILY))

        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(manager.getNotificationChannel(ReminderNotifications.CHANNEL_ID))
        assertEquals(1, shadowOf(manager).size())
        assertEquals(
            "Morning pages",
            shadowOf(manager).getNotification(7)
                .extras.getString(Notification.EXTRA_TITLE),
        )
    }

    @Test
    fun deniedPermissionMeansNoNotification() {
        shadowOf(RuntimeEnvironment.getApplication())
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.notify(FiredReminder(8, "Renew passport", ReminderParentType.TODO))

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(0, shadowOf(manager).size())
    }
}
