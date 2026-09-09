# ADR-0008: Hilt for dependency injection

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

The app has a modest but real object graph: a Room database and seven DAOs,
repositories over them, an HTTP client, the AI parsing provider, the secure API key
store, and one ViewModel per screen.

Two things make hand-wiring unattractive beyond the usual boilerplate argument:

1. **ViewModels need injection**, and Compose obtains them through
   `hiltViewModel()` / factories. Doing this manually means a factory per ViewModel.
2. **WorkManager needs injection.** The offline parse queue
   ([ADR-0015](0015-offline-capture-parse-queue.md)) runs in a `Worker` that
   WorkManager instantiates itself, so its dependencies have to arrive through a
   configured `WorkerFactory` rather than a constructor call we control.
3. **The AI provider and the SMS gateway are swapped by configuration and by build
   flavor** respectively ([ADR-0009](0009-ai-parsing-provider-abstraction.md),
   [ADR-0011](0011-personal-public-build-flavors.md)), so there needs to be a clean
   place to change one binding without touching call sites.

## Decision

Use **Hilt** (Dagger-based, KSP-processed).

- `@HiltAndroidApp` on the `Application`, `@AndroidEntryPoint` on `MainActivity`.
- `@HiltViewModel` with constructor injection for every ViewModel; screens obtain them
  via `hiltViewModel()`.
- `@Module` objects in `core/*/di` provide the database, DAOs, Ktor client, and
  `@Binds` the interface-to-implementation pairs.
- The flavor-specific `SmsCaptureGateway` binding lives in a Hilt module inside each
  flavor's source set — the mechanism that makes the flavor split invisible to
  feature code.
- `hilt-work` provides the injectable `WorkerFactory` for the parse-queue worker.

## Consequences

- Missing or ambiguous bindings are **compile-time** errors. In a financial app, a
  wiring mistake failing the build rather than crashing at runtime is worth real cost.
- Scoping is explicit and enforced: the database is a genuine singleton, ViewModels
  are per-screen, with no accidental sharing.
- Annotation processing adds build time, and Dagger's generated-code error messages are
  notoriously indirect — a missing binding can produce a wall of generated-symbol
  noise.
- Some boilerplate: a module to provide each DAO, `@Binds` declarations for each
  interface.
- Hilt is Android-coupled by design, which is fine given
  [ADR-0002](0002-native-android-kotlin-compose.md).

## Alternatives considered

**Koin.** Genuinely attractive: pure Kotlin DSL, no annotation processing so builds
stay fast, and much less ceremony. Rejected because resolution failures surface at
**runtime**, not compile time. For a personal app that runs a nightly ritual, a
missing binding discovered when opening the app at the end of a tiring day is a worse
failure mode than a slower build. Compile-time safety wins here.

**Manual constructor injection with a hand-rolled container.** Rejected: zero
dependencies and total transparency, but it means writing and maintaining a
`ViewModelProvider.Factory` per screen plus a custom `WorkerFactory`, which is
precisely the mechanical boilerplate Hilt exists to remove. Fine for three classes,
tedious at a dozen screens.

**Dagger without Hilt.** Rejected: same compile-time guarantees but Hilt's Android
entry points, ViewModel integration and WorkManager support would all have to be
rebuilt by hand.
