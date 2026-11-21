package com.example.mnlists.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mnlists.data.backup.BackupCounts
import com.example.mnlists.data.backup.BackupImportResult
import com.example.mnlists.data.backup.BackupRepository
import com.example.mnlists.data.backup.BackupValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PendingBackup(val raw: String, val counts: BackupCounts)

data class BackupEvent(val message: String, val isError: Boolean)

/** Holds the picked-but-not-yet-confirmed import and reports backup outcomes. */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
) : ViewModel() {

    private val _pending = MutableStateFlow<PendingBackup?>(null)
    val pending: StateFlow<PendingBackup?> = _pending.asStateFlow()

    private val _events = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BackupEvent> = _events.asSharedFlow()

    suspend fun exportJson(): String = repository.exportJson()

    /** Validates before showing the replace confirmation; errors leave Room alone. */
    fun prepareImport(raw: String) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.Default) { repository.validate(raw) }) {
                is BackupValidationResult.Invalid ->
                    _events.tryEmit(BackupEvent(result.reason, isError = true))
                is BackupValidationResult.Valid ->
                    _pending.value = PendingBackup(raw, result.backup.counts)
            }
        }
    }

    fun confirmImport() {
        val pending = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch {
            when (val result = repository.importJson(pending.raw)) {
                is BackupImportResult.Success ->
                    _events.tryEmit(
                        BackupEvent("Imported ${result.counts.habits} habits, ${result.counts.todos} to-dos, ${result.counts.dailies} dailies and ${result.counts.reminders} reminders.", isError = false),
                    )
                is BackupImportResult.Failure ->
                    _events.tryEmit(BackupEvent("${result.reason} Nothing was changed.", isError = true))
            }
        }
    }

    fun cancelImport() {
        _pending.value = null
    }
}
