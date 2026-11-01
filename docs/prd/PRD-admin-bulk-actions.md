# PRD: Admin Bulk Actions on Every Tab

<!--
Product Requirements Document
Filename: docs/prd/PRD-admin-bulk-actions.md
Owner: nguyenhuuca
Handoff to: Architect (/architect), UI/UX Designer (/ui-ux-designer)
Related Skills: scope, planning-artifacts
-->

## Overview

**Status:** In Review
**Author:** nguyenhuuca
**Date:** 2026-11-01
**Version:** 1.0
**Tracking:** `docs/tracking.md` row "Admin Bulk Actions"
**PR-FAQ:** N/A (extends the existing admin dashboard; no new audience)
**Stakeholders:** nguyenhuuca (owner, admin)
**ADR:** [0036 – Admin bulk actions](../adr/0036-admin-bulk-actions.md)
**Sequence:** [sequence-admin-bulk-actions](../sequence/sequence-admin-bulk-actions.md)
**Plan:** [plan-admin-bulk-actions](../plans/plan-admin-bulk-actions.md)

## Problem Statement

Admin work happens one row at a time on every admin tab except Comments. Moving 20 imported videos from FEED to COLLECTION_ONLY takes 20 selects; rejecting a burst of duplicate suggestions takes 20 modal round trips; cancelling a stuck import queue takes N clicks. Comments already has a selection model and a bulk bar (`AdminCommentTable.jsx`), so admins expect the same everywhere.

### Evidence

**Quantitative Evidence**
- Video Vault and Flagged Logs show 20 rows per page with no selection; every status, visibility, delete and "add to collection" action is per row (`AdminVideoTable.jsx`).
- There are 0 bulk endpoints outside comments. The comment bulk endpoints (`PATCH /admin/comments/moderation`, `POST /admin/comments/hard-delete`) cap at 100 ids.

**Qualitative Evidence**
- Owner request (2026-11-01): "bulk update on every admin tab", with status, visibility, delete and add-to-collection named for videos.
- Research videos (ADR-0035) arrive in batches and all need the same visibility and the same collection.

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Fewer clicks for batch moderation | Requests/clicks to change N rows | 1 request, ≤ 4 clicks regardless of N (≤ page size) |
| Same rules as single-row actions | Bulk outcome vs N single calls | Identical per-row result (same guards, same side effects) |
| Clear partial results | Rows skipped with a reason | 100% of skipped rows reported with a code; nothing silently dropped |
| No regression | Existing tests | All pass; Comments bulk behavior unchanged |

## User Stories

### Admin

- As an admin, I want to tick several videos and change their status or visibility at once, so that a batch import can be moderated in one step.
  - Acceptance: select rows on Video Vault or Flagged Logs → choose status/visibility in the bulk bar → one request; table refreshes; notice shows "N updated, M unchanged, K not found".
- As an admin, I want to delete several videos at once, with the same "also delete local file" option as the single delete, so that junk imports go away quickly.
  - Acceptance: confirm dialog states the count; Drive file deletion is not offered in bulk (per-row only); rows whose file is locked by Drive sync are skipped with a reason.
- As an admin, I want to add several videos to one collection, so that a research batch lands in its playlist in one step.
  - Acceptance: pick one collection (own, or PUBLIC as admin) → videos that are not PUBLISHED or are HIDDEN are skipped with a reason; videos already in the collection count as unchanged; the 500-item cap is respected.
- As an admin, I want to change the role of, grant/revoke one permission for, or delete several accounts, so that onboarding a group of contributors is one action.
  - Acceptance: the same rules as the single-row calls apply per account (no self change, actor must outrank target and new role, autopilot bits SUPER_ADMIN only); violating rows are skipped with a reason; granting ADMIN in bulk shows the capability confirm once.
- As an admin, I want to approve or reject several pending suggestions at once, so that a backlog is cleared quickly.
  - Acceptance: reject applies one reason (+ note when OTHER) to all; approve uses the submitted title; non-PENDING rows and duplicate-import conflicts are skipped with their error code; "import disabled" fails the whole request up front.
- As an admin, I want to cancel or retry several import jobs at once, so that a stuck or failed queue is handled in one step.
  - Acceptance: rows in the wrong state are skipped (409 code per row), the rest proceed.

## Requirements

### Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-1 | Shared selection model on admin tables: row checkbox, header "select page" checkbox (indeterminate when partial), selection cleared on page/filter change, kept after a failed request | Must |
| FR-2 | Shared bulk bar: "N selected", tab-specific actions, "Clear selection"; visible only when N > 0 | Must |
| FR-3 | Video Vault + Flagged Logs: bulk status, bulk visibility, bulk delete (local-file option only), bulk add to collection | Must |
| FR-4 | User Accounts: bulk role change, bulk grant/revoke of one permission bit, bulk delete | Must |
| FR-5 | Suggestions: bulk approve, bulk reject (one reason/note) | Must |
| FR-6 | Import Video: bulk cancel, bulk retry | Should |
| FR-7 | One request per bulk action; per-row outcome reported as `updated` / `unchanged` / `skipped[{id, code}]` / `notFound` | Must |
| FR-8 | Bulk rules are exactly the single-row rules (the bulk path calls the single-row service method per id) | Must |
| FR-9 | Every bulk endpoint: admin-only, `@AuditLog`, `@RateLimited`, max 100 ids | Must |
| FR-10 | Destructive bulk actions (delete, reject, role → USER) confirm with the count | Must |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Latency | ≤ 3 s for 20 rows on the 1 vCPU prod VM (deletes may touch files) |
| NFR-2 | Isolation | One failing row never rolls back the others (per-row transaction) |
| NFR-3 | Accessibility | Checkboxes labelled per row; bulk bar `role="toolbar"`; result `role="status"` |
| NFR-4 | i18n | All new strings in `vi` and `en` |
| NFR-5 | Coverage | Backend LINE coverage stays ≥ 90% |

## Scope

### In Scope

- Video Vault, Flagged Logs (same table), User Accounts, Suggestions, Import Video.
- Shared frontend selection hook + bulk bar, and moving Comments onto them with no behavior change.

### Out of Scope

- **Comments**: already has bulk; only refactored onto the shared pieces.
- **Drive Sync**: already multi-select with "Force sync N" (`DriveSyncPanel.jsx`).
- **Scheduled Jobs**: a handful of rows, SUPER_ADMIN only, cron edits need the live preview; bulk adds risk without saving time.
- Bulk priority edit, bulk HLS toggle, bulk Drive-file deletion, bulk suggestion reopen, bulk import run-now.
- Selection across pages ("select all 1,240 results").

## Design

- Bulk bar sits above the table, as on Comments (`.admin-bulk-bar`), reusing the redesigned modal header for dialogs.
- Bulk add to collection reuses the collection card list from `AddToCollectionModal` in a single-choice mode ("pick a collection").
- Result notice: "12 updated · 3 unchanged · 2 skipped (not published)" with skipped reasons grouped by code.

## Technical Considerations

- Decision recorded in [ADR-0036](../adr/0036-admin-bulk-actions.md): `POST /admin/<resource>/bulk` with an `action` discriminator; a bulk service loops over ids and calls the existing single-row service method through its Spring proxy, so each row gets its own transaction and all existing guards.
- Bulk approve creates one import job per suggestion; the worker already serialises jobs, and the 100-id cap plus `@RateLimited` bound the burst.
- Every role/permission change bumps `credentialsVersion` (signs the user out); this stays per row.

## Dependencies

- ADR-0035 collection-only visibility (videos); collection add rules in `VideoCollectionServiceImpl.addVideo`.
- Existing single-row services: `AdminVideoServiceImpl`, `AdminAccountServiceImpl`, `VideoSuggestionServiceImpl`, `VideoImportServiceImpl`.

## Timeline

| Milestone | Date | Description |
|-----------|------|-------------|
| Plan approved | 2026-11-02 | PRD + ADR-0036 + sequence + plan |
| Videos bulk | +2 days | BK-1..BK-5 |
| Accounts, suggestions, imports | +3 days | BK-6..BK-11 |
| Prod smoke | +1 day | BK-12 |

## Open Questions

- [ ] Should bulk delete ever offer "also delete the Drive file"? (Proposed: no; per row only.)
- [ ] Should "select all on page" on Accounts skip rows the viewer cannot change (self, outranked) up front? (Proposed: yes, those checkboxes are disabled.)

## Appendix

- Exploration (2026-11-01): comments bulk prior art, video tab actions, accounts/suggestions, import/drive/jobs — summarised in the plan.
