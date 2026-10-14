# Plan: Video Suggestions (the "+" button)

<!--
Implementation Plan
Filename: docs/plans/plan-video-suggestions.md
Owner: Builder (/swarm-execute)
Handoff to: Builder, QA Engineer
-->

## Overview

**Status:** Approved (2026-10-15). Defaults are accepted: 5 per day, approval starts the download immediately, and the suggester is shown as a masked email.
**Author:** nguyenhuuca
**Date:** 2026-10-15
**Related PRD:** [PRD-video-suggestions](../prd/PRD-video-suggestions.md)
**Builds on:** ADR-0019 (import), ADR-0017 (notifications), ADR-0020 (i18n)

No new ADR is needed: the change is additive (a new table, new notification types, one post-save event) and easy to reverse.

## Technical Approach

### Data: one migration, `202610150002-create-video-suggestions.sql`
```sql
CREATE TABLE video_suggestions (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_url     TEXT         NOT NULL,
    normalized_url TEXT         NOT NULL,
    platform       VARCHAR(16)  NOT NULL,                 -- YOUTUBE | FACEBOOK (same enum as import)
    title          VARCHAR(200),
    note           VARCHAR(300),
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    reject_reason  VARCHAR(32),
    reject_note    VARCHAR(300),
    reviewed_by    BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at    TIMESTAMPTZ,
    import_job_id  BIGINT       REFERENCES video_import_jobs(id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_video_suggestions_pending_url ON video_suggestions(normalized_url) WHERE status = 'PENDING';
CREATE INDEX idx_video_suggestions_status ON video_suggestions(status, created_at DESC);
CREATE INDEX idx_video_suggestions_user   ON video_suggestions(user_id, created_at DESC);
CREATE INDEX idx_video_suggestions_job    ON video_suggestions(import_job_id) WHERE import_job_id IS NOT NULL;
```

### Backend
- **Model:** `entity/VideoSuggestion`, `enums/SuggestionStatus` and `enums/SuggestionRejectReason` (DUPLICATE, INAPPROPRIATE, LOW_QUALITY, COPYRIGHT, OTHER), plus `repo/VideoSuggestionRepository`.
- **`service/VideoSuggestionService` and its impl.**

  User-facing:
  - **`submit(url, title, note)`**
    1. Parse the URL with `ImportUrlValidator.parse` (shared allowlist and normalization).
    2. Enforce the daily limit: `countByUserIdAndCreatedAtAfter(now − 24h) ≥ app.video-suggestion.daily-limit` (default 5) gives 429 `error.suggestion.dailyLimit`.
    3. Reject duplicates with 409 `error.suggestion.duplicate` when either:
       - a suggestion for the same `normalizedUrl` is pending, or
       - an import job for that URL exists with status in ACTIVE ∪ {DONE}.
    4. Save. The partial unique index is authoritative: `DataIntegrityViolationException` also maps to 409.
  - **`mine(status, page, size)`** returns the user's own suggestions, newest first, with an optional status filter and a size clamped to 50. Each has `liveVideoId` when its job's `drive_file_id` exists in `video_sources`. The modal uses page 0 with size 5, and History uses pages of 20.

  Admin:
  - **`list(status, page)`** lists suggestions, pending first.
  - **`approve(id, titleOverride)`**
    1. Require import to be enabled, otherwise 503 `error.import.disabled`.
    2. Lock the suggestion row and require PENDING, otherwise 409.
    3. Call `VideoImportService.create(single item, scheduledAt = now, adminId)`. If that line result is an error (for example a duplicate active job), return 409 with the import's code.
    4. Set APPROVED, `import_job_id`, `reviewed_*`.
    5. Publish `SuggestionReviewedEvent`.
  - **`reject(id, reason, note)`** requires a note when the reason is OTHER, sets REJECTED, and publishes `SuggestionReviewedEvent`.
- **Controllers:**
  - `VideoSuggestionController` `/suggestions`, authenticated:
    - `POST` with `@RateLimited(permit=10)` and `@AuditLog`.
    - `GET /mine?status&page&size` with `@RateLimited(60)`.
  - `AdminVideoSuggestionController` `/admin/video-suggestions`, with `@HasPermission(ADMIN)` like the import controller:
    - `GET ?status&page`
    - `POST /{id}/approve`
    - `POST /{id}/reject`
    - `GET /pending-count` for the tab badge.
- **"Live" hook:**
  - `VideoStorageServiceImpl.saveInfo`, after save, publishes `VideoIngestedEvent(driveFileId, videoSourceId, videoId)`.
  - `SuggestionLiveListener` (`AFTER_COMMIT` + `@Async`, like the notification listeners) does the following:
    1. Finds the import job by `drive_file_id`.
    2. Finds the suggestion by `import_job_id`.
    3. Notifies `SUGGESTION_LIVE` with `videoId`.
  - A new video is PUBLISHED and visible by default, so "live" is accurate.
- **Notifications:**
  - `NotificationType` gains `SUGGESTION_APPROVED`, `SUGGESTION_REJECTED` and `SUGGESTION_LIVE`. The type column is VARCHAR(30) with no CHECK, so no migration is needed.
  - The group key is `suggestion:<id>:<type>`, so they never collapse with each other.
  - The payload holds `{ title, reason }`.
  - The digest gets new `mail.digest.item.suggestion*` keys in both properties files.
- **i18n:** new `error.suggestion.*` keys in both properties files; the parity test keeps them in sync.
- **Config:** `app.video-suggestion.daily-limit: ${VIDEO_SUGGESTION_DAILY_LIMIT:5}`.

### Frontend
- **`api/suggestions.js`** and **`hooks/useSuggestions.js`**:
  - `useSubmitSuggestion` and `useMySuggestions`.
  - Admin: `useAdminSuggestions(status, page)`, `useApproveSuggestion`, `useRejectSuggestion` and `useSuggestionPendingCount`.
- **`components/suggestions/SuggestVideoModal.jsx`** (namespace `suggestions`):
  - Fields: URL, title, note. Client-side check that the URL is HTTPS YouTube or Facebook.
  - Errors map from `err.code`.
  - A "Đề xuất gần đây" list shows the latest 5 with status chips. A live item has a "Xem" button that opens `?v=`. A "Xem tất cả" link opens History.
  - Uses `aria-busy` on submit.
- **AppShell:**
  - The FAB and the mobile FAB open `SuggestVideoModal` for users. For admins they navigate to Admin with `initialTab='IMPORT_VIDEO'`.
  - Remove the header upload icon and `ShareModal`.
  - Rename `shareAVideo` to `suggestVideo`.
- **Admin:**
  - New `SUGGESTIONS` tab in `AdminView`, with a pending-count badge.
  - `AdminSuggestionTable.jsx`: link (opens in a new tab), title, note, suggester, time, status. Approve uses a title override and a ConfirmModal. Reject uses a reason select and a note, reusing the pattern of the comment `RemoveModal`.
- **History page:** `components/history/HistoryView.jsx` replaces the `ComingSoon` placeholder for the `history` nav item. It is built so watch history (plan-watch-history-opus) can become another section later.
  - Section "Đề xuất của tôi" / "My suggestions" (`MySuggestionsSection.jsx`, namespace `history`) shows the full list with status filter tabs (Tất cả / Chờ duyệt / Đã duyệt / Bị từ chối / Đã lên app) and paging with "Xem thêm".
  - Each row shows the link, title, note, date, status chip and reject reason. A live row has a "Xem" button.
  - Logged out, it shows a sign-in hint.
- **Notifications:** `notificationText.js` adds the three types. Clicking `SUGGESTION_LIVE` plays the video (`?v=`); the other two open History → My suggestions.
- **Clean up:**
  - Delete `components/modals/ShareModal.jsx` and `videosApi.share`/`delete`.
  - Remove the share keys from `shell.json`.
  - Update `webapp/CLAUDE.md`.

## Tasks

| ID | Task | Est. | Depends on | Acceptance criteria |
|----|------|------|-----------|---------------------|
| BE-1 | Migration, entity, enums, repository, `VideoSuggestionService` (submit, mine, list, approve, reject) and config | 1.5d | — | Unit tests cover:<br>• invalid URL 400<br>• duplicate pending 409<br>• duplicate import 409<br>• 6th suggestion in 24 h 429<br>• approve creates exactly one import job and links it<br>• double approve 409<br>• approve with import disabled 503<br>• reject OTHER without a note 400<br>• `mine` shows `liveVideoId` once ingested |
| BE-2 | Controllers (user and admin), permissions, rate limits and WebMvc tests | 0.5d | BE-1 | Guests get 401. A non-admin calling an admin endpoint gets 403. vi and en messages carry `code`. Coverage ≥ 90%. |
| BE-3 | Notifications: three types, `SuggestionReviewedEvent`, `VideoIngestedEvent` from `saveInfo`, `SuggestionLiveListener`, and digest text in vi and en | 1d | BE-1 | Approve and reject each create one notification for the suggester. Ingesting the Drive file of an approved suggestion's job creates `SUGGESTION_LIVE` with `videoId`. An ingest with no matching suggestion does nothing. The digest renders the three types in both languages. |
| FE-1 | `SuggestVideoModal`, API and hooks, AppShell FAB rewiring (users get the modal, admins get Admin → Import), removal of `ShareModal`, `videosApi.share`/`delete` and the header upload icon | 1d | BE-2 contract | Submitting shows the item as "Chờ duyệt" in the recent list. Duplicate and limit errors are translated. An admin's FAB opens the Import tab. There are no ShareModal references left. Tests run in vi with en render tests. |
| FE-2 | Admin `SUGGESTIONS` tab: table, filter, approve (title override), reject (reason and note), pending badge | 1d | BE-2 contract | Approve moves the row to "Đã duyệt" and a job appears in the Import tab. Reject requires a reason, and a note for OTHER. The badge count updates. Tests pass. |
| FE-3 | Notification rendering for the three types and their click behaviour | 0.5d | FE-1, BE-3, FE-4 | Titles are translated in vi and en. LIVE opens the video. APPROVED and REJECTED open History → My suggestions, where REJECTED shows the reason. |
| FE-4 | History page (`HistoryView`) replaces `ComingSoon`, with a "My suggestions" section: status filter, paging, live "Xem", reject reason, logged-out hint | 0.5d | BE-2 contract | The History nav shows the user's suggestions with working filters and paging. A live item plays its video. A guest sees the sign-in hint. Tests run in vi with an en render test. |
| QA-1 | Local end-to-end and prod smoke | 0.5d | all | A user suggests a link and an admin approves it. The job runs, the cron ingests the video, the user gets three notifications (approved, live, plus the digest email), and the video plays from the LIVE notification. A 6th suggestion in a day is blocked. A reject shows its reason. |

The total is about 6.5 days, which makes this a Medium feature (PRD and plan).

## Dependency Graph

```
BE-1 → BE-2 ─┬→ FE-1 ─┬→ FE-3 ─┐
             ├→ FE-4 ─┤        ├→ QA-1
             └→ FE-2 ─┘        │
BE-1 → BE-3 ───────────────────┘
```

Once BE-2 has fixed the API contract, FE-1, FE-2 and FE-4 run in parallel, alongside BE-3.

## Beads

`bd` is not installed in this repo, so tasks are tracked in this table and in `docs/tracking.md`. When `bd` is available:

```bash
bd create --title="Video suggestions (+ button)" --type=feature --priority=2
bd create --title="[BE-1] video_suggestions table + service" --type=task
bd create --title="[BE-2] Suggestion controllers + permissions" --type=task
bd create --title="[BE-3] Suggestion notifications + ingest hook" --type=task
bd create --title="[FE-1] SuggestVideoModal + FAB rewiring, remove ShareModal" --type=task
bd create --title="[FE-2] Admin suggestions tab" --type=task
bd create --title="[FE-3] Suggestion notifications UI" --type=task
bd create --title="[FE-4] History page + My suggestions" --type=task
bd create --title="[QA-1] E2E + prod smoke" --type=task
# deps: BE-2←BE-1, BE-3←BE-1, FE-1←BE-2, FE-2←BE-2, FE-4←BE-2, FE-3←FE-1+FE-4+BE-3, QA-1←all
```

## Risks

| Risk | Mitigation |
|------|------------|
| `VIDEO_IMPORT_ENABLED` is false on prod, so approvals fail | Approve returns a clear 503, and submissions still queue. Enabling import is an owner decision, and its default stays false. |
| Spam, or links to inappropriate content | Login is required, the daily limit is 5, admins approve every suggestion, and the reject reasons are logged with `@AuditLog`. |
| The "live" event is missed (async after commit, JVM restart) | `mine()` also computes `liveVideoId` from the DB, so the status stays correct even if a notification is lost. |
| Concurrent approve of the same suggestion | Row lock plus a PENDING check. The import's partial unique index blocks duplicate active jobs. |
| The extra event changes the cron ingest | It is publish-only. The listener runs after commit and asynchronously, so ingest latency and failure modes stay the same. |

## Rollback

- **FE:** revert. The FAB falls back to the previous behaviour, and the deleted ShareModal comes back with the revert.
- **BE:** the endpoints and the event are additive. The table can stay, or be dropped with `DROP TABLE video_suggestions`. The notification types are only enum values.

## Progress Log

| Date | Note |
|------|------|
| 2026-10-15 | Owner asked for a "My suggestions" section in History, so FE-4 was added. The `history` nav item becomes a real page instead of the ComingSoon placeholder. |
| 2026-10-15 | Plan created from a parallel exploration of the import pipeline, the notifications system and the FE "+" and admin UI. |
