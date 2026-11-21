package com.example.mnlists.data.backup

import androidx.room.Room
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.Todo
import java.util.concurrent.Executor
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

/** Real-Room proof that export captures everything and import restores safely. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class BackupRepositoryTest {

    private lateinit var database: MnlistsDatabase
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MnlistsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .setQueryExecutor(DIRECT_EXECUTOR)
            .setTransactionExecutor(DIRECT_EXECUTOR)
            .build()
        repository = BackupRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportCapturesAllTables_andImportRestoresThemInOneReplace() = runBlocking {
        seedSampleData()
        val exported = repository.exportJson()

        val parsed = BackupCodec.decode(exported)
        assertEquals(BackupCodec.FORMAT, parsed.format)
        assertEquals(1, parsed.habits.size)
        assertEquals(1, parsed.habitLogs.size)
        assertEquals(1, parsed.todos.size)
        assertEquals(1, parsed.dailies.size)
        assertEquals(1, parsed.dailyOccurrences.size)
        assertEquals(1, parsed.subtasks.size)
        assertEquals(1, parsed.reminders.size)

        // Simulate a different device: stale local data must disappear.
        database.backupDao().insertTodos(listOf(Todo(id = 99, title = "Stale local row")))
        val result = repository.importJson(exported)

        assertTrue("Import should succeed: $result", result is BackupImportResult.Success)
        result as BackupImportResult.Success
        assertEquals(1, result.counts.habits)
        assertEquals(1, result.counts.reminders)
        val dao = database.backupDao()
        assertEquals("Read", dao.habits().single().title)
        assertEquals("File taxes", dao.todos().single().title)
        assertEquals("Collect receipts", dao.subtasks().single().title)
        assertEquals(20_000L, dao.dailyOccurrences().single().date)
        assertEquals(setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY),
            dao.dailies().single().weekdays)
        assertEquals(1_001, dao.reminders().single().notificationId)
        assertEquals(7, dao.habitLogs().single().countAtEndOfCycle)
    }

    @Test
    fun importCorruptOrInvalidJson_leavesTheExistingDatabaseUntouched() = runBlocking {
        seedSampleData()
        val before = BackupCodec.decode(repository.exportJson()).copy(exportedAt = 0)

        val corrupt = repository.importJson("}{ not json")
        val wrongFormat = repository.importJson(
            sampleJson().replace("\"format\": \"mnlists-backup\"", "\"format\": \"other\""),
        )
        val duplicateIds = samplePayload().let { sample ->
            val habit = sample.habits.single()
            repository.importJson(
                BackupCodec.encode(sample.copy(habits = listOf(habit, habit.copy(title = "Dup")))),
            )
        }

        listOf(corrupt, wrongFormat, duplicateIds).forEach { result ->
            assertTrue("Import should fail: $result", result is BackupImportResult.Failure)
        }
        val after = BackupCodec.decode(repository.exportJson()).copy(exportedAt = 0)
        assertEquals(before, after)
    }

    private suspend fun seedSampleData() {
        val valid = BackupValidator.validate(sampleJson())
        assertTrue(valid is BackupValidationResult.Valid)
        valid as BackupValidationResult.Valid
        val dao = database.backupDao()
        with(valid.backup) {
            dao.insertHabits(habits)
            dao.insertHabitLogs(habitLogs)
            dao.insertTodos(todos)
            dao.insertDailies(dailies)
            dao.insertOccurrences(dailyOccurrences)
            dao.insertSubtasks(subtasks)
            dao.insertReminders(reminders)
        }
    }

    private companion object {
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
