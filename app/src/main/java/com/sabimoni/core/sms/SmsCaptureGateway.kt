package com.sabimoni.core.sms

/**
 * Tells feature code whether this build can auto-capture MoMo SMS, without letting it
 * know which flavor it is running in. Bound to a real implementation in `personal` and
 * to a no-op in `public`. See docs/adr/0011-personal-public-build-flavors.md.
 */
interface SmsCaptureGateway {
    val isSupported: Boolean
}
