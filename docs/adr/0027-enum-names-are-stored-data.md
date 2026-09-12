# ADR-0027: Enum names are stored data

- **Status:** Accepted — the specific rename below is moot, since
  [ADR-0028](0028-groups-have-no-type.md) deleted `GroupType` entirely. The general rule,
  that converter-backed enum names are a storage format, stands.
- **Date:** 2026-09-11
- **Builds on:** [ADR-0005](0005-room-local-first-persistence.md) (Room as the source of
  truth), [ADR-0025](0025-how-a-transaction-references-a-group.md) (database version 2)

## Context

`GroupType.CONTRIBUTION` was the wrong name. The spec's own list of this user's groups is
church, choir, charity and school-related (§3), and "contribution" is what every group
asks for, not a kind of group — so the default type was labelled with a word that
distinguishes nothing. It should be `CHURCH`.

Renaming a Kotlin enum constant is normally a refactor. Here it is not: `Converters`
persists every enum by `name` and reads it back with `valueOf`. A group already stored as
`CONTRIBUTION` — there was one on the device, "Triumphant Singers" — would throw
`IllegalArgumentException` the moment it was read, which is every time the Groups tab
opens.

## Decision

### The rename ships with a data migration, at database version 3

`MIGRATION_2_3` runs one statement:

```sql
UPDATE money_groups SET type = 'CHURCH' WHERE type = 'CONTRIBUTION'
```

Hand-written rather than an `@AutoMigration`, unlike v1 → v2. Nothing about the *schema*
changes — `type` was and remains a TEXT column — so the two exported schemas are
structurally identical and Room has nothing to derive a migration from. The change is
entirely in the data.

### Enum names used by `Converters` are treated as a storage format

Recorded here because this will come round again: `MessageSource`, `ParseStatus`,
`Direction`, `MoneySource`, `GroupType` and `ContributionStatus` all persist by `name`.
Every one of those constants is a value written into the user's database, and renaming any
of them is a migration.

`Enums.kt` now says so at the point where someone would be tempted, rather than leaving it
to be discovered by a crash.

## Alternatives rejected

- **Store enums by ordinal instead of name.** A rename would then be free. Rejected
  emphatically: ordinals make *reordering or inserting* a constant silently corrupt every
  existing row, which is a far easier mistake to make than a rename and far harder to
  notice. Names fail loudly; ordinals fail quietly.
- **A tolerant converter that maps unknown names to a fallback.** Would have avoided the
  crash with no migration. Rejected — it would turn any future typo or removed constant
  into silent data reinterpretation, and "Triumphant Singers" would have quietly become
  whatever the fallback was.
- **Keep `CONTRIBUTION` and only change the display label to "Church".** No migration, no
  version bump. Rejected: the stored value and the thing it means would then disagree
  permanently, which is exactly the kind of drift ADR-0020 was written about.
- **Add `CHURCH` and deprecate `CONTRIBUTION`.** Avoids touching data. Rejected — it
  leaves a dead option in a five-item picker and defers the migration rather than removing
  the need for it.

## Consequences

- Database version 3. The second migration, and the first hand-written one.
- Verified on the device against real data: `user_version` is 3 and the stored type reads
  `CHURCH`, with the group intact.
- `GroupEntity.type` and `GroupRepository.createGroup` now default to `CHURCH`. It is the
  most common case for this user, but it is a default rather than a judgement — the picker
  offers all five.
- A future rename of any converter-backed enum needs the same treatment. There is no test
  covering that, and a `MigrationTestHelper` test over the schema JSONs is the honest way
  to protect it — noted rather than done, since the instrumented suite is not being run
  yet.
