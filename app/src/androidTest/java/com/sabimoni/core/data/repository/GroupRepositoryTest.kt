package com.sabimoni.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.sabimoni.core.data.SabiMoniDatabase
import com.sabimoni.core.data.entity.ContributionStatus
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.GroupType
import com.sabimoni.core.money.Money
import com.sabimoni.core.reminder.ReminderScheduler
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

/** The contribution lifecycle (FR4.x) over real Room. See ADR-0025 and ADR-0026. */
@RunWith(AndroidJUnit4::class)
class GroupRepositoryTest {

    private val today = LocalDate.of(2026, 9, 11)
    private val clock = Clock.fixed(
        today.atTime(10, 0).toInstant(ZoneOffset.UTC),
        ZoneOffset.UTC,
    )
    private val scheduler = RecordingScheduler()

    private lateinit var database: SabiMoniDatabase
    private lateinit var groups: GroupRepository
    private lateinit var transactions: TransactionRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SabiMoniDatabase::class.java,
        ).build()

        groups = database.groupRepository(clock, scheduler)
        transactions = database.transactionRepository(clock, groups)
    }

    @After
    fun tearDown() = database.close()

    // --- creating obligations (FR4.2, FR4.3) ----------------------------------

    @Test
    fun addContribution_armsAReminderAtTheGroupsLeadTime() = runTest {
        val choir = groups.createGroup(
            name = "Choir",
            penalty = Money(2_000),
            reminderLeadDays = 3,
        )

        val id = groups.addContribution(choir, Money(5_000), today.plusDays(10))

        // Three days before the due date, at 09:00 local — not the moment of scheduling.
        val expected = today.plusDays(7).atTime(9, 0).toInstant(ZoneOffset.UTC)
        assertThat(scheduler.scheduledAt(id)).isEqualTo(expected)
    }

    @Test
    fun addContribution_stillArmsAReminderForADueDateAlreadyPast() = runTest {
        val choir = groups.createGroup(name = "Choir", reminderLeadDays = 2)

        val id = groups.addContribution(choir, Money(5_000), today.minusDays(3))

        // Relayed late is exactly when a reminder is most useful, so it is not dropped.
        assertThat(scheduler.scheduledAt(id)).isNotNull()
    }

    @Test
    fun updateContribution_movesTheReminderWithTheDueDate() = runTest {
        val choir = groups.createGroup(name = "Choir", reminderLeadDays = 1)
        val id = groups.addContribution(choir, Money(5_000), today.plusDays(5))
        val original = scheduler.scheduledAt(id)

        groups.updateContribution(id, Money(5_000), today.plusDays(20), note = null)

        assertThat(scheduler.scheduledAt(id)).isNotEqualTo(original)
    }

    @Test
    fun updateGroup_rearmsRemindersWhenTheLeadTimeChanges() = runTest {
        val choir = groups.createGroup(name = "Choir", reminderLeadDays = 1)
        val id = groups.addContribution(choir, Money(5_000), today.plusDays(10))
        val original = scheduler.scheduledAt(id)

        groups.updateGroup(choir, "Choir", GroupType.CONTRIBUTION, null, reminderLeadDays = 7)

        // A lead time that only applied to future obligations would silently not work.
        assertThat(scheduler.scheduledAt(id)).isNotEqualTo(original)
    }

    // --- settling (FR4.5) -----------------------------------------------------

    @Test
    fun markPaid_recordsTheTransactionAndClearsItFromWhatIsOwed() = runTest {
        val choir = groups.createGroup(name = "Choir")
        val id = groups.addContribution(choir, Money(5_000), today.plusDays(2))

        groups.markPaid(id, paidOn = today)

        val written = database.transactionDao().observeRows().first()
        assertThat(written).hasSize(1)
        with(written.single()) {
            assertThat(amountXaf).isEqualTo(5_000L)
            assertThat(direction).isEqualTo(Direction.EXPENSE)
            // Derived from the contribution, never passed in (ADR-0025).
            assertThat(groupId).isEqualTo(choir)
            assertThat(groupContributionId).isEqualTo(id)
            // The group axis carries this; stamping a category too would duplicate it.
            assertThat(categoryId).isNull()
        }
        assertThat(groups.observeContributions().first().filter { it.isOutstanding }).isEmpty()
        assertThat(scheduler.cancelled).contains(id)
    }

    @Test
    fun markPaid_keepsTheTwoGroupReferencesAgreeing() = runTest {
        val choir = groups.createGroup(name = "Choir")
        val id = groups.addContribution(choir, Money(5_000), today)

        groups.markPaid(id)

        val row = database.transactionDao().observeRows().first().single()
        val contribution = database.groupContributionDao().byId(id)!!
        assertThat(row.groupId).isEqualTo(contribution.groupId)
    }

    @Test
    fun markMissed_writesNoTransactionBecauseNothingMoved() = runTest {
        val choir = groups.createGroup(name = "Choir")
        val id = groups.addContribution(choir, Money(5_000), today)

        groups.markMissed(id)

        assertThat(database.transactionDao().observeRows().first()).isEmpty()
        assertThat(database.groupContributionDao().byId(id)!!.status)
            .isEqualTo(ContributionStatus.MISSED)
        assertThat(scheduler.cancelled).contains(id)
    }

    @Test
    fun markPaid_afterTheDueDateIsRecordedAsLate() = runTest {
        val choir = groups.createGroup(name = "Choir")
        val id = groups.addContribution(choir, Money(5_000), today.minusDays(4))

        groups.markPaid(id, paidOn = today)

        // FR4.5 wants on-time distinguished from late.
        assertThat(groups.contribution(id)!!.wasLate).isTrue()
    }

    @Test
    fun markPaid_onOrBeforeTheDueDateIsNotLate() = runTest {
        val choir = groups.createGroup(name = "Choir")
        val id = groups.addContribution(choir, Money(5_000), today.plusDays(1))

        groups.markPaid(id, paidOn = today)

        assertThat(groups.contribution(id)!!.wasLate).isFalse()
    }

    // --- deleting the payment (ADR-0026) --------------------------------------

    @Test
    fun deletingThePaymentPutsTheObligationBackOnTheBooks() = runTest {
        val choir = groups.createGroup(name = "Choir")
        val id = groups.addContribution(choir, Money(5_000), today.plusDays(2))
        groups.markPaid(id)
        val payment = database.transactionDao().observeRows().first().single().id
        scheduler.reset()

        transactions.deleteEntries(setOf(payment))

        // Otherwise "what I owe" would keep claiming it was settled (ADR-0026).
        val contribution = groups.contribution(id)!!
        assertThat(contribution.status).isEqualTo(ContributionStatus.PENDING)
        assertThat(contribution.paidDate).isNull()
        assertThat(scheduler.scheduledAt(id)).isNotNull()
    }

    @Test
    fun deletingAnUnrelatedEntryLeavesObligationsAlone() = runTest {
        val choir = groups.createGroup(name = "Choir")
        val id = groups.addContribution(choir, Money(5_000), today.plusDays(2))
        groups.markPaid(id)
        val unrelated = transactions.addManualEntry(
            amount = Money(500),
            direction = Direction.EXPENSE,
            categoryId = null,
            groupId = null,
            note = "taxi",
            occurredOn = today,
        )

        transactions.deleteEntries(setOf(unrelated))

        assertThat(groups.contribution(id)!!.status).isEqualTo(ContributionStatus.PAID)
    }

    @Test
    fun anObligationPaidTwiceSurvivesLosingOneOfThePayments() = runTest {
        val choir = groups.createGroup(name = "Choir")
        val id = groups.addContribution(choir, Money(5_000), today.plusDays(2))
        groups.markPaid(id)
        // A second payment against the same obligation — the instalment case ADR-0025
        // deliberately left possible.
        groups.markPaid(id)
        val payments = database.transactionDao().observeRows().first().map { it.id }

        transactions.deleteEntries(setOf(payments.first()))

        assertThat(groups.contribution(id)!!.status).isEqualTo(ContributionStatus.PAID)
    }

    @Test
    fun deleteContribution_cancelsItsReminder() = runTest {
        val choir = groups.createGroup(name = "Choir")
        val id = groups.addContribution(choir, Money(5_000), today.plusDays(2))

        groups.deleteContribution(id)

        assertThat(groups.contribution(id)).isNull()
        assertThat(scheduler.cancelled).contains(id)
    }

    // --- what I owe (FR4.6) ---------------------------------------------------

    @Test
    fun observeContributions_carriesTheGroupNameAndPenalty() = runTest {
        val choir = groups.createGroup(name = "Choir", penalty = Money(2_000))
        groups.addContribution(choir, Money(5_000), today.plusDays(2))

        val contribution = groups.observeContributions().first().single()

        assertThat(contribution.groupName).isEqualTo("Choir")
        assertThat(contribution.penalty).isEqualTo(Money(2_000))
    }

    @Test
    fun overdueIsRelativeToTheDayBeingAskedAbout() = runTest {
        val choir = groups.createGroup(name = "Choir")
        groups.addContribution(choir, Money(5_000), today.minusDays(1))

        val contribution = groups.observeContributions().first().single()

        assertThat(contribution.isOverdue(today)).isTrue()
        assertThat(contribution.isOverdue(today.minusDays(5))).isFalse()
    }

    // --- helpers --------------------------------------------------------------

    private class RecordingScheduler : ReminderScheduler {
        private val scheduled = mutableMapOf<Long, Instant>()
        val cancelled = mutableListOf<Long>()

        override fun schedule(contributionId: Long, at: Instant) {
            scheduled[contributionId] = at
        }

        override fun cancel(contributionId: Long) {
            cancelled += contributionId
            scheduled -= contributionId
        }

        fun scheduledAt(contributionId: Long): Instant? = scheduled[contributionId]

        fun reset() {
            scheduled.clear()
            cancelled.clear()
        }
    }
}
