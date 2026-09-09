# Sabi Moni

*"Sabi" (Pidgin: to know) + "Moni" (money) — Know Your Money.*

A single-user Android personal finance app built around one idea: you shouldn't have to
build a budget before you're allowed to log that you took a taxi. Capture is a typed
paragraph; the app interprets it. Alongside that, informal group obligations (church,
choir, charity, school contributions) get first-class tracking with due dates and
penalties, so nothing is missed.

It is a mirror, not a budget enforcer.

- Full product spec: [`product-brief-mvp-spec.md`](product-brief-mvp-spec.md)
- Architecture decisions: [`docs/adr/`](docs/adr/README.md)
- Build order and what is next: [`docs/sprint-plan.md`](docs/sprint-plan.md)

## Shape of it

- **Kotlin + Jetpack Compose**, single `:app` module, package-by-feature
- **Room** as the local source of truth — no backend, no cloud sync
- **MVVM** with unidirectional data flow, one `StateFlow<UiState>` per screen
- **Hilt** for dependency injection
- **Gemini** (Google AI Studio, free tier) behind one `AiParsingProvider` interface —
  the only part that needs the network. Structured output is enforced with a
  `responseSchema`, not just asked for in the prompt.
- Money is a `Long` of whole XAF; the CFA franc has no minor unit

Capture writes locally and returns immediately. Parsing is a queued WorkManager job, so
logging never fails because the network did.

## Build flavors

`RECEIVE_SMS` is restricted by Google Play policy to a narrow set of app categories,
and personal finance does not qualify. So there are two flavors, split by source set
rather than a runtime flag:

| Flavor | MoMo SMS auto-capture | Intended distribution |
|---|---|---|
| `personal` | Yes | Sideloaded APK |
| `public` | Permission not compiled in | Play Store / GitHub Releases |

```bash
./gradlew :app:assemblePersonalDebug   # daily driver
./gradlew :app:assemblePublicDebug     # policy-compliant build
./gradlew :app:assembleDebug           # both, to catch a public-flavor break
```

## Local setup

Nothing is on `PATH` by default on the dev machine, so:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # Git Bash
```

`local.properties` needs `sdk.dir` pointing at the Android SDK. It is gitignored.

The `android-37.0` SDK platform must be installed (`sdkmanager "platforms;android-37.0"`)
— AndroidX dependencies require compiling against API 37 or later.

The Gemini API key is **not** a build input — it is entered once in the app's Settings
screen and stored encrypted with an Android Keystore AES-GCM key. Get one from
[Google AI Studio](https://aistudio.google.com/apikey). Never commit a key.

## Status

Scaffold stage. The build system, data model, AI provider abstraction, secure key
storage and navigation exist; the capture loop is partially wired. Reports and savings
are placeholders. See `docs/adr/` for what has been decided and why.
