# US Citizenship Test — Android App

## Overview

A native Android app for studying and passing the USCIS naturalization civics test. Functionally modeled on existing apps in this category (home hub, practice test, flash cards, full question list, settings), but built around a core insight those apps miss: **the real civics test is an oral exam with no answer options.** Multiple choice trains recognition; the interview demands recall. The app's differentiating feature is a recall mode that simulates the real format, backed by progress tracking and a study timeline driven by the user's actual interview date.

Design and theme are original — only functionality is modeled on reference apps.

### Scope for v1

**In scope:** Recall Mode (including mic input), Practice Test, Flash Cards, Review Missed, All Questions, Progress tracking, daily study reminders, onboarding, settings, offline operation, manual content updates.

**Deferred to next version (decided 2026-09-27):** the full study timeline — `StudyPlanGenerator`'s phased daily goals (Learn/Practice/Review) and the Progress screen's streak calendar / timeline-phase section. The daily reminder itself (time picker + actual `WorkManager` scheduling) already ships in v1; it's only the phased-goal generator and calendar that are cut. See Open items.

**Out of scope:** ads, in-app purchases, sound effects, text-to-speech question reading, user accounts, cloud sync, backend services.

---

## Test facts this app is built on

These drive several design decisions and are worth stating explicitly:

- The civics test is **oral**. A USCIS officer reads questions aloud; the applicant answers from memory. No multiple choice, no answer sheet.
- Officers accept **reasonable paraphrases** — exact wording is not required.
- **Two active question banks**, determined by Form N-400 filing date:
  - Filed **before Oct 20, 2025** → **2008 test**: 100-question bank, up to 10 asked, 6 correct to pass, officer stops early on pass or fail (6 correct or 5 wrong).
  - Filed **on or after Oct 20, 2025** → **2025 test**: 128-question bank, 20 asked, 12 correct to pass, fail at 9 wrong. (Same bank as the briefly-used 2020 test, with modified administration.)
- **Filing date, not interview date**, decides the version. Interview backlogs mean both banks stay relevant for years.
- Roughly **20 questions in each bank are asterisked** by USCIS as a short list for applicants 65+ with 20+ years as a lawful permanent resident.
- Some answers **change over time** — elected officials, judicial appointments, statutory updates. The correct answer is whoever holds office at the time of the interview.
- Some questions have **no valid answer for DC and territory residents** (no voting senators; DC has a non-voting delegate).

Question content is public-domain USCIS material.

---

## Tech stack & architecture

Single-module Android app. A multi-module split would be unnecessary complexity at this size.

| Concern | Choice | Rationale |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose + Material 3 | — |
| Navigation | Navigation Compose, single Activity | — |
| Pattern | MVVM — ViewModel + StateFlow per screen | — |
| Content data | Bundled JSON assets + kotlinx.serialization | Question banks are read-only reference data; no DB needed |
| User settings | DataStore Preferences | Small key-value config |
| User progress | Room | Mastery stats, attempt history, study plan — queryable, growing, relational |
| Networking | OkHttp | One remote JSON fetch; Retrofit is overkill |
| Background work | WorkManager | Daily study reminder |
| Speech input | Platform `SpeechRecognizer` | Free, no API key, on-device where available |
| DI | Manual constructor injection via `AppContainer` | App is small; revisit Hilt if the graph grows |

### Project structure

```
app/
  build.gradle.kts
  src/main/
    AndroidManifest.xml              // INTERNET, POST_NOTIFICATIONS, RECORD_AUDIO
    assets/
      questions_2008.json
      questions_2025.json
      officials_fallback.json
    java/com/usctest/app/
      MainActivity.kt
      AppContainer.kt
      navigation/AppNavHost.kt
      data/
        model/
          Question.kt
          AcceptableAnswer.kt
          StateOfficials.kt
          UserProfile.kt
          UserSettings.kt
        local/
          AppDatabase.kt
          QuestionStatsDao.kt
          AttemptDao.kt
          StudyPlanDao.kt
        QuestionRepository.kt        // bundled JSON by active version
        OfficialsRepository.kt       // bundled fallback + validated remote fetch
        SettingsRepository.kt        // DataStore wrapper
        ProgressRepository.kt        // mastery, attempts, weak categories
        StudyPlanRepository.kt       // timeline + daily progress
      domain/
        AnswerNormalizer.kt          // text normalization pipeline
        AnswerGrader.kt              // lenient answer matching
        DistractorProvider.kt        // slotting + curated + adaptive difficulty
        MasteryCalculator.kt
        StudyPlanGenerator.kt
        ReadinessEstimator.kt
      speech/
        SpeechRecognizerManager.kt
      ui/
        splash/SplashScreen.kt
        profile/ProfileSetupScreen.kt + ViewModel
        onboarding/OnboardingScreen.kt + ViewModel
        home/HomeScreen.kt + ViewModel
        recall/RecallModeScreen.kt + RecallResultScreen.kt + ViewModel
        practicetest/PracticeTestScreen.kt + ResultScreen.kt + ViewModel
        review/ReviewMissedScreen.kt + ViewModel
        flashcards/FlashCardsScreen.kt + ViewModel
        allquestions/AllQuestionsScreen.kt + ViewModel
        progress/ProgressScreen.kt + ViewModel
        settings/SettingsScreen.kt + ViewModel
        theme/Color.kt, Theme.kt, Type.kt
      work/StudyReminderWorker.kt
build.gradle.kts (root)
settings.gradle.kts
gradle/libs.versions.toml
gradle.properties
```

Package: `com.usctest.app` (placeholder; renameable via Android Studio refactor).

---

## Data model

### Question schema

**Lock this before authoring any content.** Every field below is cheap to capture while writing a question and expensive to retrofit across 228 entries.

```kotlin
@Serializable
data class Question(
    val id: Int,
    val text: String,
    val acceptableAnswers: List<AcceptableAnswer>,
    val requiredCount: Int,          // 1 = "name one", 2 = "name two", etc.
    val answerMode: AnswerMode,
    val answerType: AnswerType,
    val category: String,            // Government / History / Integrated Civics
    val subcategory: String?,        // Principles of Democracy, Rights, etc.
    val isStateSpecific: Boolean,
    val isEssential: Boolean,        // USCIS-asterisked short list
    val distractors: List<String>,   // curated; empty -> slotted fallback
    val answerNote: String? = null,
    val overlapsWith: List<Int> = emptyList()
)

@Serializable
data class AcceptableAnswer(
    val canonical: String,                      // display form: "the Senate"
    val variants: List<String> = emptyList(),   // "Senate", "upper house"
    val keywords: List<String> = emptyList()    // distinctive tokens for matching
)

enum class AnswerMode { ANY_OF, N_OF, ALL_OF }

enum class AnswerType {
    PERSON, NUMBER, DATE, DOCUMENT, PLACE, RIGHT,
    RESPONSIBILITY, CONCEPT, WAR, BRANCH, SYMBOL, HOLIDAY, OTHER
}
```

**`answerMode` + `requiredCount`.** USCIS questions come in shapes that look identical as a flat answer list but teach opposite things:
- *"Name one branch or part of the government"* — six acceptable answers, needs **one** (`ANY_OF`, 1)
- *"What are the two parts of Congress?"* — two acceptable answers, needs **both** (`ALL_OF`, 2)
- *"Name two national U.S. holidays"* — eleven acceptable, needs **two** (`N_OF`, 2)

Without these fields, flash cards imply all listed answers are required, and no typed-answer grading is possible.

**`AcceptableAnswer` as a structured object.** Auto-grading requires knowing that "Senate," "the Senate," and "upper house" are the same answer, and that "Missouri" suffices for "Missouri (River)". This structure is what makes Recall Mode work without a cloud NLP service.

**`answerType`.** Constrains distractor sampling to the same semantic slot, so "Who is the Commander in Chief?" never draws "the Mississippi River" as an option. One enum per question replaces hand-curation across most of the bank.

**`distractors`.** The highest content risk in the project — see [Distractor generation](#distractor-generation).

**`overlapsWith`.** Question IDs sharing acceptable answers (rights vs. responsibilities, First Amendment freedoms vs. citizen-only rights, branches vs. lawmakers, the geography cluster). Read as a hard exclusion list during distractor generation.

**`isEssential`.** Powers Focus Mode. Captured from the asterisks present in the source material while authoring.

**`answerNote`.** Two cases: jurisdictional exceptions (DC and territory residents have no voting senators) and answers that change with elections or appointments. Surfacing a caveat only on questions where it's true keeps it meaningful, unlike a blanket disclaimer.

### Question banks

Full 2008 (100 questions) and 2025 (128 questions) USCIS sets in `questions_2008.json` / `questions_2025.json`. State-dependent questions (representative, senators, governor, state capital) are flagged `isStateSpecific` and resolved at runtime from `OfficialsRepository` against the user's selection.

### Officials data

`officials_fallback.json` bundled in assets covering 50 states + DC + territories: capital, governor, senators, representatives.

**Generation approach:** produce the congressional portion (100 senators, 435 representatives) from the public `unitedstates/congress-legislators` dataset via a small local script whose output is committed to the repo. Hand-maintain only the ~56 governor rows; capitals never change. This is less work than hand-compiling 535 rows and re-runs after every election. Governors are compiled from authoritative current sources during implementation and flagged for spot-check.

### Remote content updates

`OfficialsRepository` fetches JSON from a config-constant URL on a manual **Check for Content Updates** tap, compares a `version`/`updatedAt` field, and caches newer data to internal storage.

Hardening:
- Parse and **fully validate into memory before** atomically replacing the cache file. A malformed payload must never break state-specific questions.
- Sanity-check shape: all states present, non-empty representative lists, plausible field values.
- Always fall back to the bundled asset. The app works completely offline.
- Surface `lastUpdated` in Settings with a note that the correct interview answer is whoever holds office at interview time.

Hosting location (e.g. a GitHub raw URL under the developer's control) is swappable config.

### User data (Room)

```
QuestionStatsEntity(
  questionId, testVersion, timesSeen, timesCorrect, timesWrong,
  lastSeenAt, consecutiveCorrect, masteryLevel,
  recallCorrect, recallWrong, mcqCorrect, mcqWrong
)

AttemptEntity(
  id, testVersion, mode, startedAt, finishedAt,
  questionCount, correctCount, passed, endedEarly
)

AttemptAnswerEntity(
  attemptId, questionId, wasCorrect, submittedText, inputMethod
)
// inputMethod: TYPED | DICTATED | DICTATED_EDITED — diagnostics only, never affects scoring

StudyPlanEntity(testDate, filingDate, createdAt, phase, dailyNewTarget, dailyReviewTarget)
StudySessionEntity(date, questionsStudied, minutesStudied, goalMet)
```

Recall and MCQ results are counted separately. They measure different skills, and that split powers both the readiness estimate and the recognition-vs-recall warning.

### User profile

```kotlin
data class UserProfile(
    val name: String?,
    val testDate: LocalDate?,
    val filingDate: LocalDate?
)
```

Local only. No account, no password, no network, no age, no residency duration. Filing date auto-selects the question bank so the user never has to know which version applies (manual override available in Settings).

---

## Study modes

### Recall Mode — "The Real Test"

The flagship feature. Simulates the actual USCIS interview.

**Format, matching the real rules exactly:**
- **2008 bank:** up to 10 questions, 6 correct to pass, stops at 6 correct or 5 wrong
- **2025 bank:** 20 questions, 12 correct to pass, stops at 12 correct or 9 wrong
- Questions drawn randomly from the active bank
- No answer options shown — answer from memory
- Early stop is implemented and visible; reaching 6 correct ends the test with "You passed," exactly as an officer would

**Input: one text field, typing by default, mic optional.**

Every question presents a single answer field with the keyboard ready and a mic icon inside it. There is no mode toggle, no per-question switch, and no input-method setting.

- **Typing** is the default path.
- **Mic icon** dictates: `SpeechRecognizer` transcribes and writes the result **into the same text field** as ordinary editable text.
- **The transcript is editable before submission.** If recognition produces "Mrs. Sippy River," the user corrects it to "Mississippi River" and submits.
- Submitting grades whatever text is in the field, regardless of how it got there.

**No self-grading, anywhere in the app.** `AnswerGrader` produces every verdict. The only thing the user controls is the text being graded.

The editable transcript is what makes this safe. Users are by definition people whose English is still being assessed, and ASR word error rates are materially higher on accented speech. If a mistranscription went straight to grading, the app would punish the exact accent it exists to help someone practice with. Because correction happens *before* submission, scoring integrity is untouched — the grader still decides, it just decides on the words the user meant.

Supporting behavior:
- Prefer on-device recognition so the mic works without a network connection
- Low-confidence or empty recognition leaves the field for the user to type — no error dialog, no blocked flow
- If ASR is unavailable or permission is denied, the mic icon is simply **absent**; the field works exactly as before, and nothing nags
- `RECORD_AUDIO` requested contextually on first mic tap, never at launch

**Results:** pass/fail against the real threshold, per-question submitted text and verdict, missed questions with their full acceptable-answer sets, and a "Review these" action. Feeds mastery with heavier weight than MCQ, since recall is the harder and more diagnostic signal.

### Practice Test (multiple choice) — the scaffold

For first passes through unfamiliar material. Official counts and thresholds by default; custom configuration optional.

- **Single-select** (`ANY_OF`, requiredCount 1): one keyed correct answer plus three distractors. Where a question has several acceptable answers, one is keyed and every other acceptable answer becomes an ineligible distractor.
- **Multi-select** (`N_OF`): "Name two of the original states" presents more than two correct options among the distractors and requires exactly two selections. Correct only if both picks are acceptable.
- **Multi-select, all required** (`ALL_OF`): "What are the two parts of Congress?" requires every acceptable answer, no partial credit — matching the real rule.

**"Also accepted" reveal** — after every answer, right or wrong:

> Correct.
> *Also accepted: legislative, President, executive, the courts, judicial.*

Framed as expanding what the user knows, not as correction. Shown on wrong answers too, with the keyed answer highlighted.

This patches MCQ's core weakness: four options with one correct teaches a single answer and hides the answer *space*, so the user never learns that five other responses would also have passed. Recall Mode teaches the space naturally; MCQ needs this reveal to do the same.

### Flash Cards

`HorizontalPager`, tap to reveal, ordering options: sequential / random / weakest-first. The reveal shows the full acceptable-answer set with count framing ("any one of:" / "both of:"). Optional "mark as known" feeds mastery.

### Review Missed Questions

A session drawn only from questions the user has answered wrong, weighted by miss count and least-recently-seen. Available in either Recall or MCQ format. The single highest-value study feature and absent from reference apps.

### Focus Mode — 20 Essential Questions

A filter available to everyone, framed as:

> **Focus Mode: 20 Essential Questions** — a short list USCIS marks as core civics. If you're 65 or older and have been a permanent resident for 20+ years, these are the only 20 questions you'll be tested on.

No eligibility question is ever asked and no age or residency data is stored. A qualifying applicant recognizes themselves in that sentence and gets a 128→20 reduction; everyone else gets a high-yield set that works as a first study week or a night-before refresher. Applies across Recall Mode, Practice Test, Flash Cards, and All Questions.

---

## Answer grading

`AnswerGrader` is the riskiest logic in the app. Its failure mode is invisible: an over-lenient threshold silently credits wrong answers and tells someone they're ready when they aren't. It is built and proven in isolation before any UI depends on it.

Pipeline, identical for typed text and dictated transcripts:

1. **Normalize** (`AnswerNormalizer`) — lowercase; strip punctuation; collapse whitespace; drop leading articles and filler ("the", "a", "of the"); expand numerals ("2" → "two"); strip honorifics ("President X" → "X").
2. **Exact match** against every `canonical` and `variant`.
3. **Keyword match** — all `keywords` for an answer present in the response. Handles "I think it's the Missouri River" → matches Missouri.
4. **Fuzzy match**, split by answer shape rather than one global metric:
   - **Single-token answers** (most numbers, most names): character-level normalized Levenshtein. This is specifically for typos and ASR phoneme-confusion ("Missouri" vs. "Missoura") — word order isn't in play.
   - **Multi-word answers**: token-based comparison (set overlap / sorted-token comparison), not character distance. Character-level edit distance is the wrong metric here — a trivial reordering of a long phrase looks far apart, while a single wrong word in a short phrase can look deceptively close.
5. **Count check** — `ANY_OF` and `N_OF` need `requiredCount` distinct matches; `ALL_OF` needs every answer present.

**Fuzzy matching stays narrow — it catches typos and ASR noise, not novel paraphrases.** Those are opposite tuning goals (typo tolerance wants to be tight and safe; paraphrase coverage wants to be generous), so one global threshold shouldn't do both jobs. Paraphrase breadth belongs in per-answer authored `variants`/`keywords` instead — auditable and testable one answer at a time, not a single dial governing all 228 questions at once. Leniency mirrors the real officer's latitude (a reasonable paraphrase counts, exact wording isn't required), but it's leniency built from authored content, not from a loose fuzzy threshold.

**False-accept vs. false-reject is an explicit, asymmetric policy, decided before tuning, not discovered after.** A false reject costs the user a moment of "wait, that should've counted" — annoying, self-correctable, low stakes. A false accept costs them false confidence going into a real interview — the exact failure mode this whole feature exists to avoid. The tuning target is: accept a somewhat higher false-reject rate in exchange for a lower false-accept rate. Both rates are measured and reported separately against the test corpora below — never collapsed into one blended "accuracy" number, which can hide an ugly false-accept rate behind a good average.

### Building the test corpora (before tuning anything)

1. **Positive corpus** — realistic correct phrasings per answer.
2. **ASR near-miss corpus, built for real, not imagined** — feed each canonical answer through TTS with a few different synthetic voices, run the output through `SpeechRecognizer`, and use the actual returned transcripts as the corpus. Real ASR phoneme-confusion ("Missouri" → "Missoura," "Bill of Rights" → "Bill of Right") is a different error distribution than hand-invented typos, and this pipeline captures the real one.
3. **Hard-negative corpus** — realistic *wrong* answers, especially ones textually close to a correct one. This is the corpus that actually catches false accepts, and the one it's easiest to skip.
4. **Confusability matrix** — every acceptable answer in the full bank checked against every *other* answer in the bank (not just within its own question or `overlapsWith` list), flagged if the fuzzy match would blur them. This is the grading-side mirror of the distractor-dedup invariant, run once as a standing CI check rather than caught by luck while eyeballing one question.

All four run as an actual CI-tracked test reporting false-accept rate and false-reject rate separately. Frozen as a regression test the moment the threshold is accepted, per the existing "fuzzy threshold regression test" requirement below — just built from a real corpus first, not tuned by feel.

---

## Distractor generation

`DistractorProvider` resolution order:

1. **Curated distractors**, where present. Prioritized for the ~30–50 high-overlap questions and written to target real misconceptions — *"Who signs bills to become laws?"* → Speaker of the House, Chief Justice, Vice President. Each is a confusion a real applicant holds, so a wrong answer teaches separation of powers. "The Mississippi River" teaches nothing.
2. **Answer-type slotting** — sample other questions' answers with matching `answerType`, excluding this question's acceptable answers and anything in `overlapsWith`.
3. **Category-scoped sampling** as a last resort.

**Adaptive difficulty**, reading mastery data:
- `NEW` / `LEARNING` → easy, clearly distinguishable distractors
- `FAMILIAR` / `MASTERED` → near-miss distractors from the same slot

The test hardens as the user improves, keeping practice scores honest instead of plateauing on familiarity with option sets. Distractor selection reshuffles per session so nobody memorizes option positions.

**Hard invariant, enforced by a build-failing test:** no distractor may ever appear in any acceptable answer (canonical or variant) for its own question, or in any question listed in its `overlapsWith`. A user marked wrong for a genuinely correct answer either learns something false or stops trusting the app — unacceptable for a test with real stakes.

---

## Progress, mastery, and readiness

**Mastery levels** (`MasteryCalculator`): `NEW → LEARNING → FAMILIAR → MASTERED`. Promoted on consecutive correct answers, demoted on a miss. Leitner-style banding — transparent to the user and adequate for a 100–128 item bank. Recall results are weighted above MCQ results; the app believes the harder skill.

**Weak area detection:** `ProgressRepository` aggregates accuracy by category and subcategory, so Progress can say "Integrated Civics is your weakest area — 52%."

**Recognition-vs-recall warning.** The Progress screen flags anyone who has only ever done multiple choice:

> You're scoring 92% on practice tests but haven't tried Recall Mode. The real test has no answer options.

Recognition without recall is the most common way someone who feels prepared still fails, and the app is positioned to catch it.

**Readiness estimate** (`ReadinessEstimator`): Monte Carlo simulation over per-question accuracy using the real format (10 questions needing 6, or 20 needing 12), run a few thousand times, weighted toward recall performance. Reports estimated pass probability plus the categories dragging it down. Labeled clearly as an estimate based on practice performance, not a prediction, with the disclaimer kept visible.

---

## Study timeline

**Deferred to next version (2026-09-27) — see Scope and Open items.** The daily reminder (time picker + `WorkManager` scheduling) described at the end of this section already shipped in v1. Everything else below — `StudyPlanGenerator`'s phased daily goals and the streak calendar — is kept here as the spec to pick back up later, not as work still in progress now.

Driven by the optional test date from profile setup (editable in Settings).

`StudyPlanGenerator` produces daily goals across three phases:

1. **Learn** — MCQ on new questions
2. **Practice** — full Recall Mode tests
3. **Review** — missed questions plus mastery upkeep

Mechanics:
- `daysRemaining = testDate − today`
- Questions to master = bank size − already mastered
- `dailyNewQuestions = ceil(remaining / max(1, daysRemaining − reviewBufferDays))`
- Roughly the **last 20% of the timeline is reserved** for review and practice tests rather than new material — cramming new questions up to the interview date is how people fail
- Clamped to a sane daily maximum; if the math would demand more than ~25 new questions a day, tell the user the timeline is tight rather than silently generating an impossible goal
- Phase gating deliberately shifts users from MCQ toward Recall Mode as the date approaches

`StudySessionEntity` logs daily activity, producing a streak counter and a calendar heat strip.

**Daily reminder** via WorkManager at a user-set time. `POST_NOTIFICATIONS` requested contextually when reminders are enabled, never at launch.

With no test date set, everything degrades gracefully to plain progress tracking with no timeline.

---

## Screens

Full layout wireframes and the visual design language live in a separate companion doc — **[us-citizenship-test-design-doc.md](us-citizenship-test-design-doc.md)** — kept apart from this plan specifically so a future UI/theme pass can iterate on it without touching architecture or build order. The table below is the functional summary; the design doc is the source of truth for layout and visual direction.

| Screen | Behavior |
|---|---|
| **Splash** | Brief animation (~1.2s, skippable on tap). Covers the async DataStore read that decides the start destination, preventing the Home-then-onboarding flash. |
| **Profile Setup** | Name (optional), interview date (optional — defaults to **today + 14 days** if left blank), filing date (optional). Local only. **Not skippable** — always shown on first launch and always leads onward via a single "Continue," but no field is individually required. |
| **Onboarding** | State list → Representative list (filtered by state). State selection is the only required step. |
| **Profile** | New screen, separate from Settings. Opened from the avatar in the Home header. Shows/edits the same fields as Profile Setup (name, interview date, filing date). |
| **Home** | Header row: greeting ("Good morning, {name}") on the left, profile avatar/initial on the right (→ Profile). No test-version banner here — that lives in Settings only. Below the header: days until test, today's goal, progress ring, current streak (only rendered if a test date exists; collapses cleanly to just the cards otherwise). Cards: Recall Mode, Practice Test (2-up row), Flash Cards, Review Missed (2-up row), then **All Questions full-width** on its own row (no separate Progress card — the progress ring above already covers that). |
| **Recall Mode** | Oral-exam simulation: no options, one editable answer field (type by default, mic icon to dictate), auto-graded on submit, official counts, thresholds, and early stop. |
| **Recall Results** | Pass/fail vs. real threshold, per-question submitted text and verdict, full acceptable answers for misses, "Review these" action. |
| **Practice Test** | MCQ scaffold. Single or multi-select by `answerMode`, adaptive distractors, "also accepted" reveal, progress indicator, quit confirmation. |
| **Results** | Score vs. official passing bar, per-category breakdown, missed questions with correct answers, "Review these" action. |
| **Review Missed** | Drawn only from wrong-answered questions, weighted by miss count and recency. Either format. |
| **Flash Cards** | Pager, tap to reveal, ordering options, full answer set with count framing. |
| **All Questions** | Active version, expandable answers, mastery badge per question, filters by category / mastery / Focus Mode. |
| **Progress** | Mastery breakdown, recall vs. MCQ accuracy, category accuracy, attempt history trend, streak calendar, readiness estimate, timeline phase, recognition-vs-recall warning. |
| **Settings** | State/rep display + change, test and filing dates, Focus Mode toggle, font scale, test version (auto from filing date, manually overridable), practice config (official/custom + steppers), flash card order, reminder time, theme (Auto/Light/Dark), officials `lastUpdated` + Check for Content Updates, reset progress (with confirmation), about/version, "not affiliated with USCIS" disclaimer. |

**Bottom navigation:** Home · Progress · Settings — icons + text labels (Phosphor icon set, see design doc). Settings stays in the nav bar; it is not reachable from the Home header avatar (that goes to Profile instead).

### Cross-cutting UI notes

- **Visual direction:** clean, minimal, generous whitespace, restrained color, Apple-adjacent — detailed in the design doc, kept separate so it can be revised in a dedicated theming pass without touching this plan.
- **Icon set:** [Phosphor Icons](https://github.com/adamglin0/compose-phosphor-icon) (`com.adamglin:phosphor-icon`), Regular weight by default, chosen to approximate SF Symbols' minimal line style without the licensing issues of using Apple's actual icon set (SF Symbols are licensed for Apple-platform apps only) or reaching for Flutter-only packages like `cupertino_icons` (no Compose artifact).
- **Font sizing:** use `sp` throughout and respect the system font scale. The Settings font control is an additional multiplier, not a replacement for the system setting.
- **Accessibility:** content descriptions on flash card flips (TalkBack must announce the reveal), on the mic button state, and on the Recall Mode verdict. This audience skews older and includes non-native English speakers, so accessibility matters unusually much.
- **Theme:** Compose `isSystemInDarkTheme()` with a DataStore override.

---

## Build order

Content authoring comes late, after the schema has survived contact with real UI.

1. **Scaffold** — Gradle root + app module, version catalog, manifest, MainActivity, Compose theme skeleton. Blank app builds and runs.
2. **Content data layer** — models, `SettingsRepository` (DataStore), `QuestionRepository` (JSON parsing), `OfficialsRepository` (bundled + validated remote fetch).
3. **Sample content** — 8–10 hand-written questions per version covering every `answerMode`, every common `answerType`, a state-specific case, a Focus Mode case, and a high-overlap case. Officials data for 2–3 states.
4. **Room layer** — database, entities, DAOs, `ProgressRepository`, `StudyPlanRepository`.
5. **Splash + Profile Setup + Onboarding + Home + Profile + navigation graph**, including the async start-destination fix. Built from the wireframes in the design doc.
6. **All Questions** — simplest content screen; validates the data layer end to end.
7. **`AnswerNormalizer` + `AnswerGrader`** — pure Kotlin, heavily unit-tested against a corpus of real phrasings before any UI depends on it.
8. **Recall Mode** — full oral-exam flow, official rules, auto-grading, single answer field. Ships complete and usable with typing alone.
9. **Mic input** — `SpeechRecognizerManager`, contextual permission, transcript written into the existing field, offline preference. Purely additive; step 8 must be fully functional without it. **Done** (2026-09-27) — see Open items.
10. **Flash Cards.**
11. **`DistractorProvider` + Practice Test + Results** — slotting, curation, multi-select, adaptive difficulty, "also accepted" reveal.
12. **Schema freeze + bulk content authoring** — full 2008 and 2025 banks with structured acceptable answers, variants, keywords, answer types, `overlapsWith`, and curated distractors for the high-overlap subset. Full officials data via the generation script plus hand-compiled governors.
13. **Progress screen + Review Missed.**
14. **Study timeline + reminders.** Reminders (time picker + `WorkManager` scheduling) **done**. Study timeline (phased daily goals + streak calendar) **deferred to next version** (2026-09-27) — see Open items.
15. **Settings** wiring everything together.
16. **Verification pass.**

Wireframing happens before step 5 — see the design doc for the agreed layouts.

---

## Verification

### Automated (CI must run and pass)

`./gradlew assembleDebug`, `./gradlew test`, `./gradlew lint`

**Content invariants:**
- Every question has ≥1 acceptable answer; `requiredCount ≤ acceptableAnswers.size`; valid category and answer type
- **No distractor appears in any acceptable answer (canonical or variant) for its own question, or in any question listed in `overlapsWith`**
- Derived overlap detection flags any answer string shared between questions not declared in `overlapsWith`
- Every question with an empty `distractors` list has enough same-`answerType` peers for slotting to produce three options
- JSON schema validation fails the build on malformed content

**Grading (`AnswerGrader`) — tested hardest:**
- Exact, variant, and keyword paths each hit and each reject appropriately
- Character-level fuzzy match (single-token answers) and token-based fuzzy match (multi-word answers) each tested on their own corpus — they're different metrics for different answer shapes, not one dial
- Positive corpus (realistic correct phrasings, including conversational framing) all grade correct
- ASR near-miss corpus, generated via real TTS→`SpeechRecognizer` round-trips (not hand-imagined), grades correct where reasonable
- Hard-negative corpus (realistic wrong answers, especially ones textually close to a correct one) all grade wrong — this is the suite that actually catches false accepts
- Confusability matrix: every acceptable answer checked against every other answer in the full bank (not just its own question or `overlapsWith`), flagged if fuzzy matching would blur them
- False-accept rate and false-reject rate measured and reported **separately** — never collapsed into one blended accuracy number
- `ANY_OF` / `N_OF` / `ALL_OF` count logic, including partial answers on `ALL_OF`
- Fuzzy threshold regression test, so tuning can't silently loosen — frozen only once the corpora above are built and the threshold is deliberately accepted

**Format and logic:**
- Recall Mode early stop fires at exactly 6 correct / 5 wrong (2008) and 12 / 9 (2025)
- MCQ scoring matches official rules for both versions
- State-specific resolution for all 50 states + DC + territories, including the DC/territory senator case
- `StudyPlanGenerator` edge cases: test date today, in the past, absent, 500 days out, all questions mastered
- `MasteryCalculator` promotion and demotion, with recall weighted above MCQ

### Manual

- Splash → profile → state/rep → home → every mode → settings
- Settings and progress persist across app restart and process death
- Recall Mode by typing: field focused and keyboard up on question load; submit grades correctly
- Recall Mode via mic: transcript lands in the field as editable text, is correctable before submit, grades on the corrected text
- Recall Mode with `RECORD_AUDIO` denied or ASR unavailable: mic icon absent, typed flow unaffected, no error state
- Deliberately fail both modes; confirm misses appear in Review Missed and mastery demotes
- Set a test date 14 days out; confirm daily goals and phase labels are sane
- Airplane mode: full app works, on-device ASR works or degrades with explanation, content update fails gracefully
- State-specific questions reflect the onboarding selection
- TalkBack pass over Recall Mode, flash cards, and the practice test
- Largest system font scale — no clipped text

### Pre-release

- "Not affiliated with USCIS or DHS" disclaimer in-app and in the store listing
- Play Store data-safety declaration: local-only profile, plus microphone use declared as processed for recognition and neither stored nor transmitted (assuming on-device recognition)
- Officials data spot-checked against authoritative sources before publication

---

## Open items

Kept as one running checklist so nothing agreed-to-defer gets silently forgotten. Each item names the step that's supposed to close it.

**Content — step 12 (content authoring) is now substantially closed:**
- **Question banks are the full, authentic 100 (2008) / 128 (2025) USCIS question sets**, pulled verbatim from the official USCIS PDFs (`100q.pdf` and the 2025 `128 Civics Questions and Answers` PDF), not paraphrased from memory. Every question has correct `answerMode`/`requiredCount` for its shape, all 20 asterisked "65/20" essential questions per bank correctly flagged, state-specific questions flagged, and `answerNote` on the handful of questions whose answer is a current officeholder (President/VP/Speaker/Chief Justice) — those carry the current name as of 2026-09 plus an explicit caveat that it changes with elections/appointments.
- **`previewAnswers`** authored on every real `N_OF` question in both banks (holidays, cabinet positions, original states, rights of everyone, civic participation, Oath of Allegiance promises), not just the one sample question.
- **Curated `distractors` + `overlapsWith`** authored on the clearest high-overlap clusters in both banks (executive-branch role questions, Congress structure, Civil War pair, Lincoln/Emancipation pair, MLK/civil-rights pair, Korean/Vietnam "stop communism" pair, holiday cluster, flag/anthem symbol questions). This is a reasonable pass, not exhaustive — the full 30-50 count from the original plan wasn't separately tracked against these two banks; treat further curation as incremental, not a gap to close in one sitting.
- **Officials data now covers all 56 entries** (50 states + DC + PR + GU + VI + AS + MP): senators and representatives scripted directly from the `unitedstates/congress-legislators` `legislators-current.yaml` (539 records, CC0 public domain, verified against real current membership — e.g. 100 senators/0 vacancies, 439 reps/delegates with 2 real current House vacancies reflected accurately), capitals hardcoded (static facts), and governors hand-compiled from Wikipedia's current-governors list cross-checked against the assistant's own training knowledge — the one part of this data with no live-updating source, so it's the part most worth spot-checking before relying on it for an actual interview.
- Added `QuestionBankContentTest` (`app/src/test/java/com/usctest/app/data/`): loads both bank JSON files straight off disk (no Android context needed) and enforces the plan's content invariants — valid shape/category/requiredCount, and the hard distractor-collision invariant checked against the real content at both NEW and MASTERED mastery levels. All passing.

**Answer grading — pipeline is built and unit-tested, but not proven against real-world input yet. The full bank now exists, so this is unblocked, just not yet done:**
- **No real ASR near-miss corpus exists yet** (the real one — see below for a hand-authored stand-in). The plan calls for generating this via real TTS→`SpeechRecognizer` round-trips, not hand-imagined transcripts. Mic input (step 9) is now built (2026-09-27, see below), so this is fully unblocked — just not yet done.
- **No confusability matrix exists yet** (every acceptable answer checked against every other answer in the full bank). Unblocked by the real bank now existing, just not yet run. (A cheaper `overlapsWith`-based sweep was tried instead on 2026-09-20 — see below for why that approach doesn't substitute for the real thing.)
- **Fuzzy match thresholds are hand-picked defaults right now** (1 edit for single-token, "allow one missing token" for 3+-word answers), not yet empirically tuned against the three corpora above. Current unit tests prove the *logic* is correct, not that the *thresholds* are right.

**2026-09-20 — hard-negative + prose-paraphrase corpus built (`AnswerGraderCorpusTest`), currently red on purpose. Fixing it is deferred — logged here so it isn't forgotten:**
- Built against the real bank (not toy answer lists): a hand-authored corpus of realistic paraphrases + near-miss wrong answers for all 56 sentence-style ("What is X?"/"Why does...?") questions across both banks, plus a small hand-authored ASR word-boundary corpus (real TTS→`SpeechRecognizer` corpus still blocked on mic input, per above).
- Tried an automated sweep of every question against every `overlapsWith`-linked question's answers first. Mostly false alarms — `overlapsWith` means "don't reuse as an MCQ distractor," not "these two questions' answers are mutually exclusive" (e.g. "the President" is legitimately correct for several different executive-branch questions; Washington is legitimately both "Father of Our Country" and "the first president"). Pulled that test back out rather than leave a permanently-noisy check in the suite; the two real bugs it did catch are folded into the corpus below as explicit cases.
- **4 confirmed false accepts** (must fix before this is safe): Veterans Day accepts Memorial Day's full answer text (both satisfy Veterans Day's `(honor, military)` keyword pair); Independence Day accepts "Presidents Day (Washington's Birthday)" (the lone keyword `birthday` fires on any text containing that word) and "Juneteenth National Independence Day" (Juneteenth's own name contains "independence"); and a Ben Franklin question accepts an unrelated wrong answer because "U.S." normalizes into two throwaway single-character tokens (`u`, `s`) that cheaply satisfy fuzzy-match tolerance for any answer padded with "U.S.".
- **32 false rejects out of ~180 realistic correct paraphrases (~18%)**, three root causes: (1) missing stemming — "honors"≠"honor", "voting"≠"vote", "libraries"≠"library", "Allies"≠"Allied", etc.; (2) ordinal numerals never expand ("34th" ≠ keyword "thirty-fourth" — `AnswerNormalizer.expandNumeral` only handles plain digit tokens, not "34th"; likely affects the whole presidents/Founding-Fathers cluster since those answers are keyed on spelled-out ordinals); (3) plain missing keyword synonyms ("obey" vs "follow", "recession" vs "a long severe downturn", etc.) — the known, accepted cost of keyword-only paraphrase coverage.
- **2 ASR word-boundary gaps**: "York town" and "Jeffer son" (a name split across two tokens) both fail — single-token phoneme slips ("Rosevelt", "Vence", "Robert's", "Jonson") all pass correctly, so this is specifically a segmentation gap, not general ASR fragility.
- Fix plan when this is picked back up, roughly in priority order: the 4 false accepts (non-negotiable), ordinal-number expansion in `AnswerNormalizer` (highest leverage per line of code), basic suffix stemming, then the remaining keyword-synonym gaps — after which `AnswerGraderCorpusTest` should go green with zero tolerance either way.

**2026-09-20 — all 4 confirmed false accepts fixed.** The 32 false rejects and 2 ASR word-boundary gaps are still open (unchanged, tracked above).
- **Keyword-collision false accepts** (Veterans/Memorial, Independence/Presidents, Independence/Juneteenth): added an `excludeKeywords` field on `AcceptableAnswer` — the keyword tier now requires the listed keywords present *and* none of `excludeKeywords` present. Scoped narrowly to the 3 known collisions (Veterans Day's `(honor, military)` answer excludes `died`; Independence Day's `birthday` answer excludes `washington`/`presidents`; its `independence` answer excludes `juneteenth`) rather than a blanket rule — same content-driven philosophy as `variants`/`keywords` themselves. Considered tightening the keyword sets instead (no schema change, but raises false-rejects on legitimate paraphrases missing the added word) and phrase-proximity matching (didn't reliably distinguish these specific pairs); the exclude-list is more surgical.
- **Ben Franklin "U.S." false accept**: root cause was `AnswerNormalizer` scattering dotted abbreviations ("U.S.") into throwaway single-character tokens (`u`, `s`) via blanket punctuation-stripping. Fixed generally, not just for this question — dotted abbreviations (`U.S.`, `U.S.A.`, `U.K.`, `D.C.`, etc.) now collapse into one token before punctuation-stripping runs, and an alias step folds undotted forms onto the same token as their abbreviated counterpart (`USA` → same token as `US`), so either form matches either form on both sides of grading.
- Regression coverage added in `AnswerNormalizerTest` (abbreviation collapsing/aliasing) and `AnswerGraderTest` (excludeKeywords suppression, the U.S.-padding case) in addition to the existing `AnswerGraderCorpusTest` assertions.

**2026-09-20 — ordinal-number expansion and basic suffix stemming added; false rejects down from 32 to 24 (verified by actually running the suite — see below).** Remaining 24 are keyword-synonym gaps ("obey" vs "follow", etc.), unchanged — still the accepted cost of keyword-only paraphrase coverage, not addressed here. The 2 ASR word-boundary gaps are also unchanged.
- **Ordinal expansion**: `AnswerNormalizer` only expanded plain cardinal numerals ("27" → "twenty seven"); ordinal-suffixed numerals ("34th") passed through untouched, so they could never match a keyword authored as a spelled-out ordinal ("thirty-fourth") — the whole presidents/Founding-Fathers cluster's root cause. Added the ordinal counterpart of the existing cardinal word lists and an ordinal-suffix regex, reusing the same expansion path.
- **Suffix stemming**: added a minimal stemmer (plural `-s`/`-es`/`-ies`, verb `-ing`/`-ed`, with the two English spelling-rule reversals needed for "vote"/"voting" and "ally"/"allied") — narrow on purpose, not a general-purpose stemmer, scoped to the exact gaps the corpus found. Two safety boundaries: (1) skipped entirely for person-name answers (`isPersonName = true`), since surnames routinely end in "s" ("Adams") and stemming would corrupt them into a different name; (2) added as an opt-out parameter (`applyStemming`, default `true`) on `AnswerNormalizer.normalize` rather than a global change, because `DistractorProvider` (and `QuestionBankContentTest`'s own inline distractor-collision check) calls the same function for dedup/collision keys — broadening that key's equivalence classes was never exercised by this fix and risked shrinking an already-tight distractor pool for an unrelated concern. Both now explicitly pass `applyStemming = false` to preserve prior behavior.
- **Two bugs the JDK-21 test run itself caught** (this iteration was the first time this specific test suite actually ran, not just hand-traced — see Verification note below):
  1. The stemmer's `-es` rule dropped 2 characters unconditionally, incorrectly turning "representatives" into "representativ" instead of "representative". Fixed to only drop 2 characters for genuine sibilant plurals ("boxes", "watches"); a bare "-s" after an existing "e" ("representatives", "votes") now only loses the "s".
  2. The `excludeKeywords` fix from the false-accept work above only guarded the keyword tier — Veterans Day's false accept on Memorial Day's answer still slipped through the separate fuzzy tier via a short 4-token variant ("honors people in the military") that fuzzy-tolerates one missing word. Restructured `excludeKeywords` to veto the whole answer up front, across all three tiers, not just tier 2.
  3. Running the full suite (not just the domain package) surfaced a real, previously-invisible content quality issue: `DistractorProvider`'s "any other question" fallback had assigned Q59 ("power only for the states") the distractor "Declares war" (Q20's answer), which is the same fact as Q58's "Declare war" that Q59 legitimately overlaps with — invisible before because the two verb forms were literal-string-distinct. **Fixed 2026-09-27** (see the dated entry near the end of this section) by adding Q20 to the `overlapsWith` list; the confusability matrix work item above is still the right place to catch more of these systematically.
- Regression coverage added in `AnswerNormalizerTest` (ordinal expansion, stemming, the person-name/double-s/sibilant-plural guards) and `AnswerGraderTest` (both fixes end-to-end through the grader).

**Verification note (2026-09-20):** found a JDK 21 install already on this machine (`~/.jdks/jbr-21.0.11`, bundled with Android Studio) that this Gradle/Kotlin-DSL version actually works with — the JRE 8 / JDK 25 combination previously on `PATH` could run neither. `JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew testDebugUnitTest` now runs clean. Use this JDK for any future test/build verification in this environment instead of assuming the tests can't be run.

**2026-09-27 — Step 9 (mic input) built.** `SpeechRecognizerManager` wraps the platform `SpeechRecognizer`; the Recall Mode mic button requests `RECORD_AUDIO` contextually (on first tap, not at launch), transcribes into the same editable text field per the original design, and is fully additive on top of the typing flow from step 8.
- **Live volume-reactive UI**: `onRmsChanged` feeds a smoothed, spring-animated pulse (two translucent rings plus a subtle idle "breathing" loop) around the mic button, so it visibly reacts to the user's voice while listening — modeled on the Duolingo/Wispr Flow mic affordance, not a static icon.
- **Auto-retry on transient failures**: Android's "how long to wait before you start speaking" timeout (roughly 1-2s) isn't exposed via any public API, and on the test emulator this caused frequent spurious `ERROR_NO_MATCH`/`ERROR_SPEECH_TIMEOUT` failures — sometimes before the user had even started talking. Rather than surface every one of these as a manual-retry error, `SpeechRecognitionEvent.Error` now carries an `isTransient` flag, and the screen silently restarts listening up to 3 times before showing anything to the user. Any partial/final result resets the retry counter.
- **Trailing-silence tuning**: added `EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS` / `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` / `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS` to the recognizer intent — the default trailing-silence window was clipping answers after roughly 1 second, cutting people off mid-sentence.
- **Offline preference**: `EXTRA_PREFER_OFFLINE` set on every listen request. On API 33+, `AppContainer.init` proactively calls `SpeechRecognizerManager.ensureOfflineModelDownloaded()` at app startup (via `SpeechRecognizer.isOnDeviceRecognitionAvailable` / `createOnDeviceSpeechRecognizer` / `triggerModelDownload`) so the on-device model has the whole rest of the session to finish downloading before Recall Mode is ever opened, rather than triggering it reactively when the mic is first tapped. Below API 33, or once the model's already present, this is a no-op — the system recognizer still gracefully falls back to network if no offline model is available. **Bundling our own offline STT engine (e.g. Vosk) in the APK was considered and explicitly declined** — Google's actual on-device model is proprietary and can't be bundled by a third-party app regardless; a separate bundled engine was rejected for the size cost (~40-50MB) and accuracy tradeoff versus just triggering the system's own download.
- **Two bugs found via live emulator testing, both fixed**: (1) Recall Mode's question `Column` had no scroll modifier, so a long "also accepted" feedback list clipped the Submit/Next button off-screen — fixed with `verticalScroll(rememberScrollState())`. (2) mic state (`isListening`/`speechError`/`micLevel`/retry count) lived in the per-question composable, which stays mounted across the whole Recall Mode session, so a stale error from one question was still showing on the next question until the user interacted with the mic again — fixed with a `LaunchedEffect(current.question.id)` that resets all of it (and stops any lingering recognizer session) whenever the active question changes.
- Diagnosed via live logcat capture on the test emulator (`RecognitionServiceImpl`/`NetworkSpeechRecognizer`/`SodaSpeechRecognizer` traces) rather than guesswork — confirmed the emulator's virtual mic needed "Enable Host Microphone Access" + "Virtual microphone attached" turned on in Extended Controls before any audio reached the recognizer at all.

**2026-09-27 — both "minor nits" logged below on 2026-09-19/2026-09-20 fixed:**
- **Settings' "change state" flow now auto-chains into representative selection.** Picking a new state in `SettingsScreen` closes the state picker and, if that state has any representatives listed, immediately opens the representative picker instead of leaving "Representative" silently showing "Not set" until the user notices and taps it separately.
- **The Q20/Q58/Q59 near-duplicate distractor** (`DistractorProvider`'s fallback could hand Q59 "Declares war" — Q20's answer — which reads as the same fact as Q58's own correct answer "Declare war") is fixed by adding `"overlapsWith": [58, 59]` to Q20 in `questions_2025.json`, using the existing overlap-exclusion mechanism rather than changing the ranking algorithm. Only the 2025 bank has this pair; the 2008 bank doesn't share this content. Confirmed via `AnswerGraderCorpusTest`/`DistractorProviderTest` still showing the same pre-existing, already-logged 24-false-reject count with no new failures.

**Stale note corrected (2026-09-27): daily reminders were already fully wired, not "persisted only."** Earlier entries in this doc (step 14's build-order note, the "screens not yet built" line, and the step 15 notes below) said nothing scheduled the actual `WorkManager` notification. That was stale — `SettingsRepository.setReminder` calls `ReminderScheduler.schedule`/`cancel` when the toggle/time changes, and `StudyReminderWorker` self-reschedules for the next day after firing (see the "self-rescheduling one-time work" doc comment on `ReminderScheduler` itself). Confirmed by reading the current code, not assumed. Reminders are done; only the phased study-timeline generator and streak calendar (see Scope section above) are actually deferred, and that's now a deliberate v2 decision rather than an in-progress gap.

**Screens built so far:** Splash, Profile Setup, Onboarding, Home, Profile, All Questions (with category filters), Recall Mode + Results (typing and mic input both complete), Flash Cards, Practice Test + Results (+ `DistractorProvider`), Review Missed, Progress, Settings.

**Not yet built, deferred to next version (see Scope section):** the study-timeline phased-goal generator (`StudyPlanGenerator`) and the Progress screen's streak calendar / timeline-phase section. Daily reminders themselves (time + on/off + actual scheduling) are done, not part of this deferral.

**Naming convention, adopted 2026-09-20 — apply going forward across the app:** avoid traditional test/exam framing in copy and card names (scoring, thresholds, "estimate," "readiness") in favor of warmer, less fear-inducing language. Concretely: the readiness/pass-probability metric is called **"Confidence Score"** everywhere it appears (Home ring, Progress hero), shown without a formal label — just the ring and a one-line encouraging blurb ("You're in great shape!" / "You're getting there." / "Keep at it — you'll get there."), not "Readiness Estimate: 72%." Keep this in mind when naming any new card, section, or status message.

**Step 13b notes (2026-09-20) — Progress screen + category filters:**
- **Progress** (`ui/progress/`) built, replacing the `ComingSoonScreen` nav-bar placeholder. Sections, top to bottom: a **Confidence Score** hero (ring + friendly blurb + a soft nudge toward weakest categories when not already looking good — no formal "Readiness Estimate" label, per the naming convention above), side-by-side **Recall Mode** / **Practice Test** accuracy rings (replacing a "recall vs. MCQ" framing), a **Mastery** tile row (New/Learning/Familiar/Mastered counts), and a weakest-first **By category** list. Deliberately left out, and why: attempt history and a recognition-vs-recall warning (removed per direction, judged not worth the space); a full streak calendar and "timeline phase" (both genuinely blocked on `StudyPlanGenerator` / the unused `StudySessionEntity` logger — still step 14, sequencing unchanged from the original build order).
- New `domain/ReadinessEstimator.kt`: Monte Carlo simulation (5000 runs) over per-question accuracy using the real exam format, weighted toward Recall performance (2x) over MCQ (1x); a never-attempted question gets a conservative 0.3 prior rather than being ignored. Reports pass-probability percent plus the two weakest categories.
- Extracted `RecallRules`/`rulesFor` out of `RecallModeViewModel` into a new shared `domain/ExamFormat.kt` (`ExamRules`/`ExamFormat.rulesFor`), since `ReadinessEstimator` needed the same 10-of-6 / 20-of-12 constants.
- `ProgressRepository` gained `getConfidenceEstimate`, `getModeAccuracy` (Recall/Practice split), `getMasteryBreakdown`, and `getCurrentStreakDays` (derived directly from `AttemptEntity` timestamps via `kotlinx.datetime`, not the unused `StudySessionEntity` table — avoids wiring a session logger into every screen just for a streak number). Removed the unused `hasEverUsedRecallMode()` (only ever consumer was the now-cut recognition-vs-recall warning).
- **Home screen changed**: the progress ring now shows the same Confidence Score instead of a separate "mastery percent," so it's one consistent number in both places. The streak (flame icon + day count) moved here too, next to the ring, instead of living in a Progress-screen section.
- Extracted `CategoryAccuracyBar` (previously private to Practice Test results) into `ui/common/AccuracyBar.kt`, alongside a new generic `AccuracyRing` composable, so Progress and Practice Results share both instead of duplicating ring-drawing code.
- **All Questions** got category filter chips ("All" + each of Government/History/Integrated Civics, tap to toggle) in `AllQuestionsViewModel`/`Screen`. Mastery and Focus Mode filters from the plan's screen table are still open — only category was requested so far.

**Step 13 notes (2026-09-20):** Review Missed (`ui/reviewmissed/`) draws from `ProgressRepository.getMissedQuestionIds(testVersion)`, ordered most-missed then least-recently-seen via `AttemptDao`'s `getMissedQuestionSummaries` query. Two pre-existing gaps in that query got fixed as part of this: it wasn't scoped to a test version at all (question IDs restart from 1 in each bank, so 2008 and 2025 misses were being conflated), and it never excluded questions the user has since drilled back to `MASTERED` — both fixed now, since Review Missed is the first real consumer. The screen offers Recall or MCQ format (reusing `AnswerGrader`/`StateAnswerResolver` for Recall and `DistractorProvider` for MCQ, following the same patterns as Recall Mode and Practice Test), reviews every missed question once with no official pass/fail threshold or early stop (this is a drill, not an exam simulation — results show "N of M correct this time" rather than a pass/fail banner), and records the session as a normal Attempt so mastery updates the same way it would from Recall Mode or Practice Test. Home's "Review Missed" card and empty-state copy were already in place; only the nav-bar destination pointed at `ComingSoonScreen`, which is now replaced with the real route.

**Step 15 notes (2026-09-19):** Settings covers state/representative change, Focus Mode, test version override, practice config, flash card order, reminder time (now confirmed actually scheduled via `WorkManager` — see the 2026-09-27 stale-note correction below), theme, large-fonts, officials `lastUpdated` + Check for Content Updates, and reset progress. Wiring `themeMode` and `largeFonts` into `MainActivity` was previously entirely missing — both settings existed in the data layer with nothing ever reading them; they're now live (theme via `AppThemeMode`, font scale via a `LocalDensity` override that multiplies the *system* font scale rather than replacing it, per the plan's own cross-cutting UI note). Extracted `SelectableList` out of `OnboardingScreen` into `ui/common/` so Settings' state/rep pickers reuse the same component instead of duplicating it.

**Step 11 notes (2026-09-19):** `DistractorProvider` implements the full curated → same-`answerType` → same-category resolution order from the plan, plus one addition not in the original text: a final "any other question" fallback. This is needed only because the bundled bank is still ~10 sample questions per version — same-type/category pools are often too small otherwise. It should become moot once step 12 lands the full 100/128-question banks, but there's no reason to remove it then; it's a harmless last resort. The hard invariant (no distractor collides with the question's own answers/variants or any `overlapsWith` question's answers, checked symmetrically even when only one side declares the overlap) is enforced in code and covered by `DistractorProviderTest`. Practice Test reads the existing `practiceQuestionCount` / `practicePassingPercent` settings for official-vs-custom config rather than adding new plumbing, since Settings (step 15) doesn't have a UI for them yet. "Review these" from Results is deferred, matching Recall Results' existing precedent, until Review Missed (step 13) exists to navigate to.

**Infra / non-content:**
- **Hosting location** for the remote officials JSON (e.g. a GitHub repo or gist under the developer's control) — not yet chosen. The fetch URL is a config constant; the app works fully offline via the bundled copy regardless.
- **Cloud accounts** deliberately deferred. If cross-device sync is wanted later, the honest cost is a backend or Firebase Auth, a privacy policy, a Play Store data-safety update, an account deletion flow, and sync-conflict handling. `UserProfile` is shaped so a `userId` and sync timestamps can be added without restructuring.
- **Package name** `com.usctest.app` should become a domain you control before publishing.
- **Visual theme** is intentionally generic placeholder (see the design doc's own "open items for the later theme pass": color palette, type scale, spacing scale, avatar style, transitions, and the ad-hoc `SuccessGreen` constant used for the mastery ring).
