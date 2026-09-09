# ADR-0013: Version catalog and pinned toolchain

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

The project combines several tightly coupled build components — AGP, Gradle, the
Kotlin compiler, the Compose compiler plugin, KSP, Room, and Hilt. Mismatches between
them produce build failures whose error messages rarely name the actual cause.

Three couplings constrain everything else. All three were established by running
builds and reading published metadata, **not** by assuming — and the first two
attempts at a version set were both wrong.

**1. AGP has a hard Gradle floor, enforced via internal APIs.** The first attempt
paired AGP `9.3.2` with Gradle `9.2.1`, chosen because 9.2.1 was already in the local
wrapper cache and would need no download. It fails at task-graph calculation:

```
NoClassDefFoundError: org/gradle/features/binding/ProjectTypeBinding
```

AGP 9.3+ needs Gradle's newer software-features API. A locally cached Gradle
distribution is not a reason to pick a version.

**2. AGP 9 owns Kotlin. Applying the Kotlin plugin is now a hard error.** The second
attempt kept `org.jetbrains.kotlin.android` at 2.3.21, which fails outright:

```
The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support
since AGP 9.0.
```

AGP 9 has **built-in Kotlin support** and refuses to coexist with the standalone
plugin.

**3. Consequently, Kotlin is chosen by AGP, and KSP must follow.** AGP 9.4.0 declares
a dependency on `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10`, so 2.2.10 is the
Kotlin version. Kotlin's own latest release is 2.4.20 and KSP's newest line is 2.3.x,
but neither is reachable without either overriding AGP's Kotlin via a `buildscript`
classpath entry or opting out with `android.builtInKotlin=false`.

## Decision

Declare all versions in a single **Gradle version catalog** at
`gradle/libs.versions.toml`, referenced everywhere as `libs.*` aliases, and pin to
this verified set:

| Component | Version | Why this one |
|---|---|---|
| Gradle | 9.7.1 | Current stable; above AGP 9.4's floor |
| AGP | 9.4.0 | Current stable, paired with a same-generation Gradle |
| Kotlin | 2.2.10 | **Dictated by AGP 9.4.0's built-in Kotlin**, not chosen |
| KSP | 2.2.10-2.0.2 | The KSP release built against Kotlin 2.2.10 |
| Compose BOM | 2026.08.00 | BOM manages all `androidx.compose.*` versions together |
| JDK | 21 | Android Studio's bundled JBR; no separate JDK install |

Specifically:

- **Do not apply `org.jetbrains.kotlin.android`.** AGP provides Kotlin compilation.
- **Accept AGP's bundled Kotlin rather than overriding it.** The Compose and
  serialization plugin versions are therefore also 2.2.10 — they must match the
  compiler exactly.
- Let AGP derive the Kotlin JVM target from `compileOptions` instead of setting
  `jvmTarget` separately, so the Java and Kotlin targets cannot drift apart.
- Set **`android.disallowKotlinSourceSets=false`** in `gradle.properties`. KSP
  `2.2.10-2.0.2` — the only KSP built against AGP 9.4's Kotlin — registers its
  generated sources through `kotlin.sourceSets`, which built-in Kotlin rejects by
  default. This is a transitional flag AGP provides for exactly this gap, not a
  workaround we invented; remove it once KSP moves to `android.sourceSets`.
- No version literals in any `build.gradle.kts`; everything resolves through the
  catalog, so a version appears exactly once.
- `compileSdk` / `minSdk` / `targetSdk` also live in the catalog
  ([ADR-0007](0007-sdk-levels.md)), keeping every number that defines the build in one
  file.
- Upgrade **AGP, Kotlin and KSP as one unit**, and verify with a real build.

## Consequences

- One file to read to know what the project is built with, and one file to edit to
  upgrade — which matters when returning after weeks away.
- Two build flavors ([ADR-0011](0011-personal-public-build-flavors.md)) share one
  dependency set, so the flavors cannot drift.
- **Kotlin upgrades are now gated on AGP upgrades.** Kotlin 2.4.x language features
  are unavailable until AGP ships them. Nothing in this codebase needs them, and the
  trade buys guaranteed compiler/Compose-plugin/KSP alignment — historically the most
  common source of unexplained Android build breakage.
- The awkward pre-AGP-9 situation where KSP capped the Kotlin version is now moot:
  AGP's bundled Kotlin is conservative enough that a matching KSP always exists.
- The first build downloads Gradle 9.7.1 (~250 MB for `-all`). One-time cost; the
  `-all` variant is worth it for IDE source and docs support.
- Pinned versions do not update themselves; finding a newer coherent set is manual.

## Alternatives considered

**Override AGP's Kotlin to 2.3.21 via a `buildscript` classpath entry.** Documented and
supported, and it would have preserved the newer Kotlin. Rejected: it reintroduces
manual alignment of compiler, Compose plugin and KSP — exactly the class of breakage
built-in Kotlin removes — to gain language features the project does not use.

**Opt out of built-in Kotlin with `android.builtInKotlin=false`.** Rejected: it is a
migration escape hatch, not a destination, and betting a greenfield project on a path
Google is deprecating buys nothing.

**Gradle 9.2.1 with AGP 9.3.2.** Attempted and **rejected on evidence** — it does not
build. Recorded because the appeal (no download, works offline) will tempt a revisit.

**The AGP 8.13.x line.** Rejected as a generation behind; a greenfield project has no
migration cost to avoid.

**Versions inline in `build.gradle.kts`.** Rejected: with two flavors and a dozen
`androidx` artifacts, the same version string gets duplicated and they drift.

**A `buildSrc` object with version constants.** Rejected: any change to `buildSrc`
invalidates the whole build's configuration cache, making edits slow.

**KAPT instead of KSP.** Rejected: KSP is substantially faster and KAPT is effectively
deprecated for Kotlin.
