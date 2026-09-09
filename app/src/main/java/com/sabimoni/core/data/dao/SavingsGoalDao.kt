package com.sabimoni.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.sabimoni.core.data.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {

    @Upsert
    suspend fun upsert(goal: SavingsGoalEntity): Long

    @Delete
    suspend fun delete(goal: SavingsGoalEntity)

    @Query("SELECT * FROM savings_goals WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun byId(id: Long): SavingsGoalEntity?

    @Query("UPDATE savings_goals SET currentXaf = currentXaf + :amountXaf WHERE id = :id")
    suspend fun addToBalance(id: Long, amountXaf: Long)
}
