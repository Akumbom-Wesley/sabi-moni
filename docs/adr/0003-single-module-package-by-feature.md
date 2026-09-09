# ADR-0003: Single Gradle module, package-by-feature

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

The app has five reasonably distinct feature areas — conversational capture, group
obligations, reports, savings goals, settings — plus shared infrastructure (Room,
the AI provider, the money type, secure key storage).

It is also a solo project with a hard requirement for two build flavors
(see [ADR-0011](0011-personal-public-build-flavors.md)), and the flavor split is what
makes module structure non-obvious: flavor configuration has to be replicated or
shared via convention plugins once modules multiply.

## Decision

Use a **single `:app` Gradle module**, organised **package-by-feature**:

```
com.sabimoni/
  core/
    ai/          AiParsingProvider, Groq implementation, prompt building
    data/        Room database, entities, DAOs, repositories
    money/       Money value class, XAF formatting
    security/    ApiKeyStore (Keystore + DataStore)
    sms/         SmsCaptureGateway interface (flavor-implemented)
  feature/
    capture/     chat thread — the core loop
    groups/      obligations, due dates, paid/missed
    reports/     retrospective breakdowns
    savings/     goals
    settings/    API key entry, app lock, category management
  ui/
    theme/       Material 3 theme
    navigation/  type-safe routes, NavHost, bottom bar
```

Each `feature/*` package holds its own screen, ViewModel, and UI state — a change to
one feature touches one folder.

## Consequences

- Simplest possible Gradle setup: one `build.gradle.kts` for the app, one flavor
  block, one Hilt/KSP configuration. No convention plugins needed.
- Fast to bootstrap, and easy to reason about while the codebase is small.
- Layering is enforced only by convention, not by the compiler. Nothing stops a
  `feature/` class from reaching into another feature's internals; discipline is
  manual. Kotlin's `internal` visibility gives no protection within a single module.
- Whole-module recompiles on change. At this size that is fine; if incremental builds
  become slow, extracting `:core:data` and `:core:ai` is the first move, and
  package-by-feature makes that extraction mechanical rather than a rewrite.

## Alternatives considered

**Multi-module (`:core:*`, `:feature:*`).** Rejected as premature for a solo app.
It buys compiler-enforced layering and build parallelism, but costs real setup time,
and the `personal`/`public` flavor configuration would need to be shared across
modules via convention plugins — significant added Gradle complexity for the exact
feature that most needs to stay simple and auditable. Revisit if build times hurt or
if a second developer joins.

**Single module, package-by-layer (`data/`, `domain/`, `ui/`).** Rejected: with this
many features, adding a field to a group contribution would mean edits in three
distant top-level folders. Package-by-feature keeps related code adjacent, which
matters far more than layer purity at this scale.
