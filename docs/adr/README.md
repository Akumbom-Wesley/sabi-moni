# Architecture Decision Records

Every architectural decision on Sabi Moni gets a numbered record here. The point is
not the decision — that's visible in the code — but the *reasoning* and the
**alternatives we rejected**, which is the part that gets lost otherwise.

## Conventions

- Files are `NNNN-kebab-case-title.md`, numbered sequentially, never renumbered.
- Status is one of `Proposed`, `Accepted`, `Superseded by ADR-NNNN`, `Deprecated`.
- Never rewrite an accepted ADR to reflect a new decision. Write a new ADR and mark
  the old one superseded, so the history of thinking stays readable.
- Add the ADR in the same change that implements the decision.

## Index

| ADR | Title | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-native-android-kotlin-compose.md) | Native Android with Kotlin and Jetpack Compose | Accepted |
| [0003](0003-single-module-package-by-feature.md) | Single Gradle module, package-by-feature | Accepted |
| [0004](0004-mvvm-unidirectional-data-flow.md) | MVVM with unidirectional data flow | Accepted |
| [0005](0005-room-local-first-persistence.md) | Room as the local-first source of truth | Accepted |
| [0006](0006-money-as-integer-xaf.md) | Represent money as whole-XAF integers | Accepted |
| [0007](0007-sdk-levels.md) | SDK levels: min 26, compile/target 37 | Accepted |
| [0008](0008-hilt-for-dependency-injection.md) | Hilt for dependency injection | Accepted |
| [0009](0009-ai-parsing-provider-abstraction.md) | AI parsing behind a provider abstraction | Accepted (provider choice superseded by 0016) |
| [0010](0010-api-key-at-rest-keystore-datastore.md) | API key at rest: Keystore AES-GCM + DataStore | Accepted |
| [0011](0011-personal-public-build-flavors.md) | `personal` / `public` build flavors for SMS access | Accepted |
| [0012](0012-ktor-and-kotlinx-serialization.md) | Ktor client and kotlinx.serialization | Accepted |
| [0013](0013-gradle-version-catalog-and-toolchain-pins.md) | Version catalog and pinned toolchain | Accepted |
| [0014](0014-single-application-id.md) | One applicationId across all variants | Accepted |
| [0015](0015-offline-capture-parse-queue.md) | Capture never blocks on network; parsing queues | Accepted |
| [0016](0016-gemini-as-primary-ai-provider.md) | Gemini (Google AI Studio) as the primary AI provider | Accepted |
| [0017](0017-parse-worker-and-failure-policy.md) | Parse worker, retry policy, and what the model may create | Accepted |
| [0018](0018-corrections-and-manual-entry.md) | Corrections, manual entry, and what "today" means | Accepted (tap target and delete affordance superseded by 0019) |
| [0019](0019-explicit-edit-affordance-and-multi-select-delete.md) | Explicit edit affordance, and delete as a multi-select | Accepted |
| [0020](0020-the-amount-is-a-field-not-prose.md) | The amount is a field, not prose | Accepted |
| [0021](0021-bounded-retries-and-visible-waiting.md) | Bounded retries, and a wait you can see | Accepted |
| [0022](0022-balance-as-the-headline.md) | Balance as the headline, today beneath it | Accepted |
| [0023](0023-theme-choice-in-settings.md) | A theme the user can choose | Accepted |
| [0024](0024-system-bars-and-thread-visual-design.md) | System bars, tonal surfaces, and the shape of the thread | Accepted |
| [0025](0025-how-a-transaction-references-a-group.md) | How a transaction references a group | Accepted |
| [0026](0026-the-contribution-lifecycle-and-its-reminders.md) | The contribution lifecycle and its reminders | Accepted |
