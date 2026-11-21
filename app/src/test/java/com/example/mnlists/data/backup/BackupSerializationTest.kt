package com.example.mnlists.data.backup

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 13.5: a backup document must carry every table and round-trip exactly. */
class BackupSerializationTest {

    @Test
    fun encode_containsEveryTableAndRoundTripsToTheSamePayload() {
        val payload = samplePayload()

        val json = BackupCodec.encode(payload)

        listOf(
            "\"format\": \"mnlists-backup\"",
            "\"habits\"",
            "\"habitLogs\"",
            "\"todos\"",
            "\"dailies\"",
            "\"dailyOccurrences\"",
            "\"subtasks\"",
            "\"reminders\"",
            "20 pages",
            "Collect receipts",
            "\"timeOfDayMinutes\": 480",
            "\"weekdays\"",
        ).forEach { fragment ->
            assertTrue("Missing $fragment in exported JSON", json.contains(fragment))
        }
        assertEquals(payload, BackupCodec.decode(json))
    }

    @Test
    fun decode_validSample_producesTypedEntitiesForAllTables() {
        val result = BackupValidator.validate(sampleJson())
        assertTrue("Expected a valid backup: $result", result is BackupValidationResult.Valid)
        result as BackupValidationResult.Valid

        val backup = result.backup
        assertEquals(1, backup.counts.habits)
        assertEquals(1, backup.counts.habitLogs)
        assertEquals(1, backup.counts.todos)
        assertEquals(1, backup.counts.dailies)
        assertEquals(1, backup.counts.occurrences)
        assertEquals(1, backup.counts.subtasks)
        assertEquals(1, backup.counts.reminders)
        assertEquals(DayOfWeek.MONDAY, backup.habits.single().resetWeekday)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), backup.dailies.single().weekdays)
        assertEquals(480, backup.reminders.single().timeOfDayMinutes)
    }

    @Test(expected = kotlinx.serialization.SerializationException::class)
    fun decode_rejectsMalformedJson() {
        BackupCodec.decode("{ this is not json")
    }
}
