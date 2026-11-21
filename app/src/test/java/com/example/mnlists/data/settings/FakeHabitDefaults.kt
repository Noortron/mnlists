package com.example.mnlists.data.settings

import com.example.mnlists.data.model.ResetSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Test double for the new-habit default schedule preference. */
class FakeHabitDefaults(initialSchedule: ResetSchedule = ResetSchedule.DAILY) : HabitDefaults {
    private val schedule = MutableStateFlow(initialSchedule)

    override val defaultResetSchedule: Flow<ResetSchedule> = schedule

    fun set(schedule: ResetSchedule) {
        this.schedule.value = schedule
    }
}
