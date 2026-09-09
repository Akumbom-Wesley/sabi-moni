# ADR-0009: AI parsing behind a provider abstraction

- **Status:** Accepted — the abstraction stands; the **provider selection** (Groq
  primary) is superseded by [ADR-0016](0016-gemini-as-primary-ai-provider.md)
- **Date:** 2026-09-09

## Context

The core loop depends on turning free text ("taxi 500, lunch 1500, gave 5000 to
choir") into structured draft transactions. This is done by a hosted LLM, because
doing it on-device would mean either bundling a model or writing a brittle regex
parser, and neither fits the "talk, don't fill forms" principle.

Two constraints shape the design:

- **$0 cost.** The provider must have a genuinely free tier with no credit card.
  Groq (Llama 3.3 70B / GPT-OSS 120B, JSON mode, ~14,400 requests/day) and Gemini
  (Flash / Flash-Lite) both qualify, and both are far above a personal daily volume of
  perhaps five requests.
- **Free-tier terms change.** A no-cost tier can be throttled, restricted or
  withdrawn at any time. The app must be able to move providers without a rewrite.

The same parsing path serves both typed chat messages and auto-captured MoMo SMS
(FR3.1) — one intelligence layer, two input sources.

## Decision

Define a single narrow interface in `core/ai` and program exclusively against it:

```kotlin
interface AiParsingProvider {
    suspend fun parse(request: ParseRequest): Result<List<TransactionDraft>>
}
```

- `ParseRequest` carries the raw text plus **minimal context**: existing category
  names, existing group names, and today's date — enough for the model to resolve
  "choir" to a real `Group` record and "yesterday" to a real date, and nothing more.
- `GroqParsingProvider` is the initial implementation, using JSON-mode output against
  a strict response schema. A `GeminiParsingProvider` can be added later and swapped
  by changing one Hilt binding ([ADR-0008](0008-hilt-for-dependency-injection.md)).
- Provider-specific request/response DTOs stay **inside** the provider
  implementation. `TransactionDraft` is a domain type with no trace of any vendor's
  wire format, so switching providers cannot ripple outward.
- Failures return `Result.failure` rather than throwing, so the caller (the parse
  queue) can distinguish retryable from permanent failures and mark the message
  `failed` instead of losing it.

**Privacy boundary:** only the message text and the category/group name lists are
transmitted. No transaction history, no balances, no bulk upload — ever. Free tiers on
both providers may retain submitted input to improve their models, which is standard
for no-cost tiers. For personal financial text this is a real if modest exposure, and
it is recorded here explicitly rather than buried.

## Consequences

- Provider migration is a new class plus a one-line binding change, not a refactor.
- The abstraction is testable: a fake `AiParsingProvider` lets the whole capture flow
  and parse queue be tested with no network and no API key.
- Typed messages and SMS share one code path, so prompt and schema improvements
  benefit both at once.
- The interface must stay narrow. Leaking a Groq-specific concept (a model name, a
  token limit, a vendor error code) into `ParseRequest` would defeat the purpose.
- Slight indirection cost: reading the parse flow means looking at both the interface
  and the implementation.
- Sending personal financial text to a third party is inherent to this design. It is
  minimised, not eliminated.

## Alternatives considered

**Call the Groq HTTP API directly from the repository.** Rejected: it is less code
today and a rewrite the moment the free tier changes — which for a no-cost tier is a
matter of when, not if. The spec calls this out explicitly (FR2.4).

**An on-device model.** Rejected: bundling even a small quantised model bloats the APK
enormously, drains battery, and would parse noticeably worse than a 70B hosted model.
Revisit only if hosted free tiers disappear.

**Hand-written on-device parsing (regex / grammar).** Rejected: it fails the central
design principle. Handling "gave 5k to choir last Tuesday, and Ma sent 20000" with
rules is a losing battle, and every unhandled phrasing pushes the user back toward
filling in forms.

**A paid provider (OpenAI, Anthropic) behind the same interface.** Not rejected on
merit — the abstraction exists precisely so this is possible — but it violates the
$0-cost target, so it is not the default.
