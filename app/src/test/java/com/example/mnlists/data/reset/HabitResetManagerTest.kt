package com.example.mnlists.data.reset

import android.content.Context
import androidx.room.Room
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.Habit
import com.example.mnlists.data.local.entity.HabitLog
import com.example.mnlists.data.model.ResetSchedule
import com.example.mnlists.data.repository.HabitRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Step 5 runtime proof: real Room (in-memory) plus the real reset manager.
 * Verifies history writes, counter reset, catch-up, idempotency and that only
 * due habits are touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class HabitResetManagerTest {

    private lateinit var context: Context
    private lateinit var database: MnlistsDatabase
    private lateinit var manager: HabitResetManager
    private lateinit var repository: HabitRepository

    private val zone = ZoneId.of("UTC")

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private fun day(iso: String): Long = LocalDate.parse(iso).toEpochDay()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, MnlistsDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(DIRECT_EXECUTOR)
            .setTransactionExecutor(DIRECT_EXECUTOR)
            .build()
        manager = HabitResetManager(database)
        repository = HabitRepository(database.habitDao(), database.habitLogDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun dailyCatchUp_writesOneLogPerCycle_andKeepsExistingHistory() = runBlocking {
        val lastReset = at("2026-11-10T08:00:00Z")
        val habitId = database.habitDao().insert(
            Habit(
                title = "Read",
                resetSchedule = ResetSchedule.DAILY,
                currentCount = 7,
                lastResetAt = lastReset,
                createdAt = lastReset,
            ),
        )
        database.habitLogDao().insert(
            HabitLog(
                habitId = habitId,
                date = day("2026-11-01"),
                countAtEndOfCycle = 2,
                cycleStart = at("2026-11-01T00:00:00Z"),
                cycleEnd = at("2026-11-02T00:00:00Z"),
            ),
        )

        val applied = manager.applyDueResets(
            now = at("2026-11-12T09:00:00Z"),
            zone = zone,
        )
        assertEquals(1, applied)

        val habit = database.habitDao().getById(habitId)!!
        assertEquals("counter resets to the §9 start value", 0, habit.currentCount)
        assertEquals(at("2026-11-12T00:00:00Z"), habit.lastResetAt)

        val logs = database.habitLogDao().getForHabit(habitId)
        assertEquals("pre-existing history + two elapsed cycles", 3, logs.size)
        // Newest first: cycles ending Nov 12 and Nov 11, both with the count at reset.
        assertEquals(day("2026-11-11"), logs[0].date)
        assertEquals(day("2026-11-10"), logs[1].date)
        assertEquals(at("2026-11-11T00:00:00Z"), logs[0].cycleStart)
        assertEquals(at("2026-11-12T00:00:00Z"), logs[0].cycleEnd)
        assertEquals(7, logs[0].countAtEndOfCycle)
        assertEquals(7, logs[1].countAtEndOfCycle)
        assertEquals("old history untouched", 2, logs[2].countAtEndOfCycle)

        // Idempotent: checking again at the same instant adds nothing.
        assertEquals(0, manager.applyDueResets(now = at("2026-11-12T09:00:00Z"), zone = zone))
        assertEquals(3, database.habitLogDao().getForHabit(habitId).size)

        // The live list sees the reset, and counting restarts from zero.
        assertEquals(0, repository.observeHabits().first().single().currentCount)
        repository.adjustCount(habitId, delta = +1)
        assertEquals(1, database.habitDao().getById(habitId)!!.currentCount)
    }

    @Test
    fun onlyDueHabitsAreReset() = runBlocking {
        val dueLastReset = at("2026-11-10T08:00:00Z")
        val dueId = database.habitDao().insert(
            Habit(
                title = "Due",
                resetSchedule = ResetSchedule.DAILY,
                currentCount = 3,
                lastResetAt = dueLastReset,
                createdAt = dueLastReset,
            ),
        )
        val freshId = database.habitDao().insert(
            Habit(
                title = "Fresh",
                resetSchedule = ResetSchedule.DAILY,
                currentCount = 5,
                lastResetAt = at("2026-11-11T00:30:00Z"),
                createdAt = at("2026-11-11T00:30:00Z"),
            ),
        )

        val applied = manager.applyDueResets(
            now = at("2026-11-11T01:00:00Z"),
            zone = zone,
        )

        assertEquals(1, applied)
        assertEquals(0, database.habitDao().getById(dueId)!!.currentCount)
        assertEquals(1, database.habitLogDao().getForHabit(dueId).size)
        assertEquals(5, database.habitDao().getById(freshId)!!.currentCount)
        assertTrue(database.habitLogDao().getForHabit(freshId).isEmpty())
    }

    @Test
    fun weeklyHabitUsesTheConfiguredWeekday() = runBlocking {
        // 2026-11-09 is a Monday; the habit resets on Wednesdays.
        val habitId = database.habitDao().insert(
            Habit(
                title = "Gym",
                resetSchedule = ResetSchedule.WEEKLY,
                resetWeekday = DayOfWeek.WEDNESDAY,
                currentCount = 4,
                lastResetAt = at("2026-11-09T10:00:00Z"),
                createdAt = at("2026-11-09T10:00:00Z"),
            ),
        )

        val applied = manager.applyDueResets(
            now = at("2026-11-11T09:00:00Z"),
            zone = zone,
        )

        assertEquals(1, applied)
        val logs = database.habitLogDao().getForHabit(habitId)
        assertEquals(1, logs.size)
        assertEquals(4, logs.single().countAtEndOfCycle)
        assertEquals(at("2026-11-11T00:00:00Z"), logs.single().cycleEnd)
        assertEquals(0, database.habitDao().getById(habitId)!!.currentCount)
    }

    private companion object {
        /** Keeps Room's executor work on the calling thread so tests stay synchronous. */
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
