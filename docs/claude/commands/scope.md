---
description: Scope a feature idea into a full PRD — asks clarifying questions when the description is unclear, then writes docs/prd/PRD-{slug}.md
allowed-tools: Read, Write, Glob, Grep
argument-hint: [feature description]
---

# Scope → PRD

Turn a rough feature idea into a structured PRD. Ask clarifying questions before writing — never produce a PRD from an ambiguous description.

## How to use

```
/scope <feature description>
```

Example: `/scope add a trending videos section to the homepage`

---

## Workflow

### Step 1 — Parse the input

Read `$ARGUMENTS`. Extract:
- **Feature name** (1–5 words)
- **Problem being solved** (if stated)
- **Target users** (if stated)
- **Success signal** (if stated)

### Step 2 — Identify gaps and ask questions

Before writing anything, check which of the following are **missing or ambiguous**. Ask ONLY the questions that are actually unclear — do not ask for information already provided.

**Required to write a PRD:**

| # | Question | Why it matters |
|---|----------|---------------|
| Q1 | What problem does this solve? Who feels the pain? | Defines the "why" |
| Q2 | Who are the primary users of this feature? | Defines scope and UX requirements |
| Q3 | What does "done" look like? How will we measure success? | Defines acceptance + metrics |
| Q4 | What is explicitly IN scope for v1? | Prevents scope creep |
| Q5 | What is explicitly OUT of scope (defer to v2+)? | Sets boundaries |
| Q6 | Any known technical constraints or dependencies? | Surfaces blockers early |

**Ask all unclear questions in a single message.** Wait for answers before proceeding.

If the input is detailed enough to answer most questions with reasonable assumptions, state your assumptions clearly and ask the user to confirm before writing.

### Step 3 — Read existing codebase context

Use Glob and Grep to find relevant existing components, entities, services, or APIs related to the feature. This informs the "Background & Current State" and "Component Design" sections.

### Step 4 — Write the PRD

Once all gaps are resolved, create `docs/prd/PRD-{kebab-slug}.md` using the template below.

Also copy the file to `docs/claude/commands/scope.md` (skip — this is the command itself).

After writing, tell the user:
- The file path created
- Whether to also create an ADR and/or implementation plan (offer, don't auto-create)
- Whether to add it to `mkdocs.yml` nav

---

## PRD Template

```markdown
# PRD: {Title}

**Status:** Draft
**Author:** {from git config or "nguyenhuuca"}
**Date:** {today YYYY-MM-DD}
**Version:** 1.0

---

## 1. Overview

### Problem Statement

{What problem exists? Who feels the pain? What happens today without this feature?}

### Goal

{One paragraph: what will this feature do and why does it matter?}

### Success Metrics

| Metric | Baseline | Target |
|--------|----------|--------|
| {metric} | {current} | {goal} |

---

## 2. Background & Current State

### Existing Infrastructure (reuse)

| Component | Status | Notes |
|-----------|--------|-------|
| {component} | ✅ Active / ⚠️ Partial / ❌ Missing | {notes} |

### Gap Analysis

1. {Gap 1}
2. {Gap 2}

---

## 3. Scope

### In Scope (v1)

- [ ] {requirement 1}
- [ ] {requirement 2}

### Out of Scope (v2+)

- {deferred item 1}
- {deferred item 2}

---

## 4. Functional Requirements

### FR-1: {Name}

**When:** {trigger}
**Then:** {expected behavior}

**Acceptance Criteria:**
- {criterion 1}
- {criterion 2}

---

## 5. Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| {NFR} | {target value} |

---

## 6. Data Model Changes

{SQL migration snippets or "No data model changes required"}

---

## 7. Component Design

### New Components

```
{directory tree of new files}
```

### Modified Components

| File | Change |
|------|--------|
| {file} | {change} |

---

## 8. API Contract

{Request/response for each new or modified endpoint, or "No API changes"}

---

## 9. Implementation Plan

### Phase 1 — {Name} ({estimate})
- [ ] {task}

### Phase 2 — {Name} ({estimate})
- [ ] {task}

**Total estimate: {N} days**

---

## 10. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| {risk} | Low/Medium/High | Low/Medium/High | {mitigation} |

---

## 11. Open Questions

| # | Question | Owner | Due |
|---|----------|-------|-----|
| 1 | {question} | {owner} | {date} |

---

## 12. Related Documents

- {link to ADR, plan, or relevant code}
```

---

## Rules

- **Never skip the clarification step** if any of Q1–Q4 are unanswered
- **Assumptions must be stated explicitly** before proceeding — never silently assume
- **One question round only** — gather everything in a single message, not one question at a time
- Use the project's existing response wrappers (`ResultListInfo<T>`, `ResultObjectInfo<T>`) when writing API contracts
- Use `VideoSource` (not `YouTubeVideo`) as the active video entity
- Reference existing components found via Grep/Glob rather than inventing new ones

$ARGUMENTS
