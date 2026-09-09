# ADR-0010: API key at rest — Keystore AES-GCM + DataStore

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

The user enters a Groq (or Gemini) API key once during setup. It must be stored on the
device, never hardcoded, and never committed (FR2.5, FR7.2). If leaked, the key allows
a third party to consume the user's free-tier quota — a low-severity but real harm,
and it is a credential, which sets the bar higher than for ordinary preferences.

The spec originally named `EncryptedSharedPreferences` from
`androidx.security:security-crypto`. That library is **deprecated** — the latest
release, 1.1.0, is no longer receiving development, and Google's guidance is to move
off it. Continuing to depend on an unmaintained library for the single component
guarding a credential is a poor trade.

## Decision

Encrypt the key ourselves with a hardware-backed Keystore key and store the ciphertext
in **DataStore Preferences**:

- On first write, generate an **AES-256** key in the `AndroidKeyStore` provider under
  a fixed alias, with `setUserAuthenticationRequired(false)` — the app lock
  (FR7.1) gates access to the app; requiring re-auth per parse request would break the
  30-second nightly ritual.
- Encrypt with **AES/GCM/NoPadding** using a fresh random 12-byte IV per write. GCM is
  authenticated, so tampering with the stored ciphertext fails decryption rather than
  yielding garbage.
- Persist `Base64(iv || ciphertext)` as a single string in DataStore. The private key
  never leaves the Keystore and is non-exportable; DataStore holds nothing useful on
  its own.
- Expose it behind an interface so the storage mechanism is swappable and the AI
  provider never touches crypto:

```kotlin
interface ApiKeyStore {
    suspend fun get(): String?
    suspend fun put(key: String)
    suspend fun clear()
}
```

- The key is read only at the moment a parse request is built, never logged, never
  cached in a field, and never included in error messages or crash reports.

## Consequences

- No dependency on a deprecated crypto library. The moving parts are ~60 lines of
  well-understood, standard `javax.crypto` usage.
- Key material is hardware-backed where the device supports it (TEE or StrongBox on
  API 26+, per [ADR-0007](0007-sdk-levels.md)) and cannot be extracted even from a
  rooted device.
- We own the code, which means we own the correctness. The failure modes that must be
  handled deliberately: IV reuse (avoided by generating per write), key invalidation
  after a device credential change or app-data restore (treated as "key lost" —
  prompt for re-entry rather than crash), and Keystore unavailability on some OEM
  devices.
- Rotating or clearing the key is straightforward: delete the alias and the DataStore
  entry.
- More code than a one-line `EncryptedSharedPreferences` call, and crypto code is
  where subtle mistakes hide — so this implementation warrants a unit test covering
  round-trip, tamper-detection, and the missing-key path.

## Alternatives considered

**`EncryptedSharedPreferences`** (as the spec suggested). Rejected on maintenance
grounds: it is deprecated and unmaintained. It works today, but depending on an
abandoned library specifically for credential storage is the wrong place to accept
that risk. This ADR supersedes the spec's suggestion.

**Plain unencrypted DataStore.** Genuinely arguable — Android's app sandbox already
prevents other apps from reading it, so on a non-rooted device the practical exposure
is close to nil, and the credential is a free-tier API key rather than a bank
password. Rejected because it undercuts FR7.x, and the cost of doing it properly is
one small class.

**Hardcoding the key or reading it from `local.properties` into `BuildConfig`.**
Rejected: `BuildConfig` strings sit in plaintext in the APK, trivially recoverable by
decompiling. It also means rotating the key requires a rebuild, and it invites
accidentally committing the secret.

**Requiring biometric authentication per key access** (`setUserAuthenticationRequired(true)`).
Rejected: strictly more secure, but it would prompt for a fingerprint on every parse
request, which is incompatible with the 5-minute low-energy nightly ritual that is the
product's core premise. App-level lock is the right granularity.
