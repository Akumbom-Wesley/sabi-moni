package com.sabimoni.core.parse

import android.content.Context
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
 * fails because connectivity did. See docs/adr/0015-offline-capture-parse-queue.md and
 * docs/adr/0017-parse-worker-and-failure-policy.md.
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

        var sawTransientFailure = false

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
                    when (val failure = error.toParseFailure()) {
                        is ParseFailure.Transient -> {
                            // Left PENDING_PARSE so the retry picks it up again.
                            sawTransientFailure = true
                        }
                        is ParseFailure.Permanent -> {
                            transactions.markFailed(message.id, failure.reason)
                        }
                    }
                },
            )
        }

        // One transient failure retries the whole run. Messages already committed are
        // PARSED, so the retry re-reads a shorter queue rather than redoing work.
        return if (sawTransientFailure) Result.retry() else Result.success()
    }
}
