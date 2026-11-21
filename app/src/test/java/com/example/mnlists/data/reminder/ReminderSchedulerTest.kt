package com.example.mnlists.data.reminder

import android.content.Context
import androidx.room.Room
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.Todo
import com.example.mnlists.data.model.ReminderParentType
import com.example.mnlists.data.repository.DailyRepository
import com.example.mnlists.data.repository.ReminderRepository
import com.example.mnlists.data.repository.TodoRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.Executor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Step 11 scheduling proof over a real in-memory Room database: persistence,
 * next-occurrence math, one-shot To-Do behaviour, skip handling, deletion and
 * the reboot/timezone repair path. AlarmManager itself is replaced by
 * [FakeReminderAlarms].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ReminderSchedulerTest {

    private lateinit var database: MnlistsDatabase
    private lateinit var todoRepository: TodoRepository
    private lateinit var dailyRepository: DailyRepository
    private lateinit var reminderRepository: ReminderRepository
    private lateinit var alarms: FakeReminderAlarms
    private lateinit var scheduler: ReminderScheduler

    private val utc = ZoneId.of("UTC")
    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val day = LocalDate.parse("2026-11-20").toEpochDay()

    private fun at(date: Long, hour: Int, minute: Int = 0): Long =
        LocalDate.ofEpochDay(date)
            .atTime(LocalTime.of(hour, minute))
            .atZone(utc)
            .toInstant()
            .toEpochMilli()

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, MnlistsDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(DIRECT_EXECUTOR)
            .setTransactionExecutor(DIRECT_EXECUTOR)
            .build()
        todoRepository = TodoRepository(
            database = database,
            todoDao = database.todoDao(),
            subtaskDao = database.subtaskDao(),
            reminderDao = database.reminderDao(),
        )
        dailyRepository = DailyRepository(
            database = database,
            dailyDao = database.dailyDao(),
            occurrenceDao = database.dailyOccurrenceDao(),
            subtaskDao = database.subtaskDao(),
            reminderDao = database.reminderDao(),
        )
        reminderRepository = ReminderRepository(database.reminderDao())
        alarms = FakeReminderAlarms()
        scheduler = ReminderScheduler(
            reminderRepository = reminderRepository,
            todoRepository = todoRepository,
            dailyRepository = dailyRepository,
            alarms = alarms,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun todoReminder_isPersistedAndArmedAtTheChosenInstant() = runBlocking {
        val todoId = todoRepository.createTodo(Todo(title = "Renew passport", sortOrder = 1.0))

        val reminder = scheduler.create(
            ReminderParentType.TODO,
            todoId,
            epochDay = day + 1,
            minutesOfDay = 8 * 60 + 15,
            now = at(day, 7),
            zone = utc,
        )!!

        assertEquals(ReminderParentType.TODO, reminder.parentType)
        assertEquals(todoId, reminder.parentId)
        assertEquals(8 * 60 + 15, reminder.timeOfDayMinutes)
        assertEquals(at(day + 1, 8, 15), reminder.triggerAt)
        assertEquals(reminder.triggerAt, alarms.scheduled[reminder.notificationId])
        assertNotNull(reminderRepository.getById(reminder.id))
        assertTrue(reminder.notificationId > 1000)
    }

    @Test
    fun todoReminder_firesOnceAndIsRemoved() = runBlocking {
        val todoId = todoRepository.createTodo(Todo(title = "Renew passport", sortOrder = 1.0))
        val reminder = scheduler.create(
            ReminderParentType.TODO,
            todoId,
            day,
            8 * 60,
            now = at(day, 7),
            zone = utc,
        )!!

        val fired = scheduler.handleAlarm(reminder.notificationId, now = reminder.triggerAt, zone = utc)

        assertEquals("Renew passport", fired?.title)
        assertNull(reminderRepository.getById(reminder.id))
        assertTrue(alarms.cancelled.contains(reminder.notificationId))
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun dailyReminder_advancesToTheNextOccurrenceAfterFiring() = runBlocking {
        val dailyId = dailyRepository.createDaily(
            Daily(title = "Morning pages", startDate = day),
        )
        val reminder = scheduler.create(
            ReminderParentType.DAILY,
            dailyId,
            day,
            9 * 60,
            now = at(day, 8),
            zone = utc,
        )!!

        assertEquals(at(day, 9), reminder.triggerAt)

        val fired = scheduler.handleAlarm(reminder.notificationId, now = reminder.triggerAt, zone = utc)

        assertEquals("Morning pages", fired?.title)
        val advanced = reminderRepository.getById(reminder.id)!!
        assertEquals(at(day + 1, 9), advanced.triggerAt)
        assertEquals(9 * 60, advanced.timeOfDayMinutes)
        assertEquals(advanced.triggerAt, alarms.scheduled[reminder.notificationId])
    }

    @Test
    fun dailyReminder_skipsASkippedOccurrence() = runBlocking {
        val dailyId = dailyRepository.createDaily(Daily(title = "Morning pages", startDate = day))
        val occurrence = dailyRepository.ensureOccurrence(dailyId, day)!!
        dailyRepository.setOccurrenceSkipped(occurrence.id, true)

        val reminder = scheduler.create(
            ReminderParentType.DAILY,
            dailyId,
            day,
            9 * 60,
            now = at(day, 8),
            zone = utc,
        )!!

        assertEquals(at(day + 1, 9), reminder.triggerAt)
    }

    @Test
    fun rescheduleForParent_movesOffANewlySkippedDayAndBackWhenUnskipped() = runBlocking {
        val dailyId = dailyRepository.createDaily(Daily(title = "Morning pages", startDate = day))
        val reminder = scheduler.create(
            ReminderParentType.DAILY,
            dailyId,
            day,
            9 * 60,
            now = at(day, 8),
            zone = utc,
        )!!
        assertEquals(at(day, 9), reminder.triggerAt)

        val occurrence = dailyRepository.ensureOccurrence(dailyId, day)!!
        dailyRepository.setOccurrenceSkipped(occurrence.id, true)
        scheduler.rescheduleForParent(ReminderParentType.DAILY, dailyId, now = at(day, 8), zone = utc)

        assertEquals(at(day + 1, 9), reminderRepository.getById(reminder.id)!!.triggerAt)
        assertEquals(at(day + 1, 9), alarms.scheduled[reminder.notificationId])

        dailyRepository.setOccurrenceSkipped(occurrence.id, false)
        scheduler.rescheduleForParent(ReminderParentType.DAILY, dailyId, now = at(day, 8), zone = utc)

        assertEquals(at(day, 9), reminderRepository.getById(reminder.id)!!.triggerAt)
        assertEquals(at(day, 9), alarms.scheduled[reminder.notificationId])
    }

    @Test
    fun deleteForParent_cancelsEveryAlarmOfBothParents() = runBlocking {
        val todoId = todoRepository.createTodo(Todo(title = "Renew passport", sortOrder = 1.0))
        val dailyId = dailyRepository.createDaily(Daily(title = "Morning pages", startDate = day))
        val first = scheduler.create(ReminderParentType.TODO, todoId, day, 8 * 60, at(day, 7), utc)!!
        val second = scheduler.create(ReminderParentType.TODO, todoId, day, 9 * 60, at(day, 7), utc)!!
        val dailyReminder = scheduler.create(ReminderParentType.DAILY, dailyId, day, 7 * 60, at(day, 6), utc)!!

        scheduler.deleteForParent(ReminderParentType.TODO, todoId)

        assertTrue(reminderRepository.getForParent(ReminderParentType.TODO, todoId).isEmpty())
        assertTrue(alarms.cancelled.containsAll(listOf(first.notificationId, second.notificationId)))
        assertEquals(
            "the Daily reminder is untouched",
            dailyReminder.notificationId,
            reminderRepository.getForParent(ReminderParentType.DAILY, dailyId).single().notificationId,
        )
    }

    @Test
    fun rescheduleAll_keepsTheWallClockAcrossAZoneChange() = runBlocking {
        val dailyId = dailyRepository.createDaily(Daily(title = "Morning pages", startDate = day))
        val reminder = scheduler.create(
            ReminderParentType.DAILY,
            dailyId,
            day,
            9 * 60,
            now = at(day, 6),
            zone = utc,
        )!!
        assertEquals(at(day, 9), reminder.triggerAt)

        // 08:00 UTC is 14:00 in Dhaka, so Dhaka's next 09:00 is tomorrow.
        val now = at(day, 8)
        scheduler.rescheduleAll(now = now, zone = dhaka)

        val repaired = reminderRepository.getById(reminder.id)!!
        assertEquals(
            ReminderTimeEngine.nextDailyTrigger(
                dailyRepository.getDaily(dailyId)!!,
                9 * 60,
                now,
                dhaka,
            ),
            repaired.triggerAt,
        )
        assertEquals(9 * 60, ReminderTimeEngine.minutesOfDay(repaired.triggerAt, dhaka))
        assertEquals(repaired.triggerAt, alarms.scheduled[reminder.notificationId])
    }

    @Test
    fun rescheduleAll_dropsPastTodoRemindersAndOrphans() = runBlocking {
        val todoId = todoRepository.createTodo(Todo(title = "Renew passport", sortOrder = 1.0))
        val past = scheduler.create(
            ReminderParentType.TODO,
            todoId,
            day,
            8 * 60,
            now = at(day, 7),
            zone = utc,
        )!!
        val orphan = reminderRepository.add(
            com.example.mnlists.data.local.entity.Reminder(
                parentType = ReminderParentType.DAILY,
                parentId = 999L,
                triggerAt = at(day + 1, 9),
                notificationId = 4242,
                timeOfDayMinutes = 9 * 60,
            ),
        )
        val orphanRow = reminderRepository.getById(orphan)!!

        scheduler.rescheduleAll(now = at(day, 10), zone = utc)

        assertNull(reminderRepository.getById(past.id))
        assertNull(reminderRepository.getById(orphanRow.id))
        assertTrue(alarms.cancelled.contains(past.notificationId))
        assertTrue(alarms.cancelled.contains(orphanRow.notificationId))
    }

    @Test
    fun rescheduleForParent_recomputesAChangedSeries() = runBlocking {
        val dailyId = dailyRepository.createDaily(Daily(title = "Morning pages", startDate = day))
        val reminder = scheduler.create(
            ReminderParentType.DAILY,
            dailyId,
            day,
            9 * 60,
            now = at(day, 6),
            zone = utc,
        )!!

        // The series no longer starts today; the next occurrence moves a week.
        dailyRepository.updateDaily(
            dailyRepository.getDaily(dailyId)!!.copy(startDate = day + 7),
        )
        scheduler.rescheduleForParent(ReminderParentType.DAILY, dailyId, now = at(day, 8), zone = utc)

        val repaired = reminderRepository.getById(reminder.id)!!
        assertEquals(at(day + 7, 9), repaired.triggerAt)
        assertEquals(repaired.triggerAt, alarms.scheduled[reminder.notificationId])
    }

    private companion object {
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
