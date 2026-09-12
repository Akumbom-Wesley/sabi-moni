package com.sabimoni.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.sabimoni.core.data.SabiMoniDatabase
import com.sabimoni.core.data.entity.ContributionStatus
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.RecurrenceUnit
import com.sabimoni.core.data.model.Contribution
import com.sabimoni.core.data.model.RecurrenceSchedule
import com.sabimoni.core.money.Money
import com.sabimoni.core.reminder.ObligationScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The contribution lifecycle (FR4.x) over real Room, including recurring schedules.
 * See ADR-0025, ADR-0026 and ADR-0029. The deadline arithmetic itself is covered by the
 * JVM `RecurrenceTest`, which runs without a device.
 */
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

    // --- recurring schedules (ADR-0029) ---------------------------------------

    @Test
    fun rollForwardRecurring_materialisesEachPeriodWithoutTheUserReenteringIt() = runTest {
        groups.createGroup(
            name = "Choir",
            reminderLeadDays = 4,
            recurrence = monthly(amount = 1_000, anchor = LocalDate.of(2026, 6, 26)),
        )

        val created = groups.rollForwardRecurring(today)

        // June, July and August have passed; September's is inside the 4-day lead window.
        assertThat(created).isEqualTo(4)
        assertThat(groups.observeContributions().first().map(Contribution::dueDate))
            .containsExactly(
                LocalDate.of(2026, 6, 26),
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 8, 26),
                LocalDate.of(2026, 9, 26),
            )
    }

    @Test
    fun rollForwardRecurring_usesTheScheduledAmount() = runTest {
        groups.createGroup(
            name = "Choir",
            recurrence = monthly(amount = 1_000, anchor = today),
        )

        groups.rollForwardRecurring(today)

        assertThat(groups.observeContributions().first().single().amount)
            .isEqualTo(Money(1_000))
    }

    @Test
    fun rollForwardRecurring_isIdempotent() = runTest {
        groups.createGroup(
            name = "Choir",
            recurrence = monthly(amount = 1_000, anchor = LocalDate.of(2026, 7, 26)),
        )
        val firstRun = groups.rollForwardRecurring(today)

        val secondRun = groups.rollForwardRecurring(today)
        val thirdRun = groups.rollForwardRecurring(today)

        // Recomputes the series each run and creates only what is missing, so a retry or a
        // catch-up cannot duplicate a period.
        assertThat(firstRun).isGreaterThan(0)
        assertThat(secondRun).isEqualTo(0)
        assertThat(thirdRun).isEqualTo(0)
    }

    @Test
    fun rollForwardRecurring_bringsBackAPeriodTheUserDeleted() = runTest {
        groups.createGroup(
            name = "Choir",
            recurrence = monthly(amount = 1_000, anchor = LocalDate.of(2026, 8, 26)),
        )
        groups.rollForwardRecurring(today)
        val august = groups.observeContributions().first()
            .first { it.dueDate == LocalDate.of(2026, 8, 26) }

        groups.deleteContribution(august.id)
        groups.rollForwardRecurring(today)

        // Pinned as known behaviour rather than endorsed: the schedule still says that
        // period was owed, and suppressing it would need a tombstone (ADR-0029).
        assertThat(groups.observeContributions().first().map(Contribution::dueDate))
            .contains(LocalDate.of(2026, 8, 26))
    }

    @Test
    fun rollForwardRecurring_ignoresAGroupWithNoSchedule() = runTest {
        groups.createGroup(name = "Ad hoc charity")

        assertThat(groups.rollForwardRecurring(today)).isEqualTo(0)
        assertThat(groups.observeContributions().first()).isEmpty()
    }

    @Test
    fun rollForwardRecurring_createsNothingBeforeTheFirstDeadlineIsNear() = runTest {
        groups.createGroup(
            name = "Choir",
            reminderLeadDays = 2,
            recurrence = monthly(amount = 1_000, anchor = today.plusMonths(1)),
        )

        assertThat(groups.rollForwardRecurring(today)).isEqualTo(0)
    }

    @Test
    fun editingAScheduleChangesWhatLaterPeriodsDemand() = runTest {
        val choir = groups.createGroup(
            name = "Choir",
            recurrence = monthly(amount = 1_000, anchor = today),
        )
        groups.rollForwardRecurring(today)

        groups.updateGroup(
            id = choir,
            name = "Choir",
            penalty = null,
            reminderLeadDays = 2,
            recurrence = monthly(amount = 2_000, anchor = today),
        )
        groups.rollForwardRecurring(today.plusMonths(1))

        val amounts = groups.observeContributions().first().associate { it.dueDate to it.amount }
        // Already-materialised periods keep what they demanded at the time; the new amount
        // applies from the next one.
        assertThat(amounts[today]).isEqualTo(Money(1_000))
        assertThat(amounts[today.plusMonths(1)]).isEqualTo(Money(2_000))
    }

    @Test
    fun creatingAScheduledGroupAsksForAnImmediateCheck() = runTest {
        groups.createGroup(
            name = "Choir",
            recurrence = monthly(amount = 1_000, anchor = today),
        )

        // The first obligation should appear now, not tomorrow morning.
        assertThat(scheduler.checks).isAtLeast(1)
    }

    // --- who gets reminded ----------------------------------------------------

    @Test
    fun dueForReminder_picksUpAnObligationOnceItsLeadWindowOpens() = runTest {
        val choir = groups.createGroup(name = "Choir", reminderLeadDays = 4)
        groups.addContribution(choir, Money(5_000), today.plusDays(4))
        groups.addContribution(choir, Money(5_000), today.plusDays(30))

        val due = groups.dueForReminder(today)

        assertThat(due.map(Contribution::dueDate)).containsExactly(today.plusDays(4))
    }

    @Test
    fun dueForReminder_includesAnythingAlreadyOverdue() = runTest {
        val choir = groups.createGroup(name = "Choir", reminderLeadDays = 1)
        groups.addContribution(choir, Money(5_000), today.minusDays(9))

        val due = groups.dueForReminder(today)

        // The daily nag that exists because the fine grows while it is ignored.
        assertThat(due.map(Contribution::dueDate)).containsExactly(today.minusDays(9))
    }

    @Test
    fun dueForReminder_leavesSettledObligationsAlone() = runTest {
        val choir = groups.createGroup(name = "Choir", reminderLeadDays = 4)
        val paid = groups.addContribution(choir, Money(5_000), today.plusDays(2))
        val missed = groups.addContribution(choir, Money(5_000), today.minusDays(2))
        groups.markPaid(paid)
        groups.markMissed(missed)

        assertThat(groups.dueForReminder(today)).isEmpty()
    }

    @Test
    fun dueForReminder_listsEachObligationOnce() = runTest {
        val choir = groups.createGroup(name = "Choir", reminderLeadDays = 7)
        groups.addContribution(choir, Money(5_000), today.minusDays(1))

        // Overdue *and* inside the lead window; it must not be notified about twice.
        assertThat(groups.dueForReminder(today)).hasSize(1)
    }

    // --- the fine (FR4.4) -----------------------------------------------------

    @Test
    fun aMissedDeadlineAddsTheFineToWhatIsOwed() = runTest {
        val choir = groups.createGroup(name = "Choir", penalty = Money(1_000))
        groups.addContribution(choir, Money(1_000), today.minusDays(1))

        val contribution = groups.observeContributions().first().single()

        assertThat(contribution.fineIncurred(today)).isEqualTo(Money(1_000))
        assertThat(contribution.owedOn(today)).isEqualTo(Money(2_000))
    }

    @Test
    fun noFineAppliesBeforeTheDeadlinePasses() = runTest {
        val choir = groups.createGroup(name = "Choir", penalty = Money(1_000))
        groups.addContribution(choir, Money(1_000), today.plusDays(1))

        val contribution = groups.observeContributions().first().single()

        assertThat(contribution.fineIncurred(today)).isNull()
        assertThat(contribution.owedOn(today)).isEqualTo(Money(1_000))
    }

    @Test
    fun noFineAppliesToAGroupThatDoesNotChargeOne() = runTest {
        val choir = groups.createGroup(name = "Choir", penalty = null)
        groups.addContribution(choir, Money(1_000), today.minusDays(5))

        assertThat(groups.observeContributions().first().single().fineIncurred(today)).isNull()
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
        assertThat(scheduler.dismissed).contains(id)
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
    fun markPaid_recordsTheContributionOnlyNotTheFine() = runTest {
        val choir = groups.createGroup(name = "Choir", penalty = Money(1_000))
        val id = groups.addContribution(choir, Money(1_000), today.minusDays(3))

        groups.markPaid(id, paidOn = today)

        // Whether the fine was actually charged is the user's to say, so it is surfaced
        // but never auto-recorded as money spent (ADR-0029).
        assertThat(database.transactionDao().observeRows().first().single().amountXaf)
            .isEqualTo(1_000L)
    }

    @Test
    fun markMissed_writesNoTransactionBecauseNothingMoved() = runTest {
        val choir = groups.createGroup(name = "Choir")
        val id = groups.addContribution(choir, Money(5_000), today)

        groups.markMissed(id)

        assertThat(database.transactionDao().observeRows().first()).isEmpty()
        assertThat(database.groupContributionDao().byId(id)!!.status)
            .isEqualTo(ContributionStatus.MISSED)
        assertThat(scheduler.dismissed).contains(id)
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

        transactions.deleteEntries(setOf(payment))

        // Otherwise "what I owe" would keep claiming it was settled (ADR-0026).
        val contribution = groups.contribution(id)!!
        assertThat(contribution.status).isEqualTo(ContributionStatus.PENDING)
        assertThat(contribution.paidDate).isNull()
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
    fun deleteContribution_takesDownItsNotification() = runTest {
        val choir = groups.createGroup(name = "Choir")
        val id = groups.addContribution(choir, Money(5_000), today.plusDays(2))

        groups.deleteContribution(id)

        assertThat(groups.contribution(id)).isNull()
        assertThat(scheduler.dismissed).contains(id)
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
    fun aGroupRoundTripsItsSchedule() = runTest {
        val schedule = monthly(amount = 1_000, anchor = LocalDate.of(2026, 9, 26))
        groups.createGroup(name = "Choir", recurrence = schedule)

        assertThat(groups.observeGroups().first().single().recurrence).isEqualTo(schedule)
    }

    @Test
    fun aGroupWithNoScheduleHasNoRecurrence() = runTest {
        groups.createGroup(name = "Ad hoc charity")

        assertThat(groups.observeGroups().first().single().recurrence).isNull()
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

    private fun monthly(amount: Long, anchor: LocalDate) = RecurrenceSchedule(
        unit = RecurrenceUnit.MONTHLY,
        amount = Money(amount),
        anchor = anchor,
    )

    private class RecordingScheduler : ObligationScheduler {
        val dismissed = mutableListOf<Long>()
        var checks = 0
            private set

        override fun ensureDailyCheck() = Unit

        override fun checkNow() {
            checks++
        }

        override fun dismiss(contributionId: Long) {
            dismissed += contributionId
        }
    }
}
