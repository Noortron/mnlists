package com.example.mnlists.data.reminder

import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.repository.DailyRepository
import com.example.mnlists.data.repository.ReminderRepository
import com.example.mnlists.data.repository.TodoRepository

/**
 * Recording [ReminderAlarms] for JVM tests: no Android AlarmManager, but the
 * exact scheduled instants and cancellations are observable.
 */
class FakeReminderAlarms : ReminderAlarms {

    val scheduled = mutableMapOf<Int, Long>()
    val cancelled = mutableListOf<Int>()

    override fun schedule(notificationId: Int, triggerAt: Long) {
        scheduled[notificationId] = triggerAt
        cancelled.remove(notificationId)
    }

    override fun cancel(notificationId: Int) {
        scheduled.remove(notificationId)
        cancelled += notificationId
    }
}

class FakeReminderNotifier : ReminderNotifier {

    val fired = mutableListOf<FiredReminder>()

    override fun notify(fired: FiredReminder) {
        this.fired += fired
    }
}

/** Builds the real scheduler over a real in-memory database and fake alarms. */
fun testReminderScheduler(
    database: MnlistsDatabase,
    todoRepository: TodoRepository,
    dailyRepository: DailyRepository,
    alarms: ReminderAlarms = FakeReminderAlarms(),
): ReminderScheduler = ReminderScheduler(
    reminderRepository = ReminderRepository(database.reminderDao()),
    todoRepository = todoRepository,
    dailyRepository = dailyRepository,
    alarms = alarms,
)
