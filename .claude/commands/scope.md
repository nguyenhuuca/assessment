---
description: Scope a feature idea into a full PRD — asks clarifying questions when the description is unclear, then writes docs/prd/PRD-{slug}.md using the project's official template
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

Read `$ARGUMENTS`. Extract what is already clear:
- Feature name
- Problem being solved (if stated)
- Target users / persona (if stated)
- Success signal (if stated)
- Known constraints (if stated)

### Step 2 — Identify gaps and ask questions

Before writing anything, check which of the following are **missing or ambiguous**. Ask ONLY for what is genuinely unclear — do not re-ask information already in the input.

**Required information:**

| # | Question | Why it matters |
|---|----------|---------------|
| Q1 | What problem does this solve? Who has this problem and why does it matter? | Problem Statement + Evidence sections |
| Q2 | Who are the primary users / personas? | User Stories section |
| Q3 | How will we measure success? (metrics and targets) | Goals & Success Metrics section |
| Q4 | What is explicitly IN scope for v1? | Scope section |
| Q5 | What is explicitly OUT of scope (defer to v2+)? | Scope section |
| Q6 | Any known technical constraints, dependencies, or risks? | Dependencies + Risks sections |

**Rules:**
- Ask all unclear questions in **a single message** — never one at a time
- If the input is detailed enough to make reasonable assumptions, state assumptions explicitly and ask the user to confirm before writing
- If the user says "just write it" or "use your best judgment", proceed with clearly labeled assumptions

### Step 3 — Read existing codebase context

Use Glob and Grep to find relevant existing components, entities, services, or APIs related to the feature. This informs the Requirements, Dependencies, and Next Steps sections.

Also read the official PRD template:
```
templates/artifacts/prd.template.md
```

### Step 4 — Write the PRD

Once all gaps are resolved, create `docs/prd/PRD-{kebab-slug}.md` following the **official template** from `templates/artifacts/prd.template.md`.

Fill every section — do not leave placeholder text. Where information is assumed rather than stated by the user, mark it with `> **Assumption:** ...` so it is easy to review.

**Project-specific conventions to follow:**
- Use `VideoSource` (not `YouTubeVideo`) as the active video entity
- Use `ResultObjectInfo<T>` / `ResultListInfo<T>` for API response wrappers
- Reference `docs/adr/` (not `doc/adr/`) for ADR links
- Place new DB migrations in `api/src/main/resources/db/changelog/sql/`

### Step 5 — Offer next steps

After writing the PRD, offer:
1. Create **ADR** (`docs/adr/00NN-{slug}.md`) — architecture decisions behind the feature
2. Create **Implementation Plan** (`docs/plans/plan-{slug}.md`) — phased task breakdown
3. Add entries to **`mkdocs.yml`** nav

Do not auto-create these — wait for the user to confirm.

---

## Rules

- **Never skip the clarification step** if any of Q1–Q4 are unanswered
- **One question round only** — gather everything in a single message
- **Assumptions must be labelled** — mark with `> **Assumption:**` inline
- **Use the official template** from `templates/artifacts/prd.template.md` — do not invent a different structure
- **Fill every section** — use `N/A` or `TBD` only when genuinely unknown, not as a shortcut

$ARGUMENTS
