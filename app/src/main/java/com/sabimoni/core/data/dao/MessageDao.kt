package com.sabimoni.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sabimoni.core.data.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface MessageDao {

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: Long): MessageEntity?

    @Query("SELECT * FROM messages ORDER BY sentAt ASC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE status = 'PENDING_PARSE' ORDER BY sentAt ASC")
    suspend fun pendingParse(): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'PENDING_PARSE'")
    fun observePendingCount(): Flow<Int>

    @Query(
        "UPDATE messages SET status = 'PARSED', parsedAt = :parsedAt, failureReason = NULL " +
            "WHERE id = :id",
    )
    suspend fun markParsed(id: Long, parsedAt: Instant)

    @Query("UPDATE messages SET status = 'FAILED', failureReason = :reason WHERE id = :id")
    suspend fun markFailed(id: Long, reason: String)
}
