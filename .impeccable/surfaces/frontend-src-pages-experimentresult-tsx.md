---
version: 1
slug: "frontend-src-pages-experimentresult-tsx"
primary_target: "frontend/src/pages/ExperimentResult.tsx"
related_targets: []
---

# Result page (leads the app-wide redesign)

Mode: Operate. Audience: a developer checking whether an index helps. Job: get the answer, how far to trust it,
what changed in the plan, what it costs, and the SQL. Build path: code-led.

Chosen: mockup D "Color Blocks" (.impeccable/mocks/round2/D.html), with a more professional font. The user rejected
the first round (fonts, colours, diagrams), then chose: like Figma's website, warm sunset colours, big numbers only,
no charts. One theme on every screen. Not childish, not busy, not a generic AI look. WCAG 2.1 AA.

## Direction contract

THESIS: The answer in three large colour blocks, read in seconds; no charts or diagrams stand between the developer
and the numbers. Refuses the dashboard of small stat cards and graphs.

OWN-WORLD: White ground; plum ink; peach (before), amber (after), coral (the verdict and primary action), blush
(supporting cards). Flat fills, 24-28px rounded blocks, pill navigation and buttons. Hanken Grotesk throughout,
monospace only for SQL.

STORY: Faster, slower or no real difference; how sure; why, in plain words; what it costs; the SQL to keep it.

FIRST VIEWPORT: Pill navigation across the top; a headline stating the finding with actions at its right; Before,
After and the change as three large blocks; below them why, cost, and keep. Full plans open on request.

FORM: Colour blocks.
seed: none (pinned) "go with D except the font should be a bit more professional"

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
