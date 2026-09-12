# ADR-0025: How a transaction references a group

- **Status:** Accepted
- **Date:** 2026-09-11
- **Closes the gap left by:** [ADR-0017](0017-parse-worker-and-failure-policy.md),
  [ADR-0018](0018-corrections-and-manual-entry.md)

## Context

Two sprints have deferred to this decision.

`TransactionEntity` could reference a `GroupContributionEntity` but not a `GroupEntity`.
That was fine while contributions did not exist, and wrong as soon as anything real
happened:

- A parsed *"gave 5000 to choir"* had a group in it, and no announced obligation to hang it
  on. The group survived only as words in the note (ADR-0017).
- FR1.4 lists group among the correctable fields. ADR-0018 left it out of the editor rather
  than fabricate a contribution — an obligation with a due date and a penalty that nobody
  announced — from a user who was only fixing a category.

So: money can go to a group without settling any specific obligation, and the schema had no
way to say so.

## Decision

### `transactions.groupId`, alongside the existing `groupContributionId`

- `groupId` — which group this money went to or came from. Set for a settled obligation
  *and* for an unannounced gift.
- `groupContributionId` — the specific announced obligation this transaction settles, when
  there is one. Null for the gift.

Two nullable references rather than one, which is redundancy: the contribution already
knows its own group, so `groupId` can in principle disagree with
`groupContributionId → groupId`.

### The redundancy is made unrepresentable, not merely documented

No repository method takes both. `groupId` is **derived, never accepted**, whenever a
contribution is involved: settling a contribution looks up that contribution's own group
and writes both columns from it. Attributing money to a bare group writes `groupId` alone.

There is therefore no call the rest of the app can make that sets the two inconsistently.
This is the same move as ADR-0018's five-column correction, which cannot rewrite
provenance because provenance is not in the statement: if an invariant matters, make it
structural rather than a rule someone has to remember.

### Why not just `groupId`, with the payment recorded on the contribution

The tidier alternative — drop `groupContributionId`, give `GroupContributionEntity` a
`paidTransactionId` — puts every fact in exactly one place and was genuinely tempting,
being the same "don't store one fact twice" principle as ADR-0020.

Rejected because it makes one assumption the informal obligations this app exists for do
not support: that a contribution is settled by exactly **one** transaction. v1 marks a
contribution Paid or Missed and nothing else (FR4.5), so a single link is enough *today* —
but paying a church contribution in two instalments is an ordinary thing to do, and a
single `paidTransactionId` cannot express it. `groupContributionId` on the transaction is
many-to-one already, so the door stays open without a second migration.

That is a deliberate bet, not an oversight: the redundancy is cheap and contained, and the
schema change to undo the wrong choice later is not.

### Migrated with `@AutoMigration`, not hand-written SQL

SQLite cannot add a foreign key with `ALTER TABLE`, so v1 → v2 has to recreate
`transactions` and copy every row. Room derives that recreation from the two exported
schema JSONs, and the generated migration is byte-identical to what the schema demands
plus a `foreignKeyCheck` afterwards.

Hand-writing a twelve-column table recreation against a database that holds the user's
actual money, to save reading a generated file, is the wrong trade. The change is a pure
addition, which is the case auto-migration handles without ambiguity.

Existing rows get `groupId = NULL`, which is correct: no transaction logged before this
point was ever attributed to a group.

## Alternatives rejected

- **Only `groupId`, with `paidTransactionId` on the contribution.** See above — one home
  per fact, but it forecloses instalments.
- **Only `groupContributionId`, auto-creating a contribution for an unannounced gift.**
  Already rejected in ADR-0018 and still wrong: it invents a *financial obligation*, with a
  due date and a penalty, as a side effect of logging a gift. It also poisons FR4.6 —
  "what I owe right now" would include obligations nobody asked for.
- **A join table between transactions and groups.** Correct for many-to-many. Rejected:
  one transaction goes to one group, and a join table would buy nothing but a third table
  to keep consistent.
- **Keep the group in the note text and parse it back when needed.** Requires no schema
  change. Rejected for the obvious reason — it is not data, it is a string that happens to
  contain a word, and ADR-0020 has just finished removing the last place we relied on that.

## Consequences

- Database version 2. The first migration this project has needed, and the one both
  previous sprints were saving the version bump for.
- `GroupEntity` deletion sets `transactions.groupId` to null (`SET_NULL`), so deleting a
  group orphans its history rather than destroying money records. Groups are archived
  rather than deleted in practice (`isArchived`), so this is a backstop.
- The parser's `matchedGroup`, returned since Sprint 1 and discarded ever since, can now be
  resolved to a real `groupId` — on the same terms as categories: matched against existing
  groups, never creating one (ADR-0017).
- FR1.4's group field is now implementable, and the editor gains a group picker.
- Reporting by group is a single `WHERE groupId = ?` rather than a join through
  contributions, which matters for Sprint 4.
