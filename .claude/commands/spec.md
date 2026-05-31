---
description: Generate a Feature Specification from a PRD and ADR — asks clarifying questions one at a time, then writes docs/specs/spec-{slug}.md
allowed-tools: Read, Write, Glob, Grep, AskUserQuestion
argument-hint: <path/to/PRD.md> <path/to/ADR.md>
---

# Spec → Feature Specification

Turn an approved PRD + ADR into a precise, implementable Feature Specification.
Ask clarifying questions before writing — the spec must be unambiguous.

## How to use

```
/spec docs/prd/PRD-{slug}.md docs/adr/NNNN-{slug}.md
```

Example: `/spec docs/prd/PRD-bookmark-feature.md docs/adr/0012-bookmark-feature-design.md`

---

## Workflow

### Step 1 — Read inputs

Read both files from `$ARGUMENTS`:
- **PRD** — extract: feature name, functional requirements, user stories, scope, NFRs
- **ADR** — extract: chosen approach, schema sketch, API sketch, constraints, consequences

Derive the kebab slug from the PRD filename (e.g. `PRD-bookmark-feature.md` → `bookmark-feature`).

### Step 2 — Identify gaps and ask questions one at a time

The PRD and ADR answer the "why" and "how". The Spec must answer the "exactly what".
Ask ONLY for what is genuinely unclear or not stated in the inputs.

**Required information:**

| # | Question | Why it matters |
|---|----------|---------------|
| Q1 | What are the exact business rules that must be enforced? (numbered, precise) | Business Rules section |
| Q2 | What are all the error cases? Include HTTP status, error code, and the exact condition. | API error table |
| Q3 | Are there any rate limits, caps, or quotas? (per user / per day / global) | FR + Security sections |
| Q4 | What are the edge cases that must be explicitly handled? (boundary values, concurrency, nulls) | Edge Cases section |
| Q5 | What are the performance targets per operation? | NFR section |
| Q6 | Any security requirements beyond JWT auth? (role restrictions, data masking, audit logging) | Security section |

**Rules:**
- Ask questions **one at a time** — use `AskUserQuestion` for each, wait for the answer, then ask the next
- Work through Q1 → Q6 in order, skipping any already answered in the PRD/ADR
- For each question, provide **3–4 contextually relevant options** derived from the feature. Mark best guess `(Recommended)`.
- After each answer, acknowledge it in one sentence before asking the next
- If the user says "just write it" or "use your best judgment", proceed with clearly labeled assumptions

**Example — asking Q1 for a bookmark feature:**
```
AskUserQuestion({
  questions: [{
    question: "What are the core business rules for bookmarks?",
    header: "Business Rules",
    multiSelect: true,
    options: [
      { label: "Max 500 bookmarks per user (Recommended)", description: "Cap enforced at API layer — 409 if exceeded" },
      { label: "A video can only be bookmarked once per user", description: "Idempotent add — no duplicates" },
      { label: "Bookmarks survive video deletion", description: "video_id becomes null, bookmark entry persists" },
      { label: "Max 50 collections per user", description: "Cap on named folders to prevent abuse" }
    ]
  }]
})
```

**Example — asking Q4 for edge cases:**
```
AskUserQuestion({
  questions: [{
    question: "Which edge cases must be explicitly handled?",
    header: "Edge Cases",
    multiSelect: true,
    options: [
      { label: "Bookmark exactly at the cap (Recommended)", description: "500th bookmark → success; 501st → 409" },
      { label: "Concurrent bookmark requests from same user", description: "Two simultaneous POSTs for same video → at most one created" },
      { label: "Bookmark a deleted video", description: "video_id becomes null → UI shows placeholder" },
      { label: "Delete collection with items", description: "Bookmarks remain in 'All Bookmarks'; only collection link removed" }
    ]
  }]
})
```

### Step 3 — Read codebase context

Use Glob/Grep to verify:
- Existing entities related to the feature (field names, types)
- Existing error handling patterns (`ResultErrorInfo`, exception classes)
- Migration naming convention (latest file in `db/changelog/sql/`)
- Frontend API module pattern (`webapp/src/api/`)

This ensures the spec reflects the actual codebase, not assumptions.

### Step 4 — Write the Spec

Create `docs/specs/spec-{kebab-slug}.md` using `templates/artifacts/spec.template.md`.

Fill every section. Where information comes from assumption rather than user input, mark with `> **Assumption:** ...`.

**Project-specific conventions:**
- API base path: `/v1/funny-app` (from `AppConstant.API.BASE_URL`)
- Response wrappers: `ResultObjectInfo<T>` for single objects, `ResultListInfo<T>` for lists
- Error response format: `{ status: "ERROR", code: "...", message: "..." }` via `ResultErrorInfo`
- Auth: JWT Bearer — extracted from `SecurityContextHolder` in service layer
- DB migrations: `api/src/main/resources/db/changelog/sql/YYYYMMDDHHMM-description.sql`
- UUID primary keys for new entities (matches recent VideoComment pattern)
- Frontend queries: `['resource', 'filter']` key convention with React Query

**Sections to fill (never leave placeholder text):**
- Overview — 1–3 sentences, system-level outcome
- Business Rules — numbered, each independently testable
- Functional Requirements — FR-1, FR-2... with "must"/"must not"
- API Changes — exact request/response/error table per endpoint
- Database Changes — final SQL with indexes
- Security Requirements — auth, roles, masking
- Caching Impact — which Guava caches are affected
- Frontend Changes — new routes, components, React Query keys
- Non-Functional Requirements — latency targets per operation
- Edge Cases — EC-1, EC-2... with exact expected behavior
- Acceptance Criteria — testable checklist QA can use directly

### Step 5 — Update tracking and offer next steps

After writing the Spec:
1. Update `docs/tracking.md` — set Spec column for this feature to link + 📋
2. Offer to create **Implementation Plan** (`docs/plans/plan-{slug}.md`) via `/swarm-plan`
3. Offer to add entry to **`mkdocs.yml`** nav under Specs

Do not auto-create these — wait for the user to confirm.

---

## Rules

- **Read PRD and ADR first** — never ask questions already answered there
- **One question at a time** — use `AskUserQuestion`; wait for answer before next
- **Always provide options** — 3–4 relevant choices, mark best guess `(Recommended)`
- **Acknowledge each answer** before moving to the next question
- **No ambiguity allowed** — every edge case must be resolved, not left as TBD
- **Assumptions must be labelled** — mark with `> **Assumption:**` inline
- **Fill every section** — use `N/A` only when genuinely not applicable, never as shortcut
- **Match codebase patterns** — verify entity/API names against actual code

$ARGUMENTS
