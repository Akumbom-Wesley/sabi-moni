# ADR-0002: Native Android with Kotlin and Jetpack Compose

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

Sabi Moni is a single-user personal finance app for one Android phone. Two
requirements dominate the platform choice:

1. **SMS interception.** MTN MoMo sends an SMS for every transaction, and reading
   those is a v1 feature (FR3.x). This requires `RECEIVE_SMS` and a
   `BroadcastReceiver` — deep platform integration.
2. **Local-first with no backend.** All data lives on-device (FR8.2), there is no
   server, and the $0-cost target forbids one.

The primary UI is a chat thread — a scrolling list of messages with inline editable
parsed line items.

## Decision

Build a native Android app in **Kotlin** with **Jetpack Compose** for the UI, using
Material 3.

Compose's `LazyColumn` maps directly onto the chat-thread UI, and its state model
suits a screen where a message's parsed line items change in place after the AI
responds. Kotlin coroutines and `Flow` cover the async work (parse requests, Room
queries, WorkManager results) without extra machinery.

## Consequences

- Full, unrestricted access to `RECEIVE_SMS`, the Android Keystore, `BiometricPrompt`,
  `WorkManager`, and `AlarmManager` — every platform API the spec depends on.
- Requires Android Studio and a real compile-and-install cycle; the app cannot be
  meaningfully previewed outside a device or emulator.
- Android-only. An iOS version would be a rewrite, which is acceptable: the spec
  scopes this to Android explicitly.
- Compose is declarative and unfamiliar territory if coming from XML layouts, so
  early UI work will be slower than later UI work.

## Alternatives considered

**Flutter or React Native.** Rejected primarily on SMS access: both require
platform-channel plugins to reach `RECEIVE_SMS`, so we would write the Android-native
code anyway and then wrap it — strictly more work for a single-platform app. Cross-
platform's main payoff is iOS, which we do not want.

**A Progressive Web App.** Rejected outright: no SMS access, no reliable background
scheduling for reminders, and no Keystore for the API key. It would fail FR3.x, FR4.3
and FR7.x simultaneously.

**Kotlin Multiplatform.** Rejected as premature. KMP earns its complexity when
sharing logic across platforms; with one target it is pure overhead.

**Native Android with XML views.** Rejected: the chat thread with dynamically
re-rendering parsed rows is exactly the case where Compose's state handling beats
`RecyclerView` adapters and `ViewHolder` diffing.
