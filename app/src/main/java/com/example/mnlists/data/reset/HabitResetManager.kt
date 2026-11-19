package com.example.mnlists.data.reset

import androidx.room.withTransaction
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.entity.HabitLog
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies due habit resets on app open (SPEC §8, Step 5): every elapsed cycle
 * first gets a [HabitLog] row with the counter value it ended with, then the
 * habit's counter is set back to [HabitResetEngine.DEFAULT_START_VALUE] and
 * `lastResetAt` is moved to the cycle boundary.
 *
 * Logs and the reset are written in one transaction, and the [Mutex] makes
 * repeated opens/tab switches idempotent (no duplicate history).
 */
@Singleton
class HabitResetManager @Inject constructor(
    private val database: MnlistsDatabase,
) {

    private val mutex = Mutex()

    /**
     * @return the number of habits that were reset (0 when nothing was due).
     */
    suspend fun applyDueResets(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int = mutex.withLock {
        database.withTransaction {
            var resetHabits = 0

            database.habitDao().getAll().forEach { habit ->
                val due = HabitResetEngine.dueResets(habit, now, zone)
                if (due.isEmpty()) return@forEach

                database.habitLogDao().insertAll(
                    due.map { cycle ->
                        HabitLog(
                            habitId = habit.id,
                            date = cycle.logDate,
                            countAtEndOfCycle = cycle.countAtEndOfCycle,
                            cycleStart = cycle.cycleStart,
                            cycleEnd = cycle.cycleEnd,
                        )
                    },
                )
                database.habitDao().applyReset(
                    id = habit.id,
                    count = HabitResetEngine.DEFAULT_START_VALUE,
                    resetAt = due.last().cycleEnd,
                )
                resetHabits++
            }

            resetHabits
        }
    }
}
