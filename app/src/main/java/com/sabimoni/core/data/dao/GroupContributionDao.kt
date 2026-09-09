package com.sabimoni.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sabimoni.core.data.entity.ContributionStatus
import com.sabimoni.core.data.entity.GroupContributionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface GroupContributionDao {

    @Upsert
    suspend fun upsert(contribution: GroupContributionEntity): Long

    @Query("SELECT * FROM group_contributions WHERE id = :id")
    suspend fun byId(id: Long): GroupContributionEntity?

    @Query("SELECT * FROM group_contributions WHERE status = 'PENDING' ORDER BY dueDate ASC")
    fun observeOutstanding(): Flow<List<GroupContributionEntity>>

    @Query("SELECT * FROM group_contributions WHERE groupId = :groupId ORDER BY dueDate DESC")
    fun observeForGroup(groupId: Long): Flow<List<GroupContributionEntity>>

    @Query("SELECT COALESCE(SUM(amountXaf), 0) FROM group_contributions WHERE status = 'PENDING'")
    fun observeTotalOutstanding(): Flow<Long>

    @Query("UPDATE group_contributions SET status = :status, paidDate = :paidDate WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ContributionStatus, paidDate: LocalDate?)
}
