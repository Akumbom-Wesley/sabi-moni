# ADR-0030: Reports are a mirror, and bars beat a chart library

- **Status:** Accepted
- **Date:** 2026-10-07

## Context

Sprint 4 builds the retrospective half of the product (FR5.1–5.4): what happened, by
category, by group, income against expense, over a range the user chooses.

The DAO work was mostly already there — `observeSpendByCategory`, `observeTotal` and
`observeInRange` all predate this sprint — so the open questions were about shape
rather than data: how a range is represented, where aggregation happens, what a
breakdown looks like, and what a figure does when you tap it.

Design principle 3 constrains all of it: **mirror, not judge**. FR5.4 says the same
thing in spec terms — no caps, no blocking alerts, data only.

## Decision

**Reports never write.** `ReportRepository` exposes reads and nothing else. A report
that could change the ledger it reports on would stop being a mirror, and the type
system is a cheaper guarantee than remembering.

**A range is a period plus an integer offset**, not a stored pair of dates.
`ReportPeriod.rangeAround(anchor, offset)` derives the span, so stepping back a month
is arithmetic rather than bookkeeping, and every period resolves through one function.
`CUSTOM` sits in the same enum because it differs only in where its two dates come
from.

**Weeks start on Monday, always** — `WeekFields.ISO`, not the device locale. A locale
week would mean the same seven days of spending totalling differently on a phone set
to a different region, and a ledger that changes its mind about what a week is cannot
be a mirror.

**`today` is resolved once per screen model**, not per emission, so a range cannot
shift under someone reading it at midnight.

**Totals and breakdowns aggregate in SQL; the drill-down list is one row query.** The
four summary figures stay four numbers however long the range (ADR-0005), while the
entries behind them come from a single `observeRowsInRange`, filtered in Kotlin by
whatever slice is focused. One read behind both means a number and the list under it
cannot disagree — the same reasoning as `GroupContributionDao.observeAllRows`.

**Breakdowns are ranked horizontal bars, drawn in Compose. No charting library.**
`vico` sits in the version catalog and the sprint plan lists adding it; it is
deliberately still not a dependency. The question a breakdown answers is "what cost
the most", which is a comparison of lengths against a shared baseline — a bar does
that natively, and a pie does it worse with every slice added. The bars are about
thirty lines of `Box` and `fillMaxWidth(fraction)`.

**Uncategorised keeps its own slice**, labelled rather than hidden or folded into a
catch-all. "Uncategorised is the biggest bar" is a useful thing to be told, and
quietly dropping unlabelled money is the one thing a mirror must not do.

**The by-group breakdown counts only entries that name a group.** It answers "what did
the groups cost me", and an ungrouped bucket there would be every ordinary expense in
the month towering over the groups beside it. The category breakdown already covers
everything.

## Consequences

- No new dependency, so no library version to track against the Kotlin ceiling that
  [ADR-0013](0013-gradle-version-catalog-and-toolchain-pins.md) already pins, and
  nothing new in the APK.
- The breakdown renders identically in both themes because it uses scheme colours
  rather than a library's palette ([ADR-0024](0024-system-bars-and-thread-visual-design.md)).
- A pie chart, a trend line over time, or anything stacked would need rethinking —
  probably by adding `vico` then. This decision buys simplicity now, not forever.
- Range logic is pure and unit-tested (14 tests), including the cases that actually
  bite: stepping back from the 31st, short and leap Februaries, year boundaries, and
  consecutive periods touching without overlap so nothing is double-counted or lost.
- Filtering the drill-down in Kotlin means a year's rows are held in memory while that
  range is open. For one person that is thousands of rows at most; if it ever stops
  being so, the fix is a filtered query, not a redesign.
- `vico` stays in the catalog unused. Harmless, and it marks the intent if a later
  sprint wants a chart that bars cannot draw.

## Alternatives considered

**Add `vico` and draw a pie, as the sprint plan said.** Rejected for now, on the
grounds above: it is a dependency, a palette to reconcile with the theme, and an API
to learn, in exchange for a worse answer to the question being asked. Recorded as a
deliberate departure from the plan rather than an oversight.

**Store the range as two dates in the UI state.** Rejected: stepping would then mean
recomputing both ends by hand at every call site, and "which month am I on" would live
in two places that can disagree.

**Derive the totals by folding the row list in Kotlin.** Simpler, and it would make the
one-read consistency total rather than near-total. Rejected because a yearly report
would then carry every row across the boundary twice over, against ADR-0005's whole
point about letting SQLite do what it is good at.

**Hide the uncategorised slice.** Rejected: it would make the bars add up to less than
the total with nothing saying why, which is a mirror lying by omission.

**Budgets, targets or "you spent more than last month" nudges.** Rejected on the
spec's own terms (FR5.4). The product is explicitly not an enforcer, and comparison
framing is the first step toward judging.
