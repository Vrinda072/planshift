---
name: planshift
description: Warm sunset colour blocks that say whether an index helped, in big numbers and plain words.
colors:
  plum: "#3a1230"
  coral: "#ff7a59"
  coral-hover: "#ff8f73"
  amber: "#ffbe3d"
  amber-soft: "#ffe9b8"
  peach: "#ffdcc8"
  blush: "#fff1ea"
  white: "#ffffff"
  soft: "#6e4a63"
  rust: "#a8351b"
  line: "#f4ded3"
  line-strong: "#8d6a80"
typography:
  display:
    fontFamily: "Hanken Grotesk, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "clamp(56px, 6.8vw, 96px)"
    fontWeight: 800
    lineHeight: 1
    letterSpacing: "-0.035em"
    fontFeature: "tnum"
  block-figure:
    fontFamily: "Hanken Grotesk, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "clamp(48px, 5.6vw, 80px)"
    fontWeight: 800
    lineHeight: 1
    letterSpacing: "-0.035em"
    fontFeature: "tnum"
  card-figure:
    fontFamily: "Hanken Grotesk, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "42px"
    fontWeight: 800
    lineHeight: 1.15
    letterSpacing: "-0.03em"
  headline:
    fontFamily: "Hanken Grotesk, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "36px"
    fontWeight: 800
    lineHeight: 1.15
    letterSpacing: "-0.025em"
  title:
    fontFamily: "Hanken Grotesk, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "20px"
    fontWeight: 800
    letterSpacing: "-0.01em"
  body:
    fontFamily: "Hanken Grotesk, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "15px"
    fontWeight: 400
    lineHeight: 1.5
    fontFeature: "tnum"
  label:
    fontFamily: "Hanken Grotesk, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "13px"
    fontWeight: 600
  sql:
    fontFamily: "ui-monospace, SF Mono, Menlo, Consolas, monospace"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: 1.65
rounded:
  sm: "12px"
  md: "18px"
  lg: "28px"
  pill: "999px"
spacing:
  xs: "8px"
  sm: "12px"
  gap: "18px"
  md: "24px"
  gutter: "40px"
  gutter-phone: "16px"
components:
  button-primary:
    backgroundColor: "{colors.coral}"
    textColor: "{colors.plum}"
    rounded: "{rounded.pill}"
    padding: "0 20px"
    height: "42px"
  button-primary-hover:
    backgroundColor: "{colors.coral-hover}"
  button-secondary:
    backgroundColor: "{colors.white}"
    textColor: "{colors.plum}"
    rounded: "{rounded.pill}"
    padding: "0 20px"
    height: "42px"
  button-secondary-hover:
    backgroundColor: "{colors.peach}"
  nav-link-active:
    backgroundColor: "{colors.plum}"
    textColor: "{colors.white}"
    rounded: "{rounded.pill}"
    padding: "8px 15px"
  block-before:
    backgroundColor: "{colors.peach}"
    textColor: "{colors.plum}"
    rounded: "{rounded.lg}"
    padding: "26px 32px"
  block-after:
    backgroundColor: "{colors.amber}"
    textColor: "{colors.plum}"
    rounded: "{rounded.lg}"
    padding: "26px 32px"
  block-verdict-faster:
    backgroundColor: "{colors.coral}"
    textColor: "{colors.plum}"
    rounded: "{rounded.lg}"
    padding: "26px 32px"
  block-verdict-slower:
    backgroundColor: "{colors.plum}"
    textColor: "{colors.white}"
    rounded: "{rounded.lg}"
    padding: "26px 32px"
  block-verdict-neutral:
    backgroundColor: "{colors.blush}"
    textColor: "{colors.plum}"
    rounded: "{rounded.lg}"
    padding: "26px 32px"
  card:
    backgroundColor: "{colors.blush}"
    textColor: "{colors.plum}"
    rounded: "{rounded.lg}"
    padding: "24px 30px"
  panel:
    backgroundColor: "{colors.white}"
    textColor: "{colors.plum}"
    rounded: "{rounded.lg}"
  input:
    backgroundColor: "{colors.white}"
    textColor: "{colors.plum}"
    rounded: "{rounded.sm}"
    padding: "0 14px"
    height: "40px"
  badge-faster:
    backgroundColor: "{colors.coral}"
    textColor: "{colors.plum}"
    rounded: "{rounded.pill}"
    padding: "2px 12px"
  badge-slower:
    backgroundColor: "{colors.plum}"
    textColor: "{colors.white}"
    rounded: "{rounded.pill}"
    padding: "2px 12px"
  chip-selected:
    backgroundColor: "{colors.plum}"
    textColor: "{colors.white}"
    rounded: "{rounded.pill}"
    padding: "0 14px"
    height: "34px"
  sql-copyable:
    backgroundColor: "{colors.plum}"
    textColor: "{colors.white}"
    typography: "{typography.sql}"
    rounded: "{rounded.sm}"
    padding: "14px 18px"
---

# Design System: planshift

## Overview

**Creative North Star: "Colour Blocks"**

The answer arrives as three large flat blocks that can be read in seconds: before, after, and the change. The palette is a warm sunset on a white ground with plum as the ink. Corners are large, controls are pill-shaped, and one typeface carries everything that isn't SQL. Nothing decorative stands between a developer and the numbers.

It is fun through colour and scale, and calm through restraint: one theme (light) on every screen, no gradients, no charts, flat fills. Colour has jobs. It says which side of the experiment a number belongs to, and which verdict the result is allowed to claim. A word always says the same thing.

**Key Characteristics:**
- Big tabular numbers in flat colour blocks; no charts or diagrams on results.
- Peach is before, amber is after, coral is the primary action and a faster verdict, plum fill is slower.
- Colour follows the verdict, and every verdict is also written out.
- Large radii and pill controls; depth only on things that float.
- Hanken Grotesk throughout; monospace only for SQL.

## Colors

A warm sunset on white: one dark ink, three saturated warm fills, and pale tints for support.

### Primary
- **Coral** (`coral`): the primary action (New experiment, Run again, Run experiment, Copy SQL) and a faster verdict (the third block and the "Faster" badge). Also marks work in progress: the running status dot and the active progress step. **Coral Hover** (`coral-hover`) is its only hover shade.
- **Plum** (`plum`): all primary text, focus outlines, secondary button borders, and selected states (current nav pill, selected chip, active segment). As a fill with white text it means slower. It is also the ground for SQL meant to be copied and for tooltips.

### Secondary
- **Peach** (`peach`): "before". In a result it fills the Before block. Elsewhere it is only an interaction or utility tint: hover on secondary buttons, icon buttons and segments, the highlighted search item, step numbers, the info dot and loading bars.
- **Amber** (`amber`): "after". In a result it fills the After block. Elsewhere it appears only as a small accent: the regression count, index and column-order markers inside chips, text selection, the healthy API dot, the "Copied" state, and half of the logo.

### Neutral
- **White** (`white`): the page ground, panels, inputs and secondary buttons.
- **Blush** (`blush`): supporting surfaces with no before or after meaning: explanation cards, workload count blocks, neutral verdicts, table header rows, row hover, plan steps, light code.
- **Soft Plum** (`soft`): secondary text and labels, on white, blush, peach or amber-soft.
- **Rust** (`rust`): text for results that count as slower, and for errors. As a mark it also appears on failed and unreachable dots and the error banner outline.
- **Pale Line** (`line`): panel borders and row dividers.
- **Strong Line** (`line-strong`): input and chip borders, the "No real change" badge ring, inactive dots.
- **Soft Amber** (`amber-soft`): warnings and notices.

### Named Rules
**The Before And After Rule.** Peach means before and amber means after. No other colour takes either role, and neither one fills a data block that isn't that side. Counts across a workload are neither, so their blocks are blush.

**The Verdict Colour Rule.** Colour follows the verdict, never the sign of a number. Faster is coral (in tables, plum figures beside a coral badge). Slower is a plum fill with white text; rust text is only for results that count as slower, and for errors. A change that doesn't count is soft plum in result and candidate tables, whichever way its arrow points.

**The Say It Too Rule.** Every verdict is also a word: "Faster", "Slower", "No real change" or "No real difference", "Too few runs to tell", "Not checked". Change figures carry an arrow.

**The No Claim Rule.** With too few runs to check against variation, no direction is claimed, even if the server marked one. The result reads "Too few runs to tell", takes the neutral treatment, and stays out of regression lists and counts. Results recorded before individual runs were saved follow the same rule and read "Not checked".

**The Median Takes No Side Rule.** A median across several queries is never timing-checked, so it never takes a verdict colour. Its block is blush with a plum ring, and median figures in lists and comparisons stay plum.

## Typography

**Display Font:** Hanken Grotesk, weights 400 to 800 (with system-ui, -apple-system, Segoe UI)
**Body Font:** Hanken Grotesk
**Label/Mono Font:** ui-monospace (with SF Mono, Menlo, Consolas), for SQL only

**Character:** One sturdy grotesque doing every job. Weight 800 with tight tracking gives headings and figures their voice; everything else stays plain. Figures are tabular across the whole app.

### Hierarchy
- **Display** (800, `clamp(56px, 6.8vw, 96px)`, 1): the change in the verdict block.
- **Block Figure** (800, `clamp(48px, 5.6vw, 80px)`, 1): before and after times and workload counts. The unit or "of N" sits beside it at 0.38em.
- **Card Figure** (800, 42px, 1.15): the single figure in a card, such as index size. Compare figures run at 44px and the estimate at 40px.
- **Headline** (800, 36px, 1.15; 29px at 680px and below): the page title, which on a result states the finding.
- **Title** (800, 20px): block and card headings. Panel and step titles are 19px.
- **Body** (400, 15px, 1.5): running text. Explanatory notes are capped around 70 to 75ch, block text at 48ch.
- **Label** (600, 13px, sentence case): field labels and table headers, in soft plum.
- **SQL** (400, 13px, 1.65, mono): queries and statements, wrapping rather than scrolling.

### Named Rules
**The One Typeface Rule.** Identifiers, table and column names, timings and plan node names stay in Hanken Grotesk, set apart by weight 600. Monospace means SQL and nothing else.

## Layout

A single centred column up to 1320px with a 40px side gutter (16px on phones) under a top navigation: wordmark, pill links, then API status, search, source link and the primary action. There is no sidebar.

A page header puts the title and one line of context on the left and actions on the right; actions move below when they don't fit. Blocks, cards, panels and grid columns are separated by 18px.

A result reads top to bottom: headline, the measured SQL for a one-query run, three blocks (columns 1 : 1 : 1.35, at least 264px tall), a quiet trust note, then why, cost and keep as three cards (1.25 : 1 : 1.15). Runs and full plans open on request. A workload run replaces the cards with a per-query table and one index card capped at 640px. Run settings sit in a quiet meta row at the end.

The query builder is a main column with a sticky 380px side column. Overview uses a 2 : 1 grid.

### Responsive
- **1180px and below:** nav links take their own row and may scroll; the API status label is visually hidden.
- **1040px and below:** side columns stack. Blocks become two columns with the verdict moved first and spanning both; cards become two columns with the first spanning both.
- **680px and below:** everything is one column, nav links wrap so every destination stays on screen, and stacking tables turn each row into a small list. Each cell shows its column name, so a verdict is never scrolled out of view. Header rows stay available to screen readers; sortable tables keep their sort headers as a visible row of pills.
- **430px and below:** the status dot and source link leave the nav.

## Elevation & Depth

The system is flat. Depth comes from fill: white ground, blush and saturated blocks on it, white pills inset within blush. Panels are outlined with a 2px pale line. Rings drawn inside a surface (`box-shadow: inset`) are borders, used for neutral verdicts, changed plan steps, errors and outline badges.

### Shadow Vocabulary
- **Pop** (`box-shadow: 0 2px 6px rgba(58, 18, 48, 0.08), 0 18px 40px -12px rgba(58, 18, 48, 0.28)`): only for things floating over the page, the search palette and tooltips.

### Named Rules
**The Flat Fill Rule.** Blocks, cards, panels and buttons cast no shadow and take no gradient, at rest or on hover.

## Shapes

Large, soft and consistent. Blocks, cards, panels and the search palette use 28px. Banners, segmented tracks, the estimate box and compare tiles use 18px. Inputs, code, plan steps and tooltips use 12px. Buttons, nav links, badges, chips and tags are full pills; icon buttons and state dots are circles. Failed and unreachable dots turn into near-squares, so the state differs in shape too. Button and secondary-surface borders are 2px; inputs and table rows use 1.5px. The logo is two overlapping circles, a coral one over a smaller amber one: before and after.

## Components

Chunky, flat and plainly labelled.

### Result Blocks (signature)
Three equal-height flat blocks, each a title at the top, one huge figure, and a sentence saying what the figure is.
- **Before and After:** peach and amber, plum text. The sentence states the run count, which side, and which clock produced the timing.
- **Verdict, faster:** coral. The title is the arrow and the word; the sentence leads in bold with what the number is, then why it can be trusted.
- **Verdict, slower:** plum with white text, so it can't pass for a win at a glance.
- **Verdict, neutral** (no real difference, too few runs, A/A check, workload median): blush with a 2px inset plum ring.
- **Workload:** the first two blocks become blush "Faster" and "Slower" counts.
- **Motion:** the one entrance. Blocks rise 14px over 520ms, 80ms apart, fully visible throughout. The change figure counts when its value updates (650ms). Reduced motion removes both.

### Buttons
- **Shape:** pill, 42px tall (34px small), 2px border, weight 700.
- **Primary:** coral fill and border with plum text; hover uses coral hover.
- **Secondary:** white with a plum border; hover fills peach.
- **States:** press scales to 0.97; disabled is 50% opacity; focus is a 3px plum outline offset 2px.
- **Link button:** underlined 13.5px bold text, for per-row disclosure.
- **Copy SQL:** a borderless coral pill under the statement; turns amber and reads "Copied".

### Chips, Badges and Status
- **Choice chips:** pill with a strong-line border; selected fills plum with white text. Amber markers show an existing index or a column's position.
- **Verdict badges:** coral (Faster), plum (Slower), blush with a strong-line ring (No real change), white with a plum ring (Too few runs to tell).
- **Run status:** a dot plus a word. Hollow for queued, plum for completed, pulsing coral for running, rust square for failed.

### Cards / Containers
- **Cards:** blush, 28px radius, 24px by 30px padding (22px by 24px on phones). Plan node names sit in small white pills after the plain-words sentence.
- **Panels:** white with a 2px pale-line border and 28px radius, for tables, lists and forms. The head is at least 62px with a 19px title and a soft note.

### Tables
Blush header row, 1.5px pale dividers, 56px minimum rows, numbers right-aligned. Times are bold with their fastest-to-slowest range in soft text below; the verdict badge sits above one line of evidence. On phones, tables opt into the stacked layout.

### Code
- **Copyable SQL** (the statement to keep, and the builder's generated SQL): plum ground, white mono, with Copy SQL below.
- **Reference SQL** (the measured query, the workload list): blush ground, plum mono.

### Inputs / Fields
White, 1.5px strong-line border, 12px radius, 40px tall, with a label above. Hover turns the border plum; focus adds a 3px plum outline offset 1px. Selects use a plum chevron. Errors appear in a blush banner with an inset rust ring and rust text.

### Navigation
Pill links at weight 600; hover is blush, current is plum with white text. An amber count follows Regressions. A plum-tinted scrim (40%) sits behind the search palette, whose active item is peach.

### Disclosure
Runs and plans expand by animating height over 420ms. Every timed run is listed as a number in a blush pill. Plans are two columns of blush steps; changed steps are white with a plum ring.

## Do's and Don'ts

### Do:
- **Do** keep peach for before and amber for after wherever the two sides of a result are shown as colour fills.
- **Do** derive colour from the verdict and print the verdict as a word beside it.
- **Do** give "no real difference", too few runs, A/A checks and workload medians the neutral treatment.
- **Do** show how far to trust a result next to the result.
- **Do** use plum text on coral, amber and peach, and white text on plum.
- **Do** stack tables on phones with each value labelled, so no verdict is off screen.
- **Do** meet WCAG 2.1 AA, with a visible plum focus outline.

### Don't:
- **Don't** put charts, sparklines or timing diagrams on results; show the numbers.
- **Don't** colour a figure because it went up or down.
- **Don't** claim a direction from too few runs.
- **Don't** use rust text for anything but results that count as slower, and errors.
- **Don't** set anything but SQL in monospace.
- **Don't** put soft plum text on coral or amber; it falls below 4.5:1.
- **Don't** add gradients, or shadows to surfaces that don't float.
- **Don't** break the answer into a dashboard of small stat cards.
