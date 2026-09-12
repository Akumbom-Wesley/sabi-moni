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

    /** Groups with a standing commitment, for the daily roll-forward (ADR-0029). */
    @Query(
        "SELECT * FROM money_groups " +
            "WHERE isArchived = 0 AND recurrenceUnit IS NOT NULL " +
            "AND recurrenceAmountXaf IS NOT NULL AND recurrenceAnchor IS NOT NULL",
    )
    suspend fun recurring(): List<GroupEntity>

    @Query("SELECT * FROM money_groups WHERE id = :id")
    suspend fun byId(id: Long): GroupEntity?

    /**
     * Case-insensitive because the group name comes back from a language model, which is
     * under no obligation to match the stored casing — the same reasoning as
     * [CategoryDao.byNameIgnoreCase]. Resolves against existing groups only; a name that
     * matches nothing stays unattributed rather than creating a group (ADR-0017).
     */
    @Query("SELECT * FROM money_groups WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byNameIgnoreCase(name: String): GroupEntity?
}
