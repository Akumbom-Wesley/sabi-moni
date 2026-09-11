# ADR-0021: Bounded retries, and a wait you can see

- **Status:** Accepted
- **Date:** 2026-09-10
- **Refines:** [ADR-0017](0017-parse-worker-and-failure-policy.md) (the retry policy),
  [ADR-0015](0015-offline-capture-parse-queue.md) (the promise that capture never fails
  because connectivity did)

## Context

A message sat at "waiting to be interpreted" for over five minutes on the device. The
JobScheduler dump explained it:

```
JobInfo:
  Minimum latency: +1m59s978ms
  Backoff: policy=1 initial=+30s0ms
Satisfied constraints: TIMING_DELAY CONNECTIVITY ... WITHIN_QUOTA
```

and logcat:

```
15:08:58 WM-WorkerWrapper: Starting work for ParseMessageWorker
15:10:27 WM-WorkerWrapper: Worker result RETRY
```

Nothing was stuck. Every constraint was satisfied, the network was up, and the worker was
running and failing — an 89-second attempt (a 60-second request timeout plus overhead),
then a retry, then another, with the exponential backoff now two minutes wide and doubling.

Three separate faults, none of which is "the AI is slow":

1. **The failure was invisible to the user.** ADR-0017 decided a transient failure leaves
   the message `PENDING_PARSE`, which is right. But the screen renders that as "waiting to
   be interpreted" — identical to a message about to succeed. A retry loop stretching
   towards WorkManager's five-hour backoff ceiling looks exactly like something about to
   happen any second.
2. **The failure was invisible to us, too.** The worker's transient branch was
   `sawTransientFailure = true` and nothing else. No log line, anywhere. A repeating
   failure was undiagnosable from the app itself; it took a JobScheduler dump to find.
3. **Each attempt took far too long to admit defeat.** 60 seconds is a strange request
   ceiling for one short sentence in and a few JSON objects out. It bought nothing and
   spent a minute and a half of the user's patience per attempt.

And there was no override: no way to say "try it now" instead of waiting out a timer with
no visible existence.

## Decision

### Retries are bounded by attempts that actually reached the network

`ParseMessageWorker` reads `runAttemptCount`. On the fourth attempt (`MAX_ATTEMPTS = 3`) a
transient failure stops being retried and marks the message `FAILED`, with the reason plus
"— tap Try again".

**This does not weaken ADR-0015.** The worker carries a `NetworkType.CONNECTED` constraint,
so a phone with no connectivity never runs it and accrues no attempts at all — a capture
made in a tunnel still waits indefinitely and parses itself on reconnect, exactly as
promised. The cap counts only attempts that *had* a network and still failed, which is a
different situation and deserves a different answer.

A visible dead end the user can act on beats an invisible retry loop. With the backoff at
15 seconds this reaches a definite answer in about two minutes.

### Backoff starts at 15 seconds, not 30

Halving it is what makes three attempts land inside two minutes rather than four. The
original 30 was chosen to be gentle on a free-tier quota (ADR-0017); with a hard attempt
cap the total number of requests is now bounded anyway, so the gentleness is no longer
buying protection.

### A pending message says *why* it is still waiting

`failureReason` is now written on a transient failure too, while `status` stays
`PENDING_PARSE`. The two columns together mean: *status* says whether we are still trying,
*failureReason* says why the last attempt did not work. The thread renders
"still trying — No connection" instead of "waiting to be interpreted".

Reusing the existing nullable column rather than adding one: it needs no migration, and
"why the last attempt failed" is genuinely what the field already meant — the only thing
that changes is that a reason no longer implies we have given up. `markParsed` already
clears it.

### Every failure is logged

Both branches log the message id, the attempt number, the reason and its class. The id and
the reason only — **never the message text**, which is personal financial data and has no
business in logcat. That is the same reasoning that kept Ktor's `Logging` plugin out of the
HTTP client (ADR-0012); this ADR is not a licence to relax it.

### The user can skip the backoff

`ParseScheduler` gains `scheduleNow()`, which enqueues with `ExistingWorkPolicy.REPLACE` —
cancelling the request that is sitting out its delay and starting a fresh one immediately.
Safe mid-run, because `commitParse` is transactional: a cancelled run leaves each message
either fully parsed or still pending, never half-committed.

`CaptureRepository.retry` now uses it, and covers both states the user might be looking at:
a `FAILED` message is requeued first, a `PENDING` one is simply run now. A `PARSED` message
still schedules nothing, since `requeueFailed` refuses to move it.

The button appears on a pending message only once an attempt has actually failed — labelled
"Try now" there, and "Try again" on a failed one. A first, healthy pending message gets no
button, because nothing is wrong yet and a retry control would only invite mashing.

### The request timeout drops to 30 seconds

Connect 10s, request and socket 30s. Still generous for slow mobile data, which is the
normal case for this app, but it no longer takes ninety seconds to discover a dead
connection.

## Alternatives rejected

- **A spinner or elapsed-time counter on the pending message.** Honest about duration but
  says nothing about *cause*, and a counter climbing past two minutes with no explanation
  is more alarming than informative. The reason is the useful part.
- **Retry forever, and rely on the user noticing.** What we had. Rejected: the retry loop
  is invisible, the backoff grows towards hours, and "the user will notice" is exactly what
  did not happen for five minutes.
- **Mark it FAILED on the first transient failure.** Simple and maximally visible.
  Rejected — it turns one flaky request on mobile data into work for the user, and a
  single retry genuinely fixes most blips.
- **A `lastError` / `attemptCount` column.** Cleaner separation than reusing
  `failureReason`. Rejected for now: it needs a migration off version 1, and the one
  migration this project is going to want is Sprint 3's group reference. Not worth
  spending a schema version on a string we already have a column for.
- **Keep the 60-second request timeout.** Safest for a very slow link. Rejected: it was
  the single largest contributor to the invisible wait, and 30 seconds is still four times
  what a healthy call to this endpoint takes.
- **Foreground service with a notification while parsing.** Would make the work visible at
  the OS level. Rejected as wildly out of proportion to a request that should take two
  seconds, and it would need a notification permission the app does not yet request.

## Consequences

- A repeating failure now ends in a `FAILED` message with a reason after roughly two
  minutes, instead of retrying invisibly for hours. The user's next action is a button
  that already exists.
- `ParseFailure.Transient` reasons are now user-facing strings in two places — the pending
  label and the failed label — so they have to read as sentences for a person, not
  diagnostics. They already did.
- The 89-second attempt that started this is *still unexplained*: a timeout against
  `generativelanguage.googleapis.com` with the network up. The logging added here is what
  will name it on the next occurrence. Candidates not yet ruled out: an invalid model id
  in `GeminiParsingProvider` (`gemini-3.5-flash-lite`) behaving oddly, or the endpoint
  being slow or blocked from this network.
- `scheduleNow()` can cancel a run in flight. Acceptable because commits are atomic, but
  it does mean a user mashing "Try now" can keep restarting the drain. The button only
  appears after a failure, which limits the opportunity.
