package com.example.mnlists.data.reminder

import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.Reminder
import com.example.mnlists.data.model.ReminderParentType
import com.example.mnlists.data.repository.DailyRepository
import com.example.mnlists.data.repository.ReminderRepository
import com.example.mnlists.data.repository.TodoRepository
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns reminder persistence together with AlarmManager scheduling (SPEC §4.5,
 * Step 11).
 *
 * To-Do reminders are a single fixed instant. Daily reminders are a local
 * **time of day** attached to every scheduled occurrence; their `triggerAt` is
 * the next occurrence and is recomputed after firing, after a clock/zone change
 * and whenever the series schedule changes. A skipped occurrence is passed
 * over, matching "fires for each occurrence" (SPEC §9).
 *
 * All time math is delegated to [ReminderTimeEngine]; [now] and [zone] are
 * parameters so the behaviour is deterministic in tests.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val todoRepository: TodoRepository,
    private val dailyRepository: DailyRepository,
    private val alarms: ReminderAlarms,
) {

    /** Reads a parent's reminders; the editors use this to render their rows. */
    suspend fun getForParent(
        parentType: ReminderParentType,
        parentId: Long,
    ): List<Reminder> = reminderRepository.getForParent(parentType, parentId)

    /** Creates and schedules a new reminder, returning the stored row. */
    suspend fun create(
        parentType: ReminderParentType,
        parentId: Long,
        epochDay: Long,
        minutesOfDay: Int,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Reminder? {
        val triggerAt = initialTrigger(parentType, parentId, epochDay, minutesOfDay, now, zone)
            ?: return null
        val reminder = Reminder(
            parentType = parentType,
            parentId = parentId,
            triggerAt = triggerAt,
            notificationId = reminderRepository.nextNotificationId(),
            timeOfDayMinutes = minutesOfDay,
        )
        val id = reminderRepository.add(reminder)
        val saved = reminder.copy(id = id)
        alarms.schedule(saved.notificationId, saved.triggerAt)
        return saved
    }

    /** Changes the date/time (To-Do) or time of day (Daily) and re-arms the alarm. */
    suspend fun update(
        reminderId: Long,
        epochDay: Long,
        minutesOfDay: Int,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Reminder? {
        val existing = reminderRepository.getById(reminderId) ?: return null
        val triggerAt = initialTrigger(
            existing.parentType,
            existing.parentId,
            epochDay,
            minutesOfDay,
            now,
            zone,
        ) ?: return null
        val updated = existing.copy(
            triggerAt = triggerAt,
            timeOfDayMinutes = minutesOfDay,
        )
        reminderRepository.update(updated)
        alarms.schedule(updated.notificationId, updated.triggerAt)
        return updated
    }

    suspend fun delete(reminderId: Long) {
        val existing = reminderRepository.getById(reminderId) ?: return
        alarms.cancel(existing.notificationId)
        reminderRepository.deleteById(reminderId)
    }

    /** Cancels every pending alarm of a parent, then removes its rows. */
    suspend fun deleteForParent(parentType: ReminderParentType, parentId: Long) {
        reminderRepository.getForParent(parentType, parentId).forEach {
            alarms.cancel(it.notificationId)
        }
        reminderRepository.deleteForParent(parentType, parentId)
    }

    /** Re-arms a parent's reminders, e.g. after its recurrences changed. */
    suspend fun rescheduleForParent(
        parentType: ReminderParentType,
        parentId: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val daily = if (parentType == ReminderParentType.DAILY) {
            dailyRepository.getDaily(parentId)
        } else {
            null
        }
        reminderRepository.getForParent(parentType, parentId).forEach { reminder ->
            reschedule(reminder, daily, now, zone)
        }
    }

    /**
     * BOOT_COMPLETED / timezone / time-change repair. Future To-Do alarms are
     * re-armed, Daily reminders get the next occurrence in the new zone, and
     * reminders whose parent vanished or whose one-off moment passed are
     * dropped instead of firing a stale notification.
     */
    suspend fun rescheduleAll(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        reminderRepository.getAll().forEach { reminder ->
            when (reminder.parentType) {
                ReminderParentType.TODO -> {
                    val todo = todoRepository.getTodo(reminder.parentId)
                    if (todo == null || reminder.triggerAt <= now) {
                        alarms.cancel(reminder.notificationId)
                        reminderRepository.deleteById(reminder.id)
                    } else {
                        alarms.schedule(reminder.notificationId, reminder.triggerAt)
                    }
                }

                ReminderParentType.DAILY -> {
                    val daily = dailyRepository.getDaily(reminder.parentId)
                    reschedule(reminder, daily, now, zone)
                }
            }
        }
    }

    /**
     * Handles a fired alarm: a To-Do reminder is one-shot and is removed, while
     * a Daily reminder advances to its next non-skipped occurrence.
     *
     * @return the title to put in the notification, or `null` when the parent
     * is gone and nothing should be shown.
     */
    suspend fun handleAlarm(
        notificationId: Int,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): FiredReminder? {
        val reminder = reminderRepository.getByNotificationId(notificationId) ?: return null
        return when (reminder.parentType) {
            ReminderParentType.TODO -> {
                val todo = todoRepository.getTodo(reminder.parentId)
                alarms.cancel(notificationId)
                reminderRepository.deleteById(reminder.id)
                todo?.let {
                    FiredReminder(notificationId, it.title, ReminderParentType.TODO)
                }
            }

            ReminderParentType.DAILY -> {
                val daily = dailyRepository.getDaily(reminder.parentId)
                if (daily == null) {
                    alarms.cancel(notificationId)
                    reminderRepository.deleteById(reminder.id)
                    null
                } else {
                    val minutes = reminder.timeOfDayMinutes
                        ?: ReminderTimeEngine.minutesOfDay(reminder.triggerAt, zone)
                    val next = nextNonSkippedTrigger(
                        daily,
                        minutes,
                        maxOf(now, reminder.triggerAt),
                        zone,
                        strictlyAfter = true,
                    )
                    if (next == null) {
                        alarms.cancel(notificationId)
                        reminderRepository.deleteById(reminder.id)
                    } else {
                        reminderRepository.update(
                            reminder.copy(triggerAt = next, timeOfDayMinutes = minutes),
                        )
                        alarms.schedule(notificationId, next)
                    }
                    FiredReminder(notificationId, daily.title, ReminderParentType.DAILY)
                }
            }
        }
    }

    private suspend fun initialTrigger(
        parentType: ReminderParentType,
        parentId: Long,
        epochDay: Long,
        minutesOfDay: Int,
        now: Long,
        zone: ZoneId,
    ): Long? = when (parentType) {
        ReminderParentType.TODO -> {
            if (todoRepository.getTodo(parentId) == null) {
                null
            } else {
                ReminderTimeEngine.todoTrigger(epochDay, minutesOfDay, zone)
            }
        }

        ReminderParentType.DAILY -> {
            val daily = dailyRepository.getDaily(parentId)
            daily?.let { nextNonSkippedTrigger(it, minutesOfDay, now, zone) }
        }
    }

    private suspend fun reschedule(
        reminder: Reminder,
        daily: Daily?,
        now: Long,
        zone: ZoneId,
    ) {
        if (daily == null) {
            alarms.cancel(reminder.notificationId)
            reminderRepository.deleteById(reminder.id)
            return
        }
        val minutes = reminder.timeOfDayMinutes
            ?: ReminderTimeEngine.minutesOfDay(reminder.triggerAt, zone)
        val next = nextNonSkippedTrigger(daily, minutes, now, zone)
        if (next == null) {
            alarms.cancel(reminder.notificationId)
            reminderRepository.deleteById(reminder.id)
            return
        }
        if (next != reminder.triggerAt || minutes != reminder.timeOfDayMinutes) {
            reminderRepository.update(
                reminder.copy(triggerAt = next, timeOfDayMinutes = minutes),
            )
        }
        alarms.schedule(reminder.notificationId, next)
    }

    /**
     * Next occurrence trigger that is not marked skipped. The cap only guards
     * against pathological data; a normal week finds the date immediately.
     */
    private suspend fun nextNonSkippedTrigger(
        daily: Daily,
        minutesOfDay: Int,
        now: Long,
        zone: ZoneId,
        strictlyAfter: Boolean = false,
    ): Long? {
        var candidate = if (strictlyAfter) {
            ReminderTimeEngine.nextDailyTriggerAfter(daily, minutesOfDay, now, zone)
        } else {
            ReminderTimeEngine.nextDailyTrigger(daily, minutesOfDay, now, zone)
        } ?: return null
        repeat(MAX_SKIPPED_WALK) {
            val epochDay = ReminderTimeEngine.epochDayOf(candidate, zone)
            val occurrence = dailyRepository.getOccurrenceForDate(daily.id, epochDay)
            if (occurrence?.isSkipped != true) return candidate
            candidate = ReminderTimeEngine.nextDailyTriggerAfter(
                daily,
                minutesOfDay,
                candidate,
                zone,
            ) ?: return null
        }
        return candidate
    }

    private companion object {
        const val MAX_SKIPPED_WALK = 366
    }
}
