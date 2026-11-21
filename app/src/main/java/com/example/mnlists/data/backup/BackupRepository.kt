package com.example.mnlists.data.backup

import android.util.Log
import androidx.room.withTransaction
import com.example.mnlists.data.local.MnlistsDatabase
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of restoring a validated backup; failures never partially apply. */
sealed interface BackupImportResult {
    data class Success(val counts: BackupCounts) : BackupImportResult
    data class Failure(val reason: String) : BackupImportResult
}

/**
 * Exports the seven Room tables to one JSON document and restores them in a
 * single transaction. Restore is an intentional strict replace: every table is
 * cleared first, so imported ids and notification ids cannot collide with old
 * rows. The transaction rolls back if any insert fails.
 */
class BackupRepository @Inject constructor(
    private val database: MnlistsDatabase,
) {

    fun validate(raw: String): BackupValidationResult = BackupValidator.validate(raw)

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val payload = database.withTransaction {
            val dao = database.backupDao()
            BackupPayload(
                format = BackupCodec.FORMAT,
                version = BackupCodec.VERSION,
                exportedAt = System.currentTimeMillis(),
                habits = dao.habits().map { it.toBackup() },
                habitLogs = dao.habitLogs().map { it.toBackup() },
                todos = dao.todos().map { it.toBackup() },
                dailies = dao.dailies().map { it.toBackup() },
                dailyOccurrences = dao.dailyOccurrences().map { it.toBackup() },
                subtasks = dao.subtasks().map { it.toBackup() },
                reminders = dao.reminders().map { it.toBackup() },
            )
        }
        BackupCodec.encode(payload)
    }

    suspend fun importJson(raw: String): BackupImportResult = withContext(Dispatchers.IO) {
        when (val validation = validate(raw)) {
            is BackupValidationResult.Invalid -> BackupImportResult.Failure(validation.reason)
            is BackupValidationResult.Valid -> {
                val backup = validation.backup
                val counts = backup.counts
                try {
                    database.withTransaction {
                        val dao = database.backupDao()
                        dao.deleteReminders()
                        dao.deleteSubtasks()
                        dao.deleteOccurrences()
                        dao.deleteHabitLogs()
                        dao.deleteTodos()
                        dao.deleteDailies()
                        dao.deleteHabits()

                        dao.insertHabits(backup.habits)
                        dao.insertHabitLogs(backup.habitLogs)
                        dao.insertTodos(backup.todos)
                        dao.insertDailies(backup.dailies)
                        dao.insertOccurrences(backup.dailyOccurrences)
                        dao.insertSubtasks(backup.subtasks)
                        dao.insertReminders(backup.reminders)
                    }
                    BackupImportResult.Success(counts)
                } catch (e: Exception) {
                    Log.e(TAG, "Backup import failed", e)
                    BackupImportResult.Failure("The backup could not be restored. No changes were saved.")
                }
            }
        }
    }

    private companion object {
        const val TAG = "BackupRepository"
    }
}
