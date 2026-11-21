package com.example.mnlists.data.reminder

import android.content.Context
import android.content.Intent
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Step 11 receiver behaviour: BOOT_COMPLETED / timezone / clock broadcasts
 * rebuild alarms, the alarm broadcast advances the reminder and posts exactly
 * one notification, and the manifest really registers the receivers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderReceiverTest {

    private lateinit var context: Context
    private lateinit var database: MnlistsDatabase
    private lateinit var todoRepository: TodoRepository
    private lateinit var dailyRepository: DailyRepository
    private lateinit var reminderRepository: ReminderRepository
    private lateinit var alarms: FakeReminderAlarms
    private lateinit var scheduler: ReminderScheduler

    private val utc = ZoneId.of("UTC")
    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val day = LocalDate.parse("2026-11-20").toEpochDay()

    private fun at(date: Long, hour: Int): Long =
        LocalDate.ofEpochDay(date)
            .atTime(LocalTime.of(hour, 0))
            .atZone(utc)
            .toInstant()
            .toEpochMilli()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
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
    fun manifest_registersTheRescheduleReceiverForBootAndTimezone() {
        val receiverName = ReminderRescheduleReceiver::class.java.name

        val boot = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_BOOT_COMPLETED),
            0,
        )
        val timezone = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_TIMEZONE_CHANGED),
            0,
        )
        val timeSet = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_TIME_CHANGED),
            0,
        )

        assertTrue(boot.any { it.activityInfo.name == receiverName })
        assertTrue(timezone.any { it.activityInfo.name == receiverName })
        assertTrue(timeSet.any { it.activityInfo.name == receiverName })
    }

    @Test
    fun handler_ignoresUnknownBroadcasts_andReschedulesOnBootAndTimezone() = runBlocking {
        val dailyId = dailyRepository.createDaily(Daily(title = "Morning pages", startDate = day))
        val reminder = scheduler.create(
            ReminderParentType.DAILY,
            dailyId,
            day,
            9 * 60,
            now = at(day, 6),
            zone = utc,
        )!!
        val handler = ReminderRescheduleHandler(scheduler)

        assertFalse(handler.handle("com.example.mnlists.NOT_A_CLOCK_CHANGE", at(day, 8), utc))
        assertEquals(at(day, 9), reminderRepository.getById(reminder.id)!!.triggerAt)

        assertTrue(handler.handle(Intent.ACTION_BOOT_COMPLETED, at(day, 8), utc))
        assertEquals(
            "boot keeps the already-computed instant",
            at(day, 9),
            reminderRepository.getById(reminder.id)!!.triggerAt,
        )

        assertTrue(handler.handle(Intent.ACTION_TIMEZONE_CHANGED, at(day, 8), dhaka))
        val repaired = reminderRepository.getById(reminder.id)!!
        assertEquals(9 * 60, ReminderTimeEngine.minutesOfDay(repaired.triggerAt, dhaka))
        assertEquals(repaired.triggerAt, alarms.scheduled[reminder.notificationId])
    }

    @Test
    fun fireHandler_showsTheParentTitle_andAdvancesADaily() = runBlocking {
        val dailyId = dailyRepository.createDaily(Daily(title = "Morning pages", startDate = day))
        val reminder = scheduler.create(
            ReminderParentType.DAILY,
            dailyId,
            day,
            9 * 60,
            now = at(day, 8),
            zone = utc,
        )!!
        val notifier = FakeReminderNotifier()
        val handler = ReminderFireHandler(scheduler, notifier)

        val fired = handler.handle(reminder.notificationId, reminder.triggerAt, utc)

        assertEquals("Morning pages", fired?.title)
        assertEquals(1, notifier.fired.size)
        assertEquals(ReminderParentType.DAILY, notifier.fired.single().parentType)
        assertEquals(at(day + 1, 9), reminderRepository.getById(reminder.id)!!.triggerAt)
    }

    @Test
    fun fireHandler_removesAOneShotTodoAndDoesNotPostWhenTheParentIsGone() = runBlocking {
        val todoId = todoRepository.createTodo(Todo(title = "Renew passport", sortOrder = 1.0))
        val reminder = scheduler.create(
            ReminderParentType.TODO,
            todoId,
            day,
            8 * 60,
            now = at(day, 7),
            zone = utc,
        )!!
        val notifier = FakeReminderNotifier()
        val handler = ReminderFireHandler(scheduler, notifier)

        val fired = handler.handle(reminder.notificationId, reminder.triggerAt, utc)
        assertEquals("Renew passport", fired?.title)
        assertEquals(1, notifier.fired.size)

        // A second delivery of the same alarm has nothing left to show.
        val second = handler.handle(reminder.notificationId, reminder.triggerAt, utc)
        assertNull(second)
        assertEquals(1, notifier.fired.size)
    }

    private companion object {
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
