# ADR-0020: The amount is a field, not prose

- **Status:** Accepted
- **Date:** 2026-09-10
- **Builds on:** [ADR-0017](0017-parse-worker-and-failure-policy.md) (enforcing at the
  boundary what the response schema cannot express),
  [ADR-0018](0018-corrections-and-manual-entry.md) (corrections)

## Context

Found on the device. Sending *"Sendt 1600 to my girlfriend"* produced an entry of 1600 with
the note `sent 1600 to my girlfriend`. Correcting the amount to 1500 left the entry reading:

> −1 500 FCFA · sent 1600 to my girlfriend

The correction worked exactly as designed. The display was still wrong, because the amount
was stored **twice** — once as `amountXaf`, once inside the note — and a correction can only
update the field it is given. Two copies of one fact, and no mechanism keeping them in step.

The prompt caused it: `note: a few words of context, e.g. 'taxi to work'` says nothing about
amounts, and restating the input is the natural thing for a language model to do when asked
for context.

This will keep happening as more fields are corrected. It is not a display bug; it is
duplicated state.

## Decision

### The note may never contain the amount

The amount lives in `amountXaf` and nowhere else. The note carries what the amount cannot:
who, what, where. For *"sent 1600 to my girlfriend"* the note is `sent to my girlfriend`.

Enforced in two places, because one is not enough:

1. **The prompt says so explicitly**, with the failing example and the reason. This is where
   most of the work happens, and it costs nothing.
2. **`stripRestatedAmount` enforces it at the boundary**, in `TransactionDraft.toEntity`
   alongside the positive-amount filter and the category resolution. A prompt is advice and
   the response schema cannot express "no amount in this string", so the same reasoning
   ADR-0017 used for non-positive amounts applies: if the invariant matters, the boundary
   holds it rather than trusting the model.

The stripper removes a digit run only when it **equals** the amount, in any grouping the
model might use (`1600`, `1,600`, `1 600`, `1.600`, and the narrow-space form the app
itself formats), plus a currency word left dangling behind it. Over-matching therefore
costs a missed strip, never a wrong edit — `16000` is not `1600`, and `3 loaves of bread`
keeps its 3.

### Only model output is sanitised

A note the *user* typed is stored exactly as typed, amount and all. If someone writes
"1600 for the room" in the note field themselves, that is their sentence, not a duplicated
field, and rewriting it under them would be the app being clever about the user's own words.

The rule is about a machine copying a value it was already given a field for.

### The message text above stays as sent

The raw text of the message still reads *"Sendt 1600 to my girlfriend"* after the
correction, and that is correct. It is the record of what was said, not an assertion about
what is true now — ADR-0018 already settled that the message is immutable and only the
entries derived from it are editable. Rewriting the user's own sentence to match a later
correction would destroy the audit trail and claim they typed something they did not.

## Alternatives rejected

- **Rewrite the note when the amount is corrected.** The obvious reading of "it should
  update". Rejected: it needs the app to find and replace a number inside a sentence it did
  not write, at the moment the user is editing something else. It fails on any note that
  paraphrases ("about 1.6k"), and it treats the symptom while leaving one fact stored in two
  places.
- **Show only the category and hide the note next to the amount.** Would have hidden this
  particular contradiction. Rejected — the note is the most useful part of the confirmation
  ("to my girlfriend" is the whole point of the entry), and hiding a field to conceal that
  it disagrees with another field is not a fix.
- **Strip every number from model notes.** Simpler than an equality check. Rejected: "3
  loaves", "bus 90", "2 nights" are real context, and deleting them would make notes worse
  than the bug did.
- **Prompt only, no boundary check.** Less code. Rejected for the reason ADR-0017 gives —
  the model is the least reliable component in the system, and an invariant that only holds
  when it cooperates is not an invariant.
- **A migration to clean notes already stored.** Tempting, since those rows are
  model-generated. Rejected: it rewrites stored data on a guess about rows nobody has
  looked at, for a database with exactly one user who can fix the two or three affected
  notes by tapping edit. Not worth a migration off version 1.

## Consequences

- Entries **already in the database** keep their stale notes; only newly parsed entries are
  clean. Editing the note by hand, or deleting the entry and re-sending the message, fixes
  one.
- `stripRestatedAmount` lives in `core/parse` rather than `core/money`: it is a rule about
  what a parse is allowed to produce, not an operation on money.
- A note that was *only* the amount becomes null, and the entry then displays its category
  alone, or "uncategorised".
- The same rule will need applying to any future field the model can restate in prose — a
  date, most likely. The stripper is written for the amount specifically, and generalising
  it now would be guessing.
