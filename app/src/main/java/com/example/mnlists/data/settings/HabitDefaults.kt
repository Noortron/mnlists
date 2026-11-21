package com.example.mnlists.data.settings

import com.example.mnlists.data.model.ResetSchedule
import kotlinx.coroutines.flow.Flow

/**
 * The persisted default reset schedule applied to a brand-new habit
 * (SPEC §4.8, Step 13). Kept as a narrow interface so the habit editor can be
 * tested without Android DataStore.
 */
interface HabitDefaults {
    val defaultResetSchedule: Flow<ResetSchedule>
}
