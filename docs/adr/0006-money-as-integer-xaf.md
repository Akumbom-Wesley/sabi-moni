# ADR-0006: Represent money as whole-XAF integers

- **Status:** Accepted
- **Date:** 2026-09-09

## Context

The app's currency is the Central African CFA franc (XAF), confirmed as the only
currency in scope. XAF is a **zero-decimal currency**: it has no minor unit in
practice, ISO 4217 assigns it 0 decimal places, and prices are quoted in whole
francs. There is no such thing as 1500.50 XAF.

Money values flow through several boundaries — parsed out of AI JSON responses, stored
in Room, summed in SQL for reports, formatted for display — and a representation
mistake at any of those boundaries produces wrong numbers in a financial app, which
is the one category of bug this app cannot tolerate.

## Decision

Represent all monetary amounts as **`Long`, counting whole XAF**, wrapped in a Kotlin
inline value class:

```kotlin
@JvmInline
value class Money(val xaf: Long)
```

- Room entities store the raw `Long` in columns named `amountXaf`, so `SUM()` in SQL
  is exact integer arithmetic.
- Domain models expose `Money`. The value class is erased at runtime, so there is no
  allocation cost versus a bare `Long`.
- Arithmetic (`plus`, `minus`, `times` by an integer factor) is defined on `Money`.
  Division and floating-point operations are deliberately **not** provided — there is
  no rounding policy to get wrong because there is no case in this app that needs one.
- Formatting lives in one place, `core/money`, producing grouped output like
  `12 500 FCFA`. No feature formats currency inline.
- The AI provider contract specifies integer amounts. If a model returns `1500.0`,
  the parsing layer rejects or truncates at that single boundary rather than letting a
  `Double` propagate inward.

## Consequences

- Exact arithmetic everywhere. No accumulated floating-point drift in a yearly report
  summing hundreds of transactions.
- `SUM(amountXaf)` in SQLite is integer-exact, so reports can aggregate in the
  database (per [ADR-0005](0005-room-local-first-persistence.md)) without precision
  concerns.
- The type system prevents mixing a money value with an arbitrary count — passing a
  quantity where an amount is expected fails to compile.
- Adding a second currency later, or any currency with minor units, requires
  revisiting this ADR. `Money` would need a scale or a currency field. This is
  accepted: the spec is explicit that XAF is the only currency, and designing for a
  hypothetical multi-currency future now would be speculative complexity.
- A little friction at the AI boundary, since JSON has no integer/decimal distinction
  and models will occasionally emit a decimal. Handled once, at parse time.

## Alternatives considered

**`Double` or `Float`.** Rejected firmly. Binary floating point cannot represent
decimal fractions exactly, and in a ledger those errors accumulate across sums. This
is the classic money bug and it is entirely avoidable here.

**`BigDecimal`.** Rejected: it exists to handle scale and rounding policy, neither of
which XAF has. It costs allocation on every operation, cannot be summed natively by
SQLite (requiring a string or scaled-integer column anyway), and needs a
`TypeConverter`. All cost, no benefit for a zero-decimal currency.

**Storing minor units (`Long` of centimes).** Rejected: XAF has no centimes in
circulation. Multiplying everything by 100 to store a unit that does not exist would
mean every read and write carries a conversion that can only introduce mistakes.

**A bare `Long` with no wrapper.** Rejected: `Long` is also the type of row IDs,
epoch timestamps, and counts. The value class costs nothing at runtime and makes
`transferTo(userId, amount)`-style argument-order mistakes impossible.
