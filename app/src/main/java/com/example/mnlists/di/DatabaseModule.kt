package com.example.mnlists.di

import android.content.Context
import androidx.room.Room
import com.example.mnlists.data.local.MnlistsDatabase
import com.example.mnlists.data.local.dao.DailyDao
import com.example.mnlists.data.local.dao.DailyOccurrenceDao
import com.example.mnlists.data.local.dao.HabitDao
import com.example.mnlists.data.local.dao.HabitLogDao
import com.example.mnlists.data.local.dao.ReminderDao
import com.example.mnlists.data.local.dao.SubtaskDao
import com.example.mnlists.data.local.dao.TodoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MnlistsDatabase =
        Room.databaseBuilder(
            context,
            MnlistsDatabase::class.java,
            MnlistsDatabase.DATABASE_NAME,
        )
            .addMigrations(MnlistsDatabase.MIGRATION_1_2, MnlistsDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideHabitDao(database: MnlistsDatabase): HabitDao = database.habitDao()

    @Provides
    fun provideHabitLogDao(database: MnlistsDatabase): HabitLogDao = database.habitLogDao()

    @Provides
    fun provideTodoDao(database: MnlistsDatabase): TodoDao = database.todoDao()

    @Provides
    fun provideDailyDao(database: MnlistsDatabase): DailyDao = database.dailyDao()

    @Provides
    fun provideDailyOccurrenceDao(database: MnlistsDatabase): DailyOccurrenceDao =
        database.dailyOccurrenceDao()

    @Provides
    fun provideSubtaskDao(database: MnlistsDatabase): SubtaskDao = database.subtaskDao()

    @Provides
    fun provideReminderDao(database: MnlistsDatabase): ReminderDao = database.reminderDao()
}
