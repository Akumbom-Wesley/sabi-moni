# ADR-0015: Capture never blocks on network; parsing queues

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

The product's central premise is a 5-minute nightly ritual done at low energy
(design principle 2). If capture can fail, the ritual breaks, and a habit that breaks
once tends not to come back. Connectivity in the target environment is not
guaranteed — and the moment the user is most likely to log the day is not necessarily
a moment they have data.

But interpretation *requires* the network, because parsing is done by a hosted model
([ADR-0009](0009-ai-parsing-provider-abstraction.md)). So capture and interpretation
have fundamentally different availability requirements, and the spec separates them
explicitly (FR1.2, FR2.3, FR8.1).

## Decision

Split the write path in two, with the `Message` table as the durable boundary.

1. **Capture is a local write, always.** Sending a message inserts a `Message` row
   with `status = PENDING_PARSE` and returns. It touches no network, cannot fail for
   connectivity reasons, and is the only thing standing between the user pressing send
   and their input being safe.
2. **Parsing is a queued background job.** A `WorkManager` job with a
   `NetworkType.CONNECTED` constraint picks up pending messages, calls the provider,
   writes the resulting `TransactionDraft`s, and sets `status = PARSED`. Enqueued
   immediately on send, so it runs at once when online, and deferred by the OS when
   not.

Details:

- WorkManager is chosen because it persists across process death and reboot. A message
  captured offline is still parsed after the phone is restarted.
- Retries use WorkManager's exponential backoff. A permanent failure (malformed
  response, revoked API key) sets `status = FAILED` with a reason, so the message
  remains visible and correctable rather than silently vanishing.
- The UI surfaces queue depth — "3 entries pending" (FR2.3) — read reactively from
  Room, so the user always knows nothing was lost.
- The same path serves SMS-captured messages (FR3.1): the receiver inserts a `Message`
  with `source = SMS` and enqueues the identical worker. One queue, one parser.
- The worker gets its dependencies through Hilt's `WorkerFactory`
  ([ADR-0008](0008-hilt-for-dependency-injection.md)), since WorkManager constructs
  workers itself.

## Consequences

- Capture is unconditionally reliable, which is the property the whole product rests
  on. Nothing the user types is ever at the mercy of a network call.
- Parse results arriving asynchronously — possibly long after the app was closed —
  flow into the UI automatically via Room's reactive queries
  ([ADR-0005](0005-room-local-first-persistence.md),
  [ADR-0004](0004-mvvm-unidirectional-data-flow.md)). No polling, no refresh button.
- Every `Transaction` traces back to its originating `Message`, so the chat thread
  doubles as an audit log.
- The UI must handle a genuinely mixed thread: parsed messages, pending messages, and
  failed messages all coexist and need distinct presentation. This is real added UI
  complexity that a synchronous design would avoid.
- Timing is not guaranteed. Doze mode and battery optimisation can delay a job
  meaningfully, so "sent" and "interpreted" can be far apart, and the UI must never
  imply otherwise.
- Two writes per message instead of one, and a state machine
  (`PENDING_PARSE → PARSED | FAILED`) to keep correct.

## Alternatives considered

**Parse synchronously on send, blocking the UI.** Rejected: it makes the core loop
fail whenever the network does, breaking design principle 2 and FR1.2. It is also the
worst possible latency placement — the user waits on a 70B model before their input is
safe.

**Parse synchronously but keep the raw text on failure.** Better, and closer to
correct, but rejected: it needs a retry mechanism anyway, and a hand-rolled one
would not survive process death or reboot. That is precisely what WorkManager already
provides.

**A plain coroutine on an application-scoped `CoroutineScope`.** Rejected: it dies with
the process. A message captured offline and then followed by the user closing the app
would never be parsed.

**Foreground service for parsing.** Rejected as wildly disproportionate — this is one
short HTTP request, not a long upload, and it would require a persistent notification.

**Let the user trigger parsing manually.** Rejected: it adds a step to a ritual whose
entire design goal is having as few steps as possible.
