# ADR-0004: MVVM with unidirectional data flow

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

The capture screen is the hardest state problem in the app. A single user message
moves through several states — typed, saved locally, queued for parsing, parsed into
draft line items, individually corrected, confirmed — and any of those transitions
can happen while the user is looking at the screen or while the app is backgrounded.
Parse results arrive asynchronously, sometimes minutes later when connectivity
returns.

That rules out holding state in composables. It needs to survive configuration
changes and process-death-adjacent backgrounding, and it needs a single place where
"what does this screen look like right now" is answered.

## Decision

Use **MVVM with unidirectional data flow**:

- **Model** — Room is the source of truth. Repositories expose `Flow`s of domain
  models (not entities) and own all mutation.
- **ViewModel** — one per screen, `@HiltViewModel`, exposing exactly one
  `StateFlow<XxxUiState>`. Receives user intent as method calls. Never references
  Android UI types.
- **View** — composables that read state via `collectAsStateWithLifecycle()` and emit
  events upward as lambdas. Stateless with respect to business logic.

State flows down, events flow up. UI state is an immutable data class per screen,
recreated rather than mutated. Screen-level composables take state and lambdas as
parameters so they can be previewed and tested without a ViewModel.

## Consequences

- Asynchronous parse results land in the repository, propagate through the `Flow`, and
  re-render the thread with no explicit refresh logic — which is exactly the behaviour
  FR2.3 (offline queue, parse later) needs.
- State survives rotation and returning from background for free via `ViewModel`.
- Each screen's behaviour is unit-testable by driving the ViewModel and asserting on
  emitted state, with no UI or device involved.
- Costs boilerplate: a UiState class, a ViewModel, and a mapping layer per screen even
  when the screen is trivial. Accepted as the price of consistency.
- Requires discipline that domain models, not Room entities, cross the repository
  boundary — otherwise persistence details leak into the UI.

## Alternatives considered

**MVI with a formal reducer and sealed intents.** Rejected as over-engineered here.
It shines with complex, highly-interdependent state and time-travel debugging needs;
this app's screens are mostly a list plus a text field. The ceremony would exceed the
benefit.

**Plain state hoisting in composables with no ViewModel.** Rejected: nothing would
survive rotation, and asynchronous parse completion has nowhere to land. Fails FR2.3.

**MVC/MVP.** Rejected: presenters with imperative view interfaces fight Compose's
declarative model rather than complementing it.
