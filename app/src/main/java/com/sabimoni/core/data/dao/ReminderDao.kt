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
}
