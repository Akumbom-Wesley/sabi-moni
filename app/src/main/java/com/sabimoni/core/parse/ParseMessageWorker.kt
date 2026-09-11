package com.sabimoni.core.parse

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sabimoni.core.ai.AiParsingProvider
import com.sabimoni.core.ai.ParseRequest
import com.sabimoni.core.data.dao.CategoryDao
import com.sabimoni.core.data.dao.GroupDao
import com.sabimoni.core.data.dao.MessageDao
import com.sabimoni.core.data.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.LocalDate

/**
 * Drains the pending-parse queue (FR2.1–2.3). Capture writes locally and returns
 * immediately; this is the part that is allowed to need the network, so logging never
 * fails because connectivity did. See docs/adr/0015-offline-capture-parse-queue.md,
 * docs/adr/0017-parse-worker-and-failure-policy.md and
 * docs/adr/0021-bounded-retries-and-visible-waiting.md.
 */
@HiltWorker
class ParseMessageWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val messageDao: MessageDao,
    private val categoryDao: CategoryDao,
    private val groupDao: GroupDao,
    private val provider: AiParsingProvider,
    private val transactions: TransactionRepository,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val pending = messageDao.pendingParse()
        if (pending.isEmpty()) return Result.success()

        // Fetched once per run, not per message: this is the entire context the model
        // gets, and it does not change mid-drain (FR2.6).
        val categories = categoryDao.active().map { it.name }
        val groups = groupDao.active().map { it.name }
        val today = LocalDate.now(clock)

        // The run only happens when the CONNECTED constraint is met, so an offline phone
        // accrues no attempts at all and its captures keep waiting — ADR-0015's promise is
        // intact. This counts attempts that actually reached the network and still failed.
        val lastAttempt = runAttemptCount >= MAX_ATTEMPTS

        var retryNeeded = false

        for (message in pending) {
            val request = ParseRequest(
                rawText = message.rawText,
                categories = categories,
                groups = groups,
                today = today,
            )

            provider.parse(request).fold(
                onSuccess = { drafts -> transactions.commitParse(message, drafts) },
                onFailure = { error ->
                    val failure = error.toParseFailure()

                    // Logged so a repeating failure is diagnosable at all — this used to be
                    // swallowed in silence. The id and the reason only: the message text is
                    // personal financial data and does not belong in logcat (ADR-0012).
                    Log.w(
                        TAG,
                        "Parse attempt $runAttemptCount for message ${message.id} failed: " +
                            "${failure.reason} (${failure::class.simpleName})",
                        error,
                    )

                    when (failure) {
                        is ParseFailure.Permanent ->
                            transactions.markFailed(message.id, failure.reason)

                        is ParseFailure.Transient -> if (lastAttempt) {
                            // Out of attempts. A dead end the user can see and act on beats
                            // an invisible retry loop stretching into hours (ADR-0021).
                            transactions.markFailed(
                                message.id,
                                "${failure.reason} — tap Try again",
                            )
                        } else {
                            // Left PENDING_PARSE so the retry picks it up again, but the
                            // reason is recorded so the thread can say we are still trying.
                            transactions.noteAttemptFailure(message.id, failure.reason)
                            retryNeeded = true
                        }
                    }
                },
            )
        }

        // One transient failure retries the whole run. Messages already committed are
        // PARSED, so the retry re-reads a shorter queue rather than redoing work.
        return if (retryNeeded) Result.retry() else Result.success()
    }

    private companion object {
        const val TAG = "ParseMessageWorker"

        /**
         * Attempts that reached the network before we stop and tell the user. With the
         * 15-second exponential backoff this reaches a definite answer in roughly two
         * minutes rather than climbing towards WorkManager's five-hour ceiling.
         */
        const val MAX_ATTEMPTS = 3
    }
}
