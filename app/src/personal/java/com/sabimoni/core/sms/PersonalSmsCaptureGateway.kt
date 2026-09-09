package com.sabimoni.core.sms

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonalSmsCaptureGateway @Inject constructor() : SmsCaptureGateway {
    override val isSupported: Boolean = true
}
