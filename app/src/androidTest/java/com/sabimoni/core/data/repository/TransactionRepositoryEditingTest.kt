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
import com.sabimoni.core.money.Money
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

/**
 * Corrections (FR1.4), form entry (FR1.6) and the running total (FR1.5) over real Room.
 * See docs/adr/0018-corrections-and-manual-entry.md.
 */
@RunWith(AndroidJUnit4::class)
class TransactionRepositoryEditingTest {

    private val today = LocalDate.of(2026, 9, 10)
    private val clock = Clock.fixed(
        today.atTime(21, 15).toInstant(ZoneOffset.UTC),
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

        repository = database.transactionRepository(clock)
    }

    @After
    fun tearDown() = database.close()

    // --- corrections (FR1.4) ---------------------------------------------------

    @Test
    fun correctEntry_changesTheAmountCategoryDirectionDateAndNote() = runTest {
        val transport = database.categoryDao().upsert(CategoryEntity(name = "Transport"))
        val food = database.categoryDao().upsert(CategoryEntity(name = "Food"))
        val message = pendingMessage("taxi 5000")
        repository.commitParse(
            message,
            listOf(draft(amountXaf = 5000, category = "Transport", note = "taxi")),
        )
        val original = database.transactionDao().observeForMessage(message.id).first().single()
        assertThat(original.categoryId).isEqualTo(transport)

        repository.correctEntry(
            id = original.id,
            amount = Money(500),
            direction = Direction.INCOME,
            categoryId = food,
            groupId = null,
            note = "actually lunch money back",
            occurredOn = today.minusDays(1),
        )

        val corrected = database.transactionDao().byId(original.id)!!
        assertThat(corrected.amountXaf).isEqualTo(500L)
        assertThat(corrected.direction).isEqualTo(Direction.INCOME)
        assertThat(corrected.categoryId).isEqualTo(food)
        assertThat(corrected.note).isEqualTo("actually lunch money back")
        assertThat(corrected.occurredOn).isEqualTo(today.minusDays(1))
    }

    @Test
    fun correctEntry_leavesHowTheEntryArrivedAlone() = runTest {
        val message = pendingMessage("You have received 10000 XAF", MessageSource.SMS)
        repository.commitParse(message, listOf(draft(amountXaf = 10_000)))
        val original = database.transactionDao().observeForMessage(message.id).first().single()

        repository.correctEntry(
            id = original.id,
            amount = Money(9_000),
            direction = Direction.INCOME,
            categoryId = null,
            groupId = null,
            note = null,
            occurredOn = today,
        )

        // Provenance is history, not something a correction may rewrite (ADR-0018).
        val corrected = database.transactionDao().byId(original.id)!!
        assertThat(corrected.messageId).isEqualTo(message.id)
        assertThat(corrected.autoDetected).isTrue()
        assertThat(corrected.createdAt).isEqualTo(original.createdAt)
    }

    @Test
    fun correctEntry_doesNotReopenTheMessageForParsing() = runTest {
        val message = pendingMessage("taxi 500")
        repository.commitParse(message, listOf(draft()))
        val original = database.transactionDao().observeForMessage(message.id).first().single()

        repository.correctEntry(
            id = original.id,
            amount = Money(600),
            direction = Direction.EXPENSE,
            categoryId = null,
            groupId = null,
            note = null,
            occurredOn = today,
        )

        // v1 corrections are taps, not dialogue (spec §6) — nothing goes back to the model.
        val stored = database.messageDao().byId(message.id)!!
        assertThat(stored.status).isEqualTo(ParseStatus.PARSED)
        assertThat(stored.rawText).isEqualTo("taxi 500")
    }

    @Test
    fun correctEntry_treatsABlankNoteAsNoNote() = runTest {
        val id = repository.addManualEntry(
            amount = Money(500),
            direction = Direction.EXPENSE,
            categoryId = null,
            groupId = null,
            note = "something",
            occurredOn = today,
        )

        repository.correctEntry(
            id = id,
            amount = Money(500),
            direction = Direction.EXPENSE,
            categoryId = null,
            groupId = null,
            note = "   ",
            occurredOn = today,
        )

        assertThat(database.transactionDao().byId(id)!!.note).isNull()
    }

    @Test
    fun deleteEntries_removesTheLineAndLeavesTheMessage() = runTest {
        val message = pendingMessage("taxi 500 and a goat 40000")
        repository.commitParse(message, listOf(draft(amountXaf = 500), draft(amountXaf = 40_000)))
        val lines = database.transactionDao().observeForMessage(message.id).first()

        repository.deleteEntries(setOf(lines.first().id))

        val remaining = database.transactionDao().observeForMessage(message.id).first()
        assertThat(remaining.map { it.amountXaf }).containsExactly(40_000L)
        assertThat(database.messageDao().byId(message.id)).isNotNull()
    }

    @Test
    fun deleteEntries_removesAWholeSelectionAndLeavesTheRest() = runTest {
        val doomed = listOf(
            repository.addManualEntry(Money(500), Direction.EXPENSE, null, null, null, today),
            repository.addManualEntry(Money(1_500), Direction.EXPENSE, null, null, null, today),
        )
        val survivor = repository.addManualEntry(
            amount = Money(300),
            direction = Direction.EXPENSE,
            categoryId = null,
            groupId = null,
            note = null,
            occurredOn = today,
        )

        repository.deleteEntries(doomed.toSet())

        val remaining = database.transactionDao().observeRows().first()
        assertThat(remaining.map { it.id }).containsExactly(survivor)
    }

    @Test
    fun deleteEntries_takesTheDeletedAmountsOutOfTheDayTotal() = runTest {
        repository.addManualEntry(Money(20_000), Direction.INCOME, null, null, null, today)
        val lunch = repository.addManualEntry(Money(1_500), Direction.EXPENSE, null, null, null, today)
        val taxi = repository.addManualEntry(Money(500), Direction.EXPENSE, null, null, null, today)
        assertThat(repository.observeDayTotals(today).first().net).isEqualTo(Money(18_000))

        repository.deleteEntries(setOf(lunch, taxi))

        // What the selection bar promises: deleting recalculates the day (ADR-0019).
        val totals = repository.observeDayTotals(today).first()
        assertThat(totals.expense).isEqualTo(Money.ZERO)
        assertThat(totals.net).isEqualTo(Money(20_000))
    }

    @Test
    fun deleteEntries_doesNothingForAnEmptySelection() = runTest {
        repository.addManualEntry(Money(500), Direction.EXPENSE, null, null, null, today)

        repository.deleteEntries(emptySet())

        assertThat(database.transactionDao().observeRows().first()).hasSize(1)
    }

    // --- form entry (FR1.6) ----------------------------------------------------

    @Test
    fun addManualEntry_writesAStandaloneEntryWithNoMessage() = runTest {
        val food = database.categoryDao().upsert(CategoryEntity(name = "Food"))

        val id = repository.addManualEntry(
            amount = Money(1_500),
            direction = Direction.EXPENSE,
            categoryId = food,
            groupId = null,
            note = "lunch",
            occurredOn = today,
        )

        val written = database.transactionDao().byId(id)!!
        assertThat(written.messageId).isNull()
        assertThat(written.amountXaf).isEqualTo(1_500L)
        assertThat(written.categoryId).isEqualTo(food)
        assertThat(written.autoDetected).isFalse()
        assertThat(written.createdAt).isEqualTo(Instant.now(clock))
    }

    @Test
    fun addManualEntry_showsUpInTheRowsTheThreadIsBuiltFrom() = runTest {
        repository.addManualEntry(
            amount = Money(1_500),
            direction = Direction.EXPENSE,
            categoryId = null,
            groupId = null,
            note = "lunch",
            occurredOn = today,
        )

        val rows = database.transactionDao().observeRows().first()
        assertThat(rows).hasSize(1)
        assertThat(rows.single().messageId).isNull()
    }

    // --- the running total (FR1.5) ---------------------------------------------

    @Test
    fun observeDayTotals_sumsIncomeAndExpenseSeparately() = runTest {
        repository.addManualEntry(Money(20_000), Direction.INCOME, null, null, null, today)
        repository.addManualEntry(Money(1_500), Direction.EXPENSE, null, null, null, today)
        repository.addManualEntry(Money(500), Direction.EXPENSE, null, null, null, today)

        val totals = repository.observeDayTotals(today).first()

        assertThat(totals.income).isEqualTo(Money(20_000))
        assertThat(totals.expense).isEqualTo(Money(2_000))
        assertThat(totals.net).isEqualTo(Money(18_000))
    }

    @Test
    fun observeDayTotals_countsParsedManualAndSmsEntriesAlike() = runTest {
        val typed = pendingMessage("lunch 1500")
        repository.commitParse(typed, listOf(draft(amountXaf = 1_500)))
        val sms = pendingMessage("You have received 10000 XAF", MessageSource.SMS)
        repository.commitParse(sms, listOf(draft(amountXaf = 10_000)))
        repository.addManualEntry(Money(500), Direction.EXPENSE, null, null, null, today)

        val totals = repository.observeDayTotals(today).first()

        // commitParse dates undated drafts to today, so all three land on the same day.
        assertThat(totals.expense).isEqualTo(Money(12_000))
    }

    @Test
    fun observeDayTotals_ignoresOtherDays() = runTest {
        repository.addManualEntry(Money(9_000), Direction.EXPENSE, null, null, null, today.minusDays(1))
        repository.addManualEntry(Money(500), Direction.EXPENSE, null, null, null, today)

        assertThat(repository.observeDayTotals(today).first().expense).isEqualTo(Money(500))
    }

    @Test
    fun observeDayTotals_isZeroForADayWithNothingOnIt() = runTest {
        val totals = repository.observeDayTotals(today).first()

        assertThat(totals.income).isEqualTo(Money.ZERO)
        assertThat(totals.expense).isEqualTo(Money.ZERO)
        assertThat(totals.net).isEqualTo(Money.ZERO)
    }

    @Test
    fun observeEditorOptions_offersOnlyActiveCategories() = runTest {
        database.categoryDao().upsert(CategoryEntity(name = "Food"))
        database.categoryDao().upsert(CategoryEntity(name = "Retired", isArchived = true))

        val options = repository.observeEditorOptions().first()

        assertThat(options.categories.map { it.name }).containsExactly("Food")
    }

    @Test
    fun observeEditorOptions_offersTheGroupsThePickerCanAttributeTo() = runTest {
        database.groupRepository(clock).createGroup(name = "Choir")

        val options = repository.observeEditorOptions().first()

        // FR1.4's group field, which had no schema to sit on until ADR-0025.
        assertThat(options.groups.map { it.name }).containsExactly("Choir")
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
