# ADR-0011: `personal` / `public` build flavors for SMS access

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

MTN MoMo sends an SMS for every transaction. Reading those automatically is a v1
feature (FR3.x) and arguably the highest-leverage automation in the app: it removes
manual entry for the majority of non-cash spending.

It requires `RECEIVE_SMS`. Google Play policy restricts `READ_SMS` / `RECEIVE_SMS` to
a narrow set of app categories — default SMS handler, dialer, device management, and a
few others. **A personal finance app does not qualify.** An app requesting it will be
rejected from the Play Store regardless of how legitimate the use is.

So the app cannot both read SMS and be distributed on Play. But the daily driver is
sideloaded onto one known phone, where no such policy applies.

## Decision

Ship **two Gradle product flavors** on a `distribution` dimension, from the very first
commit:

| Flavor | SMS auto-detect | Distribution |
|---|---|---|
| `personal` | Enabled — `RECEIVE_SMS`, `BroadcastReceiver` | Sideloaded APK |
| `public` | Absent — permission and code path not compiled in | Play Store / GitHub Releases |

The split is enforced by **source sets, not runtime flags**:

- `app/src/personal/AndroidManifest.xml` declares `RECEIVE_SMS` and the receiver.
  `app/src/main/AndroidManifest.xml` declares neither, so the `public` APK's merged
  manifest genuinely has no SMS permission — verifiable by inspecting the artifact.
- `core/sms` in `main` defines an `SmsCaptureGateway` interface. `personal` binds a
  real implementation, `public` binds a no-op whose `isSupported` is `false`. Feature
  code depends only on the interface and never branches on flavor.
- Any screen offering SMS setup reads `isSupported` and hides itself in `public`.

Doing this at project setup, rather than retrofitting it later, is the whole point:
the alternative is discovering at publish time that SMS code is woven through the
codebase.

## Consequences

- The `public` APK is genuinely policy-compliant. Not "the permission is unused" —
  the permission is not present.
- No feature is lost. The daily driver keeps full automation.
- Manual and chat entry remain the fallback in both flavors (FR3.3), so `public` is
  still a complete app rather than a crippled one.
- Cost: every build task doubles (`assemblePersonalDebug`, `assemblePublicRelease`,
  …), and CI would need to build both to catch a `public` compile break.
- A real discipline requirement — SMS-touching code must live in the `personal` source
  set. Putting it in `main` behind an `if` compiles fine and silently breaks the
  policy guarantee. Worth checking the merged `public` manifest before any release.
- Testing the receiver requires the `personal` flavor on a real device; the emulator
  can fake SMS but not the MoMo message format.

## Alternatives considered

**Single flavor with a runtime feature flag.** Rejected outright: the permission would
still be in the merged manifest of the published APK, which is exactly what Play
rejects. A flag cannot solve a manifest-level policy problem.

**Single flavor, SMS-enabled, never publish.** Tempting given it is a personal app,
and it is the simplest possible setup. Rejected because it makes publishing a
retrofit — SMS assumptions spread through the code, and untangling them later is far
more work than keeping them separated from day one. The spec is explicit that this is
decided once, at setup.

**Drop SMS auto-detection entirely and rely on manual/chat entry.** Rejected: it
discards the single biggest reduction in daily friction, and the chat capture loop
already handles the text, so the marginal cost of feeding SMS into the same parser is
small.

**Use the Play Store's SMS permission declaration form to request an exception.**
Rejected: personal finance is not an eligible category, so the request would be
denied. Not a viable path.
