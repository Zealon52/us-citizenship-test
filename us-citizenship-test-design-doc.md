# US Citizenship Test — Design Doc

Companion to [us-citizenship-test-app-plan.md](us-citizenship-test-app-plan.md), kept separate on purpose: the app plan covers architecture, data, and build order and should stay stable; this doc covers layout, navigation, and visual language and is expected to change — especially once a dedicated UI/theme pass happens after the v1 build. Update this file freely; it does not need to stay in sync with any particular commit.

**Refreshed 2026-09-27** against the actual current build — every screen below (including the previously-undocumented ones) now reflects real code, not the original pre-build mockups. See the note at the end of each section where behavior diverged from an earlier version of this doc.

---

## Visual direction

**Goal:** clean, minimal, Apple-adjacent. Concretely, this means:

- Generous whitespace over dense layouts
- Minimal borders — prefer spacing, subtle elevation, and typography hierarchy over outlined boxes and heavy dividers
- Restrained color: mostly neutrals plus one accent color, not a card-per-color rainbow
- Large, legible type hierarchy (bigger headline weight, calmer body text)
- No icon-for-everything clutter — icons earn their place (nav bar, key actions), not decoration

This is a v1 lightweight pass. Colors, exact spacing, and typography scale are not finalized — they're deliberately left generic (see [Theme.kt](app/src/main/java/com/usctest/app/ui/theme/Theme.kt)) so a later theming pass can swap them without restructuring layouts.

### Icon set

**[Phosphor Icons](https://github.com/adamglin0/compose-phosphor-icon)**, via `com.adamglin:phosphor-icon` (Maven Central, MIT-licensed, Kotlin Multiplatform with a working Android/Compose artifact).

- Default weight: **Regular** (closest match to SF Symbols' typical line weight). `Thin`/`Light`/`Bold`/`Fill`/`Duotone` are also available if a specific screen needs more/less visual weight.
- Usage pattern:
  ```kotlin
  import com.adamglin.PhosphorIcons
  import com.adamglin.phosphoricons.Regular
  import com.adamglin.phosphoricons.regular.House

  Icon(imageVector = PhosphorIcons.Regular.House, contentDescription = null)
  ```
- Why not SF Symbols directly: Apple's SF Symbols license restricts use to apps built for Apple platforms — not usable here.
- Why not `cupertino_icons`: that's a Flutter/Dart package (Flutter is a Google project, hence the "Google has a Cupertino icon pack" association) with no Jetpack Compose artifact — not usable in this native Kotlin/Compose app.

---

## Navigation structure

```
Splash
  ├─(first launch)→ Profile Setup → Onboarding → Walkthrough → Home
  └─(returning user)→ Home

Home ──────────────┬─→ Recall Mode ──→ Recall Results
  (bottom nav:      ├─→ Practice Test ─→ Results
   Home·Progress·   ├─→ Flash Cards
   Settings)        ├─→ Review Missed ─→ Review Results
                     ├─→ All Questions
                     ├─→ (avatar, top-right) Profile
                     ├─→ (bottom nav) Progress
                     └─→ (bottom nav) Settings
```

- **Walkthrough** is a real, first-launch-only screen (4-page swipeable intro) that sits between Onboarding and Home — it was missing from this diagram in an earlier version of this doc. Gated by a `hasSeenWalkthrough` flag, so returning users never see it again.
- **Bottom nav:** `Home · Progress · Settings`, icons + text labels (Phosphor icons). Present on Home, Progress, and Settings; other screens (Recall Mode, Practice Test, Flash Cards, etc.) are pushed on top without the bottom nav, since they're focused tasks.
- **Profile** is reached only via the avatar in the Home header — it is not a bottom-nav destination and is not the same screen as Settings (Profile = identity/test-date fields; Settings = app configuration).
- Settings keeps the test-version indicator (2008/2025, auto or manual) — it is intentionally **not** shown on the Home header anymore.

---

## Screen wireframes

### Splash

```
┌─────────────────────────────┐
│                               │
│                               │
│                               │
│      US Citizenship Test     │
│                               │
│                               │
│    (tap anywhere to skip     │
│     the ~1.2s minimum wait)  │
│                               │
└─────────────────────────────┘
```
Plain centered title text on the theme background — no app icon/mark is actually rendered (an earlier version of this doc showed one; there's no icon asset wired in yet). Tapping anywhere sets a `skipped` flag that short-circuits the 1200ms minimum delay. Routes once the async DataStore read resolves, in order: **Profile Setup** (if profile setup isn't complete) → **Onboarding** (if not complete) → **Walkthrough** (if not yet seen) → **Home**. No visible loading spinner — the wait (real or skipped) is covered by the title screen itself.

### Profile Setup

```
┌─────────────────────────────┐
│  Let's get you set up.       │
│  This only takes a moment.   │
│                               │
│  Name                        │
│  [___________________]       │
│                               │
│  Interview date               │
│  [ Pick a date       ▾ ]     │
│  Your USCIS naturalization    │
│  interview — powers your      │
│  study timeline and countdown │
│                               │
│  Filing date                  │
│  [ Pick a date       ▾ ]     │
│  When you filed Form N-400 —  │
│  determines which question    │
│  bank applies (2008 test      │
│  before Oct 20, 2025; 2025    │
│  test on or after)            │
│                               │
│           [ Continue ]        │
│      (disabled until all      │
│       three fields are set)   │
└─────────────────────────────┘
```
- **All three fields (name, interview date, filing date) are required** — Continue stays disabled until all three are filled in. An earlier version of this doc described every field as optional with a silent "defaults to +14 days" fallback; that behavior isn't implemented in this screen.
- No "Skip" button — this screen is not skippable either way.
- → Onboarding

### Onboarding

```
┌─────────────────────────────┐
│  Select Your State            │
│  ┌─────────────────────────┐ │
│  │ Alabama                  │ │
│  │ Alaska                   │ │
│  │ ...                      │ │
│  │ Colorado          ✓      │ │
│  │ ...                      │ │
│  └─────────────────────────┘ │
│                               │
│  Select Your Representative   │
│  ┌─────────────────────────┐ │
│  │ (populates after state    │
│  │  is picked; loading       │ │
│  │  spinner until officials  │ │
│  │  data is ready)           │ │
│  │ Diana DeGette             │ │
│  │ Joe Neguse                │ │
│  │ ...                       │ │
│  └─────────────────────────┘ │
│                               │
│            [ Continue ]       │
│      (disabled until state    │
│       is selected; rep is     │
│       optional)                │
└─────────────────────────────┘
```
→ **Walkthrough** (not straight to Home — see Navigation structure above).

### Walkthrough

First-launch-only, 4-page `HorizontalPager`, shown once between Onboarding and Home.

```
┌─────────────────────────────┐
│  ←              Skip         │ ← back arrow (pages 2-3 only), Skip (hidden on last page)
│                               │
│  ┌─────────────────────────┐ │
│  │  (mocked screen preview)  │ │
│  │                           │ │
│  └─────────────────────────┘ │
│  Page headline.               │
│  Explanatory body copy.       │
│                               │
│         • ○ ○ ○  (dots)       │
│           [ Next ]            │
└─────────────────────────────┘
```

- **Page 1 — "The Real Test."**: mocked Recall Mode preview (a disabled, empty "Your answer" field). Copy: "This is Recall Mode — you answer from memory, just like the real interview. No options to pick from." Illustration area intentionally left blank pending final visual design.
- **Page 2 — "Learn It First."**: mocked Practice Test preview (three mock options, one highlighted green with a check), "Also accepted: 3 more answers" caption. Copy: "Practice Test starts you off easier — multiple-choice, with hints. We'll show you why an answer's right, and what else would've counted."
- **Page 3 — "Study Exactly What You Need."**: no preview box — two icon+text feature rows explaining **Review Missed** and **All Questions**.
- **Page 4 (closing)**: large flag-checkered icon, headline personalized with days-until-test if available ("N days until your test." / "You're all set."), body personalized with name if available.
- Progress dots below the pager; bottom button reads "Next" on pages 1-3 and "Get Started" on page 4 (finishes and routes to Home).

### Home

```
┌─────────────────────────────┐
│  Good morning, Rohan      (☺)│ ← greeting left, profile avatar right → Profile
├─────────────────────────────┤
│  14 days until your test  🔥3 ◔72%│ ← one row: countdown left, streak (if any) + confidence ring right
├─────────────────────────────┤
│  ┌───────────┐ ┌───────────┐ │
│  │ 🎤 Recall  │ │ 📋 Practice│ │
│  │    Mode    │ │    Test   │ │
│  └───────────┘ └───────────┘ │
│  ┌───────────┐ ┌───────────┐ │
│  │ 🗂 Flash   │ │ 🔄 Review │ │
│  │    Cards   │ │    Missed │ │
│  └───────────┘ └───────────┘ │
│  ┌─────────────────────────┐ │
│  │      ☑ All Questions      │ │
│  └─────────────────────────┘ │
├─────────────────────────────┤
│  ⌂ Home   ◔ Progress   ⚙ Settings │ ← bottom nav, icon + label each
└─────────────────────────────┘
```
- The countdown/streak/ring block only renders if a test date exists (always true post-Profile-Setup now that the date is required there).
- **The ring is a Confidence Score** (`ReadinessEstimator`'s pass-probability estimate), not a generic mastery/progress percent — labeled with just the percent inside the ring, no separate text label, per the app plan's "warmer, less exam-like language" convention.
- **There is no separate "today's goal" text** — an earlier version of this doc showed one; it isn't implemented.
- **Streak and ring sit in the same row as the countdown text** (`SpaceBetween`), not stacked as separate rows — the streak (flame icon + number, no "day streak" wording) only appears when `streakDays > 0`; it's hidden entirely on day 0, not shown as "0".
- The four-card grid (Recall Mode / Practice Test / Flash Cards / Review Missed) plus full-width **All Questions** row is unchanged from the earlier version of this doc.
- Greeting falls back to plain "Good morning" (no name) if no name is set.

### Profile

```
┌─────────────────────────────┐
│  ← Profile                   │
│                               │
│          (☺)                 │ ← generic person icon, not an initial
│                               │
│  Name                        │
│  [___________________]       │
│                               │
│  Interview date               │
│  [ Pick a date       ▾ ]     │
│  (same hint copy as Profile   │
│   Setup)                      │
│                               │
│  Filing date                  │
│  [ Pick a date       ▾ ]     │
│  (same hint copy as Profile   │
│   Setup)                      │
│                               │
│           [ Save ]            │
└─────────────────────────────┘
```
Same fields, hint copy, and required-ness as Profile Setup, editable after the fact. Reached only via the Home header avatar. The avatar is a plain Phosphor person icon, not a generated initial as an earlier version of this doc implied — there's no separate name label between the avatar and the Name field either.

### Recall Mode

```
┌─────────────────────────────┐
│  ✕  Recall Mode               │ ← X = quit (confirm dialog unless finished)
│                               │
│  Question 4                   │
│  Who is the governor of your  │
│  state now?                   │
│  [ Your answer___________ ]   │
│  (inline speech error text,   │
│   if any, shows here)         │
│                               │
│         ⬤ (mic button,        │
│          pulses/reacts to     │
│          your voice while     │
│          listening)           │
│       Answer by voice /       │
│       Tap to stop             │
│                               │
│  ┌─────────────────────────┐ │
│  │ ✓ Correct / ✕ Not quite  │ │
│  │ (if wrong: full answer    │ │
│  │  content shown)           │ │
│  └─────────────────────────┘ │
│                               │
│         [ Submit / Next ]     │
└─────────────────────────────┘
```
The whole question view scrolls if content (long feedback, etc.) doesn't fit. On completion:

```
┌─────────────────────────────┐
│  You passed! / Not this time  │
│  N correct, N incorrect       │
│  ┌─────────────────────────┐ │
│  │ ✓/✕ Question text          │ │
│  │ You said: "..."            │ │
│  │ (if wrong: correct answer) │ │
│  └─────────────────────────┘ │
│  ...(scrollable list)...      │
│           [ Done ]            │
└─────────────────────────────┘
```
Not documented at all in an earlier version of this doc. The mic is a large standalone circular button below the field with a live, voice-reactive pulse animation (not a small trailing icon inside the field, and not a static icon) — see the app plan's mic-input entry for the interaction details (auto-retry on transient recognition errors, offline preference, etc.).

### Practice Test

```
┌─────────────────────────────┐
│  ✕  Practice Test             │
│                               │
│  Question 4 of 20             │
│  What is one power of the     │
│  federal government?          │
│  Select 1                     │
│  ○ Print money                │
│  ○ Provide education          │
│  ○ Declare war                │
│                               │
│  ┌─────────────────────────┐ │
│  │ ✓ Correct / ✕ Not quite  │ │
│  │ Also accepted: [chip]     │ │
│  │  [chip] [chip]            │ │
│  └─────────────────────────┘ │
│         [ Submit / Next ]     │
└─────────────────────────────┘
```
Options become radio buttons (single-select) or checkboxes (multi-select) depending on the question's `answerMode`, and highlight green/red once revealed. Results screen: a custom circular score-arc with a tick mark at the official passing threshold, Pass/Fail headline, per-category accuracy bars, and a scrollable list of only the missed questions with correct answers shown.

### Flash Cards

```
┌─────────────────────────────┐
│  ← Flash Cards                │
│  Card 4 of 20                 │
│  ┌─────────────────────────┐ │
│  │                           │ │
│  │   Question text            │ │
│  │   (tap to reveal answer)   │ │
│  │                           │ │
│  └─────────────────────────┘ │
│      ◂           ▸            │ ← swipeable pager
│         [ I know this ]       │
└─────────────────────────────┘
```
Tapping the card flips it to show the full acceptable-answer set (with "any one of:"/"both of:" framing). "I know this" relabels to "Marked as known" once tapped. Empty-state and loading-spinner states exist for when the active bank has nothing to show yet.

### Review Missed

```
┌─────────────────────────────┐
│  ✕  Review Missed             │
│  12 questions to review        │
│  Weighted toward your most-    │
│  missed, least-recently-seen   │
│                               │
│  ┌───────────┐ ┌───────────┐ │
│  │  Recall    │ │  Multiple │ │
│  │            │ │  choice   │ │
│  └───────────┘ └───────────┘ │
└─────────────────────────────┘
```
Picking a format drops into the same question-view shape as Recall Mode or Practice Test respectively. If there's nothing to review yet, an empty state (icon + "Nothing to review yet" + explanation + Back button) replaces the format picker entirely. Results screen: "Review complete", "N of Total correct this time" (no pass/fail framing — this is a drill, not an exam simulation), scrollable per-question list, Done button.

### All Questions

```
┌─────────────────────────────┐
│  ← All Questions              │
│  [All] [Government] [History] │
│  [Integrated Civics]          │ ← filter chips, single-select toggle
│                               │
│  ▸ What is the supreme law    │
│    of the land?          (◔)  │ ← mastery ring shown if attempted
│  ▾ Name one branch of gov't   │
│    Legislative, Executive,    │
│    Judicial                   │
│  ▸ ...                        │
└─────────────────────────────┘
```
Tapping a question expands/collapses it in place (animated) to reveal the full answer content and any `answerNote`. Mastery and Focus Mode filters called for in the app plan's screen table aren't built yet — only the category chip row exists so far.

### Progress

```
┌─────────────────────────────┐
│  Progress                     │
│                               │
│  ┌─────────────────────────┐ │
│  │      Confidence            │ │
│  │        ◔ 72%                │ │
│  │  "You're getting there."   │ │
│  │  Weakest: Integrated Civics │ │
│  └─────────────────────────┘ │
│  ┌───────────┐ ┌───────────┐ │
│  │  Recall    │ │  Practice │ │
│  │   ◔ 68%     │ │   ◔ 81%   │ │
│  └───────────┘ └───────────┘ │
│  New 12  Learning 8  Familiar 6  Mastered 4 │
│  By category                  │
│  ▓▓▓▓▓▓▓░░░ Government   74%  │
│  ▓▓▓▓░░░░░░ History      41%  │
└─────────────────────────────┘
```
No `TopAppBar` — just a plain "Progress" header text under the shared bottom nav. Every section (mode accuracy, mastery tiles, category list) only renders once there's actual attempt data; empty states show encouraging placeholder copy instead. There's no streak calendar or "timeline phase" section — both are still blocked on the study-timeline work, which is deferred to the next version (see the app plan's Scope section).

### Settings

```
┌─────────────────────────────┐
│  Settings                      │
│                               │
│  Location                      │
│    State            Colorado ▸ │
│    Representative  Joe Neguse ▸│
│  Study                         │
│    Focus Mode            [ ⏻ ]│
│    Test version          Auto ▸│
│    Practice question count  20 │
│    Practice passing percent 60%│
│    Flash card order  Sequential▸│
│  Reminders                     │
│    Daily study reminder   [⏻] │
│    Reminder time      8:00 PM ▸│
│  Appearance                    │
│    Theme                 Auto ▸│
│    Larger text            [⏻] │
│  Content                       │
│    Officials data updated  ... │
│    Check for content updates ▸ │
│  Data                          │
│    Reset progress    (destructive)│
│                               │
│  Version 1.0                  │
│  Not affiliated with USCIS...  │
└─────────────────────────────┘
```
No `TopAppBar` either — a plain "Settings" header text, same pattern as Progress. Picking a new state auto-chains straight into the representative picker (if that state has any representatives), rather than leaving "Representative" showing "Not set" until the user taps it separately. Enabling the daily reminder requests `POST_NOTIFICATIONS` on Android 13+ if not already granted.

---

## Open items for the later theme pass

- Concrete color palette (currently generic Material 3 blue in `Theme.kt`)
- Exact type scale / font choice
- Spacing scale (currently ad hoc `8.dp`/`16.dp`/`24.dp` values inline)
- Whether the avatar shows an initial, a generated color avatar, or an actual photo/picker (currently a plain generic person icon everywhere)
- Motion/transition style between screens
- Splash has no app icon/mark yet — just title text; worth a real mark once the theme pass happens
- Walkthrough's page-1 illustration area is an intentional placeholder pending final visual design
