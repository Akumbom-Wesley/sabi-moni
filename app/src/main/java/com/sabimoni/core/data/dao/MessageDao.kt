package com.sabimoni.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sabimoni.core.data.entity.MessageEntity
import com.sabimoni.core.data.entity.ParseStatus
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

    @Query("SELECT status FROM messages WHERE id = :id")
    suspend fun statusOf(id: Long): ParseStatus?

    /**
     * Records why the last attempt did not succeed, **without** giving up on the message.
     *
     * `failureReason` therefore means "why the last attempt failed" and `status` says
     * whether we are still trying. Reusing the column beats adding one: a message that is
     * quietly retrying behind an invisible backoff timer is indistinguishable, on screen,
     * from one that is about to be parsed any second — and that is the whole complaint
     * ADR-0021 exists to fix.
     */
    @Query(
        "UPDATE messages SET failureReason = :reason " +
            "WHERE id = :id AND status = 'PENDING_PARSE'",
    )
    suspend fun noteAttemptFailure(id: Long, reason: String)

    /**
     * Puts a failed message back on the queue, returning how many rows that changed.
     *
     * The `status = 'FAILED'` guard lives in SQL rather than in the caller, which makes a
     * second tap on "Try again" a no-op instead of a way to requeue a message that has
     * since been parsed — that would run it through the parser twice and duplicate every
     * entry it produced. See docs/adr/0018-corrections-and-manual-entry.md.
     */
    @Query(
        "UPDATE messages SET status = 'PENDING_PARSE', failureReason = NULL, parsedAt = NULL " +
            "WHERE id = :id AND status = 'FAILED'",
    )
    suspend fun requeueFailed(id: Long): Int
}
