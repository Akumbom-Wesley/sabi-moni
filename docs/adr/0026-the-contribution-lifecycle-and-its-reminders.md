# ADR-0026: The contribution lifecycle and its reminders

- **Status:** Accepted
- **Date:** 2026-09-11
- **Builds on:** [ADR-0025](0025-how-a-transaction-references-a-group.md) (the group
  reference), [ADR-0015](0015-offline-capture-parse-queue.md) and
  [ADR-0021](0021-bounded-retries-and-visible-waiting.md) (how this project uses
  WorkManager)

## Context

Sprint 3 builds FR4.x: log an announced obligation, get warned before it is due with the
penalty attached, mark it Paid or Missed, and see what is owed across all groups. The
steps are not independent — creating one arms a reminder, settling one writes a
transaction and disarms that reminder — so the decisions below are mostly about where each
fact lives and who is allowed to move it.

## Decision

### Deleting the payment puts the obligation back

`TransactionRepository.deleteEntries` reads which contributions the doomed transactions
were settling *before* deleting them, and reverts any that no longer have a payment to
`PENDING`, re-arming the reminder.

Sprint 2 gave the user multi-select delete over the thread. Without this, deleting the
payment for a church contribution would leave it reading "Paid" with no payment behind it
— the owed view asserting something that is no longer true, in the one place the app
exists to be trusted about.

Only reverted when **no** payment remains, because `groupContributionId` is many-to-one
(ADR-0025) and a contribution settled in two instalments should survive losing one.

### WorkManager owns the schedule; the `reminders` table is a log

`ReminderScheduler` enqueues one uniquely-named work request per contribution. The
`reminders` row is written when a reminder **fires**, never when it is armed.

Keeping `remindAt` rows in step with WorkManager's own queue would be two copies of one
fact, and they would drift the first time a due date was edited — the mistake ADR-0020 was
written about. What the table is genuinely useful for is history: the spec's nightly flow
(§7) wants a fired reminder sitting in the thread, and that needs a record that one fired.

`ExistingWorkPolicy.REPLACE` keyed per contribution, so editing a due date moves that one
reminder instead of leaving the old time armed as well.

### The reminder re-reads the contribution when it fires

Between arming and firing, the obligation may have been paid, marked missed, edited or
deleted. The worker loads it fresh and does nothing unless it is still `PENDING`. A
notification about a bill you settled yesterday is worse than no notification.

### Reminders fire at 09:00 local, `reminderLeadDays` before the due date

A fixed morning hour rather than the clock time the obligation happened to be logged at. A
reminder that arrives at 02:00 because that is when the WhatsApp message was relayed is a
reminder you sleep through.

Morning, specifically: paying is a daytime errand. This is the opposite of capture, which
is deliberately an evening ritual (design principle 2) — the two features want opposite
ends of the day, and the app should not assume one hour suits both.

A lead time that lands in the past schedules immediately rather than being dropped.
"Choir wants 5000 by the 15th" relayed on the 16th is exactly when a warning is most
useful.

### Changing a group's lead time re-arms its outstanding reminders

Otherwise the setting would apply only to obligations announced after the change — a
control that appears to work and silently does not for everything already on the books.

### No category on a contribution payment

`markPaid` writes the transaction with `groupId` set and `categoryId` null. Reports treats
category and group as separate axes (FR5.1), so the group attribution is already carried
structurally; stamping "Groups" on it as well would be a second copy of the same fact, and
picking a category on the user's behalf is not the app's call.

### Every group view is derived from one read

The Groups screen needs three things — what I owe in total (FR4.6), what each group owes,
and each group's full history (FR4.5). All three come from one `observeAllRows()` plus the
group list, derived in the ViewModel.

Three SQL queries would each be correct in isolation and could still render three
different answers about the same row mid-update. This is one person's social obligations,
not a ledger, so the read is small enough that correctness is the only consideration that
matters.

### History lives inside the group card

Tapping a group expands its history in place rather than pushing a detail screen. It is a
short list, and keeping it here means no navigation state to save, restore, or get wrong
on rotation. If it outgrows the card, a route is the obvious next step.

### The notification permission is asked for next to the obligations

`POST_NOTIFICATIONS` has been declared in the manifest since the scaffold and was never
requested, which on Android 13+ means every reminder would have been posted into a void —
indistinguishable, from the user's side, from a reminder that never fired.

Asked for on the Groups screen, and only once something is actually outstanding: a
permission prompt on an empty screen has no reason attached to it, and a refusal there is
harder to come back from than one next to "Choir · 5 000 · due in 3 days".

The notifier reports whether it could post, the worker logs when it could not, and the
reminder is recorded as having fired either way.

## Alternatives rejected

- **Leave the contribution marked Paid when its payment is deleted.** Less code, and
  arguably the user meant to delete only the transaction. Rejected: the two facts are one
  event, and "what I owe" is the screen that must not lie.
- **Cascade-delete the contribution with its payment.** Also keeps them consistent.
  Rejected — deleting a wrongly-entered payment should not destroy the record that the
  church asked you for money.
- **Keep `remindAt` rows as the schedule and have a periodic worker sweep them.** Would
  survive WorkManager quirks and give an easy "pending reminders" query. Rejected: a
  periodic sweep is a polling loop where an exact schedule already exists, and it doubles
  the state that has to agree.
- **Fire the reminder at the time of day the contribution was logged.** No arbitrary
  constant. Rejected for the 02:00 case above.
- **Ask for notification permission at first launch.** Conventional. Rejected: at first
  launch there are no groups, no obligations and no reason given, which is the worst moment
  to ask for the permission this feature depends on.
- **A `GroupDetailScreen` route for history.** The Android-idiomatic answer and probably
  where this ends up. Rejected for now as navigation machinery ahead of the need.

## Consequences

- `ReminderDao.observePending()` and `due()` are now unused: they assume the table is a
  schedule, which this ADR decided it is not. Left in place rather than deleted, because
  the fired-reminder-in-the-thread feature (spec §7) will want to read this table — but
  they should not be taken as evidence that the table drives anything.
- Reminders do not survive the app's data being cleared, since WorkManager's queue goes
  with it. Acceptable: so does the contribution.
- A reminder that fires while the app has never been granted notification permission is
  silently lost apart from a log line and a `reminders` row. The prompt on the Groups
  screen is the mitigation, not a guarantee.
- `GroupRepository` has six dependencies and holds the whole lifecycle. That is deliberate
  — the steps are coupled in reality — but it is the largest class in `core/data` and
  worth splitting if savings goals end up sharing any of it.
- `TransactionRepository` now depends on `GroupRepository`. Acyclic, because
  `GroupRepository` reaches for `transactionDao` directly rather than the other repository.
  Worth remembering before anyone adds the reverse.
