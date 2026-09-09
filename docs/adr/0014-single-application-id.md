# ADR-0014: One applicationId across all variants

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

With two product flavors and two build types
([ADR-0011](0011-personal-public-build-flavors.md)) there are four variants. Android
convention often adds `applicationIdSuffix` values — `.debug` for debug builds,
or a per-flavor suffix — so multiple variants can be installed side by side.

The `applicationId` determines app identity on the device. Two APKs with different
ids are different apps with **separate, non-shared databases**. That is the crux here,
because this app's data is its entire value and there is no cloud backup in v1
(FR8.3): a data directory that is orphaned by an id change is data that is simply
gone.

The realistic usage pattern is unusual: the daily driver will be a *debug* build of the
`personal` flavor, installed and iterated on for weeks or months while features land,
accumulating real financial history the whole time.

## Decision

Use a single `applicationId` — **`com.sabimoni`** — for every variant. No
`applicationIdSuffix` on either build type or either flavor.

## Consequences

- Real logged data survives the transitions that will actually happen: debug →
  release, and `personal` → `public`. Switching from the debug daily driver to a
  signed release build keeps months of history in place, with no export/import step
  and no bespoke migration tooling.
- Only one variant can be installed at a time. Installing `publicDebug` over
  `personalDebug` replaces it.
- Comparing flavors side by side on one device is not possible. Acceptable: verifying
  the `public` flavor is mostly a matter of confirming it compiles and that the merged
  manifest carries no SMS permission — both checkable at build time without installing.
- Installing a build signed with a different key over an existing install fails and
  requires an uninstall, which **does** wipe app data. So the moment a release keystore
  is introduced, that transition must be handled deliberately — that is the one real
  hazard this decision leaves open, and it is why local backup/export is worth having
  before the first release build.
- If the `public` flavor is ever published, it publishes under `com.sabimoni`, which is
  a clean id with no suffix artifact leaking into the store listing.

## Alternatives considered

**`applicationIdSuffix = ".debug"` on the debug build type.** The most common Android
convention, and rejected specifically because of how this app will be used. It would
mean the debug build the user actually lives in for months has a different identity
from the eventual release build, so moving to release silently starts from an empty
database. The usual justification — installing debug and release together — is worth
much less than not orphaning real financial history.

**A per-flavor suffix (e.g. `public` → `.pub`).** Rejected on two counts: it would
publish under an awkward id like `com.sabimoni.pub`, and it would split data between
flavors for no benefit, since the two are never used simultaneously by one person.

**A domain-qualified id (`com.rinork.sabimoni`).** Considered, since reverse-domain
naming is the Java/Android convention and the user has a domain. Rejected as
unnecessary coupling: this is a personal app, not a company product, and `com.sabimoni`
is short and unclaimed. This is cheap to change **now** and effectively permanent
after the first install with real data — so it is worth objecting now if the
domain-qualified form is preferred.
