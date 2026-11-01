# Plan: Admin Bulk Actions on Every Tab

<!--
Implementation Plan
Filename: docs/plans/plan-admin-bulk-actions.md
Owner: Builder (/builder) or Architect (/architect)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
Related Skills: swarm-plan, testing
-->

## Overview

**Status:** In Progress (BK-1..BK-11 built 2026-11-01; BK-12 land pending)
**Author:** nguyenhuuca
**Date:** 2026-11-01
**Tracking:** `docs/tracking.md` row "Admin Bulk Actions"; `TASKS.md` section "Admin bulk actions"
**Related PRD:** [PRD-admin-bulk-actions](../prd/PRD-admin-bulk-actions.md)
**Related ADR:** [ADR-0036](../adr/0036-admin-bulk-actions.md)
**Sequence:** [sequence-admin-bulk-actions](../sequence/sequence-admin-bulk-actions.md)

## Objective

Add multi-select and a bulk bar to Video Vault, Flagged Logs, User Accounts, Suggestions and Import Video, backed by one `POST …/bulk` endpoint per resource that reuses the existing single-row service methods (ADR-0036).

## Scope

### In Scope

- Videos (Vault + Flagged, same table): status, visibility, delete (local-file option), add to collection.
- Accounts: role, grant/revoke one permission, delete.
- Suggestions: approve, reject.
- Imports: cancel, retry.
- Shared FE `useBulkSelection` + `BulkActionBar` + `formatBulkResult`; Comments refactored onto them (no behavior change).

### Out of Scope

- Comments endpoints (already bulk), Drive Sync (already bulk), Scheduled Jobs (few rows, SUPER_ADMIN, cron preview).
- Bulk priority, HLS, Drive-file delete, suggestion reopen, import run-now, cross-page selection.

## Technical Approach

### Architecture Changes

```
Table (useBulkSelection) ── BulkActionBar ── useBulk<Resource>() ──► POST …/bulk
                                                                       │
                       Controller (@HasPermission ADMIN, @AuditLog, @RateLimited, @Valid ids 1–100)
                                                                       │
                       <Resource>BulkServiceImpl  (no @Transactional; validates params once)
                         └─ loop id → existing <Resource>Service.method(id, …)  (own @Transactional via proxy)
                              ok → updated · no-op → unchanged · AppException(code) → skipped · missing → notFound
                                                                       │
                       BulkResultDto {requested, updated, unchanged, skipped[{id,code,message}], notFound[]}
```

Flows: [sequence diagrams](../sequence/sequence-admin-bulk-actions.md) 1–5.

### Key Decisions

| Decision | Rationale |
|----------|-----------|
| Per-row transactions through existing services (ADR-0036 Option 3) | One copy of business rules; a 409 on one row does not roll back others |
| `action` discriminator, one endpoint per resource | Mirrors comments moderation; one audit entry and one rate-limit permit per batch |
| Grant/revoke one permission bit server-side | Avoids stale full-mask overwrite from the client snapshot |
| No Drive-file delete in bulk | Irreversible external deletion stays a deliberate per-row act |
| Selection per page (max = page size 20, hard cap 100) | Matches Comments; no "select all results" ambiguity |
| Disable checkboxes the viewer cannot act on (accounts: self, outranked) | Fewer predictable skips |

## Implementation Steps

Each task is independently mergeable to `main` (BE before its FE). Size target ≤ 400 changed LOC excl. tests.

| ID | Task | Layer | Depends on | Acceptance criteria |
|----|------|-------|-----------|---------------------|
| **BK-1** | Shared bulk DTOs + videos bulk endpoint (STATUS, VISIBILITY, DELETE) | BE | — | `BulkResultDto`, `BulkSkip`, `BulkResultCollector`; `AdminVideoBulkRequest` (`@NotEmpty @Size(max=100)` ids, action, params) ; `POST /admin/videos/bulk` with `@HasPermission(ADMIN)`, `@AuditLog("bulkVideos")`, `@RateLimited(10)`; `AdminVideoBulkServiceImpl` loops `updateStatus/updateVisibility/deleteVideo(id, local, drive=false)`; status/visibility return changed-or-not; missing param → 400 before any row; tests: mixed ids (updated/unchanged/notFound/locked-file skipped), one throwing row does not affect others, permission tests (200/403/400 empty/400 >100) |
| **BK-2** | FE shared selection + bulk bar; refactor Comments onto them | FE | — | `hooks/useBulkSelection.js` (toggle, toggleAll(pageIds), indeterminate state, clear on `resetKey`, cap), `components/admin/BulkActionBar.jsx`, `utils/bulkResult.js` (`formatBulkResult`, i18n vi/en); `AdminCommentTable` uses them; all existing `AdminCommentTable.test.jsx` pass unchanged; new unit tests for hook + formatter |
| **BK-3** | Video Vault / Flagged bulk UI: status, visibility, delete | FE | BK-1, BK-2 | Row + header checkboxes in `AdminVideoTable`; bar with status select, visibility select, Delete (confirm with count + local-file option, no Drive option); `useBulkVideos` mutation invalidates `['admin','videos']` + stats; notice from result; selection cleared on page change and on success, kept on error; tests for each action + clear rules |
| **BK-4** | Bulk add to collection endpoint | BE | BK-1 | `POST /collections/{id}/videos/bulk {videoIds}` (1–100), `@RateLimited(10)`, `@AuditLog`; `CollectionBulkServiceImpl`: manageable check once (403/404 whole request), per-id `addVideo` → added / unchanged (already in) / skipped `collection.videoNotEligible` / `collection.full`; tests incl. cap reached mid-batch and admin vs other user's private collection |
| **BK-5** | Bulk add to collection UI | FE | BK-3, BK-4 | "Add to collection" in the video bulk bar opens a pick-one modal reusing the collection card list styling (`atc-*`) and `ModalHeader` (subtitle "N videos"); create-new inline then add; result notice; invalidates collection lists; tests |
| **BK-6** | Accounts bulk endpoint (ROLE, GRANT/REVOKE_PERMISSION, DELETE) | BE | BK-1 | `POST /admin/accounts/bulk`; `AdminAccountBulkServiceImpl` calls `updateRole/updatePermissions/deleteAccount` per id with actor id; permission bit applied to the **current** mask; self/outranked/autopilot violations → skipped with code; unknown role/permission → 400 up front; tests incl. ADMIN actor vs ADMIN target, SUPER_ADMIN autopilot bit |
| **BK-7** | Accounts bulk UI | FE | BK-2, BK-6 | Checkboxes (disabled for self/outranked), bar: role select (ADMIN viewer: USER/CONTRIBUTOR only), permission grant/revoke menu, Delete (danger confirm with count); granting ADMIN shows the capability confirm once; tests |
| **BK-8** | Suggestions bulk endpoint (APPROVE, REJECT) | BE | BK-1 | `POST /admin/video-suggestions/bulk`; import disabled → 503 for APPROVE before any row; REJECT+OTHER without note → 400 before any row; per-id `approve(id, null title)` / `reject(id, reason, note)`; notPending 4093 / duplicate 4094 → skipped; `importWorker.wakeUp()` still after commit per row; tests |
| **BK-9** | Suggestions bulk UI | FE | BK-2, BK-8 | Checkboxes on PENDING rows only; bar: Approve (confirm with count), Reject (reason select + note for OTHER, reuses reject dialog); error mapping reused; tests |
| **BK-10** | Imports bulk endpoint (CANCEL, RETRY) | BE | BK-1 | `POST /admin/video-imports/bulk`; per-id cancel/retry; 409 state conflicts → skipped with code; tests |
| **BK-11** | Imports bulk UI | FE | BK-2, BK-10 | Checkboxes in `ImportTable`, bar: Cancel, Retry (enabled when any selected row qualifies via `canCancel/canRetry`); tests |
| **BK-12** | Land: push (owner approval), CI green, prod smoke per tab, docs As-built | Ops/docs | all | Owner smoke on prod: one bulk action per tab with a mixed selection; sequence diagrams marked As-built; tracking row ✅ |

### Dependency graph

```
BK-1 ──┬─► BK-3 ──► BK-5
       ├─► BK-4 ───┘
       ├─► BK-6 ──► BK-7
       ├─► BK-8 ──► BK-9
       └─► BK-10 ─► BK-11
BK-2 ──┴─► (BK-3, BK-7, BK-9, BK-11)
all ───► BK-12
```

Parallel waves: (BK-1, BK-2) → (BK-3, BK-4, BK-6, BK-8, BK-10) → (BK-5, BK-7, BK-9, BK-11) → BK-12.

## Files to Modify

| Area | Files |
|------|-------|
| BE shared | `dto/admin/BulkResultDto.java`, `dto/admin/BulkSkip.java`, `service/impl/BulkResultCollector.java` (new) |
| BE videos | `web/AdminController.java`, `dto/admin/AdminVideoBulkRequest.java`, `service/AdminVideoBulkService.java`, `service/impl/AdminVideoBulkServiceImpl.java`, `service/impl/AdminVideoServiceImpl.java` (boolean returns) |
| BE collections | `web/VideoCollectionController.java`, `service/impl/CollectionBulkServiceImpl.java` (+ interface), request DTO |
| BE accounts | `web/AdminController.java`, `service/impl/AdminAccountBulkServiceImpl.java` (+ interface), request DTO |
| BE suggestions | `web/AdminVideoSuggestionController.java`, `service/impl/SuggestionBulkServiceImpl.java` (+ interface), request DTO |
| BE imports | `web/AdminVideoImportController.java`, `service/impl/ImportBulkServiceImpl.java` (+ interface), request DTO |
| FE shared | `hooks/useBulkSelection.js`, `components/admin/BulkActionBar.jsx`, `utils/bulkResult.js`, `styles/index.css` (`.admin-bulk-bar` reuse) |
| FE tables | `AdminCommentTable.jsx`, `AdminVideoTable.jsx`, `AdminAccountTable.jsx`, `AdminSuggestionTable.jsx`, `ImportTable.jsx`, new `BulkAddToCollectionModal.jsx` |
| FE data | `api/admin.js`, `api/collections.js`, `hooks/useAdmin.js`, `hooks/useCollections.js`, `i18n/locales/{vi,en}/admin.json`, `library.json` |

New beans with 2+ constructors need `@Autowired` (`BeanConstructorTest` guards it).

## Dependencies

### Code Dependencies

- Existing single-row services (unchanged rules); `AppException` codes for skip reasons.

### Service Dependencies

- None new. Approve still enqueues import jobs for the async worker.

## Testing Strategy

### Unit Tests

- Each `*BulkServiceImpl`: mixed outcome table test; param validation before any call (verify no service interaction); exception in row k does not stop row k+1.
- `useBulkSelection`, `formatBulkResult`.

### Integration Tests

- `*ControllerPermissionTest`: 200 admin, 403 USER, 400 empty, 400 > 100, 400 bad action params.
- Table tests per tab: select page → action → request body → notice → selection cleared; error keeps selection.

## Rollback Plan

- Every task is additive (new endpoints, new UI). Revert the task commit; single-row actions are untouched.
- BK-1 changes `updateStatus/updateVisibility` return types only (callers unaffected); revert with it.

## Risks

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Bulk approve floods the import queue | Low | Page-size selection (20), 100 cap, `@RateLimited(10)`; worker is serial |
| Mass role change signs many users out | Medium | Confirm shows the count; per-row `credentialsVersion` is expected behavior |
| Mid-batch crash leaves partial changes | Low | Idempotent per-row ops; result lists every id; rerun is safe |
| 1 vCPU prod VM latency on bulk delete | Medium | Max 20 per page; files deleted per row as today |
| Spring self-invocation skips per-row `@Transactional` | Medium | Bulk logic in a separate bean calling the service interface; unit test asserts calls go through the interface |
