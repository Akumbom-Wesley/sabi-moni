# ADR-0028: Groups have no type

- **Status:** Accepted
- **Date:** 2026-09-11
- **Supersedes:** the rename in [ADR-0027](0027-enum-names-are-stored-data.md) — there is
  no longer a `GroupType` to name. That ADR's general rule, that converter-backed enum
  names are a storage format, stands and still applies to the five remaining enums.

## Context

`GroupType` was in the schema from the scaffold: `CONTRIBUTION`, `TONTINE`, `CHARITY`,
`SCHOOL`, `OTHER`. ADR-0027 had just renamed the first one, which is what prompted the
better question: what reads it?

Nothing. A grep found exactly three uses, all of them plumbing — the picker writing it, the
repository storing it, the dialog reading it back to pre-select the chip. No query filters
on it, nothing branches on it, and Sprint 4's reports are by category and by group, not by
group type.

So it was a required choice, from a five-option picker, on every group the user creates,
that changed nothing about how the app behaves. The product brief's own test is "does this
add structure the user must maintain?" — and this was structure the user had to maintain
for no return. A group needs a name, a penalty, and a lead time; what it *is* only matters
to a human already reading its name.

## Decision

### `GroupType` is deleted, and `money_groups.type` dropped at version 4

The enum, the two `Converters` methods, the field on `GroupEntity`, the field on
`MoneyGroup`, the field on `GroupEdit`, and the Type chip row in the dialog all go.

The column is dropped with `@DeleteColumn` on a `DropGroupType : AutoMigrationSpec`, rather
than inferred. Room deliberately refuses to guess that a vanished column was meant to go,
because the other reading — someone renamed it — would silently discard a column of real
data. Declaring it is how you say you meant it, and Room then generates the table
recreation, since SQLite cannot `DROP COLUMN` at this project's minimum API level
(ADR-0007: min 26).

Verified on the device against real data: `user_version` is 4, `money_groups` no longer has
a `type` column, and the existing group survived.

### The v2 → v3 migration stays, though it is now pointless

`MIGRATION_2_3` writes `'CHURCH'` into a column that v3 → v4 then deletes. Dead work, kept
because **a database exists in the world at version 3** — the daily driver ran it for about
ten minutes — and Room needs an unbroken path from every version that was ever real.

Collapsing 2, 3 and 4 into one step would be tidier and would break that device.

## Alternatives rejected

- **Keep the column, drop it from the UI.** No migration, and the data is there if reports
  ever want it. Rejected: a column nothing writes and nothing reads is a field that will be
  wrong whenever someone finally looks at it, and "we might want it" is how the taxonomy
  problem ADR-0017 guards against starts.
- **Keep the type as a free-text label instead of an enum.** More flexible. Rejected — it
  is the group's name that already carries this. "Choir" does not need to also be tagged
  "choir".
- **Keep it for Sprint 4's reports.** Rejected after reading FR5.1, which asks for by
  category, by group, and income versus expense. Grouping several groups by type is a
  report nobody asked for, over a field nobody filled in accurately.
- **Leave it until it is actually in the way.** Rejected because it is in the way now: it
  is a mandatory five-option decision on the create-group form, and the previous two
  changes to this project were both about removing choices that earn nothing.

## Consequences

- Database version 4, and the third migration in two days. The `money_groups` table is now
  `id, name, penaltyXaf, reminderLeadDays, isArchived`.
- The new-group dialog is down to three fields, one of which is optional. Creating a group
  is a name and a tap.
- `GroupEditorDialog` lost the `ChipFlowRow` that was added minutes earlier to stop the
  Type row clipping. The lead-time row still needs it, and the shared component is used by
  the date rows too, so it stays.
- Any future "spending by kind of obligation" report would need this back. If that ever
  happens it should come from what the user actually wants to see, not from a field kept
  alive on spec.
