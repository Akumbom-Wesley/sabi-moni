# ADR-0024: System bars, tonal surfaces, and the shape of the thread

- **Status:** Accepted
- **Date:** 2026-09-11
- **Builds on:** [ADR-0023](0023-theme-choice-in-settings.md) (the theme choice),
  [ADR-0022](0022-balance-as-the-headline.md) (the header),
  [ADR-0002](0002-native-android-kotlin-compose.md) (Compose + Material 3)

## Context

Two reports from the device, one a bug and one a judgement.

**The bug.** In light theme the top of the screen went blank white and the clock, battery
and Wi-Fi icons disappeared. The cause is that `enableEdgeToEdge()` is called once in
`onCreate`, and its default `SystemBarStyle.auto` decides status-bar icon contrast from the
**system** dark-mode setting — not from the theme this app chose. ADR-0023 gave the user a
theme override and thereby created a case the edge-to-edge default cannot see: a phone in
dark mode running the app in Light got light (white) icons over a white header.

It was invisible until the theme became a choice, which is a good reminder that adding a
setting adds a combination.

**The judgement.** Every parsed message was one full-width card tinted
`primaryContainer`, holding the raw text, a status line and the entry rows. A screen of
them was a wall of solid colour, and it gave the same visual weight to two different kinds
of thing: a sentence the user wrote, and a set of entries the app derived from it.

## Decision

### System bar appearance follows the theme being painted, not the system setting

`MainActivity` re-applies `isAppearanceLightStatusBars` and
`isAppearanceLightNavigationBars` in a `SideEffect` keyed on the resolved `darkTheme`, so
every theme change — including a manual override mid-session — updates icon contrast.

Kept in `MainActivity`, which already owns `window` and already resolves `ThemeChoice` to a
boolean, rather than inside `SabiMoniTheme`, which would need an `Activity` cast and would
break the rule ADR-0023 set: `ui/theme` stays pure presentation.

### The Scaffold's container is a tonal surface, not `background`

`Scaffold`'s container fills the whole window including behind the system bars, so its
colour *is* the colour of the strip behind the clock. Under a light dynamic palette
`background` is close enough to white that the strip read as blank page.

`surfaceContainer` gives it a soft tone continuous with the header beneath it, so the top
of the screen reads as part of the app. The thread then paints itself `surface`, which
makes the boundary between chrome and content a tone change rather than a drawn line.

### The `surfaceContainer*` roles are defined explicitly

The palette set `primary`, `surface` and friends but left the tonal container roles to
Material's baseline, which is a purple-cast grey that clashes with the green brand ramp.
The capture screen now layers a header, a thread background and two kinds of card, so
those roles are load-bearing.

`Color.kt` gains intermediate neutral steps and `Theme.kt` assigns all five container roles
plus `outline`/`outlineVariant` in both schemes. This only affects builds *without* dynamic
colour — the `public` flavour and pre-Android-12 devices — but that is exactly where an
unreviewed baseline palette would have shipped unnoticed.

### An exchange is two shapes: what you said, and what the app made of it

`MessageExchange` replaces the single card:

- **A right-aligned bubble** in `primaryContainer`, squared off on its bottom-end corner,
  holding the raw text. Content-hugging up to 300dp, so "taxi 500" is a small bubble.
- **A left-aligned reply card** in `surfaceContainerHigh`, squared on its bottom-start
  corner, holding the status line and the entries.

This is the chat metaphor the product is built on (FR1.1, FR1.3: "the app replies in the
thread"), and it fixes the weighting problem — the user's sentence and the app's reading of
it are visibly different kinds of object. `errorContainer` is reserved for the reply card of
a failed message, where a full-card alarm colour is actually warranted.

### Entry rows read as a ledger

Category leads at `bodyMedium` with the note beneath it at `bodySmall`/`onSurfaceVariant`,
and the amount right-aligned in its own column at `titleSmall`. Divider between rows.

The old row put amount and a run-together "category · note" string on one line, which does
not scan when there are three of them. Direction is carried by the sign and, for income,
the primary colour — not by a word, and not by colouring every expense red, which would
make a normal day look like an emergency.

When a selection is running, the pencil is replaced by a spacer of the same width so the
amount column does not shift.

### Day separators are centred chips

A pill in `surfaceContainerHighest`, centred. ADR-0022 removed today's separator because
two "Today" headings said the same thing twice; this makes the remaining ones read as
position markers in a scrolling list rather than as section titles competing with the
header.

## Alternatives rejected

- **Set a solid status bar colour.** The obvious fix for a white strip. Rejected:
  `Window.statusBarColor` is deprecated and a no-op on Android 15+, and the app is already
  edge-to-edge by choice. The correct lever is icon contrast plus the colour the app draws
  up there itself.
- **Call `enableEdgeToEdge(statusBarStyle = …)` on every theme change.** Works, and is the
  documented way to set a style. Rejected: it re-runs the whole edge-to-edge setup for one
  boolean, where `WindowInsetsControllerCompat` sets exactly the thing that is wrong.
- **Turn off dynamic colour so the brand palette always shows.** Tempting, since the green
  and sand palette is currently invisible on any Android 12+ phone. Rejected as out of
  scope here — it is a deliberate product decision about whether the app looks like the
  phone or like itself, and it deserves its own ADR rather than riding along with a
  bug fix.
- **Keep one card per message and just soften the tint.** The smallest change. Rejected:
  the problem was not saturation but that one container held two different kinds of
  content at one weight.
- **Colour every expense amount in the error colour.** Makes direction unmissable.
  Rejected — almost every entry is an expense, so the screen would be uniformly red and
  the colour would carry no information. "Mirror, not judge."

## Consequences

- On a cold start the app still paints the `SYSTEM` theme for the few frames before
  DataStore reads (ADR-0023), and the status bar icons now flip with it. Briefly visible,
  and the alternative is blocking startup on a disk read.
- `MessageCard` is now `MessageExchange` and renders two surfaces per message, so the
  thread has more vertical rhythm and fits fewer messages per screen. Acceptable: the
  nightly ritual is a handful of messages, not a scrollback.
- The visual design has **not** been verified on the device by the author of this ADR —
  the phone was locked at the time. The build is installed; the look is the user's call.
- Nothing here is covered by a test. Compose UI testing is not set up beyond the
  dependency, and screenshot testing would be the honest way to protect a layout
  decision — a gap worth naming rather than pretending the build passing means it looks
  right.
