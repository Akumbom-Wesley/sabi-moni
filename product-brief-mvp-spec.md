# Sabi Moni — Product Brief & MVP Spec

*"Sabi" (Pidgin: to know) + "Moni" (money) — Know Your Money.*

---

## 1. Problem Statement

You have money coming in from predictable (salary) and unpredictable (gifts, side income) sources. You lose visibility into where it goes because tracking tools demand upfront structure — categories, budgets, plans — before you're allowed to just *log* something. On top of that, you carry informal financial obligations (church, choir, charity, school-related group contributions) that are announced irregularly via WhatsApp, with no central place tracking what's owed, to whom, and by when — so you miss payments and eat penalties.

The core insight: **you don't want a budgeting app. You want a fast, honest mirror of your money, with a separate lightweight system for tracking recurring social obligations, and reports built from real data instead of a plan you have to maintain.**

---

## 2. Design Principles

These are the filters every feature decision should pass through:

1. **Talk, don't fill forms.** Capture happens in natural language — a typed paragraph, not fields and category pickers. The system does the interpreting, not you.
2. **5-minute nightly ritual, not real-time discipline.** The product must work even if used once a day, at low energy, at the end of a tiring day.
3. **Mirror, not judge.** Show reality (spend, balance, trends). Don't gate or shame. Decisions stay with you.
4. **Groups are obligations, not budget categories.** Church/choir/charity contributions get their own first-class tracking — due dates, penalties, payment history — separate from general spend categories.
5. **Local-first storage, cloud-assisted intelligence.** Your data lives on the phone; only the text needed to interpret a new entry is sent out, and only at the moment of parsing.
6. **Retrospective first, predictive later.** v1 reports answer "what happened." Forward-looking ("what's coming due") comes after.

---

## 3. Core User & Context

- Single user, personal use, Android only.
- Income: monthly salary (lump sum) + irregular smaller inflows during the month.
- Money moves through cash, MTN Mobile Money, and occasionally virtual cards (subscriptions).
- MTN MoMo sends SMS alerts for every transaction — a future automation hook.
- Currency: **XAF (CFA franc)** — confirmed.
- Default preset categories: Transport, Food, Airtime/Data, School, Groups, Savings, Misc (editable later).
- 4 active recurring-obligation groups today (church, choir, charity, school-related), each with irregular/randomly-announced due dates and amounts.

---

## 4. Functional Requirements

### 4.1 Conversational Capture (core loop)
- FR1.1: Primary input is a chat thread. User types a free-text paragraph describing one or more things that happened ("took a taxi for 500, bought lunch for 1500, gave 5000 to choir") and sends it.
- FR1.2: Text is saved locally the instant it's sent, regardless of connectivity — capture is never blocked by being offline.
- FR1.3: The app replies in the thread with a short natural-language confirmation summarizing what it understood as one or more line items (amount, category, note, group match if any).
- FR1.4: Each parsed line is tappable to correct directly (amount, category, group, direction) — no re-typing the whole message.
- FR1.5: A single "today" view/thread shows everything logged so far today with a running total — supports the end-of-day batch ritual.
- FR1.6: Manual, form-based entry remains available as a fallback for when a quick tap is faster than typing, or the parse needs a full redo.

### 4.2 AI Parsing Engine
- FR2.1: Each captured message is sent to a **free-tier cloud AI provider** — primarily **Groq** (hosting open models like Llama 3.3 70B / GPT-OSS 120B, JSON-mode output, no credit card required, free limits of ~14,400 requests/day — far beyond personal daily volume) — along with lightweight context: the user's existing categories, existing group names, and current date, so it can resolve references like "choir" to the actual Groups record instead of guessing blind.
- FR2.2: The model returns a structured list of draft transactions: amount, direction (income/expense), category guess, note, matched group (nullable). No free-form prose is parsed manually on-device.
- FR2.3: If the device is offline when a message is sent, it's stored as a **pending capture** and automatically parsed the next time the app has connectivity — the user sees a "3 entries pending" indicator rather than losing anything.
- FR2.4: The AI provider is accessed behind a small abstraction layer (single interface, swappable implementation) rather than hardcoded — free-tier terms can change, and swapping to **Gemini** (also free, no billing account required, Flash/Flash-Lite models) or another provider should be a config change, not a rewrite.
- FR2.5: The user's API key (Groq or Gemini) is provided once during setup and stored securely on-device (Android Keystore-backed encrypted storage) — never hardcoded or committed to source.
- FR2.6: Only the message text and the minimal category/group context are sent per request — no bulk history upload, no data leaves the device beyond what's needed to parse that one entry.
- **Privacy note:** free tiers on both providers may retain and use submitted input/output data to improve their models (this is standard for no-cost tiers, not unique to either). Since this is personal financial data, worth being aware of even though the practical risk for a personal-use app is low — flagging it rather than hiding it.

### 4.3 MTN MoMo Auto-Detection *(in v1)*
- FR3.1: With permission, incoming MTN MoMo SMS alerts are captured and fed into the **same parsing engine** (4.2) as if they were a chat message — one intelligence layer handles both typed paragraphs and auto-captured SMS text.
- FR3.2: Parsed SMS-based entries appear in the chat thread as a system-flagged message for one-tap confirm, distinguishing them from user-typed entries.
- FR3.3: Manual entry remains fully available as a fallback for cash and anything SMS parsing misses or gets wrong.
- **Constraint:** Android's `READ_SMS`/`RECEIVE_SMS` permissions are restricted by Google Play policy to a narrow set of app categories (default SMS handler, dialer, device management, etc.). A personal finance app does not qualify if distributed via the Play Store.
- **Resolution:** Ship two build flavors from the start — a `personal` flavor with SMS auto-detect enabled (sideloaded on your device now), and a `public` flavor with that permission and code path stripped out, manual/chat entry only. If you ever publish, you publish the `public` flavor; your daily-driver stays on `personal`. No feature is lost, no rework needed later — this is decided once, at project setup.

### 4.4 Group Contribution Tracking
- FR4.1: User can create a Group (name, type, optional penalty rule).
- FR4.2: When a group announces a contribution (via WhatsApp, manually relayed), user can log it either by typing it into chat ("choir wants 5000 by the 15th") or via a quick form: group, amount, due date.
- FR4.3: System sends a reminder N days/hours before due date (configurable per group).
- FR4.4: Reminder surfaces the configured "cost of missing" (penalty amount) as motivation.
- FR4.5: User marks a contribution Paid/Missed; system keeps full history per group (amounts, dates, on-time vs late).
- FR4.6: A "what I owe right now, across all groups" view.

### 4.5 Budgeting & Reports (retrospective)
- FR5.1: Reports by category, by group, and income vs. expense.
- FR5.2: Default ranges: daily, weekly, monthly, quarterly, yearly — plus custom date range.
- FR5.3: Visual breakdown (simple bar/pie) of "where money went," with drill-down into transactions behind a number.
- FR5.4: No budget caps or blocking alerts in v1 — data only, per your instruction that this should be a mirror, not an enforcer.

### 4.6 Savings Goals
- FR6.1: User can create a named goal with a target amount (and optional target date).
- FR6.2: User manually "sets aside" an amount into a goal; this is logged as its own transaction type and reduces visible available balance.
- FR6.3: Progress view per goal (saved so far vs. target).

### 4.7 Security
- FR7.1: App lock via Android biometric/PIN prompt on open.
- FR7.2: API key and any parsed financial text in transit are never logged or cached beyond what's needed to complete the parse request.

### 4.8 Offline & Data
- FR8.1: Capture is always available offline; only parsing (interpretation into structured transactions) requires connectivity, and queues automatically when it doesn't have it (see FR2.3).
- FR8.2: All confirmed/structured data is stored locally on-device.
- FR8.3: No cloud sync required for v1 (flag for later if you ever want backup/multi-device).

---

## 5. Rough Data Model

```
Message
  id, rawText, source (typed/sms), sentAt,
  status (pending_parse/parsed/failed), parsedAt (nullable)

Transaction
  id, messageId (nullable — links back to originating chat message),
  date, amount, direction (income/expense),
  category, note, source (cash/momo/card),
  groupContributionId (nullable), autoDetected (bool)

Category
  id, name, icon

Group
  id, name, type (contribution/tontine/etc.), penaltyAmount (nullable)

GroupContribution
  id, groupId, amount, dueDate, status (pending/paid/missed),
  paidDate (nullable)

SavingsGoal
  id, name, targetAmount, currentAmount, targetDate (nullable)

Reminder
  id, groupContributionId, remindAt
```

Every `Transaction` traces back to the `Message` that produced it — so the chat thread doubles as an audit log: you can always see exactly what you typed (or what SMS came in) that led to any entry in your reports.

---

## 6. MVP Scope

**Build now (v1):**
- Conversational chat capture with instant offline save (FR1.x)
- AI parsing engine (free-tier Groq/Gemini, swappable) turning messages into structured transactions, with offline queueing (FR2.x)
- MTN MoMo SMS auto-detection feeding the same parsing engine (FR3.x) — requires sideloaded distribution, not Play Store
- Group creation + contribution logging + reminders + paid/missed history (FR4.x)
- Retrospective reports across default + custom ranges (FR5.x)
- Manual savings goals (FR6.x)
- App lock (FR7.x)
- Local-only structured storage, offline-capable capture (FR8.x)

**Deliberately deferred (v1.1+):**
- Predictive/forward-looking reports ("here's what's due in the next 5 days")
- Cloud backup / multi-device sync
- Conversational corrections ("no, taxi was actually 500") — v1 corrections are direct taps/edits on the parsed line, not re-parsed dialogue

This gives you a real, usable app fast, built around the one thing you said would already be life-changing: **daily capture done well**, with groups as the second pillar since it directly stops money loss (penalties).

---

## 7. The Core Nightly Flow (what it should feel like)

1. Open app → biometric unlock.
2. Land on today's chat thread.
3. Type one paragraph covering everything from the day ("taxi 500, lunch 1500, gave 5000 to choir, got 20000 from Ma") and send — 30 seconds, one message.
4. App replies with what it understood as a few line items; tap anything that's wrong to fix it directly.
5. If a group reminder fired earlier, it's sitting in the thread ready to mark paid/missed.
6. Close app. No forced review, no forced budget-setting. That's it.

Reports and goals exist for when you *choose* to look, not as a gate on logging.

---

## 8. Recommended Tech Stack

Given: native Android, chat-based capture with cloud AI parsing, SMS access needed, local-first storage, single user, no backend server, **$0 cost target**.

- **Language/UI:** Kotlin + Jetpack Compose — chat thread UI is a natural fit for Compose's `LazyColumn`
- **Local storage:** Room (SQLite) — structured, offline, queryable for reports; stores both `Message` and `Transaction` records
- **AI parsing:** Groq API (Llama 3.3 70B or GPT-OSS 120B, JSON-mode) as primary — free tier, no credit card, ~14,400 requests/day limit. Gemini API (Flash/Flash-Lite, also free, no billing account) as a documented fallback/swap-in. Both accessed behind one `AiParsingProvider` interface so switching later is a config change.
- **API key storage:** Android Keystore-backed `EncryptedSharedPreferences` — entered once during setup, never hardcoded or committed
- **Offline queueing:** WorkManager job that retries pending-parse messages once connectivity returns
- **Reminders:** WorkManager + AlarmManager for scheduled notifications
- **App lock:** AndroidX BiometricPrompt
- **SMS auto-detect:** BroadcastReceiver on `SMS_RECEIVED`, feeding raw text into the same AI parsing call as chat messages, isolated behind the `personal` build flavor
- **Build variants:** `personal` (SMS enabled) vs `public` (SMS stripped) via Gradle product flavors
- **Charts/reports UI:** Compose-native charting (e.g. Vico) or a simple custom canvas chart — no need for anything heavy

Local data storage and app logic run fully offline; the AI parsing step is the one part of the app that needs internet, and it degrades gracefully (queue-and-retry) when it doesn't have it. There is no backend server anywhere in this architecture, so there is nothing to host and nothing to pay for there — the only external dependency is a free-tier API call per entry.

### Cost Breakdown — everything that could cost money, and how it's avoided

| Item | Cost | How it's kept free |
|---|---|---|
| Hosting/backend | $0 | No backend exists — client talks directly to Groq/Gemini |
| AI parsing | $0 | Groq/Gemini free tiers, both no-credit-card, both far above personal usage volume |
| Local storage | $0 | On-device SQLite via Room, no cloud database |
| Distribution (personal use) | $0 | Sideloaded APK, no store fee |
| Distribution (if published later) | $25 one-time (Play Store) or $0 | Play Store has a one-time developer fee; free alternatives exist (GitHub Releases, F-Droid) if that's ever a concern |

Nothing in this stack has a recurring bill under normal personal usage.

## 9. Next Steps

1. Confirm this spec matches your vision (adjust anything before we lock it in).
2. Scaffold the Android project structure — Gradle setup, build flavors, Room schema, base navigation.
3. Build the core loop first (FR1.x — daily capture), since that's your highest-priority piece, then layer in Groups, Reports, Savings, and finally SMS auto-detect.

Because this is a real installable Android app (compiled APK, Gradle, Android SDK), the actual build-and-run cycle is best done in **Claude Code or Android Studio** on your machine, where the project can be compiled and tested on your phone — this chat is well suited for finishing the spec and scaffolding starter code, but not for compiling/running an APK.
