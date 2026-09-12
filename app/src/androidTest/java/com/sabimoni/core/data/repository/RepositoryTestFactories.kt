package com.sabimoni.core.data.repository

import com.sabimoni.core.data.SabiMoniDatabase
import com.sabimoni.core.reminder.ObligationScheduler
import java.time.Clock

/**
 * Wiring shared by the repository tests. The repositories take a lot of DAOs, and three
 * copies of the same constructor call is three places to update every time one is added.
 */
internal fun SabiMoniDatabase.groupRepository(
    clock: Clock,
    obligations: ObligationScheduler = NoOpObligationScheduler,
) = GroupRepository(
    database = this,
    groupDao = groupDao(),
    contributionDao = groupContributionDao(),
    transactionDao = transactionDao(),
    obligations = obligations,
    clock = clock,
)

internal fun SabiMoniDatabase.transactionRepository(
    clock: Clock,
    groups: GroupRepository = groupRepository(clock),
) = TransactionRepository(
    database = this,
    transactionDao = transactionDao(),
    categoryDao = categoryDao(),
    groupDao = groupDao(),
    contributionDao = groupContributionDao(),
    messageDao = messageDao(),
    groupRepository = groups,
    clock = clock,
)

/** Reminders have nowhere to go in a test; the daily check is asserted where it matters. */
internal object NoOpObligationScheduler : ObligationScheduler {
    override fun ensureDailyCheck() = Unit

    override fun checkNow() = Unit

    override fun dismiss(contributionId: Long) = Unit
}
