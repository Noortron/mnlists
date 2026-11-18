package com.example.mnlists.data.local

import androidx.room.TypeConverter
import com.example.mnlists.data.model.ParentType
import com.example.mnlists.data.model.ReminderParentType
import com.example.mnlists.data.model.RepeatType
import com.example.mnlists.data.model.ResetSchedule
import java.time.DayOfWeek

/**
 * Room type converters. Enums are stored by name so schema dumps stay readable;
 * weekdays are stored as a sorted, comma-separated list of ISO day numbers
 * (Monday = 1 … Sunday = 7), which keeps weekly schedules query-friendly and
 * stable across locales.
 */
class Converters {

    @TypeConverter
    fun fromResetSchedule(value: ResetSchedule): String = value.name

    @TypeConverter
    fun toResetSchedule(value: String): ResetSchedule = ResetSchedule.valueOf(value)

    @TypeConverter
    fun fromRepeatType(value: RepeatType): String = value.name

    @TypeConverter
    fun toRepeatType(value: String): RepeatType = RepeatType.valueOf(value)

    @TypeConverter
    fun fromParentType(value: ParentType): String = value.name

    @TypeConverter
    fun toParentType(value: String): ParentType = ParentType.valueOf(value)

    @TypeConverter
    fun fromReminderParentType(value: ReminderParentType): String = value.name

    @TypeConverter
    fun toReminderParentType(value: String): ReminderParentType = ReminderParentType.valueOf(value)

    @TypeConverter
    fun fromDayOfWeek(value: DayOfWeek?): Int? = value?.value

    @TypeConverter
    fun toDayOfWeek(value: Int?): DayOfWeek? = value?.let(DayOfWeek::of)

    @TypeConverter
    fun fromWeekdays(value: Set<DayOfWeek>): String =
        value.map { it.value }.sorted().joinToString(separator = ",")

    @TypeConverter
    fun toWeekdays(value: String): Set<DayOfWeek> {
        if (value.isBlank()) return emptySet()
        return value.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .map(DayOfWeek::of)
            .toSet()
    }
}
