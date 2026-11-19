package com.example.mnlists.data.daily

import android.content.Context
import androidx.room.Room
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.model.ParentType
import com.example.mnlists.data.model.RepeatType
import com.example.mnlists.data.repository.DailyRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.Executor
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
 * Step 8 runtime proof: real Room (in-memory), the real generation manager and
 * the real repository. Covers today + missed-day backfill, every repeat type,
 * idempotency, per-occurrence completion and Skip isolation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class DailyOccurrenceManagerTest {

    private lateinit var database: MnlistsDatabase
    private lateinit var manager: DailyOccurrenceManager
    private lateinit var repository: DailyRepository

    private fun day(iso: String): Long = LocalDate.parse(iso).toEpochDay()

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, MnlistsDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(DIRECT_EXECUTOR)
            .setTransactionExecutor(DIRECT_EXECUTOR)
            .build()
        manager = DailyOccurrenceManager(database)
        repository = DailyRepository(
            database,
            database.dailyDao(),
            database.dailyOccurrenceDao(),
            database.subtaskDao(),
            database.reminderDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createDaily(
        start: String,
        repeat: RepeatType = RepeatType.DAILY,
        everyN: Int = 1,
        weekdays: Set<DayOfWeek> = emptySet(),
    ): Long = database.dailyDao().insert(
        Daily(
            title = "Test daily",
            startDate = day(start),
            repeatType = repeat,
            everyN = everyN,
            weekdays = weekdays,
        ),
    )

    private suspend fun datesFor(dailyId: Long): List<String> =
        database.dailyOccurrenceDao().getForDaily(dailyId)
            .map { LocalDate.ofEpochDay(it.date).toString() }
            .sorted()

    @Test
    fun generatesTodaysOccurrence_andBackfillsEveryMissedDay() = runBlocking {
        val dailyId = createDaily(start = "2026-11-01")

        val generated = manager.generateDueOccurrences(today = day("2026-11-06"))

        assertEquals(6, generated)
        assertEquals(
            listOf(
                "2026-11-01", "2026-11-02", "2026-11-03",
                "2026-11-04", "2026-11-05", "2026-11-06",
            ),
            datesFor(dailyId),
        )
        // Missed rows are generated incomplete and unskipped — never retroactively checked (SPEC §9).
        assertTrue(
            database.dailyOccurrenceDao().getForDaily(dailyId).all {
                !it.isCompleted && !it.isSkipped && it.completedAt == null
            },
        )
        // The list/calendar read the same rows through the live Flow.
        assertEquals(
            1,
            repository.observeOccurrencesForDate(day("2026-11-04")).first().size,
        )
    }

    @Test
    fun repeatedGenerationIsIdempotent_andOnlyAddsNewDays() = runBlocking {
        val dailyId = createDaily(start = "2026-11-01")

        assertEquals(6, manager.generateDueOccurrences(today = day("2026-11-06")))
        assertEquals(0, manager.generateDueOccurrences(today = day("2026-11-06")))
        assertEquals(6, database.dailyOccurrenceDao().getForDaily(dailyId).size)

        assertEquals(1, manager.generateDueOccurrences(today = day("2026-11-07")))
        assertEquals(7, database.dailyOccurrenceDao().getForDaily(dailyId).size)
        assertNotNull(database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-07")))
    }

    @Test
    fun weeklySeriesGeneratesOnlyItsPickedWeekdays() = runBlocking {
        val dailyId = createDaily(
            start = "2026-11-01",
            repeat = RepeatType.WEEKLY,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )

        assertEquals(3, manager.generateDueOccurrences(today = day("2026-11-10")))
        assertEquals(listOf("2026-11-02", "2026-11-04", "2026-11-09"), datesFor(dailyId))
    }

    @Test
    fun monthlySeriesGeneratesOnItsStartDayIncludingShortMonths() = runBlocking {
        val dailyId = createDaily(start = "2026-01-31", repeat = RepeatType.MONTHLY)

        assertEquals(4, manager.generateDueOccurrences(today = day("2026-04-30")))
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"),
            datesFor(dailyId),
        )
    }

    @Test
    fun everyNSeriesBackfillsOnItsOwnCadence() = runBlocking {
        val dailyId = createDaily(start = "2026-11-01", everyN = 3)

        assertEquals(4, manager.generateDueOccurrences(today = day("2026-11-10")))
        assertEquals(
            listOf("2026-11-01", "2026-11-04", "2026-11-07", "2026-11-10"),
            datesFor(dailyId),
        )
    }

    @Test
    fun futureStartOrBackwardsClockCreatesNothing() = runBlocking {
        val futureId = createDaily(start = "2026-11-10")
        assertEquals(0, manager.generateDueOccurrences(today = day("2026-11-06")))
        assertTrue(database.dailyOccurrenceDao().getForDaily(futureId).isEmpty())

        val existingId = createDaily(start = "2026-11-01")
        assertEquals(3, manager.generateDueOccurrences(today = day("2026-11-03")))
        assertEquals(0, manager.generateDueOccurrences(today = day("2026-11-02")))
        assertEquals(3, database.dailyOccurrenceDao().getForDaily(existingId).size)
    }

    @Test
    fun checkingOneOccurrenceDoesNotAffectAnyOtherDay() = runBlocking {
        val dailyId = createDaily(start = "2026-11-01")
        manager.generateDueOccurrences(today = day("2026-11-03"))

        val first = database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-01"))!!
        repository.setOccurrenceCompleted(first.id, completed = true)

        val checked = database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-01"))!!
        assertTrue(checked.isCompleted)
        assertNotNull(checked.completedAt)

        val others = listOf("2026-11-02", "2026-11-03").map { date ->
            database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day(date))!!
        }
        assertTrue(others.none { it.isCompleted })
        assertTrue(others.all { it.completedAt == null })

        repository.setOccurrenceCompleted(first.id, completed = false)
        assertFalse(database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-01"))!!.isCompleted)
        assertNull(database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-01"))!!.completedAt)
    }

    @Test
    fun skipIsScopedToOneOccurrenceAndDoesNotCompleteIt() = runBlocking {
        val dailyId = createDaily(start = "2026-11-01")
        manager.generateDueOccurrences(today = day("2026-11-03"))

        val second = database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-02"))!!
        repository.skipOccurrence(second.id)

        val skipped = database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-02"))!!
        assertTrue(skipped.isSkipped)
        assertFalse(skipped.isCompleted)
        assertFalse(database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-01"))!!.isSkipped)
        assertFalse(database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-03"))!!.isSkipped)
        assertEquals("the series is not deleted by a skip", 3, database.dailyOccurrenceDao().getForDaily(dailyId).size)

        repository.unskipOccurrence(second.id)
        assertFalse(database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-02"))!!.isSkipped)

        // A freshly generated later day is not inherits the earlier skip.
        manager.generateDueOccurrences(today = day("2026-11-04"))
        assertFalse(database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-04"))!!.isSkipped)
    }

    @Test
    fun subtaskCompletionCascadeIsScopedToOneOccurrence() = runBlocking {
        val dailyId = createDaily(start = "2026-11-01")
        manager.generateDueOccurrences(today = day("2026-11-03"))

        val first = database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-01"))!!
        val childId = repository.addSubtask(
            Subtask(parentType = ParentType.DAILY_OCCURRENCE, parentId = first.id, title = "Step one"),
        )
        assertFalse(repository.getSubtasks(first.id).single().isCompleted)

        repository.setOccurrenceCompleted(first.id, completed = true)
        assertTrue(repository.getSubtasks(first.id).single().isCompleted)
        assertTrue(database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-01"))!!.isCompleted)

        val second = database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-02"))!!
        assertFalse(second.isCompleted)
        assertTrue("siblings have their own subtasks", repository.getSubtasks(second.id).isEmpty())

        // Unchecking the child reopens only its own occurrence.
        repository.setSubtaskCompleted(childId, completed = false)
        assertFalse(database.dailyOccurrenceDao().getForDailyAndDate(dailyId, day("2026-11-01"))!!.isCompleted)
    }

    @Test
    fun multipleSeriesGenerateIndependentlyInOnePass() = runBlocking {
        val dailyId = createDaily(start = "2026-11-01")
        val weeklyId = createDaily(
            start = "2026-11-01",
            repeat = RepeatType.WEEKLY,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )
        val monthlyId = createDaily(start = "2026-11-01", repeat = RepeatType.MONTHLY)

        // 10 daily + 3 weekly (Mon/Wed) + 1 monthly = 14 fresh rows.
        assertEquals(14, manager.generateDueOccurrences(today = day("2026-11-10")))
        assertEquals(10, datesFor(dailyId).size)
        assertEquals(listOf("2026-11-02", "2026-11-04", "2026-11-09"), datesFor(weeklyId))
        assertEquals(listOf("2026-11-01"), datesFor(monthlyId))
    }

    private companion object {
        /** Keeps Room's executor work on the calling thread so tests stay synchronous. */
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
