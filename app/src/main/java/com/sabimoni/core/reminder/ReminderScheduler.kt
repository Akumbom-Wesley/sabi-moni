package com.sabimoni.core.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the reminder for one contribution (FR4.3). An interface so `GroupRepository`
 * stays free of WorkManager and can be tested without it.
 */
interface ReminderScheduler {

    fun schedule(contributionId: Long, at: Instant)

    /**
     * Stop reminding about this contribution: disarm the pending reminder *and* dismiss
     * one already showing. Both halves are the same intent — the obligation is settled, so
     * stop bothering the user about it — and splitting them would leave a notification for
     * a bill you just paid sitting in the shade.
     */
    fun cancel(contributionId: Long)
}

@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) : ReminderScheduler {

    /**
     * WorkManager is the single source of truth for *when* a reminder fires — the
     * `reminders` table records only that one did. Keeping a schedule in both places
     * would be two copies of one fact, and they would drift (ADR-0026).
     *
     * A time already past schedules with no delay rather than being dropped: "choir wants
     * 5000 by the 15th" relayed on the 16th is exactly when a reminder is most useful.
     */
    override fun schedule(contributionId: Long, at: Instant) {
        val delay = Duration.between(Instant.now(clock), at).coerceAtLeast(Duration.ZERO)

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay)
            .setInputData(workDataOf(ReminderWorker.KEY_CONTRIBUTION_ID to contributionId))
            .build()

        // REPLACE, keyed per contribution: editing a due date reschedules that one
        // reminder and must not leave the old time armed as well.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(contributionId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(contributionId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(contributionId))
        notifier.cancel(contributionId)
    }

    private fun workName(contributionId: Long) = "$WORK_PREFIX$contributionId"

    private companion object {
        const val WORK_PREFIX = "contribution-reminder-"
    }
}
