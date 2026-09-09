# Sabi Moni — Sprint Plan

Derived from `product-brief-mvp-spec.md` §6 (MVP Scope) and §9.3 (build order):

> Build the core loop first (FR1.x — daily capture), since that's your highest-priority
> piece, then layer in Groups, Reports, Savings, and finally SMS auto-detect.

The spec gives an ordering, not a plan. This file is the plan: what each sprint
delivers, what "done" means, and what is deliberately not in it yet. Update the status
table and tick the boxes as work lands — this is the file that answers "what's next".

**State as of:** 2026-09-09 · scaffold complete (`3b7c792`), running on the Pixel 6
daily driver, Gemini key stored.

---

## Status at a glance

| Sprint | Focus | FRs | Status |
|---|---|---|---|
| 0 | Scaffold | — | Done |
| 1 | Close the capture loop | FR1.3, FR2.1–2.6 | ✅ Done |
| 2 | Make the day usable | FR1.4, FR1.5, FR1.6 | ▶ **Next** |
| 3 | Groups | FR4.1–4.6 | Not started |
| 4 | Reports | FR5.1–5.4 | Not started |
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

## Sprint 2 — Make the day usable

**Goal:** the 5-minute nightly ritual works end to end without a keyboard fight.

- FR1.4 — each parsed line tappable to correct amount, category, group, direction
- FR1.5 — "today" view with a running total
- FR1.6 — manual form entry as a fallback

### Tasks

- [ ] Editable parsed-line component with inline correction, no re-typing the message
- [ ] Running total for today in `CaptureUiState`, wired to `observeForDay`
- [ ] Manual entry form (amount, direction, category, group, date)
- [ ] Corrections persist without re-parsing — v1 corrections are taps, not dialogue (§6)

### Done when

A wrong parse can be fixed in two taps, and the day's total is visible without leaving
the capture tab.

---

## Sprint 3 — Groups

**Goal:** the second pillar, and the one that directly stops penalty loss.

- FR4.1–4.2 — create groups; log an announced contribution by form (the chat path is
  Sprint 1's parser plus group matching)
- FR4.3–4.4 — reminder N days before the due date, surfacing the penalty as motivation
- FR4.5 — mark Paid/Missed, full per-group history, on-time versus late
- FR4.6 — what I owe right now, across all groups

`GroupRepository` already creates groups and observes total outstanding; contributions,
reminders and history are unbuilt.

### Tasks

- [ ] Contribution create and edit (group, amount, due date), Paid/Missed transitions
- [ ] `ReminderWorker` scheduling off `reminderLeadDays`, penalty in the notification body
- [ ] **Runtime `POST_NOTIFICATIONS` request** — currently never requested anywhere
- [ ] Owed-across-all-groups view, and a per-group history screen
- [ ] Paying a contribution writes the linked transaction

### Done when

A contribution due in three days fires a reminder naming the penalty, and marking it paid
records the transaction and clears it from the owed view.

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
