# ADR-0012: Ktor client and kotlinx.serialization

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

The app makes network calls to exactly **one** endpoint: a chat-completions POST to
Groq, or its Gemini equivalent if the provider is swapped
([ADR-0009](0009-ai-parsing-provider-abstraction.md)). There is no backend
([ADR-0002](0002-native-android-kotlin-compose.md)), so there is no REST API surface
of our own to model.

The response must be parsed as strict JSON — the provider is asked for JSON-mode
output against a fixed schema, and the result maps onto `TransactionDraft` objects.

## Decision

Use **Ktor Client** with the OkHttp engine, and **kotlinx.serialization** for JSON.

- Ktor: `ContentNegotiation` with the JSON serializer, a configured timeout, and the
  `Logging` plugin enabled only in debug builds — and configured to never log request
  bodies, since those contain both the API key header and personal financial text
  ([ADR-0010](0010-api-key-at-rest-keystore-datastore.md)).
- kotlinx.serialization with `ignoreUnknownKeys = true`, so a provider adding response
  fields does not break parsing.
- DTOs are `@Serializable` and `internal` to the provider package, keeping the wire
  format from leaking outward.
- `ktor-client-mock` in tests provides canned responses, so the provider is testable
  with no network and no key.

## Consequences

- One HTTP client, no annotation-processed interfaces, no converter factories. For a
  single endpoint this is close to the minimum viable amount of machinery.
- kotlinx.serialization is compiler-plugin based, so no reflection and no
  `proguard`/R8 keep rules for model classes — which matters because the release build
  minifies.
- `ignoreUnknownKeys` means a provider silently *removing* a field we depend on
  surfaces as a missing-value error at parse time rather than a schema mismatch. The
  provider must fail cleanly into `Result.failure` in that case.
- Ktor pulls in a coroutines-native API surface, so calls compose naturally with the
  `suspend` interface and the WorkManager-driven parse queue.
- Slightly less Android-conventional than Retrofit, so Stack Overflow answers skew
  toward the other choice.

## Alternatives considered

**Retrofit + OkHttp + Moshi.** The Android default and a perfectly good option.
Rejected because Retrofit's value is in modelling a broad API surface through annotated
interfaces — with one endpoint, the interface-plus-converter-factory ceremony is
overhead. It would also mean either Moshi (needing codegen and R8 rules) or
kotlinx.serialization via a converter adapter, i.e. more moving parts for the same
result.

**Bare OkHttp with manual JSON.** Rejected: hand-rolling request building and
`JSONObject` traversal for a nested chat-completions response is exactly the
error-prone code that a serializer exists to eliminate.

**`HttpURLConnection` / no library.** Rejected: no coroutine integration, manual
timeout and error handling, and no meaningful way to fake it in tests.

**Gson.** Rejected: reflection-based, requires R8 keep rules, no Kotlin
null-safety awareness, and it will happily construct a data class with `null` in a
non-nullable field — a bad property for parsing financial amounts.
