# ADR-0016: Gemini (Google AI Studio) as the primary AI provider

- **Status:** Accepted
- **Date:** 2026-09-09
- **Supersedes:** the provider *selection* in [ADR-0009](0009-ai-parsing-provider-abstraction.md) (its abstraction still stands)

## Context

[ADR-0009](0009-ai-parsing-provider-abstraction.md) established that all AI parsing
goes through one `AiParsingProvider` interface, and named **Groq** as the initial
implementation with Gemini as a documented fallback. That ordering came from the
product spec, not from a hard requirement.

The user has since decided to use a **Google AI Studio (Gemini) key** instead. This is
exactly the swap ADR-0009 was designed to absorb, so the interesting question is not
"can we" but "what changes as a result."

Checking the current state of the Gemini API rather than relying on the spec's
description turned up two things worth recording:

1. The model lineup has moved on substantially. `gemini-3.5-flash-lite` is now the
   documented "fastest, most cost-effective" option, well past the `2.0-flash` era the
   spec was written against.
2. Gemini supports **enforced structured output** — `responseMimeType:
   "application/json"` together with a `responseSchema` — rather than Groq's
   `json_object` mode, which only guarantees *syntactically* valid JSON and leaves
   shape compliance to the prompt.

## Decision

Use **Gemini via Google AI Studio** as the primary and only implementation of
`AiParsingProvider`.

- Endpoint: `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`
- Auth: the `x-goog-api-key` header, so the key never appears in a URL (and therefore
  never in a log, proxy trace, or crash report).
- Model: **`gemini-3.5-flash-lite`**, as a single named constant. Parsing a one-line
  spending note is not a reasoning task; the cheapest, fastest model on the free tier
  is the correct default.
- **Enforce the response shape with `responseSchema`**, not just prompt instructions.
  The schema declares `amount` as `INTEGER`, which pushes back at the API boundary on
  the exact failure ADR-0006 warns about — a model returning `1500.0` for money.
- The prompt keeps its role (resolving "choir" to a real group, "yesterday" to a real
  date, `5k` to 5000), but shape compliance is now the API's job rather than the
  prompt's.
- The **Groq implementation is deleted**, not kept alongside. An unused second
  implementation is dead code; the interface plus a test fake is what keeps the
  abstraction honest.

Free tier: available on an active project without setting up billing. Exact
requests-per-minute and per-day limits are shown per-project in the AI Studio rate
limit dashboard rather than in the public docs, so they are not restated here — they
would rot. Personal volume is a handful of requests a day, orders of magnitude below
any published free-tier ceiling.

## Consequences

- `AiParsingProvider`, `ParseRequest`, `TransactionDraft`, the repositories, the parse
  queue, and every ViewModel are **completely unchanged**. The swap touched one
  implementation class, its DTOs, and one Hilt `@Binds`. ADR-0009's abstraction earned
  its keep on the first real test.
- Stronger malformed-response guarantees than the Groq design had. A schema violation
  fails at the API rather than becoming a bad row in the ledger.
- `ApiKeyStore` needed no change — it stores an opaque credential and never knew which
  vendor it belonged to. Only user-facing labels changed from "Groq API key" to
  "Google AI Studio API key".
- Vendor concentration: the device already talks to Google services, so this adds no
  new third party, but it does put parsing and the platform in one vendor's hands.
- **The privacy note from ADR-0009 still applies and is not softened.** Google's free
  tier may use submitted prompts and responses to improve its models. Personal
  financial text is being sent to a third party under terms designed for
  no-cost usage. Minimised (one message plus category and group names, never history),
  not eliminated.
- Model IDs move fast — this lineup shifted several generations between the spec being
  written and the code being written. The model name is one constant precisely so
  tracking that is trivial.

## Alternatives considered

**Keep Groq as primary.** Rejected simply because the user has a Gemini key and no
Groq key. Groq's ~14,400 requests/day free ceiling is generous, but irrelevant at a
volume of roughly five requests a day.

**Keep both implementations and choose at runtime.** Rejected as speculative
generality. It would mean a provider-selection setting, two key entries, and two code
paths to keep working, to solve a problem nobody has. If a provider fails, adding one
class back is a small change — that is the whole point of the abstraction.

**Use `gemini-3.8-flash` or another top-tier model.** Rejected: extracting "taxi 500"
into a structured row does not need frontier reasoning, and a larger model is slower
per request for no accuracy gain on this task. Revisit only if real parse failures show
the small model genuinely mis-splitting compound sentences.

**Prompt-only JSON with no `responseSchema`** (mirroring the Groq approach). Rejected:
given the API can enforce the contract, declining that and hand-validating instead
would be choosing a worse failure mode for free.
