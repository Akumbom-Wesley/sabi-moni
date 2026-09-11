package com.sabimoni.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.sabimoni.core.ai.TransactionDraft
import com.sabimoni.core.data.SabiMoniDatabase
import com.sabimoni.core.data.entity.CategoryEntity
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.MessageEntity
import com.sabimoni.core.data.entity.MessageSource
import com.sabimoni.core.data.entity.ParseStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class TransactionRepositoryTest {

    private val today = LocalDate.of(2026, 9, 9)
    private val clock = Clock.fixed(
        today.atTime(20, 30).toInstant(ZoneOffset.UTC),
        ZoneOffset.UTC,
    )

    private lateinit var database: SabiMoniDatabase
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SabiMoniDatabase::class.java,
        ).build()

        repository = TransactionRepository(
            database = database,
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            messageDao = database.messageDao(),
            clock = clock,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun commitParse_writesEntriesAndMarksTheMessageParsed() = runTest {
        database.categoryDao().insertAll(listOf(CategoryEntity(name = "Transport")))
        val message = pendingMessage("took a taxi for 500")

        repository.commitParse(
            message,
            listOf(draft(amountXaf = 500, category = "Transport", note = "taxi to work")),
        )

        val written = database.transactionDao().observeForMessage(message.id).first()
        assertThat(written).hasSize(1)
        assertThat(written.single().amountXaf).isEqualTo(500L)
        assertThat(written.single().occurredOn).isEqualTo(today)
        assertThat(database.messageDao().byId(message.id)!!.status)
            .isEqualTo(ParseStatus.PARSED)
    }

    @Test
    fun commitParse_resolvesCategoryNamesRegardlessOfCasing() = runTest {
        val categoryId = database.categoryDao().upsert(CategoryEntity(name = "Transport"))
        val message = pendingMessage("taxi 500")

        repository.commitParse(message, listOf(draft(category = "transport")))

        val written = database.transactionDao().observeForMessage(message.id).first()
        assertThat(written.single().categoryId).isEqualTo(categoryId)
    }

    @Test
    fun commitParse_leavesAnUnknownCategoryUnsetRatherThanInventingOne() = runTest {
        database.categoryDao().insertAll(listOf(CategoryEntity(name = "Transport")))
        val message = pendingMessage("bought a goat for 40000")

        repository.commitParse(message, listOf(draft(category = "Livestock")))

        val written = database.transactionDao().observeForMessage(message.id).first()
        assertThat(written.single().categoryId).isNull()
        // The whole point of ADR-0017: no new taxonomy appears behind the user's back.
        assertThat(database.categoryDao().active().map { it.name })
            .containsExactly("Transport")
    }

    @Test
    fun commitParse_dropsNonPositiveAmounts() = runTest {
        val message = pendingMessage("some nonsense")

        repository.commitParse(
            message,
            listOf(draft(amountXaf = 0), draft(amountXaf = -100), draft(amountXaf = 250)),
        )

        val written = database.transactionDao().observeForMessage(message.id).first()
        assertThat(written.map { it.amountXaf }).containsExactly(250L)
    }

    @Test
    fun commitParse_marksAMessageWithNothingToLogAsParsedNotFailed() = runTest {
        val message = pendingMessage("happy birthday mum")

        repository.commitParse(message, emptyList())

        assertThat(database.transactionDao().observeForMessage(message.id).first()).isEmpty()
        assertThat(database.messageDao().byId(message.id)!!.status)
            .isEqualTo(ParseStatus.PARSED)
    }

    @Test
    fun commitParse_flagsSmsSourcedEntriesAsAutoDetected() = runTest {
        val message = pendingMessage("You have received 10000 XAF", MessageSource.SMS)

        repository.commitParse(message, listOf(draft(amountXaf = 10_000)))

        val written = database.transactionDao().observeForMessage(message.id).first()
        assertThat(written.single().autoDetected).isTrue()
    }

    @Test
    fun commitParse_keepsTheAmountOutOfTheNote() = runTest {
        val message = pendingMessage("Sendt 1600 to my girlfriend")

        repository.commitParse(
            message,
            listOf(draft(amountXaf = 1600, note = "sent 1600 to my girlfriend")),
        )

        // Two copies of the amount diverge the moment one is corrected (ADR-0020).
        val written = database.transactionDao().observeForMessage(message.id).first()
        assertThat(written.single().note).isEqualTo("sent to my girlfriend")
    }

    @Test
    fun commitParse_keepsAnExplicitDateFromTheParser() = runTest {
        val yesterday = today.minusDays(1)
        val message = pendingMessage("yesterday I spent 300 on bread")

        repository.commitParse(message, listOf(draft(occurredOn = yesterday)))

        val written = database.transactionDao().observeForMessage(message.id).first()
        assertThat(written.single().occurredOn).isEqualTo(yesterday)
    }

    // --- helpers ---------------------------------------------------------------

    private suspend fun pendingMessage(
        text: String,
        source: MessageSource = MessageSource.TYPED,
    ): MessageEntity {
        val entity = MessageEntity(
            rawText = text,
            source = source,
            sentAt = Instant.now(clock),
            status = ParseStatus.PENDING_PARSE,
        )
        val id = database.messageDao().insert(entity)
        return entity.copy(id = id)
    }

    private fun draft(
        amountXaf: Long = 500,
        category: String? = null,
        note: String? = null,
        occurredOn: LocalDate? = null,
    ) = TransactionDraft(
        amountXaf = amountXaf,
        direction = Direction.EXPENSE,
        category = category,
        note = note,
        occurredOn = occurredOn,
    )
}
