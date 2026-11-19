package com.example.mnlists.data.daily

import androidx.room.withTransaction
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.DailyOccurrence
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Creates missing Daily occurrences on app open (SPEC §8/§9, Step 8).
 *
 * For every series it asks [DailyOccurrenceEngine] for the dates between its
 * last generated occurrence (or its start date) and today, then inserts a
 * **fresh** row per date — all incomplete and unskipped. Existing rows are
 * never rewritten, so a check or skip made on one day cannot leak into
 * another. The [Mutex] plus the unique `(dailyId, date)` index make repeated
 * calls idempotent.
 */
@Singleton
class DailyOccurrenceManager @Inject constructor(
    private val database: MnlistsDatabase,
) {

    private val mutex = Mutex()

    /**
     * @param today today as an epoch day; defaults to the current local date.
     * @return how many new occurrence rows were created.
     */
    suspend fun generateDueOccurrences(
        today: Long? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val targetDay = today ?: DailyOccurrenceEngine.today(zone)
        return mutex.withLock {
            database.withTransaction {
                var generated = 0
                database.dailyDao().getAll().forEachIndexed { index, daily ->
                    val last = database.dailyOccurrenceDao().latestDateFor(daily.id)
                    DailyOccurrenceEngine.datesToGenerate(daily, last, targetDay)
                        .forEach { date ->
                            val id = database.dailyOccurrenceDao().insert(
                                DailyOccurrence(
                                    dailyId = daily.id,
                                    date = date,
                                    // Older series render first; Step 10 owns manual reordering.
                                    sortOrder = index.toDouble(),
                                ),
                            )
                            if (id > 0) generated++
                        }
                }
                generated
            }
        }
    }
}
