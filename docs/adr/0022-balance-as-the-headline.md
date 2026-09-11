# ADR-0022: Balance as the headline, today beneath it

- **Status:** Accepted
- **Date:** 2026-09-10
- **Refines:** the FR1.5 summary decided in
  [ADR-0018](0018-corrections-and-manual-entry.md)

## Context

The capture screen said "Today" twice: once as the heading of the summary block, and again
as the first day separator in the thread directly beneath it. Two headings, same word, a
divider apart.

The duplication was a symptom rather than the disease. The summary was answering only one
question — *what have I logged tonight* — while the screen had room for the more useful
one: *how am I doing*. With today's net as the largest number on a screen whose whole
purpose is logging today, the header was restating what the list already showed.

## Decision

### The headline figure is the balance, not the day

Income minus expense over everything logged, as the largest number on the screen. Today's
net, income and expense sit beneath it at `titleMedium`/`bodySmall`.

Two questions, two weights. The balance is the one you cannot get by reading the thread;
today's figures are a running tally of the list right below them.

Summed in SQL (`observeBalance`) rather than over a list of rows, unlike the day total
which is deliberately summed in Kotlin off a single day query (ADR-0018) — a day is a
handful of rows, all of history is not.

### It is labelled as derived, not as truth

The caption reads "from everything logged so far". The figure is only as accurate as what
has been entered, and someone who has logged three taxis and no salary will correctly see
a negative number. Calling it "Balance" unqualified would claim knowledge of a real
account that the app does not have and never will — it is local-only by design (ADR-0005).

Shown truthfully in the error colour when negative. This is the "mirror, not judge"
principle from the product brief: the number is the number, and the app does not soften it.

### Today gets no day separator

`withDayHeaders` skips the header for today, because the summary above already carries it.
Earlier days keep theirs, which is where a separator earns its place — marking position in
a list you are scrolling back through.

One label per thing on screen.

## Alternatives rejected

- **Drop the summary's "Today" heading and keep the separator.** Also removes the
  duplication. Rejected because it leaves today's figures floating unlabelled under the
  balance, and the fixed header is a better place for a label than a row that scrolls away.
- **Restyle the separators as centred chips so the repetition reads as intentional.** The
  WhatsApp approach. Rejected: it makes the two labels *look* different without making the
  screen say less, and the complaint was that the screen says the same thing twice.
- **Balance for the current month rather than all time.** More meaningful for someone with
  a monthly salary cycle. Rejected for now — a month boundary is a reporting decision that
  belongs with Sprint 4's range selector, and picking one here would pre-empt it.
- **Hide the balance until some income has been logged**, to avoid opening on a negative
  number. Rejected: a first-run special case that makes the headline figure appear and
  disappear, to hide something that is true.

## Consequences

- The balance ignores savings set-asides, which do not exist yet. FR6.2 says a set-aside
  reduces available balance, so Sprint 5 will have to decide whether this figure means
  "logged net" or "available" — and probably needs both.
- `dayLabel`'s `today` branch is now unreachable in practice. Kept as the fallback for a
  null `skip`.
- The header is taller than it was. Acceptable: the thread scrolls, the header does not,
  and the two figures it now carries are the two the screen exists to show.
