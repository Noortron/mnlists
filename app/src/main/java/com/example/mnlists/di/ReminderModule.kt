package com.example.mnlists.di

import com.example.mnlists.data.reminder.AndroidReminderAlarms
import com.example.mnlists.data.reminder.AndroidReminderNotifier
import com.example.mnlists.data.reminder.ReminderAlarms
import com.example.mnlists.data.reminder.ReminderNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the Android-backed reminder side effects for Hilt (Step 11). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {

    @Binds
    @Singleton
    abstract fun bindReminderAlarms(impl: AndroidReminderAlarms): ReminderAlarms

    @Binds
    @Singleton
    abstract fun bindReminderNotifier(impl: AndroidReminderNotifier): ReminderNotifier
}
