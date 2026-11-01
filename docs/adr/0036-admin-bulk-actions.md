# ADR-0036: Admin Bulk Actions – One `/bulk` Endpoint per Resource, Per-Row Transactions via Existing Services

## Metadata

**Status:** Accepted (2026-11-01) · **Date:** 2026-11-01 · **Deciders:** nguyenhuuca · **Tags:** api, frontend, convention
**Related PRD:** [PRD-admin-bulk-actions](../prd/PRD-admin-bulk-actions.md) · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ✅ Follows Golden Path (explicit admin controllers, layered Controller → Service → Repository, `@AuditLog`, `@RateLimited`, React Query mutations). No new dependency.

---

## Context

- **Only Comments has bulk today.** `PATCH /admin/comments/moderation` and `POST /admin/comments/hard-delete` (`AdminController.java:233-258`) take `ids` (max 100, `BulkModerateCommentRequest.MAX_IDS`). `AdminCommentServiceImpl.bulkModerate` runs the whole batch in one `@Transactional`, treats business no-ops as `unchanged`, and returns counts plus `notFound`.
- **The other tabs' rules live in single-row service methods**, each `@Transactional` with its own guards and side effects:
  - `AdminVideoServiceImpl.updateStatus / updateVisibility / deleteVideo` – `feedCache.invalidate()`; delete also touches local/Drive files and HLS under `driveFileLocks.tryClaim`.
  - `VideoCollectionServiceImpl.addVideo` – PUBLISHED and not HIDDEN only, 500-item cap (409), `requireManageable` (own or PUBLIC-as-admin).
  - `AdminAccountServiceImpl.updateRole / updatePermissions / deleteAccount` – no self change, actor outranks target and new role, autopilot bits SUPER_ADMIN only, `credentialsVersion` bump.
  - `VideoSuggestionServiceImpl.approve / reject` – row lock, import-job creation (409 on duplicate), 503 when import is disabled, `SuggestionReviewedEvent`.
  - `VideoImportServiceImpl` cancel / retry – conditional updates, 409 on wrong state.
- **Several of these can fail per row for legitimate reasons** (409 state conflicts, locked files, outranked accounts). With comments' all-or-nothing transaction, one such row would roll back the rest.
- **Frontend selection lives inside `AdminCommentTable.jsx`** (`selected` Set, `toggleAll`, clear on page/filter change, `.admin-bulk-bar`). Five more tables need the same.

## Decision Drivers

1. Bulk must apply exactly the single-row rules (no second copy of business logic – Core Principle 5).
2. One bad row must not undo the good ones; every skipped row must be reported with a reason.
3. One HTTP request per bulk action (rate limit, audit, latency on a 1 vCPU VM).
4. Consistent API and UI across tabs; small, independently mergeable tasks.

## Considered Options

### Option 1: Client loops over the existing single-row endpoints

- ✅ No backend work.
- ❌ N requests per action: N audit entries without a batch id, rate limits hit at 10–30/min, partial failure handling in every table, slow on mobile.

### Option 2: Comments-style bulk – one transaction per batch, set-based logic in the service

- ✅ Atomic; one query for simple updates.
- ❌ Duplicates every guard in set form (role hierarchy, collection cap, Drive locks); one 409 rolls back the batch; delete/approve side effects (files, events, import jobs) do not fit one transaction.

### Option 3: `POST /admin/<resource>/bulk` with an `action` discriminator; a bulk service calls the single-row method per id through its Spring proxy (chosen)

- ✅ Each row runs the existing `@Transactional` method → own transaction, all guards and side effects reused unchanged.
- ✅ Domain exceptions per row become `skipped[{id, code}]`; other rows proceed.
- ✅ One request, one `@AuditLog` entry with the ids, one rate-limit permit.
- ❌ N transactions instead of 1 (acceptable: N ≤ 100, in practice ≤ 20 = page size).
- ❌ Not atomic – by design; the result reports exactly what happened.

## Decision Outcome

**Chosen: Option 3.**

### API convention

| Resource | Endpoint | Actions |
|----------|----------|---------|
| Videos | `POST /admin/videos/bulk` | `STATUS {status}`, `VISIBILITY {visibility}`, `DELETE {deleteLocalFile}` |
| Collection items | `POST /collections/{id}/videos/bulk` | add `{videoIds}` |
| Accounts | `POST /admin/accounts/bulk` | `ROLE {role}`, `GRANT_PERMISSION {permission}`, `REVOKE_PERMISSION {permission}`, `DELETE` |
| Suggestions | `POST /admin/video-suggestions/bulk` | `APPROVE`, `REJECT {reason, note}` |
| Imports | `POST /admin/video-imports/bulk` | `CANCEL`, `RETRY` |

- Request: `{ ids: [..] (1–100, unique), action, ...params }`, validated with `@NotEmpty @Size(max = 100)`; action-specific params validated once **before** any row is touched (e.g. REJECT+OTHER needs a note, `import disabled` → 503 for APPROVE).
- Response: `ResultObjectInfo<BulkResultDto>` with `requested`, `updated`, `unchanged`, `skipped: [{id, code, message}]`, `notFound: [id]`.
- Every endpoint: class-level admin `@PreAuthorize`, `@HasPermission(perm = Permission.ADMIN)`, `@AuditLog("bulk<Resource>")`, `@RateLimited(permit = 10)`.
- `GRANT/REVOKE_PERMISSION` change one bit server-side from the **current** mask (no stale client mask), then call `updatePermissions`.
- Bulk video delete never deletes Drive files (per-row only).

### Backend structure

- `dto/admin/BulkRequest` base (ids + validation) and one request record per resource; shared `BulkResultDto` + `BulkResultCollector` (counts, skipped, notFound).
- One `*BulkService` bean per resource (`AdminVideoBulkServiceImpl`, …) that is **not** `@Transactional` itself and calls the existing service interface per id; `AppException`/`ResponseStatusException` with a code → `skipped`; not-found → `notFound`; a no-op returned by the single method → `unchanged`.
- Single-row methods that currently cannot tell "changed" from "no-op" get a boolean return where needed (status, visibility); existing callers ignore it.

### Frontend structure

- `hooks/useBulkSelection.js` (Set, toggle, toggleAll(pageIds), clear on `resetKey` change, max 100) and `components/admin/BulkActionBar.jsx` (count, children actions, clear) + `formatBulkResult()` for the notice.
- `AdminCommentTable` moves onto them first with no behavior change (Comments keep their existing endpoints and DTOs).

### Quantified Impact

| Metric | Before | After |
|--------|--------|-------|
| Requests to change 20 videos | 20 | 1 |
| Places holding role/collection/lock rules | 1 | 1 (unchanged) |
| Bulk-capable admin tabs | 1 (Comments) + Drive Sync | 6 |

## Consequences

### Positive

- Bulk is a thin orchestration layer; rule changes in single-row services apply to bulk automatically.
- Uniform result shape lets one notice component serve every tab.

### Negative

- Not atomic: a crash mid-batch leaves the processed rows changed. Mitigation: per-row idempotent operations, result lists every id, the admin can rerun.
- N transactions and N `feedCache.invalidate()` calls per batch. Mitigation: ≤ 100 ids; invalidation is a cheap flag.

### Neutral

- Comments keep their atomic bulk endpoints; their semantics fit (no external side effects besides events).

## Validation

- Unit tests per bulk service: mixed ids (ok / no-op / guarded / missing) → exact counts and codes; one row throwing does not affect the others.
- Permission tests per endpoint: 200 admin, 403 USER, 400 empty ids, 400 > 100 ids, 400 missing action param.
- Frontend: selection clear on page/filter change, kept on error, notice text from a mixed result.

## Links

- PRD: [PRD-admin-bulk-actions](../prd/PRD-admin-bulk-actions.md)
- Sequence: [sequence-admin-bulk-actions](../sequence/sequence-admin-bulk-actions.md)
- Plan: [plan-admin-bulk-actions](../plans/plan-admin-bulk-actions.md)
- Prior art: [plan-admin-comment-moderation](../plans/plan-admin-comment-moderation.md); ADR-0035 (visibility enum)

## Changelog

- 2026-11-01: Proposed.
- 2026-11-01: Accepted by the owner (no Drive-file delete in bulk; Accounts checkboxes disabled for self/outranked).
