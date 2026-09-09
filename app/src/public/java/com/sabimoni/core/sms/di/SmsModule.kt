package com.sabimoni.core.sms.di

import com.sabimoni.core.sms.NoOpSmsCaptureGateway
import com.sabimoni.core.sms.SmsCaptureGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SmsModule {

    @Binds
    abstract fun bindSmsCaptureGateway(impl: NoOpSmsCaptureGateway): SmsCaptureGateway
}
