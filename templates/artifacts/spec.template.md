# Feature Specification: [Feature Name]

<!--
Feature Specification
Filename: docs/specs/spec-{slug}.md
Owner: Builder (/builder) or Architect (/architect)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)

Purpose: Defines exact system behavior, API contract, and acceptance criteria.
         Dev reads this and implements. QA reads this and tests.
         No ambiguity allowed — every edge case must be resolved here.

Related:
- PRD: docs/prd/PRD-{slug}.md         (why + what, business perspective)
- ADR: docs/adr/NNNN-{slug}.md        (architectural decisions)
- Plan: docs/plans/plan-{slug}.md     (task breakdown, optional for small features)
-->

## Metadata

**Status:** Draft | Approved | Implemented
**Author:** [name]
**Date:** YYYY-MM-DD
**Related PRD:** [link]
**Related ADR:** [link]

---

## Overview

[1–3 sentences. What does this feature do? Who is affected? What is the system-level outcome?
No why, no metrics — just what the system must do.]

---

## Business Rules

### Rule 1

[State the rule precisely. Use exact values, not approximations.]

### Rule 2

[Each rule is independently testable. If a rule has exceptions, list them here.]

### Rule 3

[Order rules from most to least critical.]

---

## Functional Requirements

### FR-1: [Short name]

[Describe what the system must do. Use "must", "must not", "should".]

### FR-2: [Short name]

[Be specific about inputs, conditions, and outputs.]

### FR-3: [Short name]

[If a requirement has sub-conditions, list them as bullets.]

---

## API Changes

### New Endpoints

#### [METHOD] [/path]

**Description:** [What this endpoint does]

**Auth:** Required (JWT Bearer) | Public
**Rate limit:** [e.g., 10 req/min per user] | None

**Request**

```json
{
  "field": "type"
}
```

**Response — Success**

```json
{
  "status": "SUCCESS",
  "data": {
    "field": "value"
  }
}
```

> Uses `ResultObjectInfo<T>` wrapper. For lists, use `ResultListInfo<T>`.

**Response — Error**

| HTTP Status | Code | Message | Condition |
|-------------|------|---------|-----------|
| 400 | `INVALID_REQUEST` | [message] | [when] |
| 401 | `UNAUTHORIZED` | [message] | Missing or invalid JWT |
| 409 | `[ERROR_CODE]` | [message] | [when] |
| 429 | `RATE_LIMITED` | [message] | Rate limit exceeded |

### Modified Endpoints

#### [METHOD] [/path]

**Change:** [What changed and why]

**Before:**
```json
{}
```

**After:**
```json
{}
```

---

## Database Changes

### New Tables

```sql
CREATE TABLE [table_name] (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- columns
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_[table]_[column] ON [table_name]([column]);
```

> Migration file: `api/src/main/resources/db/changelog/sql/YYYYMMDDHHMM-[description].sql`

### Modified Tables

| Table | Change | Column | Type | Notes |
|-------|--------|--------|------|-------|
| [table] | Add column | [column] | [type] | [nullable/default] |
| [table] | Add index | [column] | — | [reason] |

### No Changes

[State explicitly if no DB changes are needed — removes ambiguity.]

---

## Security Requirements

### Authentication

[Which endpoints require JWT? Which are public?]

### Authorization

[Role checks if any. e.g., "Only ADMIN role can call DELETE /admin/users/{id}"]

### Data Validation

| Field | Rule | Error |
|-------|------|-------|
| [field] | [constraint, e.g., not null, max 100 chars] | [error code] |

### Sensitive Data

[Any fields that must be masked in logs via @AuditLog? List them.]

---

## Caching Impact

[Does this feature read from or write to Guava LRU cache?]

| Cache | Impact | Action |
|-------|--------|--------|
| [CacheName] | Reads / Writes / Invalidates | [What must happen] |

> If no cache impact: state "No cache impact."

---

## Frontend Changes

### New Routes

| Path | Component | Auth Required |
|------|-----------|---------------|
| `/[path]` | `[ComponentName].jsx` | Yes / No |

### Modified Components

| Component | File | Change |
|-----------|------|--------|
| [ComponentName] | `webapp/src/...` | [What changes] |

### State Management

[New React Query keys? New API calls? Cache invalidation strategy?]

```javascript
// Query key convention
['resource', 'filter']

// Invalidate on mutation
queryClient.invalidateQueries(['resource'])
```

---

## Event / Job Changes

### Domain Events

| Event | Change | Schema |
|-------|--------|--------|
| [EventName] | New / Modified / No change | [Link or inline] |

### Scheduled Jobs

[Any AppScheduler changes? If none, state explicitly.]

---

## Non-Functional Requirements

### Performance

| Operation | Target | Notes |
|-----------|--------|-------|
| [Endpoint] | < [X] ms p99 | |
| [Query] | < [X] ms | With index on [column] |

### Availability

[Any degradation risk to existing flows? What is the rollback path?]

### Scalability

[Any concern at scale? e.g., "Works within 500-item user cap."]

---

## Edge Cases

### EC-1: [Short description]

**Condition:** [Exact input or system state]
**Expected:** [Exact system behavior]

### EC-2: [Short description]

**Condition:** [Exact input or system state]
**Expected:** [Exact system behavior]

### EC-3: Concurrent requests

**Condition:** Two requests from the same user arrive simultaneously.
**Expected:** [One succeeds / Both fail / Idempotent — pick one and state it explicitly]

---

## Acceptance Criteria

- [ ] [Testable statement. Start with a verb. "User can...", "System rejects...", "Response contains..."]
- [ ] [Each criterion maps to at least one FR or Business Rule above]
- [ ] [Edge cases EC-1, EC-2, EC-3 are covered by integration tests]
- [ ] Existing flows are unaffected (regression)
- [ ] API response matches contract defined in this spec
- [ ] `mvn verify` passes with coverage gate met
- [ ] `npm run test` passes

---

## Open Questions

- [ ] [Any unresolved decisions that block implementation]
- [ ] [Escalate to architect or product if needed]

---

## Version History

| Version | Date | Author | Change |
|---------|------|--------|--------|
| 1.0 | YYYY-MM-DD | [name] | Initial draft |
