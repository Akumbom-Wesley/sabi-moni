package com.sabimoni.core.parse.di

import com.sabimoni.core.parse.ParseScheduler
import com.sabimoni.core.parse.WorkManagerParseScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ParseModule {

    @Binds
    abstract fun bindParseScheduler(impl: WorkManagerParseScheduler): ParseScheduler
}
