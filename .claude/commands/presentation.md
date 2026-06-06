---
description: Render a self-contained HTML tab-bar presentation from a markdown PRD, Spec, ADR, or implementation Plan. Render-only — opens directly in a browser.
allowed-tools: Read, Write, Glob, Grep
argument-hint: <path/to/prd-or-spec-or-adr-or-plan.md>
---

# Presentation → HTML tab-bar deck

Turn ONE markdown **PRD / Spec / ADR / Plan** into ONE self-contained `.html`
presentation that opens directly in a browser (no build step). **Render only**
— never add, drop, or reinterpret content.

## How to use

```
/presentation docs/prd/PRD-watch-history.md
/presentation docs/specs/spec-watch-history.md
/presentation docs/adr/0006-hls-video-streaming.md
/presentation docs/plans/plan-bookmark-feature.md
```

Input doc: `$ARGUMENTS`. If empty, ask for the path (defaults live in
`docs/prd/`, `docs/specs/`, `docs/adr/`, `docs/plans/`).

---

## Workflow

### Step 1 — Read source & detect type
Read the `.md` from `$ARGUMENTS`. Detect type from title / `## Metadata` / headings:
- `# PRD:` or `## Problem Statement` + `## Goals & Success Metrics` + `## User Stories` → **PRD**
- `# Feature Specification:` or `## Functional Requirements` → **Spec**
- `# ADR-` / `## Decision Drivers` / `## Considered Options` → **ADR**
- `# Plan:` / `## Implementation Steps` / `### Phase N` → **Plan**

### Step 2 — Copy the template
Copy `.claude/skills/design/presentation/template-tabbar.html` to the output
path. Default output: **next to the source**, same base name, `.html` extension
(e.g. `docs/adr/0006-hls.md` → `docs/adr/0006-hls.html`). Or
`docs/presentations/<name>.html` if the user prefers.

### Step 3 — Fill header
`<header class="hero">`: eyebrow = type + id (e.g. `ADR-0006`), `<h1>` = doc
title, chips = Status / Date / Owner / Version from `## Metadata` when present.
Keep the `.back-link` (`← Presentations`) anchor from the template — it points to
`https://nguyenhuuca.github.io/assessment/presentations/`.

### Step 4 — Map sections to tabs (one `.tab-btn` + matching `.panel` each)
First tab gets `active`, first panel gets `show`.

- **PRD:** `🎯 Overview` · `❓ Problem` · `📈 Goals & Metrics` · `👤 User Stories` ·
  `✅ Requirements` · `🧭 Scope` · `🔗 Dependencies` · `⚠️ Risks` (only those present).
  Goals/metrics → a `<table>`; each user story → a `.card.cyan`; Scope → one card
  with In-Scope (green) vs Out-of-Scope (muted); Risks → `<table>` in `.card.amber`.
- **Spec:** `🎯 Overview` · `📐 Business Rules` · `✅ Requirements (FR-*)` ·
  `🔌 API Changes` · `🗄️ Data / DB` · `🔒 Security` · `🧪 Testing` (only those present).
  Each FR-N → a `.card.cyan` with `.tag` (FR-1) + acceptance bullets; API → a
  `<table>`; DDL → `<pre>` in `.card.violet`.
- **ADR:** `📌 Context` · `🎚️ Drivers` · `⚖️ Options` · `✅ Decision` ·
  `📊 Consequences` · `🔗 Links`. Chosen option = `.card.green` with `Chosen`
  tag; rejected = `.card.red`/`.amber`; deep analysis in `<details>`.
- **Plan:** `🎯 Objective` · `🧭 Scope` · `🏗️ Approach` · `🪜 Phases` ·
  `📁 Files` · `⚠️ Risks`. Each Phase = a `.card`, number as `.tag`, estimate as
  `.time` (e.g. `1.5d`); files → `<table>` (path, action, note).

### Step 5 — Fill panels with cards (consistent colors)
| Meaning | Block |
|---------|-------|
| Goal / overview / primary path | `.card.blue`, `.hl.goal` |
| Chosen / done / deliverable | `.card.green`, `.hl.prod` |
| Requirement / rule / quote | `.card.cyan` / `.prompt` |
| Trade-off / consequence | `.card.amber` |
| Risk / rejected / breaking | `.card.red` |
| Data / API / technical | `.card.violet` |
| Note / example | `.card.yellow`, `.ex` |

Keep tables as `<table>`, code & ASCII diagrams as `<pre>`, long rationale in
`<details>`. **Reuse template classes only — no new CSS.**

### Step 6 — Verify & summarize
Confirm the `.html` exists and every `data-tab` has a matching panel `id`. Tell
the user the output path and navigation: click a tab or press <kbd>←</kbd> /
<kbd>→</kbd>; click 🔎 to expand details. Do **not** open the browser unless asked.

---

## Rules
- **One `.md` in, one `.html` out** — render only, content stays faithful.
- **Reuse the template's classes** in `.claude/skills/design/presentation/template-tabbar.html`; invent no CSS.
- **Compress prose to bullets is OK; new facts are not.**
- Keep wording, decisions, tables, code, diagrams, and estimates verbatim.

$ARGUMENTS
