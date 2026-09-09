# ADR-0007: SDK levels — min 26, compile/target 37

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

This is a single-user app whose primary install target is one known phone, sideloaded
(see [ADR-0011](0011-personal-public-build-flavors.md)). Broad device reach is
therefore worth much less than it would be for a published app, and the usual
"maximise compatibility" instinct does not apply with full force.

**The minimum** is pushed upward by two platform features:

- `java.time` (`LocalDate`, `Instant`) is used throughout for due dates and
  timestamps. It is available natively from **API 26**; below that it requires Gradle
  core library desugaring.
- Android Keystore's AES-GCM key properties used for API key encryption
  ([ADR-0010](0010-api-key-at-rest-keystore-datastore.md)) are cleanest from API 26.

**The compile level is not a free choice.** The first build attempt used
`compileSdk = 36`, because android-36 and android-36.1 were the platforms already
installed. It fails: eighteen AndroidX dependencies refuse it, including

```
androidx.compose.ui:ui-android:1.12.0 requires ... compile against version 37 or later
androidx.core:core-ktx:1.19.0        requires ... compile against version 37 or later
```

The Compose BOM ([ADR-0013](0013-gradle-version-catalog-and-toolchain-pins.md)) pulls
in artifacts built against API 37, and an AndroidX library's declared compile
requirement is a hard floor, not advice.

## Decision

- **`minSdk = 26`** (Android 8.0 Oreo)
- **`compileSdk = 37`**
- **`targetSdk = 37`**

Installing `platforms;android-37.0` was required — the SDK had only 36.x. Note that
`android-37.2` also exists; a plain integer `compileSdk = 37` resolves to
`android-37.0`, and using 37.2 would mean the separate minor-version DSL. Plain
integers were chosen to keep the build config boring.

## Consequences

- `java.time` works natively. No core library desugaring, no `ThreeTenABP` shim, no
  divergent date handling between debug and release.
- `minSdk 26` still covers roughly 98% of active Android devices, so publishing the
  `public` flavor later is not meaningfully constrained.
- Targeting 37 means the app opts in to current platform behaviour — notification
  permissions, background execution limits, and exact-alarm restrictions all apply.
  This matters for group reminders (FR4.3): scheduling must be written against modern
  `WorkManager`/`AlarmManager` rules rather than legacy permissiveness.
- `targetSdk = compileSdk` avoids the class of bug where the app compiles against APIs
  whose runtime behaviour it has not opted into.
- The SDK platform is now a real setup prerequisite. A fresh clone needs
  `platforms;android-37.0` installed, which is worth knowing before a confusing
  first-build failure.
- Bumping `compileSdk`/`targetSdk` in future is routine, and will likely be forced
  again by AndroidX rather than chosen. Raising `minSdk` is not routine, so 26 is the
  number worth being deliberate about.

## Alternatives considered

**`compileSdk = 36`.** Attempted and **rejected on evidence** — AndroidX dependencies
reject it outright. Recorded because "use the SDK you already have installed" is a
tempting shortcut, and it is the same mistake as picking a Gradle version because it
was cached (see [ADR-0013](0013-gradle-version-catalog-and-toolchain-pins.md)).

**Pin older AndroidX versions that still allow compileSdk 36.** Rejected: it would
mean holding back Compose, `core-ktx` and much of AndroidX at the very start of a
greenfield project, to avoid installing one SDK platform.

**`minSdk = 24`.** Rejected: forces core library desugaring for `java.time`, adding
build configuration and subtle behavioural differences, to reach Android 7.x devices
that are not the target device.

**`minSdk = 29` or higher.** Considered — for a single known device it is defensible —
but rejected as gratuitous, since nothing in the spec requires an API above 26 and a
higher floor only narrows options if the `public` flavor is ever published.

**`targetSdk` below `compileSdk`.** Rejected: it postpones work that reminders will
require anyway, and running in a legacy compatibility mode makes behaviour harder to
reason about, not easier.
