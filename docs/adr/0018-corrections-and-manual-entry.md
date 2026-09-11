# ADR-0018: Corrections, manual entry, and what "today" means

- **Status:** Accepted — the **tap target and delete affordance** below are superseded by
  [ADR-0019](0019-explicit-edit-affordance-and-multi-select-delete.md). Everything else
  here stands.
- **Date:** 2026-09-10
- **Builds on:** [ADR-0017](0017-parse-worker-and-failure-policy.md) (what the model may
  create), [ADR-0006](0006-money-as-integer-xaf.md) (money as whole XAF)

## Context

Sprint 1 made the parser produce line items. Sprint 2 has to make them *usable*: fixable
when the model guessed wrong (FR1.4), summed for the day (FR1.5), and enterable by hand
when a tap beats a sentence (FR1.6).

Each of those is a place where a plausible-looking choice quietly breaks something:

- Re-parse after a correction, and the user's fix competes with the model's next guess —
  and burns free-tier quota to re-derive an answer the user just gave.
- Read-modify-write a corrected row, and a field the correction had no business touching
  (how the entry arrived) can be silently rewritten.
- Put manual entries anywhere but the thread, and an entry becomes invisible in the exact
  place the user just logged it, which reads as a lost entry.
- Let the editor create categories, and ADR-0017's rule holds against the model but leaks
  through the human path instead.
- Offer "Try again" on a failed message without a guard, and a double tap re-parses a
  message that has since succeeded, duplicating every entry it produced.

## Decision

### One editor for corrections and for manual entry

`EntryEditorDialog` takes a nullable entry: an entry means "correct this" (FR1.4), null
means "add one" (FR1.6). They collect identical values — amount, direction, category,
date, note — so keeping them as one composable is what guarantees a hand-typed entry is
correctable by exactly the same tap as a parsed one.

A dialog rather than a bottom sheet: the form is short, it needs the keyboard, and
`AlertDialog` is a stable API where `ModalBottomSheet` is not.

`LoggedEntry` is likewise one domain type for both. The only thing that distinguishes a
manual entry is that its `messageId` is null.

### A correction is a targeted UPDATE of five fields, and nothing else

`TransactionDao.applyCorrection` names `amountXaf`, `direction`, `categoryId`, `note` and
`occurredOn` in one SQL statement, rather than loading the row, copying it and writing it
back. Two reasons, and the second is the real one:

- No lost update if anything else touches the row between read and write.
- **`messageId`, `createdAt` and `autoDetected` are not in the statement, so a correction
  cannot rewrite them.** How an entry arrived — typed, parsed from a MoMo SMS, entered by
  hand, and when — is history. A correction changes what happened, never the record of how
  we came to know it. Making that a property of the SQL rather than of the caller's
  diligence means it stays true as more callers appear.

### A correction never re-parses the message

The raw text stays byte-for-byte as it was sent and the message stays `PARSED`. v1
corrections are taps, not dialogue (spec §6): conversational correction — *"no, the taxi
was actually 500"* — is explicitly out of MVP scope, and re-parsing would also spend a
request from a daily-capped free tier to re-derive an answer the user has just supplied by
hand.

Deleting an entry likewise leaves its message alone. The message is the audit record of
what was said; the entries are what we concluded from it, and only those are editable.

### Manual entries appear in the thread in their own right

`ThreadItem` is a sealed interface — `Captured` (a message with the lines it produced) or
`Manual` (a standalone entry) — merged and ordered by when each was logged.

FR1.5 asks for "a single today view/thread showing everything logged". A manual entry has
no message to sit under, so without its own place in the timeline it would be invisible
until Reports, in the exact screen where the user just logged it.

Day separators are inserted wherever the day changes, grouped by **when a thing was
logged**, not the day it happened: this is a chat thread, and an entry backdated to last
Tuesday still arrived tonight. The day it happened on is on the line itself, and in the
totals.

### The editor picks categories; it does not create them either

ADR-0017 stopped the *model* from inventing taxonomy. The editor offers existing
categories plus "Uncategorised", resolving by id against a row the user can see, so the
human path does not become the leak. Adding, renaming and archiving categories is its own
feature with its own decisions — not a side effect of fixing an amount at 11pm.

### Retry is guarded in SQL, not in the caller

`MessageDao.requeueFailed` carries `AND status = 'FAILED'` and returns the number of rows
it changed; `CaptureRepository.retry` only asks for a drain when that number is non-zero.
A second tap on "Try again" is therefore a no-op rather than a way to run an
already-parsed message through the parser again and duplicate its entries. This closes the
dead end ADR-0017 left open.

### "Today" is resolved per subscription, and does not move while you are looking at it

`CaptureViewModel` reads `LocalDate.now(clock)` inside the state flow, so each
subscription re-resolves it and reopening the app after midnight shows the new day.

It deliberately does **not** roll over while the screen is open. Someone finishing the
nightly ritual at 23:59 would otherwise watch the entries they had just logged drop out of
the total mid-task — the app is built around a low-energy end-of-day ritual (design
principle 2), and a total that empties itself during it is worse than one that is a few
minutes stale.

### Typed amounts have no decimal point

`parseMoney` ignores spaces, dots, commas and apostrophes as thousands separators and then
requires digits. XAF has no minor unit (ADR-0006), so `1.500`, `1,500` and `1 500` are all
1500 and there is no reading under which any of them is one and a half. The keyboard is
`KeyboardType.Number`. Anything that is not a positive whole amount returns null, which
disables Save rather than storing a guess.

### Group correction waits for Sprint 3

FR1.4 lists group among the correctable fields. It is **not** in this editor, and that is
a decision rather than an omission.

`TransactionEntity` can only reference a `GroupContributionEntity`, not a `GroupEntity`
(the gap ADR-0017 recorded). Attaching a group today would mean fabricating a contribution
row — an obligation with a due date and a penalty that nobody announced — from a user who
was only trying to fix a category. That fails the product brief's own test: it adds
structure the user then has to maintain, and it invents a *financial obligation* to do it.
A disabled picker was rejected too: dead controls teach users the app is broken.

Sprint 3 owns the schema decision, and group correction lands with it.

## Alternatives rejected

- **Re-parse the message after a correction.** Would let one fix improve the whole line,
  and is the "conversational" thing to do. Rejected: explicitly out of v1 scope (spec §6),
  it spends capped quota to re-derive what the user just typed, and it puts the user's
  correction in competition with the model's next guess.
- **Read-modify-write for corrections.** Ordinary, and reads more naturally in Kotlin.
  Rejected because the field list is the invariant: enumerating five columns in SQL is what
  makes provenance unrewritable, rather than trusting every future caller to copy the
  right fields.
- **Manual entries only in Reports, or on their own screen.** Keeps the thread purely
  conversational. Rejected — FR1.5 asks for one view of everything logged, and an entry
  missing from the screen you logged it in reads as data loss.
- **Let the editor add a category when nothing fits.** Fewer dead ends for the user.
  Rejected per ADR-0017's reasoning: the cost of a messy taxonomy is cumulative and paid
  later, in exactly the manual tidying this app exists to avoid. Category management should
  be a deliberate act, not a by-product.
- **Roll the day over at midnight while the screen is open.** Technically more correct.
  Rejected: it empties the total during the one task the app is designed for.
- **A `groupId` column on `TransactionEntity` now.** Would unblock FR1.4's group field
  this sprint. Rejected because it pre-empts Sprint 3's real question — whether a
  transaction points at a group, at a contribution, or at both — and a schema guess made
  to satisfy a picker is the expensive kind of guess.

## Consequences

- Corrections are silent and immediate; there is no undo. Delete is behind the editor
  rather than a swipe, so it takes two deliberate taps.
- A `PARSED` message whose entries have all been deleted reads "nothing to log here",
  which is indistinguishable from a message that never had anything to log. Telling those
  apart needs state we do not otherwise want, and the label is not wrong — nothing is
  logged from it now.
- `observeThread` loads every message and every entry on each change. Correct and simple
  at one person's volume; pagination becomes a real concern only if this app ever holds
  years of data.
- `Category` and `DayTotals` join the domain models, and `ParsedLine` is now `LoggedEntry`
  — the same type whether the parser or the user produced it.
- `observeCategories` lives on `TransactionRepository` rather than a repository of its
  own. Categories exist only to classify transactions, and one query does not justify a
  third repository; if category management lands, that is the moment to split it out.
- No schema change, so the database stays at version 1 and there is no migration. The one
  change that *would* have needed a migration — a group reference — is the one deferred.
