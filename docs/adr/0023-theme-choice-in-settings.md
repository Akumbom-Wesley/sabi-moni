# ADR-0023: A theme the user can choose

- **Status:** Accepted
- **Date:** 2026-09-10
- **Builds on:** [ADR-0010](0010-api-key-at-rest-keystore-datastore.md) (the DataStore that
  already exists), [ADR-0004](0004-mvvm-unidirectional-data-flow.md)

## Context

`SabiMoniTheme` already took a `darkTheme` parameter, defaulted to
`isSystemInDarkTheme()`, and nothing ever passed anything else. The app followed the phone
and offered no say in it.

The theme is the one piece of state that has to be resolved *above* the whole app, before
any screen composes, which makes it slightly awkward in an architecture where state lives
in per-screen ViewModels (ADR-0004).

## Decision

### Three choices, and System is the default

`ThemeChoice` is `SYSTEM`, `LIGHT`, `DARK`. System stays the default because the phone
already knows whether it is night, and an app that ignores that is an app you have to
manage twice.

### Stored in the DataStore that already exists

`ThemePreference` writes a string into the same
`sabimoni_settings` preferences file the encrypted API key lives in (ADR-0010). No new
store, no schema change — Room is for the financial record, DataStore is for how the app is
configured, and this is the second thing in the second category.

An unset *or* unrecognised value reads back as `SYSTEM`. That second half matters: a
preferences file written by a later version with a new choice must not leave an older build
unable to pick a theme at all.

### Read by an activity-scoped ViewModel, above the app

`ThemeViewModel` is obtained in `MainActivity`'s `setContent` and scoped to the activity,
not to a screen. Settings writes the preference; this reads it; the DataStore flow is the
only thing connecting them, so there is no cross-ViewModel call and no shared mutable
state.

It uses `SharingStarted.Eagerly` — the only state flow in the app that does. Everything
else uses `WhileSubscribed(5_000)` because a screen's data is worth dropping shortly after
the screen goes away; a five-second window here would be an opportunity to repaint the
entire app in the wrong theme on the way back, and the state in question is one enum.

### `ui/theme` learns nothing about preferences

`MainActivity` resolves `ThemeChoice` to a boolean — including calling
`isSystemInDarkTheme()` for `SYSTEM` — and passes that to `SabiMoniTheme`, whose signature
is unchanged. The theme layer stays pure presentation with no knowledge of DataStore,
which keeps it previewable and testable with a plain boolean.

Dynamic colour is untouched and still on: on Android 12+ the palette comes from the
wallpaper, and this decision only ever chose between its light and dark variants.

## Alternatives rejected

- **A two-state switch (light/dark) instead of three chips.** Fewer controls. Rejected —
  dropping "System" means the app can no longer follow the phone at all, which is the
  behaviour most people want and the one it had before this ADR.
- **Inject `ThemePreference` into `MainActivity` with `@Inject lateinit var`.** Fewer
  moving parts than a ViewModel for one enum. Rejected: it puts a `collectAsState` on a
  raw flow with no lifecycle-aware caching, and it is the one place in the app that would
  read state outside a ViewModel for no reason other than brevity.
- **Put the choice in `SettingsViewModel` and hoist it.** It is a setting, after all.
  Rejected because the theme outlives the Settings screen; a ViewModel that is destroyed
  when its tab goes away is the wrong owner for state the whole app is painted with.
- **A `ThemeChoice` parameter on `SabiMoniTheme`.** Slightly less code in the activity.
  Rejected: it drags `core/settings` into `ui/theme` and makes every `@Preview` import an
  app-specific enum to say "light".

## Consequences

- The app opens on `SYSTEM` for the few frames before DataStore reads, so a phone in light
  mode with `DARK` selected can flash light on cold start. `Eagerly` makes that window as
  small as a disk read; removing it entirely would need the value at
  `Application.onCreate`, which is not worth blocking startup for.
- `ThemeViewModel` sits in the root `com.sabimoni` package next to `MainActivity`, not in a
  feature package, because it belongs to the activity rather than to a screen.
- Settings now has an Appearance section above the API key. It is the first thing on that
  screen that changes something visible, which is a reasonable place for it.
