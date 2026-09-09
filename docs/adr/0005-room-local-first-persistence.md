# ADR-0005: Room as the local-first source of truth

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

All confirmed financial data lives on the device (FR8.2), there is no backend, and no
cloud sync in v1 (FR8.3). The data is relational: transactions reference categories,
messages and group contributions; contributions reference groups; reminders reference
contributions.

Reports (FR5.x) need aggregation across arbitrary date ranges grouped by category and
by group — sums, filters, date bucketing. The chat thread needs to observe changes
reactively so that a parse completing in the background updates the UI without a
manual refresh.

## Decision

Use **Room** over SQLite as the single on-device source of truth.

- DAOs return `Flow<T>` for anything the UI observes, so Room's invalidation tracker
  drives recomposition automatically.
- Aggregation for reports is done in SQL (`SUM`, `GROUP BY`, date-range `WHERE`),
  not by loading rows into memory and folding them in Kotlin.
- `exportSchema = true`, with schemas committed under `app/schemas/`, so migrations
  can be written and verified against real historical schemas.
- Entities are persistence types and stay in `core/data`. Repositories map them to
  domain models before they cross into features (per
  [ADR-0004](0004-mvvm-unidirectional-data-flow.md)).
- Every `Transaction` keeps a nullable foreign key to the `Message` that produced it,
  making the chat thread a genuine audit log: any number in a report can be traced
  back to the exact text that created it.

## Consequences

- Compile-time verification of every query. A typo'd column name fails the build
  rather than crashing on a user's report screen.
- Reactive queries make the offline-parse-later flow work with no extra plumbing.
- Reports stay fast because aggregation happens in SQLite, which is what it is good at.
- Schema changes require real migrations once there is data worth keeping. Committed
  schema JSON makes this tractable but it is genuine ongoing work.
- KSP annotation processing adds to build time.

## Alternatives considered

**SQLDelight.** A legitimate contender — SQL-first, generates Kotlin from `.sq` files,
excellent type safety. Rejected because Room is the Android-standard choice with
first-class AndroidX integration (`WorkManager`, Paging, testing artifacts), and its
`Flow` invalidation is battle-tested. SQLDelight's main edge is multiplatform, which
[ADR-0002](0002-native-android-kotlin-compose.md) explicitly rules out.

**Realm / ObjectBox.** Rejected: object databases make ad-hoc range aggregation for
reports awkward, and both add a heavier runtime plus vendor risk for no gain here.

**DataStore or JSON files.** Rejected: no query capability whatsoever. Computing
"spend by category for Q2" would mean loading and folding the entire history in
memory. DataStore is used in this project, but only for small key-value settings
(see [ADR-0010](0010-api-key-at-rest-keystore-datastore.md)).

**Raw SQLite with hand-written helpers.** Rejected: reimplements Room's mapping,
invalidation and migration support, with no compile-time query checking.
