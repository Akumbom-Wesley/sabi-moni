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
    fun schedule()
}

@Singleton
class WorkManagerParseScheduler @Inject constructor(
    // `@param:` is explicit about the target, which Kotlin will otherwise change the
    // default for — see KT-73255.
    @param:ApplicationContext private val context: Context,
) : ParseScheduler {

    override fun schedule() {
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
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "parse-pending-messages"
        const val BACKOFF_SECONDS = 30L
    }
}
