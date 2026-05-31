---
description: Scope a feature idea into a full PRD — asks clarifying questions when the description is unclear, then writes docs/prd/PRD-{slug}.md using the project's official template
allowed-tools: Read, Write, Glob, Grep, AskUserQuestion
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

### Step 2 — Identify gaps and ask questions one at a time

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
- Ask questions **one at a time** — use `AskUserQuestion` for each, wait for the answer, then ask the next
- Work through Q1 → Q6 in order, skipping any already answered in the input
- For each question, provide **3–4 contextually relevant options** derived from the feature description. Mark your best guess with `(Recommended)` at the end of its label. The tool automatically appends an "Other" option so the user can type a free-form answer.
- After each answer, briefly acknowledge it (one sentence) before asking the next question
- Once all gaps are filled, summarize what you understood and confirm before writing
- If the user says "just write it" or "use your best judgment", proceed with clearly labeled assumptions

**Example — asking Q1 for a "trending videos" feature:**
```
AskUserQuestion({
  questions: [{
    question: "What problem does this feature solve, and who has it?",
    header: "Problem",
    multiSelect: false,
    options: [
      { label: "Users can't find popular content (Recommended)", description: "Homepage lacks social proof; users churn after not discovering trending videos" },
      { label: "Returning users have no reason to come back", description: "No fresh or dynamic content to pull users back" },
      { label: "New creators can't get visibility", description: "Without trending, small creators never surface to new audiences" }
    ]
  }]
})
```

Adapt the options to the actual feature — do not reuse these verbatim.

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
1. Create **ADR** (`docs/adr/00NN-{slug}.md`) using `templates/artifacts/adr.template.md`
   - Key sections to fill: Metadata, Context, Decision Drivers, Considered Options (pros/cons per option), Decision Outcome, Consequences, Validation, Links, Changelog
   - Do NOT include Technical Details or Implementation Steps in the ADR — those go in the Plan
2. Create **Implementation Plan** (`docs/plans/plan-{slug}.md`) using `templates/artifacts/plan.template.md`
3. Add entries to **`mkdocs.yml`** nav

Do not auto-create these — wait for the user to confirm.

---

## Rules

- **Never skip the clarification step** if any of Q1–Q4 are unanswered
- **One question at a time** — use `AskUserQuestion` for each; wait for answer before asking Q2, etc.
- **Always provide options** — 3–4 contextually relevant choices, mark best guess `(Recommended)`, "Other" is auto-added for free text
- **Acknowledge each answer** before moving to the next question
- **Assumptions must be labelled** — mark with `> **Assumption:**` inline
- **Use the official template** from `templates/artifacts/prd.template.md` — do not invent a different structure
- **Fill every section** — use `N/A` or `TBD` only when genuinely unknown, not as a shortcut

$ARGUMENTS
