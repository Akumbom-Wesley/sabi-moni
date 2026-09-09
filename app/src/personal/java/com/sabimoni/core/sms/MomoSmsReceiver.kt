package com.sabimoni.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.sabimoni.core.data.entity.MessageSource
import com.sabimoni.core.data.repository.CaptureRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Only senders on this list are captured. Without the filter every OTP and private
 * message on the device would be forwarded to a third-party model.
 */
private val MOMO_SENDERS = listOf("MTN", "MOMO", "MOBILE MONEY")

@AndroidEntryPoint
class MomoSmsReceiver : BroadcastReceiver() {

    // Goes through the repository, not the DAO, so an auto-captured MoMo alert takes
    // exactly the same path as a typed message — including being queued for parsing.
    @Inject
    lateinit var captureRepository: CaptureRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        val sender = parts.first().displayOriginatingAddress.orEmpty()
        if (MOMO_SENDERS.none { sender.contains(it, ignoreCase = true) }) return

        val body = parts.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        if (body.isBlank()) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                captureRepository.capture(rawText = body, source = MessageSource.SMS)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
