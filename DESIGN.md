# US Citizenship Test — Visual Direction

Goal: feel like a native iOS app. Apple-adjacent, calm, focused study tool — not a gamified quiz app.

This is the design system authored in Stitch (project "US Citizenship Test", design system
"US Citizenship Test — iOS Native") that every generated mockup was built against. Kept here
as a standalone reference alongside `us-citizenship-test-design-doc.md`.

**Design tokens:** Inter (headline/body/label), accent `#007AFF` (iOS system blue), light mode,
neutral color variant, `ROUND_TWELVE` roundness (~12–16px corners).

## Typography
- SF-style rhythm using Inter as the stand-in for SF Pro: large, confident headline weight (700/600) paired with calmer regular-weight body text (400).
- Generous line-height on body copy for readability during study sessions.
- Headlines are the primary visual anchor on every screen — bigger and bolder than the supporting UI around them, never competing decoration.
- Role naming, for consistent reuse across screens:
  - **Screen titles / large headers** — bold/semibold, tight tracking.
  - **Section headers** — uppercase secondary text (e.g. `text-xs font-semibold uppercase tracking-wider text-slate-400`).
  - **Body & options** — regular weight, high legibility (e.g. `text-base text-slate-900`).
  - **Captions / subtext** — muted gray (e.g. `text-sm text-slate-500`).

## Color
- Mostly neutral: off-white/near-white background, neutral gray surfaces, dark neutral text. No tinted card backgrounds, no card-per-color rainbow.
- Single accent color (iOS system blue, #007AFF) used sparingly: primary buttons, active tab icon, progress rings/bars, links, selection states. Never use the accent for large background fills.
- Status colors (green for correct, red for incorrect) appear only in feedback moments (Practice Test, Recall Mode, Review Missed) — never as decorative UI chrome elsewhere.
- Exact swatches (also the values used in `Color.kt`):
  - Primary / tint — `#007AFF`
  - Success / passed — `#34C759`
  - Destructive / error / missed — `#FF3B30`
  - Background (primary) — `#F2F2F7` grouped-view style, or pure `#FFFFFF` depending on view hierarchy
  - Card & container surfaces — `#FFFFFF` with a subtle 1px border or minimal soft elevation
  - Primary text — `#000000` / `#1C1C1E`
  - Secondary text — `#8E8E93` / `#636366`
  - Separators / borders — `#E5E5EA`

## Elevation & borders
- Avoid heavy outlined card borders. Prefer soft, subtle elevation (very light shadow or a barely-there 1px hairline in a neutral tone) to separate content groups.
- Prefer whitespace and section spacing over dividers. Where a divider is unavoidable (e.g. Settings rows), use a hairline at low opacity, full-bleed, not boxed.

## Shape
- Rounded corners throughout (roughly 12–16px) on buttons, cards, and sheets — iOS-style, never sharp rectangles, never fully pill-shaped except small chips/tags.

## Iconography
- Icons are earned, not decorative. Use them only for: bottom tab bar (Home/Progress/Settings), key primary actions (mic button, X to quit, back chevron), and status indicators (correct/incorrect check marks, confidence ring).
- Do not add a leading icon to every settings row or list item — plain text rows with trailing chevrons are the default.
- Icon style: simple regular-weight line icons (Phosphor Regular in the real app) — consistent stroke weight throughout, never mixed filled/outline styles on the same screen except to indicate active/selected state.

## Navigation patterns
- Bottom tab bar (Home, Progress, Settings) with icon + label, accent color on the active tab only.
- Standard iOS navigation bar (44pt height) with a leading back chevron or leading X (for modal/focused-task flows: Recall Mode, Practice Test, Flash Cards, Review Missed).
- Trailing chevrons on all navigable settings rows.
- Swipeable horizontal pagers for Flash Cards and Walkthrough, with dot page indicators.
- Sheet-style pickers for date/state/representative selection rather than full-screen forms where possible.

## Layout rhythm
- Generous outer margins (~16-24px) and consistent vertical rhythm between sections (24-32px between major blocks).
- Large tap targets, comfortable line length, plenty of breathing room — never dense or cramped.

## Tone & interactivity
- Focused, dignified, and calm — avoid flashy gamification badges, confetti, or streak gimmicks.
- Clean progress rings, clear feedback badges, and high-contrast readable question cards do the work instead.

## What to avoid
- Card-per-color rainbow layouts.
- Icon clutter (an icon next to every row/label).
- Heavy borders/outlines as the primary way of grouping content.
- Loud, saturated accent usage — the accent should read as a quiet signal (this is tappable / this is progress), not a design flourish.
- Gamification chrome (streak counters, confetti, badges) — Home's streak-flame indicator was removed for this reason.
