# ADR-0017: Parse worker, retry policy, and what the model may create

- **Status:** Accepted
- **Date:** 2026-09-09
- **Builds on:** [ADR-0015](0015-offline-capture-parse-queue.md) (the queue),
  [ADR-0016](0016-gemini-as-primary-ai-provider.md) (the provider)

## Context

ADR-0015 decided that capture writes locally and parsing happens later, off a queue.
It did not say what drains the queue, when a failed parse should be tried again, or what
the model is allowed to create in the database. Sprint 1 needs all three answered, and
each of them is easy to get quietly wrong:

- Retry everything, and a bad API key spins in a loop against a free tier with a daily
  request cap.
- Retry nothing, and a subway-tunnel network blip silently loses the entry — breaking the
  one promise the app makes, that capture never fails because connectivity did.
- Let the model create categories, and a taxonomy the user never asked for accumulates
  from a component that is, by construction, guessing.

## Decision

### A `CoroutineWorker` drains the whole queue per run

`ParseMessageWorker` reads every `PENDING_PARSE` message, not one message per run.
Category and group context is fetched once per run and reused, since it cannot change
mid-drain and each fetch would otherwise be per-message overhead.

Enqueued as unique work under `parse-pending-messages` with
`ExistingWorkPolicy.APPEND_OR_REPLACE`, constrained to `NetworkType.CONNECTED`, with
exponential backoff from 30 seconds.

`APPEND_OR_REPLACE` rather than `KEEP`: the worker drains everything pending, so a second
enqueue is usually redundant — *except* for a message captured while a run is already in
flight, which that run has already read past. `KEEP` would drop the enqueue that should
have caught it, leaving the message pending until some unrelated later capture. Appending
costs one no-op run in the common case and closes that gap.

### Failures are classified transient or permanent, never retried blindly

`Throwable.toParseFailure()` maps each failure to `ParseFailure.Transient` or
`ParseFailure.Permanent`:

| Failure | Class | Why |
|---|---|---|
| No connection, timeout, `IOException` | Transient | The entry is fine; the network is not |
| HTTP 429 | Transient | Free-tier rate limit — backoff is the correct response |
| HTTP 5xx | Transient | Google's problem, not the user's |
| HTTP 401/403 | Permanent | The key is wrong; retrying cannot fix it |
| Other 4xx | Permanent | Malformed request — a code bug, not a blip |
| No API key configured | Permanent | Needs the user to act |
| Unreadable AI response | Permanent | Retrying burns quota on a likely-repeatable fault |

A transient failure leaves the message `PENDING_PARSE` and returns `Result.retry()`, so
WorkManager's backoff handles it. A permanent failure marks the message `FAILED` with a
reason written for a human ("AI key was rejected — check it in Settings"), shown in the
thread.

One transient failure retries the entire run. Messages committed earlier in that run are
already `PARSED`, so the retry re-reads a shorter queue rather than redoing work.

### Commit and status change are one database transaction

`TransactionRepository.commitParse` writes the parsed transactions and marks the message
`PARSED` inside `withTransaction`. Doing these separately would let a crash between them
leave transactions written against a message still queued as pending — which the next run
would parse again, duplicating every entry.

### The model resolves categories; it never creates them

A returned category name is matched case-insensitively against existing categories. No
match means `categoryId = null` — the transaction is recorded as uncategorised rather than
inventing a row.

This is the "does this add structure the user must maintain?" test from the product
brief's design principles. Auto-creation would let one hallucinated name ("Meals",
"Foods", "food") permanently enter a taxonomy the user then has to tidy, and the mess
arrives from the least reliable component in the system. Uncategorised is honest, visible
in Reports, and fixable by tap once FR1.4 lands in Sprint 2.

Zero and negative amounts are filtered out for the same reason: the response schema can
enforce INTEGER but not "positive", so the boundary enforces it.

### A message that yields nothing is parsed, not failed

"happy birthday mum" is a message with nothing to log, not an error. It is marked
`PARSED` with zero line items and shown as "nothing to log here".

## Alternatives rejected

- **One message per worker run.** Simpler retry semantics — a failure only affects its own
  message. Rejected because the nightly-ritual usage pattern produces a burst of messages
  at once, and per-message runs would refetch context and pay WorkManager scheduling
  overhead for each.
- **Retry everything with a max attempt count.** Simpler to write, and WorkManager caps
  attempts anyway. Rejected because the free tier has a daily request cap: burning
  retries against a rejected key is spending a scarce, shared, resettable-only-tomorrow
  resource on a request that cannot succeed.
- **Auto-create categories from model output.** Fewer taps for the user, and arguably in
  the spirit of "talk, don't fill forms". Rejected per above — the cost lands later, is
  cumulative, and is paid in exactly the manual tidying the app exists to avoid.

## Consequences

- A `FAILED` message currently has no in-app retry — Sprint 2's correction UI (FR1.4) is
  where "try again" and manual entry belong. Until then a permanent failure is a dead end
  visible in the thread.
- `Clock` is now injected (`TimeModule`) so that relative-date resolution and `createdAt`
  are testable against a fixed today.
- `MomoSmsReceiver` now captures through `CaptureRepository` rather than `MessageDao`
  directly, so SMS-sourced messages are queued for parsing on the same path as typed ones.
  Previously an SMS would have been stored and never parsed.
- Transactions link to messages, but **not yet to groups**. `TransactionEntity` can only
  reference a `GroupContributionEntity`, and contributions do not exist until Sprint 3, so
  a parsed "gave 5000 to choir" keeps the group only in its note text for now. Structured
  group linking is Sprint 3's problem, and may need a schema change.
