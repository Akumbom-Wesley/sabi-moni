package com.sabimoni.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.sabimoni.core.data.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Upsert
    suspend fun upsert(group: GroupEntity): Long

    @Delete
    suspend fun delete(group: GroupEntity)

    @Query("SELECT * FROM money_groups WHERE isArchived = 0 ORDER BY name ASC")
    fun observeActive(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM money_groups WHERE isArchived = 0 ORDER BY name ASC")
    suspend fun active(): List<GroupEntity>

    @Query("SELECT * FROM money_groups WHERE id = :id")
    suspend fun byId(id: Long): GroupEntity?
}
