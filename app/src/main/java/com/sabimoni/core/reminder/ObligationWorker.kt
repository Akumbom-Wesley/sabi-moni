package com.sabimoni.core.reminder

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sabimoni.core.data.dao.ReminderDao
import com.sabimoni.core.data.entity.ReminderEntity
import com.sabimoni.core.data.repository.GroupRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * The daily obligation check (FR4.3–4.4): roll recurring schedules forward, then remind.
 *
 * One job rather than two, because the second depends on the first — you cannot warn about
 * next month's church contribution until next month's contribution exists, and nothing but
 * this worker creates it (ADR-0029).
 *
 * Reminding follows two rules:
 *
 * 1. **Once**, as soon as the lead window opens, for an obligation not yet due. Four days'
 *    lead means one notification on the 26th, not four.
 * 2. **Daily**, once the deadline has passed unpaid, naming the fine — the nag that exists
 *    because the fine grows while you ignore it.
 *
 * Both are deduplicated through the `reminders` log, so running twice in a day — a
 * WorkManager retry, or a `checkNow()` after an edit — cannot double-notify.
 */
@HiltWorker
class ObligationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val groups: GroupRepository,
    private val reminderDao: ReminderDao,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val today = LocalDate.now(clock)

        val created = groups.rollForwardRecurring(today)
        if (created > 0) {
            Log.i(TAG, "Materialised $created recurring contribution(s) up to $today")
        }

        val startOfToday = today.atStartOfDay(clock.zone).toInstant()

        groups.dueForReminder(today).forEach { contribution ->
            val overdue = contribution.isOverdue(today)

            // Overdue nags daily; the pre-deadline warning goes out once and stays quiet.
            val alreadySaidToday = reminderDao.firedSince(contribution.id, startOfToday)
            val everSaid = reminderDao.firedSince(contribution.id, Instant.EPOCH)
            val shouldRemind = if (overdue) !alreadySaidToday else !everSaid

            if (!shouldRemind) return@forEach

            val posted = notifier.notifyDue(contribution, today)
            reminderDao.insert(
                ReminderEntity(
                    groupContributionId = contribution.id,
                    remindAt = Instant.now(clock),
                    firedAt = Instant.now(clock),
                ),
            )

            if (!posted) {
                Log.w(TAG, "Notification permission not granted; reminder not shown")
            }
        }

        return Result.success()
    }

    private companion object {
        const val TAG = "ObligationWorker"
    }
}
