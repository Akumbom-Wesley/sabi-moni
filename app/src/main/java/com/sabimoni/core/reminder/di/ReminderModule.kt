package com.sabimoni.core.reminder.di

import com.sabimoni.core.reminder.ObligationScheduler
import com.sabimoni.core.reminder.WorkManagerObligationScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {

    @Binds
    abstract fun bindObligationScheduler(
        impl: WorkManagerObligationScheduler,
    ): ObligationScheduler
}
