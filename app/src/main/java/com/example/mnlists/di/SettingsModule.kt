package com.example.mnlists.di

import com.example.mnlists.data.settings.AndroidNotificationAccessChecker
import com.example.mnlists.data.settings.HabitDefaults
import com.example.mnlists.data.settings.NotificationAccessChecker
import com.example.mnlists.data.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the Android-backed preference and notification-status APIs (Step 13). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindHabitDefaults(impl: SettingsRepository): HabitDefaults

    @Binds
    @Singleton
    abstract fun bindNotificationAccessChecker(
        impl: AndroidNotificationAccessChecker,
    ): NotificationAccessChecker
}
