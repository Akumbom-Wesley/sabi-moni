package com.sabimoni.core.parse

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks for the pending-parse queue to be drained. An interface so `CaptureRepository`
 * stays free of WorkManager, and so capture can be unit-tested without it.
 */
interface ParseScheduler {

    /** A new capture arrived. Joins whatever is already queued. */
    fun schedule()

    /**
     * The user asked for it *now*. Discards a run that is sitting out a backoff delay,
     * because a person watching the screen is not willing to wait two more minutes for a
     * timer they cannot see.
     */
    fun scheduleNow()
}

@Singleton
class WorkManagerParseScheduler @Inject constructor(
    // `@param:` is explicit about the target, which Kotlin will otherwise change the
    // default for — see KT-73255.
    @param:ApplicationContext private val context: Context,
) : ParseScheduler {

    override fun schedule() = enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)

    // REPLACE cancels the backing-off request and starts a fresh one with no delay. Safe
    // to do mid-run: `commitParse` is transactional, so a cancelled run leaves messages
    // either fully parsed or still pending, never half-committed.
    override fun scheduleNow() = enqueue(ExistingWorkPolicy.REPLACE)

    private fun enqueue(policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ParseMessageWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        // APPEND_OR_REPLACE rather than KEEP: the worker drains every pending message,
        // but a message captured *while* a run is in flight would be missed by that run,
        // and KEEP would silently drop the enqueue that should have caught it.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    private companion object {
        const val WORK_NAME = "parse-pending-messages"

        // 15s rather than 30s: with the attempt cap in ParseMessageWorker this is the
        // difference between reaching a definite answer in about two minutes and in four.
        const val BACKOFF_SECONDS = 15L
    }
}
