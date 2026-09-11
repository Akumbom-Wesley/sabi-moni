# Sabi Moni — Sprint Plan

Derived from `product-brief-mvp-spec.md` §6 (MVP Scope) and §9.3 (build order):

> Build the core loop first (FR1.x — daily capture), since that's your highest-priority
> piece, then layer in Groups, Reports, Savings, and finally SMS auto-detect.

The spec gives an ordering, not a plan. This file is the plan: what each sprint
delivers, what "done" means, and what is deliberately not in it yet. Update the status
table and tick the boxes as work lands — this is the file that answers "what's next".

**State as of:** 2026-09-11 · groups and reminders landed, database at version 2, running
on the Pixel 6 daily driver, Gemini key stored.

---

## Status at a glance

| Sprint | Focus | FRs | Status |
|---|---|---|---|
| 0 | Scaffold | — | Done |
| 1 | Close the capture loop | FR1.3, FR2.1–2.6 | ✅ Done |
| 2 | Make the day usable | FR1.4, FR1.5, FR1.6 | ✅ Done (FR1.4 completed in Sprint 3) |
| 3 | Groups | FR4.1–4.6 | ✅ Done (tests unrun) |
| 4 | Reports | FR5.1–5.4 | ▶ **Next** |
| 5 | Savings | FR6.1–6.3 | Not started |
| 6 | MoMo SMS auto-detect | FR3.1–3.3 | Not started |
| 7 | App lock and hardening | FR7.1–7.2 | Not started |

---

## Sprint 0 — Scaffold (done)

Build system, data model, and core abstractions. Landed in `3b7c792`.

Delivered: Gradle and version catalog on AGP 9 (ADR-0013), `personal`/`public` flavors
(ADR-0011), full Room schema and DAOs (ADR-0005), Hilt graph (ADR-0008),
`AiParsingProvider` abstraction (ADR-0009) with the Gemini implementation (ADR-0016),
Keystore-backed key storage (ADR-0010), five-tab navigation.

Also true today, verified on device: capture writes to Room instantly (FR1.2, FR8.1),
and the API key round-trips as an AES-GCM blob with no plaintext at rest (FR7.2, FR2.5).

---

## Sprint 1 — Close the capture loop ✅ DONE

**Goal:** a typed paragraph comes back as a confirmed, structured set of line items.
This is the one thing the spec calls already life-changing, and right now it is the only
thing standing between a working scaffold and a usable app.

Today messages land at `PENDING_PARSE` and stay there forever — nothing drains the
queue, so the Gemini key that is already installed is never called.

- FR2.1–2.2 — parse worker calls `AiParsingProvider` with categories, group names, date
- FR2.3 — offline messages parse on next connectivity; pending count is already plumbed
- FR1.3 — thread replies with a natural-language confirmation of what was understood
- FR2.6 — send only message text plus minimal category/group context

### Tasks

- [x] `ParseMessageWorker` (`@HiltWorker`, `CoroutineWorker`) draining `pendingParse()`
- [x] Enqueue from `CaptureRepository.capture()` with a `NetworkType.CONNECTED` constraint
- [x] Unique work and backoff policy, so retries do not stack or hammer the free tier
      (`APPEND_OR_REPLACE`, exponential from 30s — ADR-0017)
- [x] Transient vs. permanent failure classification (`ParseFailure`) — ADR-0017
- [x] `TransactionRepository` — persist `TransactionDraft`s, resolve categories against
      existing rows only, drop non-positive amounts
- [x] Write drafts and `markParsed` in one `withTransaction` — no half-parsed state
- [x] Render the assistant confirmation and the `FAILED` state in `CaptureScreen`
- [x] Pending-count indicator — already present in `CaptureScreen` from the scaffold
- [x] Route `MomoSmsReceiver` through `CaptureRepository` so SMS captures queue too
      (previously stored and never parsed)
- [x] Tests: Ktor `MockEngine` provider tests + failure classification — 15 passing
- [x] Instrumented `TransactionRepositoryTest` over in-memory Room — 7 passing
- [x] On-device acceptance run — passed on the Pixel 6 (2026-09-09), verified against
      the database, not just the screen

> Note for future sprints: run instrumented tests on an emulator, not the daily driver.
> `connectedAndroidTest` uninstalls the app when it finishes, which wipes app data —
> including the stored API key.

### Done when

Typing *"took a taxi for 500, bought lunch for 1500, gave 5000 to choir"* on the device
produces three transactions in Room, an in-thread confirmation naming each, and — with
aeroplane mode on — a pending entry that parses by itself once connectivity returns.

### Decisions taken

Recorded in [ADR-0017](adr/0017-parse-worker-and-failure-policy.md): whole-queue drain per
run, transient vs. permanent failure classification, commit-and-mark in one database
transaction, and — the one with lasting consequences — **the model resolves categories but
never creates them**, so a hallucinated name cannot enter the taxonomy.

One thing the sprint surfaced that it could not fix: `TransactionEntity` can reference a
`GroupContributionEntity` but not a `GroupEntity`, so a parsed "gave 5000 to choir" keeps
the group only in its note text. Structured group linking needs a schema decision in
Sprint 3.

---

## Sprint 2 — Make the day usable ✅ DONE

**Goal:** the 5-minute nightly ritual works end to end without a keyboard fight.

- FR1.4 — each parsed line tappable to correct amount, category, direction, date
- FR1.5 — "today" view with a running total
- FR1.6 — manual form entry as a fallback

### Tasks

- [x] `EntryEditorDialog` — one editor for both correcting a parsed line and adding one by
      hand, so a manual entry is fixable by the same tap as a parsed one
- [x] Editing behind a pencil button on each entry; the raw message is never re-typed, and
      a tap on the row itself does nothing (ADR-0019)
- [x] The editor opens pre-filled from the parse, amount already grouped (`12 500`)
- [x] Long-press anywhere on a card to select everything on it, checkboxes replace the
      pencils, contextual bar in place of the summary; a selection deletes in one statement
      so the total recalculates once
- [x] The note never restates the amount — prompt rule plus `stripRestatedAmount` at the
      parse boundary, so correcting an amount cannot leave the note contradicting it
      (ADR-0020)
- [x] Confirmation on a bulk delete — a long-press plus a tap can select several rows and
      there is no undo
- [x] Running total for today in `CaptureUiState`, wired to `observeForDay`
- [x] Balance (income minus expense over everything logged) as the screen's headline
      figure, today's net beneath it, and only one "Today" on screen (ADR-0022)
- [x] Bounded retries and a visible wait: a repeating failure reaches a `FAILED` message
      with a reason in ~2 min instead of retrying invisibly for hours; a pending message
      says *why* it is still waiting and offers "Try now" (ADR-0021)
- [x] Light/dark/system theme choice in Settings, persisted (ADR-0023)
- [x] System bar icon contrast follows the app's theme, not the phone's dark-mode setting —
      forcing Light on a dark-mode phone was hiding the clock and battery (ADR-0024)
- [x] Thread redesign: a right-aligned bubble for what was sent, a left-aligned reply card
      for what the app understood, ledger-style entry rows, centred day chips (ADR-0024)
- [x] `surfaceContainer*` and outline roles defined explicitly, so the non-dynamic palette
      (the `public` flavour, pre-Android-12) is coherent rather than baseline purple
- [x] Manual entry form (amount, direction, category, date, note)
- [x] Manual entries appear in the thread as `ThreadItem.Manual`, interleaved by time —
      an entry invisible where you logged it reads as a lost entry
- [x] Day separators in the thread, so "today" is legible in a thread holding every day
- [x] Corrections persist without re-parsing — v1 corrections are taps, not dialogue (§6)
- [x] Correction is one targeted UPDATE of five fields, so `messageId`, `createdAt` and
      `autoDetected` cannot be rewritten by a correction (ADR-0018)
- [x] **"Try again" on a FAILED message** — the dead end ADR-0017 left open. Guarded on
      `status = 'FAILED'` in SQL so a double tap cannot re-parse a parsed message
- [x] `parseMoney` — no decimal point, separators ignored (XAF has no minor unit)
- [x] Date chips single-line and short, so the row cannot wrap
- [x] Tests: 16 new JVM (`MoneyTest` 6, `NoteHygieneTest` 10), 31 JVM total passing; both
      flavors build
- [x] Instrumented: `TransactionRepositoryEditingTest` (16), `CaptureRepositoryTest` (7)
      and one added to `TransactionRepositoryTest` (8) — written and compiling, **not yet
      run**, see below
- [ ] Run the 31 instrumented tests — on an emulator, not the daily driver
- [ ] On-device acceptance run

### Deferred out of this sprint

**The group field on the editor (part of FR1.4)** — ✅ since landed in Sprint 3.
`TransactionEntity` could only reference a `GroupContributionEntity`, not a `GroupEntity`,
so attaching a group would have meant fabricating an obligation with a due date and penalty
nobody announced. Deferred in
[ADR-0018](adr/0018-corrections-and-manual-entry.md), unblocked by
[ADR-0025](adr/0025-how-a-transaction-references-a-group.md).

**Category management.** The editor picks from existing categories plus "Uncategorised" and
cannot create one, keeping ADR-0017's rule intact on the human path. Add/rename/archive is
its own feature, not a side effect of fixing an amount at 11pm.

### Done when

A wrong parse can be fixed in two taps, and the day's total is visible without leaving
the capture tab. — Met in code; awaiting the test and acceptance runs.

### Decisions taken

[ADR-0018](adr/0018-corrections-and-manual-entry.md): one editor for both jobs, a
correction as a five-field UPDATE that cannot rewrite provenance, no re-parse after a
correction, manual entries as first-class thread items, the SQL-guarded retry, and — the
one with lasting consequences — **"today" is resolved per subscription and deliberately
does not roll over while the screen is open**, so the total cannot empty itself mid-ritual
at 23:59.

[ADR-0019](adr/0019-explicit-edit-affordance-and-multi-select-delete.md), written after
using the first version on the device: editing moves to an explicit button because the
thread is mostly something you *read* and a stray tap should cost nothing; delete becomes a
long-press multi-select, because a bad parse goes wrong in bulk and the unit of the fix
should match the unit of the mistake. It supersedes ADR-0018's tap-target and
delete-affordance decisions; the rest of 0018 stands.

[ADR-0020](adr/0020-the-amount-is-a-field-not-prose.md), also from device use: the model was
restating the amount inside the note, so correcting 1600 to 1500 left the note asserting
1600. The fix is not to rewrite notes on correction but to stop storing the amount twice —
prompt rule plus boundary enforcement, on the ADR-0017 principle that an invariant the
model has to cooperate with is not an invariant.

[ADR-0021](adr/0021-bounded-retries-and-visible-waiting.md): a message waited five minutes
saying only "waiting to be interpreted". Nothing was stuck — the worker was failing and
retrying behind a backoff that had grown to two minutes and was doubling, and the transient
branch logged *nothing*. Retries are now bounded by attempts that actually reached the
network (so ADR-0015's offline promise is untouched), the reason is shown while pending, and
the user can skip the backoff.

[ADR-0022](adr/0022-balance-as-the-headline.md) and
[ADR-0023](adr/0023-theme-choice-in-settings.md): the screen said "Today" twice, which was a
symptom of the summary answering the smaller of the two available questions. Balance is now
the headline, labelled as derived from what has been logged. Plus a light/dark/system choice
in Settings, on the DataStore that already existed.

---

## Sprint 3 — Groups

**Goal:** the second pillar, and the one that directly stops penalty loss.

- FR4.1–4.2 — create groups; log an announced contribution by form (the chat path is
  Sprint 1's parser plus group matching)
- FR4.3–4.4 — reminder N days before the due date, surfacing the penalty as motivation
- FR4.5 — mark Paid/Missed, full per-group history, on-time versus late
- FR4.6 — what I owe right now, across all groups

### Tasks

- [x] **Decided how a transaction references a group** — `groupId` alongside the existing
      `groupContributionId`, with `groupId` *derived* from the contribution whenever one is
      involved so the two cannot disagree (ADR-0025)
- [x] Database **version 2**, via `@AutoMigration` rather than hand-written SQL: SQLite
      cannot add a foreign key with `ALTER TABLE`, so the table is recreated and every row
      copied, and Room derives that from the two exported schemas
- [x] Group create and edit: name, type, penalty, reminder lead time (FR4.1)
- [x] Contribution create, edit and delete (group, amount, due date, note) (FR4.2)
- [x] `ReminderWorker` + `ReminderScheduler`, fired `reminderLeadDays` before the due date
      at 09:00 local, with the penalty in the body (FR4.3–4.4)
- [x] The reminder re-reads the contribution when it fires, so a settled obligation does
      not notify
- [x] **Runtime `POST_NOTIFICATIONS` request** — asked for on the Groups screen, and only
      once something is outstanding
- [x] Paid/Missed transitions; paying writes the linked transaction in one database
      transaction, with `groupId` derived from the contribution (FR4.5)
- [x] Deleting a payment reverts its obligation to `PENDING` and re-arms the reminder,
      unless another payment still settles it (ADR-0026)
- [x] Owed-across-all-groups headline, due-now list, and per-group history in expandable
      cards, all derived from one read (FR4.5, FR4.6)
- [x] Changing a group's lead time re-arms its outstanding reminders
- [x] The parser's `matchedGroup` — returned since Sprint 1 and discarded ever since — now
      resolves to a real group, on the same "match, never create" terms as categories
- [x] **Group field added to `EntryEditorDialog`, completing FR1.4.** Fixed, not editable,
      on a transaction that settles an announced obligation
- [x] Migration verified on the device against real data: `user_version = 2` and
      `transactions.groupId` present, no data loss
- [x] Both flavors build; 31 JVM tests still pass
- [ ] Run the instrumented tests — `GroupRepositoryTest` (14) is new, ~45 in total, on an
      emulator rather than the daily driver
- [ ] On-device acceptance run

### Done when

A contribution due in three days fires a reminder naming the penalty, and marking it paid
records the transaction and clears it from the owed view. — Met in code; awaiting the test
and acceptance runs.

### Decisions taken

[ADR-0025](adr/0025-how-a-transaction-references-a-group.md): the schema question two
sprints deferred. Two references rather than one, with the redundancy made unrepresentable
by deriving `groupId` instead of accepting it — and a deliberate bet that a contribution
may one day be paid in instalments, which the tidier single-reference design would have
foreclosed.

[ADR-0026](adr/0026-the-contribution-lifecycle-and-its-reminders.md): WorkManager owns the
reminder schedule and the `reminders` table is only a log of what fired; reminders land at
09:00 because paying is a daytime errand, unlike capture; and deleting a payment puts its
obligation back, because "what I owe" is the one screen that must not lie.

---

## Sprint 4 — Reports

**Goal:** the retrospective mirror. Data only — no caps, no blocking alerts (FR5.4).

The DAO layer is already done here: `observeSpendByCategory`, `observeTotal` and
`observeInRange` all exist. This sprint is mostly presentation.

- FR5.1 — by category, by group, income versus expense
- FR5.2 — daily, weekly, monthly, quarterly, yearly, plus a custom range
- FR5.3 — bar/pie breakdown with drill-down to the transactions behind a number

### Tasks

- [ ] Range selector and `ReportsViewModel` over the existing queries
- [ ] Charting — `vico` is already in the version catalog but is not yet a dependency
- [ ] Drill-down from any figure to its transaction list

---

## Sprint 5 — Savings

- FR6.1 — named goal, target amount, optional target date
- FR6.2 — manual set-aside logged as its own transaction type, reducing available balance
- FR6.3 — per-goal progress

`SavingsGoalDao` exists; the repository, the UI and the set-aside transaction type do not.

---

## Sprint 6 — MoMo SMS auto-detect

**Last in the spec's build order, deliberately.** `personal` flavor only (ADR-0011).

The receiver, gateway and manifest entry already exist. What is missing is that
`RECEIVE_SMS` is never requested at runtime, so the receiver can never fire — and
Settings currently claims "This build can read MTN MoMo alerts automatically" regardless
of whether the permission was granted, which is actively misleading.

### Tasks

- [ ] Runtime `RECEIVE_SMS` request with rationale UI
- [ ] Settings reflects real permission state, not just the build flavor
- [x] MTN MoMo sender filter — already in `MomoSmsReceiver` (MTN / MOMO / MOBILE MONEY)
- [ ] Tighten the filter to MoMo *transaction* formats, not just MoMo senders
- [ ] SMS entries appear system-flagged in the thread for one-tap confirm (FR3.2)

---

## Sprint 7 — App lock and hardening

- FR7.1 — biometric/PIN prompt on open (`androidx.biometric` is already a dependency and
  entirely unused)
- FR7.2 — audit that no financial text or key material reaches logs; strip Ktor logging
  in release
- [ ] Release build sanity pass: R8 rules for Room, Hilt and kotlinx-serialization

---

## Deferred to v1.1+ (§6)

Predictive and forward-looking reports · cloud backup and multi-device sync ·
conversational corrections ("no, taxi was actually 500")

---

## Known divergences from the spec

- **§4.1 and §4.2 name Groq as the primary AI provider.** Superseded by ADR-0016 —
  Gemini via Google AI Studio is primary and only. The spec prose is stale; the ADR is
  current.
- **§4.2 assumes `2.0-flash`-era models.** The implementation targets
  `gemini-3.5-flash-lite` with enforced `responseSchema` output (ADR-0016).
