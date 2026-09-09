package com.sabimoni.core.sms

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpSmsCaptureGateway @Inject constructor() : SmsCaptureGateway {
    override val isSupported: Boolean = false
}
