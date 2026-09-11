package com.sabimoni.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.sabimoni.core.data.SabiMoniDatabase
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.MessageEntity
import com.sabimoni.core.data.entity.MessageSource
import com.sabimoni.core.data.entity.ParseStatus
import com.sabimoni.core.data.entity.TransactionEntity
import com.sabimoni.core.data.model.ThreadItem
import com.sabimoni.core.money.Money
import com.sabimoni.core.parse.ParseScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** The merged capture thread (FR1.5) and retry, over real Room. See ADR-0018. */
@RunWith(AndroidJUnit4::class)
class CaptureRepositoryTest {

    private val today = LocalDate.of(2026, 9, 10)
    private val clock = TickingClock(today.atTime(21, 0).toInstant(ZoneOffset.UTC))
    private val scheduler = RecordingScheduler()

    private lateinit var database: SabiMoniDatabase
    private lateinit var repository: CaptureRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SabiMoniDatabase::class.java,
        ).build()

        repository = CaptureRepository(
            messageDao = database.messageDao(),
            transactionDao = database.transactionDao(),
            parseScheduler = scheduler,
            clock = clock,
        )
    }

    @After
    fun tearDown() = database.close()

    // --- the thread (FR1.5) ----------------------------------------------------

    @Test
    fun observeThread_attachesParsedLinesToTheirMessage() = runTest {
        val messageId = repository.capture("taxi 500, lunch 1500")
        insertEntry(messageId = messageId, amountXaf = 500)
        insertEntry(messageId = messageId, amountXaf = 1_500)

        val items = repository.observeThread().first()

        val captured = items.single() as ThreadItem.Captured
        assertThat(captured.message.lineItems.map { it.amount })
            .containsExactly(Money(500), Money(1_500))
    }

    @Test
    fun observeThread_showsAManualEntryInItsOwnRight() = runTest {
        insertEntry(messageId = null, amountXaf = 1_500)

        val items = repository.observeThread().first()

        // An entry that is invisible where you logged it reads as a lost entry (ADR-0018).
        val manual = items.single() as ThreadItem.Manual
        assertThat(manual.entry.amount).isEqualTo(Money(1_500))
        assertThat(manual.entry.occurredOn).isEqualTo(today)
    }

    @Test
    fun observeThread_ordersMessagesAndManualEntriesByWhenTheyWereLogged() = runTest {
        repository.capture("first, typed")
        clock.advance(60)
        insertEntry(messageId = null, amountXaf = 1_500)
        clock.advance(60)
        repository.capture("third, typed")

        val items = repository.observeThread().first()

        assertThat(items).hasSize(3)
        assertThat(items.map { it::class }).containsExactly(
            ThreadItem.Captured::class,
            ThreadItem.Manual::class,
            ThreadItem.Captured::class,
        ).inOrder()
    }

    @Test
    fun observeThread_givesEveryItemADistinctKey() = runTest {
        // Message 1 and transaction 1 both exist: an id alone would collide in LazyColumn.
        val messageId = repository.capture("taxi 500")
        insertEntry(messageId = messageId, amountXaf = 500)
        insertEntry(messageId = null, amountXaf = 1_500)

        val keys = repository.observeThread().first().map { it.key }

        assertThat(keys).containsNoDuplicates()
    }

    // --- retry -----------------------------------------------------------------

    @Test
    fun retry_requeuesAFailedMessageAndAsksForADrain() = runTest {
        val id = insertMessage(ParseStatus.FAILED, failureReason = "AI key was rejected")
        scheduler.reset()

        repository.retry(id)

        val stored = database.messageDao().byId(id)!!
        assertThat(stored.status).isEqualTo(ParseStatus.PENDING_PARSE)
        assertThat(stored.failureReason).isNull()
        assertThat(scheduler.immediateCalls).isEqualTo(1)
    }

    @Test
    fun retry_isANoOpOnAMessageThatIsAlreadyParsed() = runTest {
        val id = insertMessage(ParseStatus.PARSED)
        scheduler.reset()

        repository.retry(id)

        // The guard that makes a double tap harmless: re-parsing a parsed message would
        // duplicate every entry it produced (ADR-0018).
        assertThat(database.messageDao().byId(id)!!.status).isEqualTo(ParseStatus.PARSED)
        assertThat(scheduler.calls).isEqualTo(0)
        assertThat(scheduler.immediateCalls).isEqualTo(0)
    }

    @Test
    fun retry_runsAPendingMessageImmediatelyRatherThanWaitingOutTheBackoff() = runTest {
        val id = insertMessage(ParseStatus.PENDING_PARSE)
        scheduler.reset()

        repository.retry(id)

        // "Try now" on a message whose last attempt failed — ADR-0021. It stays pending;
        // what changes is that the run happens now instead of behind a hidden timer.
        assertThat(database.messageDao().byId(id)!!.status).isEqualTo(ParseStatus.PENDING_PARSE)
        assertThat(scheduler.immediateCalls).isEqualTo(1)
    }

    @Test
    fun noteAttemptFailure_recordsTheReasonWithoutGivingUpOnTheMessage() = runTest {
        val id = insertMessage(ParseStatus.PENDING_PARSE)

        database.messageDao().noteAttemptFailure(id, "No connection")

        // The pair the thread reads to say "still trying — No connection" (ADR-0021).
        val stored = database.messageDao().byId(id)!!
        assertThat(stored.status).isEqualTo(ParseStatus.PENDING_PARSE)
        assertThat(stored.failureReason).isEqualTo("No connection")
        // Still on the queue, so the next run picks it up.
        assertThat(database.messageDao().pendingParse().map { it.id }).contains(id)
    }

    @Test
    fun noteAttemptFailure_cannotReopenAMessageThatHasSinceBeenParsed() = runTest {
        val id = insertMessage(ParseStatus.PARSED)

        database.messageDao().noteAttemptFailure(id, "No connection")

        assertThat(database.messageDao().byId(id)!!.failureReason).isNull()
    }

    // --- helpers ---------------------------------------------------------------

    private suspend fun insertMessage(
        status: ParseStatus,
        failureReason: String? = null,
    ): Long = database.messageDao().insert(
        MessageEntity(
            rawText = "taxi 500",
            source = MessageSource.TYPED,
            sentAt = Instant.now(clock),
            status = status,
            failureReason = failureReason,
        ),
    )

    private suspend fun insertEntry(messageId: Long?, amountXaf: Long): Long =
        database.transactionDao().insert(
            TransactionEntity(
                messageId = messageId,
                occurredOn = today,
                amountXaf = amountXaf,
                direction = Direction.EXPENSE,
                createdAt = Instant.now(clock),
            ),
        )

    private class RecordingScheduler : ParseScheduler {
        var calls = 0
            private set

        /** Counted apart, because "run it now" and "queue it" are different promises. */
        var immediateCalls = 0
            private set

        override fun schedule() {
            calls++
        }

        override fun scheduleNow() {
            immediateCalls++
        }

        fun reset() {
            calls = 0
            immediateCalls = 0
        }
    }

    /** A clock the test can step forward, so "logged later" is a real ordering. */
    private class TickingClock(private var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = current

        fun advance(seconds: Long) {
            current = current.plusSeconds(seconds)
        }
    }
}
