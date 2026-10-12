# ADR-0031: The IDE caps AGP, so AGP is pinned to what Studio supports

- **Status:** Accepted
- **Date:** 2026-10-12
- **Amends:** the AGP pin in [ADR-0013](0013-gradle-version-catalog-and-toolchain-pins.md)

## Context

Opening the project in Android Studio fails to sync:

```
The project is using an incompatible version (AGP 9.4.0) of the Android Gradle
plugin. Latest supported version is AGP 9.1.0.
```

The installed IDE is Android Studio **2025.3.2** (`AI-253.30387.90`), which is the
same build that was installed when [ADR-0013](0013-gradle-version-catalog-and-toolchain-pins.md)
pinned AGP 9.4.0. So the project was never IDE-compatible; it simply was not noticed,
because every build in this project's history had been run from the command line,
where AGP 9.4.0 works perfectly.

That is the useful part of this failure. **A green `./gradlew assembleDebug` does not
mean the project opens.** The IDE enforces a ceiling that Gradle does not, and the two
can disagree for weeks without anyone finding out.

## Decision

Pin **AGP to 9.1.0**, the highest version the installed Studio accepts.

The cost is lower than it looks, and worth stating precisely because it is the reason
this is a one-line change rather than another version cascade:

**AGP 9.1.0 declares `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10` — exactly the
same Kotlin as AGP 9.4.0 did.** Read from the POM, not assumed. So:

- Kotlin stays `2.2.10`
- KSP stays `2.2.10-2.0.2`
- The Compose and serialization plugins stay `2.2.10`
- `android.disallowKotlinSourceSets=false` is still required for the same reason
- Gradle stays `9.7.1`, comfortably above AGP 9.1's floor

Verified by a full build: both flavours assemble and all 54 unit tests pass.

**AGP is now bounded above by the IDE.** Raising it is no longer a free upgrade —
it requires upgrading Android Studio first, and then checking what Kotlin the new AGP
bundles, because that cascade is real even when this particular step avoided it.

## Consequences

- The project opens and syncs in Android Studio again, which matters: this is an
  Android app, and the emulator, layout inspector and device deployment all live there.
- No code changed. Nothing about the Kotlin ceiling, KSP, or the source-set flag moves.
- A standing rule: **upgrade Studio before AGP, never the reverse.** The version catalog
  now carries a comment saying so at the pin itself.
- AGP 9.1.0 is three minor versions behind current. That costs nothing today, and the
  gap will close whenever Studio is next updated.
- `9.1.1` exists and is probably fine, but 9.1.0 is the exact version the IDE named, so
  that is what is pinned. There is no reason to gamble a patch version on the inference
  that "supported up to 9.1.0" includes 9.1.1.

## Alternatives considered

**Upgrade Android Studio to a build that supports AGP 9.4.** The other correct answer,
and the one to take when a Studio upgrade is wanted for its own sake. Rejected as the
fix here because it makes an IDE upgrade a prerequisite for a project that otherwise
builds and tests cleanly, and nothing in the codebase needs anything AGP 9.2–9.4 added.

**Keep AGP 9.4.0 and work from the command line.** Rejected. It builds, but it gives up
the emulator, the debugger and device deployment — and this project's largest
outstanding risk is already that **the app has never been run**. Making the IDE
unusable is precisely the wrong trade.

**Downgrade to AGP 9.0.x.** Rejected: unnecessarily far back. 9.1.0 is supported and
closer to current.
