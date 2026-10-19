# Plan: Suggestion Re-review (Admin Reopen + User Request)

<!--
Implementation Plan
Filename: docs/plans/plan-suggestion-rereview.md
Owner: Builder (/swarm-execute) — run by /autopilot
Handoff to: Builder, QA Engineer
-->

## Overview

**Status:** Built 2026-10-19 (autopilot) · BE `6c16388e`, FE `29c1fd94`; SR-QA-1 verified on prod by owner (2026-10-19)
**Author:** nguyenhuuca
**Date:** 2026-10-19
**Related plan:** [plan-video-suggestions](plan-video-suggestions.md) (the flow this extends)
**ADR:** none. This adds two state transitions and two endpoints that follow the existing approve/reject pattern, plus one additive Liquibase column. It is easy to undo (`swarm-plan` Artifact Requirements).
**Size:** Small feature (~1 day). This plan is the only artifact.

## Objective

1. **Admin "Đề xuất" tab:** an admin can move a reviewed suggestion back to **Pending** ("Chờ duyệt"), then approve or reject it again with the existing buttons.
2. **User "My suggestions" (History):** after a suggestion is **rejected**, its submitter can **request a re-review**, with an optional message. The suggestion goes back to Pending and shows up again in the admin queue and the pending-count badge.

## Verified Facts (exploration 2026-10-19)

| Area | Fact | Impact |
|------|------|--------|
| Entity | `VideoSuggestion` (`entity/VideoSuggestion.java:29`) has `status`, `rejectReason`, `rejectNote`, `reviewedBy`, `reviewedAt`, `importJobId`. `SuggestionStatus` = PENDING, APPROVED, REJECTED (`enums/SuggestionStatus.java:4`) | Re-review needs no new status. Reopening = back to `PENDING` with the decision fields cleared |
| Schema | `202610150002-create-video-suggestions.sql:21`: partial unique index on `normalized_url WHERE status='PENDING'` | Reopening can collide with another pending suggestion for the same URL. Handle it like `submit` (D4) |
| Duplicate rule | `submit` (`VideoSuggestionServiceImpl.java:114-131`) rejects when a PENDING suggestion exists for the URL, or an import job in `BLOCKING_IMPORT_STATUSES` (`:54`), and maps `DataIntegrityViolationException` to `error.suggestion.duplicate` | Reuse the same check and the same mapping on reopen |
| Lock / guard | `lockPending` (`:218`) takes a PESSIMISTIC_WRITE lock. Non-PENDING → 409 4093 `error.suggestion.notPending`; missing → 404 4042 | Add a sibling lock helper for "reviewed" states |
| Approve side effect | Approve creates an import job (`importService.createResolved`, `:239`). The video appears when the job finishes (`ImportStatus`: PENDING, DOWNLOADING, UPLOADING, DONE, FAILED, CANCELLED) | Reopening an APPROVED suggestion whose job is active or DONE would leave a video or job behind it → allow it only when the job is FAILED or CANCELLED (D1) |
| Admin API | `AdminVideoSuggestionController` (`web/AdminVideoSuggestionController.java:30`) has class-level `hasRole('ADMIN')` + per-method `@HasPermission(perm=ADMIN)`. `POST /{id}/approve` `:54`, `/{id}/reject` `:64` | New `POST /{id}/reopen` follows the same annotations |
| User API | `VideoSuggestionController` (`web/VideoSuggestionController.java:40-57`): `POST /suggestions` `@RateLimited(10)` + `@AuditLog`, `GET /mine` `@RateLimited(60)`; null-user check → 401 4011 | New `POST /suggestions/{id}/rereview` follows the same pattern |
| Notifications | `SuggestionReviewedEvent` → `SuggestionNotificationListener.onReviewed` (`service/notification/SuggestionNotificationListener.java:62`) sends APPROVED / REJECTED | Re-approve and re-reject reuse it unchanged. No new notification type (D5) |
| Admin UI | `components/admin/AdminSuggestionTable.jsx`: approve/reject buttons only when `status === 'PENDING'` (`:215-234`); `ModalShell` modals (`:27`); hooks `hooks/useAdminSuggestions.js` invalidate `['admin','suggestions']` (`:19-29`); API `api/suggestions.js:21-24` | Add a Reopen button for REJECTED rows and for APPROVED rows with a failed or cancelled job. Reuse `useSuggestionMutation` |
| User UI | `components/history/SuggestionCard.jsx` (status chip `:66`), `SuggestionStatusChip.jsx:15-19` shows the reject reason; hook `hooks/useSuggestions.js` keys `['suggestions','mine-infinite',…]` (`:31`) | Add a "Request re-review" action on REJECTED cards. Invalidate `['suggestions']` |
| i18n | `webapp/src/i18n/locales/{en,vi}/admin.json` (`sugg.*` `:223-250`), `history.json`, `suggestions.json`. Parity is checked by `src/i18n/__tests__/parity.test.js` | New keys go in both languages |
| Tests | BE: `VideoSuggestionServiceImplTest`, `VideoSuggestionControllerTest`, `AdminVideoSuggestionControllerTest`. FE: `AdminSuggestionTable.test.jsx`, `HistoryView.test.jsx`; there is no test for `SuggestionCard` | Extend these, and add `SuggestionCard.test.jsx` |

## Design Decisions

- **D1 — What an admin can reopen.**
  - **REJECTED → PENDING** is always allowed.
  - **APPROVED → PENDING** is allowed only when its import job is `FAILED` or `CANCELLED`. Any other job state, or a missing job, gets 409 `error.suggestion.cannotReopen`. This prevents a live video or a running download from sitting behind a "pending" suggestion. Re-approving then creates a fresh import job.
  - **PENDING** gets 409 `error.suggestion.alreadyPending`.
  - **Race with an import retry** (`POST /admin/video-imports/{id}/retry` → `VideoImportServiceImpl.retry` `:222` → `VideoImportJobRepository.retryIfFinal` `:97`, a conditional `UPDATE … WHERE status IN ('FAILED','CANCELLED')`): reopen locks the suggestion row (as `lockPending`), **then locks the job row** with a new `@Lock(PESSIMISTIC_WRITE) findLockedById`. It re-reads the job status **under that lock** and only then detaches the job (`importJobId = null`). A retry that runs at the same time blocks on the job row:
    - **Retry commits first:** reopen sees the job as active and returns 409 `cannotReopen`.
    - **Reopen commits first:** the retry still runs on the now-detached job. The suggestion stays PENDING. A later approve then hits the existing `BLOCKING_IMPORT_STATUSES` check in `createResolved` and returns 409 `error.import.duplicateActive`, so the system **never creates a second job or a duplicate video**. The admin can then reject it as DUPLICATE. This is accepted behavior: a retry after reopen is an explicit admin action on the import tab.
- **D2 — Reopen clears the decision.** `rejectReason`, `rejectNote`, `reviewedBy`, `reviewedAt`, `importJobId` are set to null and `status` to PENDING. The trail stays in `@AuditLog` on both endpoints, and the old import job row is kept.
- **D3 — User re-review request.**
  - Only the **owner** of a **REJECTED** suggestion can request one. Not the owner → 404, so other users' ids are not revealed. Not REJECTED → 409 `error.suggestion.notRejected`.
  - Each suggestion allows at most **2** user re-review requests: new column `rereview_count INT NOT NULL DEFAULT 0`. Over the limit → 409 `error.suggestion.rereviewLimit`.
  - The user may add an optional message. It comes in a new `RereviewSuggestionRequest` DTO (`dto/suggestion/`, like `SubmitSuggestionRequest.java:18`) with `@Size(max = 300)`, and the controller takes `@Valid @RequestBody(required = false)`. The service applies the same handling as submit's note (`VideoSuggestionServiceImpl.java:124`): trim, and a blank becomes null. The message **replaces** `note`, so the admin sees why the user is asking again. The UI renders it as React text only, with no `dangerouslySetInnerHTML` (CWE-79). A test checks that markup is shown as literal text.
  - The cap is enforced in SQL so two concurrent calls cannot both pass. The update is `… SET rereview_count = rereview_count + 1, status='PENDING' … WHERE id=:id AND user_id=:uid AND status='REJECTED' AND rereview_count < 2` **after** the suggestion lock. 0 rows updated → the service tells 404, 409 notRejected and 409 limit apart by re-reading the row.
  - Anti-abuse (CWE-770): 2 requests per suggestion, plus `@RateLimited(10)` per user on the endpoint. The daily submit quota already limits how many suggestions a user can create. Together these keep re-review traffic on the order of the user's own rejected suggestions.
  - The endpoint has `@RateLimited(permit=10)` + `@AuditLog`. It does not count toward the daily submit quota.
  - An admin reopen does **not** increase `rereview_count`.
- **D4 — Duplicates on reopen.** Both paths run the `submit` duplicate check: another PENDING suggestion for the URL, or an import job in `BLOCKING_IMPORT_STATUSES`. They also map `DataIntegrityViolationException` from the partial unique index to 409 `error.suggestion.duplicate`. Both paths take the PESSIMISTIC_WRITE lock.
- **D5 — No new notification type (accepted).** Neither path sends a notification: an admin reopen tells nobody, and a user re-review request does not notify admins. The admin pending-count badge already shows the suggestion again. The next approve or reject sends the existing notification.
- **D6 — DTO.** `SuggestionDto` gains `rereviewCount` and `canRequestRereview`. `canRequestRereview` is true when the status is REJECTED and the count is < 2, so the UI does not repeat the rule. The admin DTO gains `canReopen`, which applies the D1 rule on the server. For APPROVED rows the job statuses come from **one batched query** per page (`findAllById(jobIds)` → map), the same approach as the existing live-ref lookup (`VideoSuggestionRepository.findLiveRefs` `:91`). There is no per-row query (no N+1).

## Tasks

| ID | Task | Acceptance criteria | Depends on |
|----|------|---------------------|------------|
| **BE-1** | Migration `202610190001-suggestion-rereview-count.sql` (`rereview_count`); service `reopen(id, adminId)` + `requestRereview(id, userId, message)`; DTO fields; i18n keys (`messages*.properties`) | Unit tests:<br>• reopen REJECTED → PENDING with fields cleared<br>• APPROVED + FAILED/CANCELLED job → PENDING and the job is detached; APPROVED + DONE/active job → 409 cannotReopen<br>• the job status is read through `findLockedById` (verify the call)<br>• retry after reopen: detached job active again → approve gives 409 `error.import.duplicateActive`, no second job<br>• PENDING → 409; duplicate pending/blocking job → 409 duplicate<br>• owner rereview OK and count +1; 3rd request → 409 limit; non-owner → 404; not REJECTED → 409<br>• message is trimmed, blank → null<br>• admin list `canReopen` comes from a single batched job lookup (one repository call per page)<br><br>Migration: existing rows get `rereview_count = 0` (`NOT NULL DEFAULT 0`) | — |
| **BE-2** | Endpoints `POST /admin/video-suggestions/{id}/reopen` (role + `@HasPermission(ADMIN)`, `@AuditLog`) and `POST /suggestions/{id}/rereview` (`{message?}`, `@RateLimited(10)`, `@AuditLog`, 401 without a user); `api/Mock-Test.http` unchanged (writes are not smoke-tested) | Controller tests: 200 paths, 401 anonymous on rereview, 403 without ADMIN on reopen, message > 300 → 400 (`@Valid`), error codes passed through | BE-1 |
| **FE-1** | Admin tab: Reopen button when `canReopen`, confirmed with `useConfirm` (`hooks/useConfirm.jsx`, used in `DriveSyncPanel.jsx:61-83`); `suggestionsApi.reopen`; `useReopenSuggestion` via `useSuggestionMutation`; vi/en keys | Test: button only on rows with `canReopen`; confirm → POST → list invalidated; 409 shows the translated error | BE-2 |
| **FE-2** | My suggestions: "Request re-review" on cards with `canRequestRereview`, small modal with optional message (≤ 300); `suggestionsApi.requestRereview`; hook invalidates `['suggestions']`; vi/en keys; the card shows "Re-review requested n/2" | New `SuggestionCard.test.jsx`: action only when `canRequestRereview`; submit sends the message; error is shown; i18n parity passes | BE-2 |
| **QA-1** | Owner on prod after deploy: reject one suggestion → user requests re-review → admin sees it in Pending → approve. Also reopen a rejected one from the admin tab | Done by the owner (prod writes are not run by the agent) | all |

Order: BE-1 → BE-2 → FE-1 and FE-2 in parallel → QA-1. Each unit can be committed to `main` on its own.

## Risks

| Risk | Mitigation |
|------|------------|
| A user spams re-review requests | Cap of 2 per suggestion + `@RateLimited(10)` |
| Reopening an approved suggestion orphans a running import | D1 allows it only for FAILED or CANCELLED jobs, checked on the server |
| Race between two admins (reopen vs approve) | Same PESSIMISTIC_WRITE lock as approve/reject |
| Race between reopen and an import retry | Job row locked and re-read under lock (D1); a retry after reopen ends in 409 duplicateActive on approve, never a second job |
| Concurrent re-review requests | Conditional UPDATE with `rereview_count < 2` (D3) |
