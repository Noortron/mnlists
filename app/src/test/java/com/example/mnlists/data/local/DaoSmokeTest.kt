package com.example.mnlists.data.local

import androidx.room.Room
import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.DailyOccurrence
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.local.entity.HabitLog
import com.example.mnlists.data.local.entity.Reminder
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.local.entity.Todo
import com.example.mnlists.data.model.ParentType
import com.example.mnlists.data.model.ReminderParentType
import com.example.mnlists.data.model.RepeatType
import com.example.mnlists.data.model.ResetSchedule
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Step 3 acceptance: a basic Room create/read smoke test that runs on the JVM
 * via Robolectric (no device needed). Exercises every entity family and the
 * type converters, plus the DAO queries the UI will build on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class DaoSmokeTest {

    private lateinit var database: MnlistsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MnlistsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun habit_createReadUpdateCount_andConvertersRoundTrip() = runBlocking {
        val habitDao = database.habitDao()
        val habitId = habitDao.insert(
            Habit(
                title = "Read",
                note = "20 pages",
                resetSchedule = ResetSchedule.EVERY_N,
                resetEveryN = 3,
                resetWeekday = DayOfWeek.MONDAY,
                allowUp = true,
                allowDown = true,
            ),
        )
        assertTrue("insert must return a row id", habitId > 0)

        val stored = habitDao.getById(habitId)
        assertNotNull(stored)
        assertEquals("Read", stored!!.title)
        assertEquals(ResetSchedule.EVERY_N, stored.resetSchedule)
        assertEquals(3, stored.resetEveryN)
        assertEquals(DayOfWeek.MONDAY, stored.resetWeekday)
        assertEquals(0, stored.currentCount)

        habitDao.updateCount(habitId, 4, System.currentTimeMillis())
        assertEquals(4, habitDao.getById(habitId)!!.currentCount)

        habitDao.applyReset(habitId, 0, System.currentTimeMillis())
        assertEquals(0, habitDao.getById(habitId)!!.currentCount)

        // Flow query returns the same row.
        val observed = habitDao.observeAll().first()
        assertEquals(listOf(habitId), observed.map { it.id })
    }

    @Test
    fun habitLog_insertAndReadHistory() = runBlocking {
        val habitId = database.habitDao().insert(Habit(title = "Stretch"))
        val logDao = database.habitLogDao()

        val now = System.currentTimeMillis()
        logDao.insert(
            HabitLog(
                habitId = habitId,
                date = 20_000L,
                countAtEndOfCycle = 7,
                cycleStart = now - 86_400_000L,
                cycleEnd = now,
            ),
        )

        val logs = logDao.getForHabit(habitId)
        assertEquals(1, logs.size)
        assertEquals(7, logs.first().countAtEndOfCycle)
        assertEquals(1, logDao.getForHabitBetween(habitId, 19_999L, 20_001L).size)
    }

    @Test
    fun todo_withSubtasks_andCompletionCascadePrimitives() = runBlocking {
        val todoDao = database.todoDao()
        val subtaskDao = database.subtaskDao()

        val todoId = todoDao.insert(
            Todo(title = "File taxes", dueDate = 20_100L, sortOrder = 1.0),
        )
        assertTrue(todoId > 0)

        subtaskDao.insert(
            Subtask(parentType = ParentType.TODO, parentId = todoId, title = "Collect receipts"),
        )
        // sortOrder auto-derives from sibling max in the repository; DAO default is fine here.
        subtaskDao.insert(
            Subtask(parentType = ParentType.TODO, parentId = todoId, title = "Fill form"),
        )

        assertEquals(2, subtaskDao.getForParent(ParentType.TODO, todoId).size)
        assertEquals(2, subtaskDao.countIncompleteForParent(ParentType.TODO, todoId))

        todoDao.setCompleted(todoId, true, System.currentTimeMillis(), System.currentTimeMillis())
        subtaskDao.setCompletedForParent(ParentType.TODO, todoId, true)

        assertTrue(todoDao.getById(todoId)!!.isCompleted)
        assertEquals(0, subtaskDao.countIncompleteForParent(ParentType.TODO, todoId))
        assertEquals(2, subtaskDao.countForParent(ParentType.TODO, todoId))

        // Incomplete todos sort before completed ones.
        val otherId = todoDao.insert(Todo(title = "Open task", dueDate = 20_101L))
        val ordered = todoDao.observeAll().first()
        assertEquals(listOf(otherId, todoId), ordered.map { it.id })
    }

    @Test
    fun dailyOccurrence_uniquePerDate_andSkipFlag() = runBlocking {
        val dailyId = database.dailyDao().insert(
            Daily(
                title = "Water plants",
                startDate = 20_000L,
                repeatType = RepeatType.WEEKLY,
                everyN = 2,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            ),
        )
        assertTrue(dailyId > 0)

        val daily = database.dailyDao().getById(dailyId)!!
        assertEquals(RepeatType.WEEKLY, daily.repeatType)
        assertEquals(2, daily.everyN)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), daily.weekdays)

        val occurrenceDao = database.dailyOccurrenceDao()
        val firstId = occurrenceDao.insert(DailyOccurrence(dailyId = dailyId, date = 20_000L))
        val duplicateId = occurrenceDao.insert(DailyOccurrence(dailyId = dailyId, date = 20_000L))

        assertTrue(firstId > 0)
        assertEquals("duplicate (dailyId, date) must be ignored", -1L, duplicateId)
        assertEquals(1, occurrenceDao.getForDate(20_000L).size)

        val occurrence = occurrenceDao.getForDailyAndDate(dailyId, 20_000L)!!
        occurrenceDao.setSkipped(occurrence.id, true)
        assertTrue(occurrenceDao.getForDailyAndDate(dailyId, 20_000L)!!.isSkipped)
        assertFalse(occurrenceDao.getForDailyAndDate(dailyId, 20_000L)!!.isCompleted)
    }

    @Test
    fun reminder_insertReadAndDeleteForParent() = runBlocking {
        val todoId = database.todoDao().insert(Todo(title = "Call dentist", dueDate = 20_200L))
        val reminderDao = database.reminderDao()

        val reminder = Reminder(
            parentType = ReminderParentType.TODO,
            parentId = todoId,
            triggerAt = System.currentTimeMillis() + 60_000L,
            notificationId = reminderDao.maxNotificationId() + 1,
        )
        val reminderId = reminderDao.insert(reminder)
        assertTrue(reminderId > 0)

        assertEquals(1, reminderDao.getForParent(ReminderParentType.TODO, todoId).size)
        assertNotNull(reminderDao.getByNotificationId(reminder.notificationId))
        assertEquals(1, reminderDao.getDue(Long.MAX_VALUE).size)

        reminderDao.deleteForParent(ReminderParentType.TODO, todoId)
        assertNull(reminderDao.getByNotificationId(reminder.notificationId))
        assertEquals(0, reminderDao.getForParent(ReminderParentType.TODO, todoId).size)
    }
}
