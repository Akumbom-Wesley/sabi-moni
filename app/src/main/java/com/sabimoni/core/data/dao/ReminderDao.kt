package com.sabimoni.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sabimoni.core.data.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ReminderDao {

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("SELECT * FROM reminders WHERE firedAt IS NULL ORDER BY remindAt ASC")
    fun observePending(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE firedAt IS NULL AND remindAt <= :now ORDER BY remindAt ASC")
    suspend fun due(now: Instant): List<ReminderEntity>

    @Query("UPDATE reminders SET firedAt = :firedAt WHERE id = :id")
    suspend fun markFired(id: Long, firedAt: Instant)

    @Query("DELETE FROM reminders WHERE groupContributionId = :contributionId")
    suspend fun deleteForContribution(contributionId: Long)

    /**
     * Whether this contribution has already been reminded about since [since].
     *
     * The overdue nag is *daily* (ADR-0029), and the daily worker can run more than once a
     * day — WorkManager retries, and the user opening the app can trigger a catch-up. This
     * log is what keeps one reminder per contribution per day rather than one per run, and
     * is the first real job the `reminders` table has had (ADR-0026 made it a log).
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM reminders " +
            "WHERE groupContributionId = :contributionId AND firedAt >= :since)",
    )
    suspend fun firedSince(contributionId: Long, since: Instant): Boolean
}
