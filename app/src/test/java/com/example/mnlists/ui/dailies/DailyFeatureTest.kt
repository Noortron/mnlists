package com.example.mnlists.ui.dailies

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.example.mnlists.data.daily.DailyOccurrenceManager
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.data.model.ParentType
import com.example.mnlists.data.model.ReminderParentType
import com.example.mnlists.data.model.RepeatType
import com.example.mnlists.data.reminder.FakeReminderAlarms
import com.example.mnlists.data.reminder.ReminderScheduler
import com.example.mnlists.data.reminder.testReminderScheduler
import com.example.mnlists.data.repository.DailyRepository
import com.example.mnlists.data.repository.TodoRepository
import com.example.mnlists.ui.navigation.MnlistsRoutes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
 * Step 9 runtime proof without a device: real Room (in-memory), the real
 * Daily repository/manager and the real list/editor ViewModels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DailyFeatureTest {

    private lateinit var database: MnlistsDatabase
    private lateinit var repository: DailyRepository
    private lateinit var manager: DailyOccurrenceManager
    private lateinit var todoRepository: TodoRepository
    private lateinit var reminderScheduler: ReminderScheduler
    private lateinit var alarms: FakeReminderAlarms

    /** The real "today" keeps editor generation aligned with the test data. */
    private val day: Long = DailyDates.today()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, MnlistsDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(DIRECT_EXECUTOR)
            .setTransactionExecutor(DIRECT_EXECUTOR)
            .build()
        repository = DailyRepository(
            database = database,
            dailyDao = database.dailyDao(),
            occurrenceDao = database.dailyOccurrenceDao(),
            subtaskDao = database.subtaskDao(),
            reminderDao = database.reminderDao(),
        )
        manager = DailyOccurrenceManager(database)
        todoRepository = TodoRepository(
            database = database,
            todoDao = database.todoDao(),
            subtaskDao = database.subtaskDao(),
            reminderDao = database.reminderDao(),
        )
        alarms = FakeReminderAlarms()
        reminderScheduler = testReminderScheduler(database, todoRepository, repository, alarms)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun tabEntry_generatesTodaysOccurrence_andIsIdempotent() = runBlocking {
        createDaily("Morning pages")

        val viewModel = DailiesViewModel(repository, manager, reminderScheduler)
        viewModel.generateTodayOccurrences(day)

        val occurrences = repository.getOccurrencesForDate(day)
        assertEquals(1, occurrences.size)

        // Re-entering the tab must never duplicate or rewrite an existing row.
        repository.setOccurrenceCompleted(occurrences.single().id, true)
        viewModel.generateTodayOccurrences(day)
        assertEquals(1, repository.getOccurrencesForDate(day).size)
        assertTrue(repository.getOccurrence(occurrences.single().id)!!.isCompleted)
    }

    @Test
    fun seriesEditor_createsAndEditsWholeSeries_withoutTouchingGeneratedOccurrences() = runBlocking {
        val editor = DailyEditorViewModel(repository, manager, reminderScheduler, SavedStateHandle())
        assertEquals(DailyEditorMode.SERIES, editor.mode)
        assertEquals("1", editor.uiState.everyN)

        editor.setTitle("  Morning pages  ")
        editor.setNote("  three pages  ")
        editor.setStartDate(day)
        editor.setRepeatType(RepeatType.WEEKLY)
        editor.setEveryN("2")
        editor.toggleWeekday(DayOfWeek.MONDAY)
        editor.toggleWeekday(DayOfWeek.WEDNESDAY)
        var saved = false
        editor.save { saved = true }

        assertTrue(saved)
        val created = repository.observeDailies().first().single()
        assertEquals("Morning pages", created.title)
        assertEquals("three pages", created.note)
        assertEquals(RepeatType.WEEKLY, created.repeatType)
        assertEquals(2, created.everyN)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), created.weekdays)

        val occurrenceId = repository.ensureOccurrence(created.id, day)!!.id
        repository.setOccurrenceCompleted(occurrenceId, true)

        val edit = DailyEditorViewModel(
            repository,
            manager,
            reminderScheduler,
            SavedStateHandle(mapOf(MnlistsRoutes.DAILY_ID_ARG to created.id)),
        )
        assertEquals("Morning pages", edit.uiState.title)
        assertEquals("2", edit.uiState.everyN)
        edit.setTitle("Evening pages")
        edit.setRepeatType(RepeatType.DAILY)
        edit.save {}

        val updated = repository.getDaily(created.id)!!
        assertEquals("Evening pages", updated.title)
        assertEquals(RepeatType.DAILY, updated.repeatType)
        assertTrue("weekday picks are cleared for a non-weekly series", updated.weekdays.isEmpty())
        assertTrue("series edits never rewrite an occurrence", repository.getOccurrence(occurrenceId)!!.isCompleted)
    }

    @Test
    fun seriesEditor_newSeriesStagesSubtasksUntilSave() = runBlocking {
        val editor = DailyEditorViewModel(repository, manager, reminderScheduler, SavedStateHandle())
        editor.setTitle("Morning pages")
        editor.setStartDate(day)

        var added = false
        editor.addSubtask("Book flights", "window seat", day + 1) { added = true }
        assertTrue(added)
        assertEquals("Book flights", editor.subtasks.single().title)
        assertTrue("nothing is persisted before Save", repository.observeDailies().first().isEmpty())

        // Draft children can be edited and checked before the series exists.
        val draftId = editor.subtasks.single().id
        assertTrue("draft ids are negative", draftId < 0)
        editor.updateSubtask(draftId, "Book train", "aisle", null) {}
        editor.setSubtaskCompleted(draftId, true)
        assertEquals("Book train", editor.subtasks.single().title)
        assertTrue(editor.subtasks.single().isCompleted)

        var saved = false
        editor.save { saved = true }
        assertTrue(saved)

        val created = repository.observeDailies().first().single()
        val occurrence = repository.getOccurrenceForDate(created.id, day)!!
        val stored = repository.getSubtasks(occurrence.id).single()
        assertEquals("Book train", stored.title)
        assertEquals("aisle", stored.note)
        assertTrue(stored.isCompleted)
    }

    @Test
    fun seriesEditor_validatesTitleAndEveryN() = runBlocking {
        val editor = DailyEditorViewModel(repository, manager, reminderScheduler, SavedStateHandle())
        editor.save {}
        assertTrue(editor.uiState.titleError)
        assertTrue("blank title fails before an N check", repository.observeDailies().first().isEmpty())

        editor.setTitle("Daily")
        editor.setEveryN("0")
        editor.save {}
        assertTrue(editor.uiState.everyNError)
        assertTrue(repository.observeDailies().first().isEmpty())
    }

    @Test
    fun moveOccurrence_reordersTheDayAndPersistsSortOrder() = runBlocking {
        val firstSeries = createDaily("First")
        val secondSeries = createDaily("Second")
        val thirdSeries = createDaily("Third")
        val first = repository.ensureOccurrence(firstSeries, day, sortOrder = 0.0)!!.id
        val second = repository.ensureOccurrence(secondSeries, day, sortOrder = 1.0)!!.id
        val third = repository.ensureOccurrence(thirdSeries, day, sortOrder = 2.0)!!.id

        val viewModel = DailiesViewModel(repository, manager, reminderScheduler)
        viewModel.setToday(day)
        assertEquals(3, viewModel.entries.first { it.isNotEmpty() }.size)

        viewModel.moveOccurrence(first, third)

        val ordered = repository.getOccurrencesForDate(day)
        assertEquals(listOf(second, third, first), ordered.map { it.id })
        assertEquals(listOf(0.0, 1.0, 2.0), ordered.map { it.sortOrder })
    }

    @Test
    fun skip_isPerOccurrence_andCanBeUndone() = runBlocking {
        val dailyId = createDaily("Morning pages")
        manager.generateDueOccurrences(today = day)
        val occurrence = repository.getOccurrencesForDate(day).single()
        val tomorrow = repository.ensureOccurrence(dailyId, day + 1)!!

        val viewModel = DailiesViewModel(repository, manager, reminderScheduler)
        viewModel.setSkipped(occurrence.id, true)

        assertTrue(repository.getOccurrence(occurrence.id)!!.isSkipped)
        assertFalse(repository.getOccurrence(occurrence.id)!!.isCompleted)
        assertFalse(repository.getOccurrence(tomorrow.id)!!.isSkipped)
        assertNotNull(repository.getDaily(dailyId))

        viewModel.setSkipped(occurrence.id, false)
        assertFalse(repository.getOccurrence(occurrence.id)!!.isSkipped)
    }

    @Test
    fun skip_reschedulesTheSeriesReminder_andUnskipReArmsIt() = runBlocking {
        val utc = ZoneId.of("UTC")
        val now = LocalDate.ofEpochDay(day).atTime(LocalTime.of(8, 0))
            .atZone(utc).toInstant().toEpochMilli()
        val nine = LocalDate.ofEpochDay(day).atTime(LocalTime.of(9, 0))
            .atZone(utc).toInstant().toEpochMilli()
        val nextNine = LocalDate.ofEpochDay(day + 1).atTime(LocalTime.of(9, 0))
            .atZone(utc).toInstant().toEpochMilli()

        val dailyId = createDaily("Morning pages")
        manager.generateDueOccurrences(today = day)
        val occurrence = repository.getOccurrencesForDate(day).single()
        val reminder = reminderScheduler.create(
            ReminderParentType.DAILY,
            dailyId,
            day,
            9 * 60,
            now = now,
            zone = utc,
        )!!
        assertEquals(nine, reminder.triggerAt)

        val viewModel = DailiesViewModel(repository, manager, reminderScheduler)
        viewModel.setSkippedAt(occurrence.id, true, now, utc)

        assertEquals(nextNine, database.reminderDao().getById(reminder.id)!!.triggerAt)
        assertEquals(nextNine, alarms.scheduled[reminder.notificationId])

        viewModel.setSkippedAt(occurrence.id, false, now, utc)

        assertEquals(nine, database.reminderDao().getById(reminder.id)!!.triggerAt)
        assertEquals(nine, alarms.scheduled[reminder.notificationId])
    }

    @Test
    fun subtaskCascade_allCheckedChecksOccurrence_andBothDirectionsStayScoped() = runBlocking {
        val occurrenceId = createOccurrence("Morning pages")
        val first = repository.addSubtask(
            Subtask(parentType = ParentType.DAILY_OCCURRENCE, parentId = occurrenceId, title = "Book flights"),
        )
        val second = repository.addSubtask(
            Subtask(parentType = ParentType.DAILY_OCCURRENCE, parentId = occurrenceId, title = "Pack bags"),
        )

        repository.setSubtaskCompleted(first, true)
        assertFalse(repository.getOccurrence(occurrenceId)!!.isCompleted)
        repository.setSubtaskCompleted(second, true)
        assertTrue(repository.getOccurrence(occurrenceId)!!.isCompleted)
        assertNotNull(repository.getOccurrence(occurrenceId)!!.completedAt)

        repository.setOccurrenceCompleted(occurrenceId, false)
        assertTrue(repository.getSubtasks(occurrenceId).none { it.isCompleted })
        repository.setOccurrenceCompleted(occurrenceId, true)
        assertTrue(repository.getSubtasks(occurrenceId).all { it.isCompleted })

        // Adding an open child to a done occurrence reopens it (SPEC §8).
        repository.addSubtask(
            Subtask(parentType = ParentType.DAILY_OCCURRENCE, parentId = occurrenceId, title = "New child"),
        )
        assertFalse(repository.getOccurrence(occurrenceId)!!.isCompleted)
    }

    @Test
    fun occurrenceEditor_editsOneDayOnly_andManagesItsSubtasks() = runBlocking {
        val occurrenceId = createOccurrence("Morning pages")
        val dailyId = repository.getOccurrence(occurrenceId)!!.dailyId
        val editor = DailyEditorViewModel(
            repository,
            manager,
            reminderScheduler,
            SavedStateHandle(mapOf(MnlistsRoutes.DAILY_OCCURRENCE_ID_ARG to occurrenceId)),
        )

        assertEquals(DailyEditorMode.OCCURRENCE, editor.mode)
        assertEquals("Morning pages", editor.uiState.title)

        editor.setSkipped(true)
        assertTrue(repository.getOccurrence(occurrenceId)!!.isSkipped)

        var added = false
        editor.addSubtask("Book flights", "window seat", day + 1) { added = true }
        assertTrue(added)
        val child = editor.subtasks.single()
        assertEquals("window seat", child.note)
        assertEquals(day + 1, child.deadline)

        editor.setSubtaskCompleted(child.id, true)
        assertTrue(repository.getOccurrence(occurrenceId)!!.isCompleted)

        editor.setCompleted(false)
        assertFalse(repository.getOccurrence(occurrenceId)!!.isCompleted)
        assertTrue("unchecking the day unchecks its children", repository.getSubtask(child.id)!!.isCompleted.not())

        editor.updateSubtask(child.id, "Book train", "", null) {}
        assertEquals("Book train", repository.getSubtask(child.id)!!.title)
        editor.deleteSubtask(child.id)
        assertTrue(repository.getSubtasks(occurrenceId).isEmpty())

        var deleted = false
        editor.delete { deleted = true }
        assertTrue(deleted)
        assertNull(repository.getOccurrence(occurrenceId))
        assertNotNull("occurrence deletion never removes the series", repository.getDaily(dailyId))
    }

    @Test
    fun seriesEditor_stagesThenCommitsReminder_andUpdatesDeletesIt() = runBlocking {
        val editor = DailyEditorViewModel(repository, manager, reminderScheduler, SavedStateHandle())
        editor.setTitle("Morning pages")
        editor.setStartDate(day)
        editor.addReminder(day, 7 * 60)

        val draft = editor.reminders.single()
        assertTrue("staged reminders use negative ids", draft.id < 0)
        assertTrue("nothing is persisted before Save", database.reminderDao().getAll().isEmpty())
        assertTrue(alarms.scheduled.isEmpty())

        editor.save {}

        val daily = repository.observeDailies().first().single()
        val stored = editor.reminders.single()
        assertTrue(stored.id > 0)
        assertEquals(ReminderParentType.DAILY, stored.parentType)
        assertEquals(daily.id, stored.parentId)
        assertEquals(7 * 60, stored.timeOfDayMinutes)
        assertEquals(stored.triggerAt, alarms.scheduled[stored.notificationId])

        editor.updateReminder(stored.id, day, 8 * 60)

        val updated = editor.reminders.single()
        assertEquals(8 * 60, updated.timeOfDayMinutes)
        assertEquals(updated.triggerAt, alarms.scheduled[updated.notificationId])

        editor.deleteReminder(updated.id)

        assertTrue(editor.reminders.isEmpty())
        assertTrue(alarms.cancelled.contains(updated.notificationId))
    }

    @Test
    fun occurrenceEditor_neverShowsSeriesReminders() = runBlocking {
        val dailyId = createDaily("Morning pages")
        reminderScheduler.create(ReminderParentType.DAILY, dailyId, day, 9 * 60)
        val occurrenceId = repository.ensureOccurrence(dailyId, day)!!.id

        val editor = DailyEditorViewModel(
            repository,
            manager,
            reminderScheduler,
            SavedStateHandle(mapOf(MnlistsRoutes.DAILY_OCCURRENCE_ID_ARG to occurrenceId)),
        )

        assertEquals(DailyEditorMode.OCCURRENCE, editor.mode)
        assertTrue(editor.reminders.isEmpty())
    }

    @Test
    fun seriesDeletion_keepsPastOccurrences_andDropsFutureOnes() = runBlocking {
        val dailyId = createDaily("Morning pages")
        val past = repository.ensureOccurrence(dailyId, day - 1)!!.id
        val future = repository.ensureOccurrence(dailyId, day + 1)!!.id

        val editor = DailyEditorViewModel(
            repository,
            manager,
            reminderScheduler,
            SavedStateHandle(mapOf(MnlistsRoutes.DAILY_ID_ARG to dailyId)),
        )
        var deleted = false
        editor.delete { deleted = true }

        assertTrue(deleted)
        assertNull(repository.getDaily(dailyId))
        assertNotNull("history is preserved", repository.getOccurrence(past))
        assertNull("future occurrences are removed", repository.getOccurrence(future))
    }

    private suspend fun createDaily(title: String): Long = repository.createDaily(
        Daily(title = title, startDate = day),
    )

    private suspend fun createOccurrence(title: String): Long {
        val dailyId = createDaily(title)
        return repository.ensureOccurrence(dailyId, day)!!.id
    }

    private companion object {
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
