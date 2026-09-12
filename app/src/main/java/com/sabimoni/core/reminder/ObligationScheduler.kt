package com.sabimoni.core.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the daily obligation check running, and lets something ask for it now.
 *
 * Replaces the per-contribution exact reminders of ADR-0026. Once a recurring schedule
 * creates its own obligations there is nothing to hang a per-contribution alarm on at the
 * moment the user presses Save — the obligation for next month does not exist yet. One
 * daily sweep decides what to warn about instead (ADR-0029).
 */
interface ObligationScheduler {

    /** Idempotent: enqueues the daily check if it is not already running. */
    fun ensureDailyCheck()

    /**
     * Runs the check immediately, for when something changed that the user expects to see
     * acted on — a new obligation, an edited schedule — rather than tomorrow morning.
     */
    fun checkNow()

    /** Takes down a notification for an obligation that is no longer outstanding. */
    fun dismiss(contributionId: Long)
}

@Singleton
class WorkManagerObligationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) : ObligationScheduler {

    override fun ensureDailyCheck() {
        val request = PeriodicWorkRequestBuilder<ObligationWorker>(Duration.ofDays(1))
            // Aimed at the morning: paying is a daytime errand, and a notification that
            // arrives at 02:00 is one you sleep through (ADR-0026).
            .setInitialDelay(untilNextMorning())
            .build()

        // KEEP, not REPLACE: this runs on every app start, and replacing would reset the
        // period each time — on a phone opened daily the check would never fire.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun checkNow() {
        val request = OneTimeWorkRequestBuilder<ObligationWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun dismiss(contributionId: Long) = notifier.cancel(contributionId)

    private fun untilNextMorning(): Duration {
        val now = LocalDate.now(clock).atTime(LocalTime.now(clock))
        val target = LocalDate.now(clock).atTime(REMINDER_HOUR, 0)
        val next = if (now < target) target else target.plusDays(1)
        return Duration.between(now, next)
    }

    private companion object {
        const val DAILY_WORK_NAME = "obligation-daily-check"
        const val IMMEDIATE_WORK_NAME = "obligation-check-now"
        const val REMINDER_HOUR = 9
    }
}
