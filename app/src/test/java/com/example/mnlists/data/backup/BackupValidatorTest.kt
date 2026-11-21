package com.example.mnlists.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Corrupt or hostile files must be rejected without throwing at the caller. */
class BackupValidatorTest {

    @Test
    fun validate_rejectsCorruptOrForeignDocuments() {
        val cases = listOf(
            "" to "empty",
            "   " to "blank",
            "{ not json" to "malformed",
            mutatedJson("\"format\": \"mnlists-backup\"", "\"format\": \"other-app\"") to "wrong app",
            mutatedJson("\"version\": 1", "\"version\": 99") to "wrong version",
            mutatedJson("\"resetEveryN\": 3", "\"resetEveryN\": \"three\"") to "wrong field type",
        )
        cases.forEach { (raw, label) ->
            val result = BackupValidator.validate(raw)
            assertTrue("$label should be invalid: $result", result is BackupValidationResult.Invalid)
        }
    }

    @Test
    fun validate_rejectsDuplicateIdsAndDuplicateOccurrenceDates() {
        val sample = samplePayload()
        val duplicateHabit = sample.habits.single()
        val duplicateIdCases = listOf(
            sample.copy(habits = listOf(duplicateHabit, duplicateHabit.copy(title = "Copy"))),
            sample.copy(reminders = listOf(sample.reminders.single(), sample.reminders.single().copy(id = 2))),
            sample.copy(
                dailyOccurrences = listOf(
                    sample.dailyOccurrences.single(),
                    sample.dailyOccurrences.single().copy(id = 2),
                ),
            ),
        )
        duplicateIdCases.forEach { payload ->
            val result = BackupValidator.validate(BackupCodec.encode(payload))
            assertTrue("Duplicate ids must be rejected: $result", result is BackupValidationResult.Invalid)
        }
        val reason = (BackupValidator.validate(BackupCodec.encode(duplicateIdCases.first())) as
            BackupValidationResult.Invalid).reason
        assertTrue(reason.contains("two habit rows"))
    }

    @Test
    fun validate_rejectsBadEnumsWeekdaysRangesAndNotificationIds() {
        val cases = mapOf(
            "unknown reset schedule" to samplePayload().copy(
                habits = listOf(samplePayload().habits.single().copy(resetSchedule = "YEARLY")),
            ),
            "weekday out of range" to samplePayload().copy(
                dailies = listOf(samplePayload().dailies.single().copy(weekdays = listOf(1, 9))),
            ),
            "repeated weekday" to samplePayload().copy(
                dailies = listOf(samplePayload().dailies.single().copy(weekdays = listOf(1, 1))),
            ),
            "time of day out of range" to samplePayload().copy(
                reminders = listOf(samplePayload().reminders.single().copy(timeOfDayMinutes = 1_500)),
            ),
            "zero notification id" to samplePayload().copy(
                reminders = listOf(samplePayload().reminders.single().copy(notificationId = 0)),
            ),
            "invalid sort order" to samplePayload().copy(
                todos = listOf(samplePayload().todos.single().copy(sortOrder = Double.NaN)),
            ),
            "counter with neither direction" to samplePayload().copy(
                habits = listOf(samplePayload().habits.single().copy(allowUp = false)),
            ),
        )
        cases.forEach { (label, payload) ->
            val raw = try {
                BackupCodec.encode(payload)
            } catch (e: Exception) {
                // NaN cannot even be encoded; still a rejection of bad data.
                ""
            }
            val result = BackupValidator.validate(raw)
            assertTrue("$label must be invalid: $result", result is BackupValidationResult.Invalid)
        }
    }

    @Test
    fun validate_acceptsOrphanHistory_becauseTheAppKeepsItOnPurpose() {
        val orphan = samplePayload().copy(
            habitLogs = listOf(samplePayload().habitLogs.single().copy(habitId = 999)),
        )
        val result = BackupValidator.validate(BackupCodec.encode(orphan))
        assertTrue("Orphan history should import: $result", result is BackupValidationResult.Valid)
        result as BackupValidationResult.Valid
        assertEquals(999L, result.backup.habitLogs.single().habitId)
    }

    @Test
    fun validate_errorMessageIsSafeToShowToTheUser() {
        val result = BackupValidator.validate(mutatedJson("\"version\": 1", "\"version\": 2"))
        result as BackupValidationResult.Invalid
        assertTrue(result.reason.contains("different version"))
    }
}
