---
name: presentation
description: >-
  Render a self-contained HTML "tab-bar" presentation from a markdown
  PRD, Spec, ADR, or implementation Plan. Use when the user asks to "make a
  presentation", "render slides", "turn this prd/spec/adr/plan into a deck",
  "present this doc", "export to html", or "show this on screen". INPUT is
  ONE markdown file (a PRD, Spec, ADR, or Plan). OUTPUT is ONE .html slideshow
  that opens directly in a browser. Render only — do not invent content.
allowed-tools: Read, Write, Glob, Grep
---

# Presentation — HTML tab-bar deck from PRD / Spec / ADR / Plan

You receive ONE markdown file — a **PRD** (Product Requirements Document), a
**Feature Specification**, an **ADR** (Architecture Decision Record), or an
implementation **Plan** — and render it into a single self-contained **`.html`**
presentation that opens directly in a browser (no build step, no VS Code
extension).

## 0. Golden rules

- **Input is exactly one `.md` file.** If none is given, ask for the path. Good
  defaults: `docs/prd/PRD-*.md`, `docs/specs/spec-*.md`, `docs/adr/0xxx-*.md`,
  `docs/plans/plan-*.md`.
- **Output is exactly one `.html` file.** Render only — never add, drop, or
  reinterpret content. Keep wording, decisions, tables, code blocks, diagrams,
  and estimates faithful. You may compress prose into bullets, but no new facts.
- **Reuse the template's classes only.** Build from `template-tabbar.html` in
  this skill folder. Do not invent new CSS.
- **Do not open the browser / run `Start-Process`** unless the user asks.

## 1. Workflow

1. **Read the source `.md`** and detect its type from the title / `## Metadata`
   / heading shape:
   - `# PRD:` or `## Problem Statement` + `## Goals & Success Metrics` + `## User Stories` → **PRD**
   - `# Feature Specification:` or `## Functional Requirements` → **Spec**
   - `# ADR-` / `## Decision Drivers` / `## Considered Options` → **ADR**
   - `# Plan:` / `## Implementation Steps` / `### Phase N` → **Plan**
   - If ambiguous, pick the closest mapping below; don't ask unless truly unclear.
2. **Copy `template-tabbar.html`** to the output path (see §3).
3. **Fill the `<header class="hero">`**: eyebrow = type + id (e.g. `ADR-0006`),
   `<h1>` = document title, chips = Status / Date / Owner / Version pulled from
   `## Metadata` when present. Keep the `.back-link` (`← Presentations`) anchor
   from the template — it links to
   `https://nguyenhuuca.github.io/assessment/presentations/`.
4. **Map sections to tabs** — one `.tab-btn` + matching `.panel` per major `##`
   section, using the type-specific maps below. First tab gets `active` / first
   panel gets `show`.
5. **Fill panels with cards.** One `.card` per logical unit. Use accent colors
   to encode meaning (see §2). Put long rationale, alternatives, and code inside
   `<details>`. Keep tables as `<table>`, ASCII diagrams and code as `<pre>`.
6. **Verify** the `.html` exists and every `data-tab` has a matching panel `id`.
7. **Summarize** for the user: output location + navigation (← / → to switch
   sections, click 🔎 to expand details).

## 2. Color & block conventions (consistent across all types)

| Meaning | Block |
|---------|-------|
| Goal / overview / primary path | `.card.blue`, `.hl.goal` |
| Chosen / done / success / deliverable | `.card.green`, `.hl.prod` |
| Requirement / rule / question / quote | `.card.cyan` or `.prompt` |
| Caution / trade-off / consequence | `.card.amber` |
| Risk / rejected option / breaking change | `.card.red` |
| Data model / API / technical detail | `.card.violet` |
| Note / example | `.card.yellow`, `.ex` |
| Estimate / duration | `.time` on the title (e.g. `1.5d`) |
| Tag (FR-1, Phase 3, Decision) | `.tag` |

## 3. Output location

Default: write `<doc-name>.html` **next to the source `.md`**, same base name,
only the extension changes (e.g. `docs/adr/0006-hls.md` → `docs/adr/0006-hls.html`).
If the user prefers a dedicated folder, use `docs/presentations/<doc-name>.html`.

## 4. Type-specific tab maps

### 🎯 PRD → tabs
`🎯 Overview` · `❓ Problem` · `📈 Goals & Metrics` · `👤 User Stories` ·
`✅ Requirements` · `🧭 Scope` · `🔗 Dependencies` · `⚠️ Risks` (include only
sections present; fold Open Questions / Appendix / Approval / Version History
into the nearest panel or a final `📌 Notes` tab).
- **Overview / Problem Statement** → `.card.blue` + `.hl.goal`.
- **Goals & Success Metrics** → metrics in a `<table>`; goals as green bullets.
- **User Stories** → one `.card.cyan` per story (or a list), role/need/value.
- **Requirements** → `.card.cyan` cards; tag must-have vs nice-to-have with `.tag`.
- **Scope** → In-Scope (green bullets) vs Out-of-Scope (muted) in one card.
- **Risks & Mitigations** → `<table>` (risk, impact, mitigation) in `.card.amber`.

### 📐 Spec → tabs
`🎯 Overview` · `📐 Business Rules` · `✅ Requirements (FR-*)` · `🔌 API Changes`
· `🗄️ Data / DB` · `🔒 Security` · `🧪 Testing` (include only sections present).
- Each **FR-N** → one `.card.cyan` with a `.tag` (`FR-1`) and its acceptance
  criteria as a bullet list. Group several FRs per panel if short.
- Each **Rule** → a row in a card or a `.prompt`.
- **API endpoints** → a `<table>` (method, path, auth, purpose).
- **DB changes** → `<pre>` for the DDL / table sketch in a `.card.violet`.

### ⚖️ ADR → tabs
`📌 Context` · `🎚️ Decision Drivers` · `⚖️ Options` · `✅ Decision` · `📊 Consequences`
· `🔗 Links` (and `📜 Changelog` only if useful).
- **Considered Options** → one `.card` per option; the **chosen** one is
  `.card.green` with a `Chosen` `.tag`, rejected ones `.card.red`/`.amber`.
  Pros/cons as sub-lists; put deep analysis in `<details>`.
- **Decision Outcome / Quantified Impact** → `.card.green` + `.hl.prod`, metrics
  in a `<table>`.
- **Consequences** → split positive (`.card.green`) vs negative (`.card.amber`).

### 🪜 Plan → tabs
`🎯 Objective` · `🧭 Scope` · `🏗️ Technical Approach` · `🪜 Phases` · `📁 Files`
· `⚠️ Risks / Dependencies` (include only what exists).
- **Scope** → one card with In-Scope (green bullets) and Out-of-Scope (muted).
- **Implementation Steps / Phase N** → each phase a `.card`, phase number as
  `.tag`, the time estimate as `.time` (e.g. `1.5d`). Long phase detail/code in
  `<details>`. If there are many phases, one panel listing all phases as cards.
- **Files to Create / Modify** → a `<table>` (path, action, note).

## 5. Handoff

After rendering, leave `<doc-name>.html` at the output path. Tell the user:
- **Open:** double-click the `.html` (renders in any browser, no server).
- **Navigate:** click a tab, or press <kbd>←</kbd> / <kbd>→</kbd>; click 🔎 to
  expand details.
- **Update:** when the source `.md` changes, re-run this skill to regenerate.

## 6. Related skills

Pipeline: `scope` (PRD) → `spec` (Spec) / `architect` (ADR) / `swarm-plan`
(Plan) → **`presentation`** (export `.html` deck). Any stage's artifact — PRD,
Spec, ADR, or Plan — can be fed straight into this skill.
