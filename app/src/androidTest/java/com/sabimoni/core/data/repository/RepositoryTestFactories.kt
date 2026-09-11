package com.sabimoni.core.data.repository

import com.sabimoni.core.data.SabiMoniDatabase
import com.sabimoni.core.reminder.ReminderScheduler
import java.time.Clock
import java.time.Instant

/**
 * Wiring shared by the repository tests. The repositories take a lot of DAOs, and three
 * copies of the same constructor call is three places to update every time one is added.
 */
internal fun SabiMoniDatabase.groupRepository(
    clock: Clock,
    reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
) = GroupRepository(
    database = this,
    groupDao = groupDao(),
    contributionDao = groupContributionDao(),
    transactionDao = transactionDao(),
    reminderScheduler = reminderScheduler,
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

/** For tests that never touch obligations, so reminders have nowhere to go. */
internal object NoOpReminderScheduler : ReminderScheduler {
    override fun schedule(contributionId: Long, at: Instant) = Unit

    override fun cancel(contributionId: Long) = Unit
}
