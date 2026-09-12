# ADR-0029: Recurring contributions, and one daily check instead of many alarms

- **Status:** Accepted
- **Date:** 2026-09-11
- **Supersedes:** the per-contribution reminder scheduling in
  [ADR-0026](0026-the-contribution-lifecycle-and-its-reminders.md). The rest of that ADR —
  deleting a payment reverts its obligation, the `reminders` table is a log, reminders land
  in the morning — stands.

## Context

Most of this user's obligations are not announced ad hoc. They are standing commitments:
*at least 1000 francs every month, or a 1000 franc fine*. Sprint 3 made every contribution
something you enter by hand, which means re-entering the same obligation twelve times a
year and remembering to do it — exactly the maintenance burden the product brief exists to
remove.

The requirement, in the user's words: set on the group that a contribution is owed, how
much, and how often; get the reminder however many days before the deadline you chose; and
if the period goes unpaid, get **daily** reminders to pay the fine. Configure it once.

That breaks the reminder mechanism ADR-0026 built. Per-contribution exact alarms are
armed when the user presses Save — but next month's obligation does not exist yet, and
nothing exists to hang an alarm on.

## Decision

### The schedule lives on the group

Three columns on `money_groups`: `recurrenceUnit`, `recurrenceAmountXaf`,
`recurrenceAnchor`. Null unit means the group is ad hoc.

On the group rather than in a table of its own, because that is the user's mental model —
*"for a group I should have the option to set that I have to contribute a certain amount"*
— and because one schedule per group is the whole requirement. A separate table would buy
multiple schedules per group, which nobody has asked for.

The three travel together as a `RecurrenceSchedule`, and a partially-set row reads as *no*
schedule rather than a broken one: an amount with no frequency cannot produce a deadline.

### Deadlines come from an anchor, not a day-of-month

`recurrenceAnchor` is the first deadline; every later one is derived by advancing from it.
One field covers "the 26th of every month", "every other Friday" and "every quarter",
where day-of-month plus day-of-week would need a different field per unit.

It also gets month-ends right. `LocalDate.plusMonths` clamps, so a schedule anchored on the
31st falls due on the 28th in February — and because later deadlines are computed from the
*anchor* rather than from the previous clamped date, March returns to the 31st instead of
the schedule walking backwards a few days every short month. That is the one piece of this
feature with real edge cases, so it lives in pure functions with JVM tests that actually
run.

### The app materialises each period; nothing is generated on the fly

A period becomes a real `GroupContributionEntity` row. Deadlines are not computed at read
time.

This keeps everything Sprint 3 built working unchanged — the owed view, the history, the
Paid/Missed transitions, the payment transaction, the "deleting a payment reverts the
obligation" rule. A virtual obligation would need all of those to understand two kinds of
contribution.

Roll-forward is **idempotent by `(groupId, dueDate)`**: each run recomputes the whole
series and creates only what is missing. Running twice, or catching up after a fortnight
with the app closed, cannot duplicate a period — and no extra column is needed to identify
one, because a group cannot owe the same group twice on the same day.

Periods are generated up to *today plus the group's lead time*, so the upcoming deadline
exists early enough to be warned about. A long-dormant schedule generates at most the 24
most recent periods: resurrecting two years of missed church contributions on first launch
would be technically honest and practically useless.

### One daily worker replaces every per-contribution alarm

`ObligationWorker` runs once a day and does both jobs, in order: roll schedules forward,
then decide what to notify about. The second depends on the first — you cannot warn about
next month's contribution until next month's contribution exists.

Reminding follows two rules:

1. **Once**, as soon as the lead window opens, for something not yet due. Four days' lead
   means one notification on the 26th, not four.
2. **Daily**, once the deadline has passed unpaid, naming the fine and what it now takes to
   settle.

Both are deduplicated through the `reminders` log — which is the first real job that table
has had since ADR-0026 made it a log rather than a schedule. A WorkManager retry, or a
`checkNow()` after an edit, cannot double-notify.

Losing exact alarm timing costs nothing: reminders are day-granular by design. What it buys
is that there is no per-contribution work to schedule, replace or cancel, nothing to drift
when a due date is edited, and one place that decides whether the user hears anything.

`checkNow()` runs the same worker immediately after a change the user expects to see acted
on. `ensureDailyCheck()` is called from `Application.onCreate` — idempotent, and it has to
be somewhere that runs whether or not the Groups tab is ever opened.

### The fine is derived, shown, and not auto-recorded

A contribution past its deadline unpaid incurs the group's penalty. `fineIncurred` and
`owedOn` compute that from the dates; no stored flag, because `paidDate > dueDate` already
records it and a second field could disagree (ADR-0020).

The fine counts towards what is owed, appears on the card, and is what the daily nag is
about. It is **not** written as a transaction when the contribution is marked paid.
Whether the fine was actually charged — or waived, or argued down, which is how informal
groups work — is the user's to say, and inventing a payment they did not make is worse
than making them log it.

### A contribution must have its group chosen explicitly

The dialog no longer pre-selects the first group. Filling in an amount and a date and
logging it against whichever group sorted first is a wrong obligation, which is worse than
one more tap.

## Alternatives rejected

- **Generate obligations lazily at read time.** No rows, no roll-forward, no duplicates.
  Rejected: every existing feature — marking paid, the payment transaction, history,
  reverting on delete — operates on a row, and all of them would need to understand a
  second kind of contribution that cannot be marked or paid.
- **Create the next period when the current one is settled.** Simpler than recomputing a
  series. Rejected because it breaks on exactly the case that matters: *not* paying. An
  unsettled period would never produce a successor, so missing one month would silently
  stop the schedule.
- **Keep per-contribution exact alarms and add a daily worker only for recurrence.** Less
  to change. Rejected — two mechanisms deciding when the user hears about the same
  obligation is one too many, and the daily worker has to exist anyway.
- **Store a period index or `periodStart` on the contribution.** Explicit is usually
  better. Rejected as redundant: `(groupId, dueDate)` already identifies a period uniquely,
  and a derived column that can disagree with the dates is the ADR-0020 mistake again.
- **Auto-record the fine as a transaction on late payment.** Keeps the balance honest.
  Rejected — see above; the app would be asserting a payment that may never have happened.
- **Ask on first launch whether each group is recurring.** Rejected as setup ceremony; the
  checkbox sits in the group dialog where it is discovered when it is relevant.

## Consequences

- Database version 5. Three nullable columns, no foreign key, so this migration is a plain
  `ALTER TABLE` rather than a table recreation.
- **Deleting a materialised period brings it back on the next roll-forward.** The schedule
  still says it was owed, and suppressing it would need a tombstone. Pinned by a test as
  known behaviour rather than endorsed — the honest fix is to make the user edit the
  schedule, and if this proves annoying it needs its own decision.
- Changing a schedule's amount affects future periods only; already-materialised ones keep
  what they demanded at the time. That is right for history and may surprise someone who
  expected an edit to fix this month too.
- Reminders now depend on the daily worker running. If the OS defers it — Doze, battery
  optimisation — reminders are late rather than missed, and the roll-forward catches up
  whenever it next runs. Worth watching on a real phone, since exactness was the one thing
  per-contribution alarms were better at.
- `GroupRepository` has grown again. It now owns schedules as well as the contribution
  lifecycle, and is comfortably the largest class in `core/data`.
- The recurrence arithmetic is covered by nine JVM tests that run without a device — the
  first genuinely tricky logic in this project that is testable that way, and deliberately
  kept that way by putting it in pure functions.
