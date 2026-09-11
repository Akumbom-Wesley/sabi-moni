package com.sabimoni.core.reminder

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sabimoni.core.data.dao.ReminderDao
import com.sabimoni.core.data.entity.ContributionStatus
import com.sabimoni.core.data.entity.ReminderEntity
import com.sabimoni.core.data.repository.GroupRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Fires one contribution reminder (FR4.3–4.4).
 *
 * Re-reads the contribution rather than trusting what was scheduled: between arming the
 * reminder and it going off, the obligation may have been paid, marked missed, edited or
 * deleted. A notification for a bill you already settled is worse than no notification.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val groups: GroupRepository,
    private val reminderDao: ReminderDao,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val contributionId = inputData.getLong(KEY_CONTRIBUTION_ID, INVALID_ID)
        if (contributionId == INVALID_ID) {
            Log.w(TAG, "Reminder ran with no contribution id")
            return Result.failure()
        }

        val contribution = groups.contribution(contributionId)
        if (contribution == null) {
            Log.i(TAG, "Contribution $contributionId is gone; nothing to remind about")
            return Result.success()
        }
        if (contribution.status != ContributionStatus.PENDING) {
            Log.i(TAG, "Contribution $contributionId is ${contribution.status}; not reminding")
            return Result.success()
        }

        val now = Instant.now(clock)
        val posted = notifier.notifyDue(contribution, LocalDate.now(clock))

        // Logged either way. A reminder that could not be shown because the permission
        // was refused still happened, and the thread should be able to say so.
        reminderDao.insert(
            ReminderEntity(
                groupContributionId = contributionId,
                remindAt = now,
                firedAt = now,
            ),
        )

        if (!posted) {
            Log.w(TAG, "Notification permission not granted; reminder not shown")
        }
        return Result.success()
    }

    companion object {
        const val KEY_CONTRIBUTION_ID = "contributionId"

        private const val TAG = "ReminderWorker"
        private const val INVALID_ID = -1L
    }
}
