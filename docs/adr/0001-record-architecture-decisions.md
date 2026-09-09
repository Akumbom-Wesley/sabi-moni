# ADR-0001: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

Sabi Moni is a solo-developer project built in bursts, with gaps between sessions.
The risk is not forgetting *what* was built — the code shows that — but forgetting
*why*. Six weeks from now, "why is money a `Long` and not a `BigDecimal`?" or "why
are there two build flavors?" are questions whose answers are expensive to
reconstruct and easy to accidentally reverse.

That risk is amplified by the fact that much of the work happens with an AI
assistant, which starts each session without memory of previous reasoning. A
written record is the only reliable way for prior thinking to survive.

## Decision

Record every architectural decision as a numbered Markdown file in `docs/adr/`,
following a lightweight version of Michael Nygard's ADR format: Context, Decision,
Consequences, Alternatives considered.

Rules:

- Write the ADR in the same change that implements the decision — not later.
- Always record the alternatives that were rejected and why. This is the highest-value
  section and the one most likely to be skipped.
- Never edit an accepted ADR to reflect a changed mind. Write a new ADR and mark the
  old one `Superseded by ADR-NNNN`.
- Keep them short. An ADR nobody rereads is worthless; one page is the target.

## Consequences

- Small, constant overhead per decision, paid at the moment the decision is cheapest
  to articulate.
- A new contributor (or a future session) can read `docs/adr/` and understand the
  shape of the system without archaeology.
- Reversing a decision becomes a deliberate, visible act rather than a silent drift.

## Alternatives considered

**Document decisions in code comments.** Rejected: comments explain local mechanics,
not system-level tradeoffs, and there is no natural place to put "we chose Hilt over
Koin." They also rot silently when the code moves.

**A single long DECISIONS.md.** Rejected: append-only files get skimmed and then
ignored. Discrete numbered files are individually linkable, individually
supersedable, and show up in diffs as clearly named additions.

**A wiki or Notion page.** Rejected: decisions drift out of sync with the repo when
they live outside it, and this project has a hard $0-cost, no-external-dependency
principle.
