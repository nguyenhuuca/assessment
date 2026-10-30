# Feature Specification: Collection-Only Videos

<!--
Feature Specification
Filename: docs/specs/spec-collection-only-videos.md
Owner: Builder (/builder) or Architect (/architect)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
-->

## Metadata

**Status:** Approved
**Author:** nguyenhuuca
**Date:** 2026-10-30
**Related PRD:** [PRD-collection-only-videos](../prd/PRD-collection-only-videos.md)
**Related ADR:** [ADR-0035](../adr/0035-collection-only-videos.md)
**Sequence diagrams:** [sequence-collection-only-videos](../sequence/sequence-collection-only-videos.md)

---

## Overview

Every video gets one `visibility` value, `FEED`, `COLLECTION_ONLY` or `HIDDEN`, which replaces `video_sources.is_hide`. Only `FEED` videos are in the feed (and therefore in feed search, the Explore hero and the Library "Add videos" picker). `COLLECTION_ONLY` videos still play everywhere a video is opened on purpose: collections, watch history, direct links and notifications. The admin sets visibility on the Admin videos page or when creating an import.

---

## Business Rules

### Rule 1: Three exclusive values

`visibility` is exactly one of `FEED`, `COLLECTION_ONLY`, `HIDDEN`, never null. The DB enforces it with a CHECK constraint.

### Rule 2: Feed rule

A video is in the feed when `visibility = 'FEED' AND status = 'PUBLISHED'`.

### Rule 3: Playable rule

A video can be listed in a collection, returned by `GET /video-stream/{id}`, counted as a view, reacted to, recorded in or listed from watch history, and served as HLS when `visibility <> 'HIDDEN' AND status = 'PUBLISHED'`. Every place that today checks `is_hide = false` uses this rule, except the feed (Rule 2).

### Rule 4: Migration mapping

Existing rows map `is_hide = true` to `HIDDEN` and `is_hide = false` to `FEED`. No video changes behaviour on deploy.

### Rule 5: Import visibility

An import request carries one `visibility` for all its items: `FEED` (default when absent or null) or `COLLECTION_ONLY`. `HIDDEN` is rejected. The value is stored on every `video_import_jobs` row of that request.

### Rule 6: Visibility at row creation

When a Drive file becomes a `video_sources` row (scheduler `saveInfo`, or Drive force sync `upsertInfo` INSERT branch), the row gets the visibility of the import job whose `drive_file_id` matches. With no matching job, it gets `FEED`.

### Rule 7: Existing rows keep their visibility

If a `video_sources` row already exists for the Drive file (re-import, re-sync), its `visibility` is not changed. This matches today's handling of `is_hide`, `status` and `priority`.

### Rule 8: Retry keeps visibility

Retrying a FAILED or CANCELLED import job reuses the job row, so its `visibility` is kept.

### Rule 9: Admin change takes effect on the next feed request

Every visibility change through the admin endpoint invalidates `FeedCache`.

### Rule 10: Collection items are never deleted by visibility

Changing a video to `HIDDEN` does not delete its collection items; they are filtered out (Rule 3) and reappear when the video is no longer `HIDDEN`.

> **Assumption:** Rule 10 keeps today's `is_hide` behaviour. The owner did not ask for a change.

---

## Functional Requirements

### FR-1: Schema

- `video_sources` must have `visibility VARCHAR(20) NOT NULL DEFAULT 'FEED'` with the Rule 1 CHECK, and must no longer have `is_hide`.
- `video_import_jobs` must have `visibility VARCHAR(20) NOT NULL DEFAULT 'FEED'` with a CHECK limited to `FEED` and `COLLECTION_ONLY`.

### FR-2: Domain model

- A new enum `VideoVisibility { FEED, COLLECTION_ONLY, HIDDEN }` in `entity/`.
- `VideoSource.isHide` becomes `VideoSource.visibility` (`@Enumerated(EnumType.STRING)`, default `FEED`).
- `VideoImportJob` gets the same field, default `FEED`.

### FR-3: Feed

- `StreamVideoServiceImpl.getVideosToStream` must load `findAllByVisibilityAndStatusOrderByPriorityDescCreatedAtDesc(FEED, PUBLISHED)`, replacing `findAllByIsHideAndStatus...`.
- The query must use the recreated partial index `idx_video_sources_feed`.

### FR-4: Playable checks

Each of these must switch from `is_hide = false` / `!isHide()` to Rule 3:

| Location | Today |
|----------|-------|
| `VideoCollectionItemRepository.VISIBLE` (and its Javadoc) | `v.is_hide = false AND v.status = 'PUBLISHED'` |
| `VideoSourceRepository.existsByIdAndIsHideAndStatus`, used by `VideoCollectionServiceImpl` (~:209) and `VideoReactionServiceImpl` (~:59) | `(id, false, PUBLISHED)` |
| `VideoSourceRepository.incrementViewCount` | `AND is_hide = false` |
| `WatchHistoryRepository` (~:52, ~:86, ~:94) | `v.is_hide = false` |
| `StreamVideoServiceImpl.getVideoById` (~:100) | `!v.isHide()` |
| `HlsPlaybackServiceImpl.findPlaylist` (~:44) | `!v.isHide()` |

The replacement for `existsByIdAndIsHideAndStatus` is `existsByIdAndVisibilityNotAndStatus(id, HIDDEN, PUBLISHED)`.

### FR-5: Admin visibility endpoint

- `PATCH /admin/videos/{id}/visibility` must accept `{"visibility": "FEED" | "COLLECTION_ONLY" | "HIDDEN"}`.
- For one release it must also accept the legacy `{"hidden": true|false}`, mapped to `HIDDEN|FEED`.
- It must call `feedCache.invalidate()`.

### FR-6: Admin list

`GET /admin/videos` items must include `visibility`, and for one release the derived `hidden` (`visibility == HIDDEN`).

### FR-7: Import

- `POST /admin/video-imports` must accept an optional top-level `visibility` (Rule 5) and store it on each created job.
- `VideoImportJobDto` must include `visibility`.
- Jobs created from approved suggestions must get `FEED`.

### FR-8: Row creation

- `VideoStorageServiceImpl.saveInfo` must set the builder's `visibility` from `videoImportJobRepository.findFirstByDriveFileId(fileId)` (Rule 6).
- `VideoStorageServiceImpl.upsertInfo` must pass that same visibility to `upsertDriveSource`. Its INSERT branch writes the value, and its `ON CONFLICT` branch must not touch `visibility` (Rule 7).

### FR-9: Admin UI

The Admin videos table must:
- replace the hidden checkbox with a three-state control (Feed / Collection only / Hidden);
- show a badge for `COLLECTION_ONLY` and keep the row style for `HIDDEN`;
- offer an "Add to collection" row action that opens `AddToCollectionModal` for that video.

### FR-10: Import UI

- `ImportForm` must have a "Collection only" checkbox, off by default, that sends `visibility: "COLLECTION_ONLY"` when checked.
- `ImportTable` must show a badge on `COLLECTION_ONLY` jobs.

### FR-11: Deep-link fallback

When `PublicFeed` receives a `deepLinkId` that is not in the feed list, it must:
- fetch `GET /video-stream/{id}`;
- insert the result at index 0 of the feed it renders;
- start there, applying `deepLinkStartAt` if set;
- leave the rest of the feed unchanged.

If the fetch fails (400, hidden or unknown video), it must show the feed from `initialIndex ?? 0`, as today when an id is not found (there is no not-found message today, and none is added). In both outcomes it must call `onDeepLinkResolved()`, so `AppShell` clears `deepLinkId` and the lookup does not re-run on every render (today a missing id is never resolved). This applies to `?v=` links, watch-history play and notification open, which all set `deepLinkId` (`AppShell.jsx` ~:125-137).

### FR-12: Notifications

`SUGGESTION_LIVE` and other notifications that carry a `videoId` must keep linking to a `COLLECTION_ONLY` video. FR-11 opens it, so no notification code changes.

### FR-13: Compatibility cleanup (follow-up)

After the new frontend is live, a follow-up removes the legacy `{hidden}` body and the `hidden` DTO field.

---

## API Changes

All paths are under `/v1/funny-app`.

### New Endpoints

None.

### Modified Endpoints

#### PATCH /admin/videos/{id}/visibility

**Change:** takes a visibility value instead of a boolean. The legacy body is accepted for one release.

**Auth:** Required (JWT Bearer), class-level `hasRole('ADMIN')` (unchanged). `@AuditLog("updateVideoVisibility")` (unchanged).
**Rate limit:** None (unchanged).

**Before:**
```json
{ "hidden": true }
```

**After:**
```json
{ "visibility": "COLLECTION_ONLY" }
```

**Response — Success** (unchanged)
```json
{ "status": "SUCCESS", "message": "Visibility updated" }
```

**Response — Error**

| HTTP Status | Code | Message | Condition |
|-------------|------|---------|-----------|
| 400 | — | `visibility or hidden is required` | Body is null, or has neither field |
| 400 | — | `send either visibility or hidden, not both` | Body has both fields |
| 400 | — | `invalid visibility` | `visibility` is not one of the three values |
| 400 | — | `Video not found: {id}` | Unknown id (`IllegalArgumentException`, unchanged behaviour) |
| 401 | `TOKEN_INVALID` / `TOKEN_EXPIRED` | — | Missing or invalid JWT (unchanged) |
| 403 | — | Access denied | Caller is not ADMIN or SUPER_ADMIN (unchanged) |

> **Assumption:** the error codes stay on today's `Contract.require` / `IllegalArgumentException` path (400, no subcode), as for `/priority`. The spec does not introduce a new error style for this controller.

#### GET /admin/videos

**Change:** each `AdminVideoDto` gains `visibility`. `hidden` stays, derived, for one release.

**Before:**
```json
{ "id": 12, "title": "…", "hidden": false, "priority": 0, "status": "PUBLISHED" }
```

**After:**
```json
{ "id": 12, "title": "…", "visibility": "COLLECTION_ONLY", "hidden": false, "priority": 0, "status": "PUBLISHED" }
```

#### POST /admin/video-imports

**Change:** `CreateVideoImportRequest` gains an optional top-level `visibility`.

**Before:**
```json
{ "items": [{ "url": "https://…", "title": "…" }], "scheduledAt": null }
```

**After:**
```json
{ "items": [{ "url": "https://…", "title": "…" }], "scheduledAt": null, "visibility": "COLLECTION_ONLY" }
```

**Response — Error:** existing errors (`IMPORT_DISABLED` 503/4100, `EMPTY_ITEMS` 400/4101, `TOO_MANY_ITEMS` 400/4102, `INVALID_SCHEDULE` 400/4103, per-line `INVALID_URL` / `DUPLICATE_ACTIVE`) unchanged, plus:

| HTTP Status | Code | Message | Condition |
|-------------|------|---------|-----------|
| 400 | subCode 4104 | `INVALID_VISIBILITY` | `visibility` is `HIDDEN` or not a known value |

#### GET /admin/video-imports

**Change:** each `VideoImportJobDto` gains `visibility`.

#### GET /video-stream/list, GET /video-stream/{id}, collection endpoints, watch history endpoints

**Change:** none in the contract. Results follow Rules 2 and 3. `VideoDto` does not expose `visibility`.

---

## Database Changes

### New Tables

None.

### Modified Tables

| Table | Change | Column | Type | Notes |
|-------|--------|--------|------|-------|
| `video_sources` | Add column | `visibility` | `VARCHAR(20)` | `NOT NULL DEFAULT 'FEED'`, CHECK `FEED/COLLECTION_ONLY/HIDDEN` |
| `video_sources` | Backfill | `visibility` | — | `HIDDEN` where `is_hide = true` |
| `video_sources` | Recreate index | `idx_video_sources_feed` | — | Predicate `visibility = 'FEED' AND status = 'PUBLISHED'` |
| `video_sources` | Drop column | `is_hide` | — | After backfill and index |
| `video_import_jobs` | Add column | `visibility` | `VARCHAR(20)` | `NOT NULL DEFAULT 'FEED'`, CHECK `FEED/COLLECTION_ONLY` |
| `video_import_jobs` | Add index | `drive_file_id` | — | Partial, `WHERE drive_file_id IS NOT NULL`; used by `findFirstByDriveFileId` at row creation |

> Migration file: `api/src/main/resources/db/changelog/sql/202610300001-video-visibility.sql`

The changelog uses `includeAll` with plain SQL files, which cannot carry a rollback. This file uses Liquibase formatted SQL so it can carry one (first formatted file in the repo):

```sql
--liquibase formatted sql

--changeset nguyenhuuca:202610300001-video-visibility
-- One visibility value per video (ADR-0035): FEED, COLLECTION_ONLY or HIDDEN replaces is_hide.
ALTER TABLE video_sources
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'FEED';
UPDATE video_sources SET visibility = 'HIDDEN' WHERE is_hide = true;
ALTER TABLE video_sources
    ADD CONSTRAINT chk_video_sources_visibility
        CHECK (visibility IN ('FEED', 'COLLECTION_ONLY', 'HIDDEN'));

-- Feed reads only FEED rows (replaces the is_hide predicate from 202610240002).
DROP INDEX IF EXISTS idx_video_sources_feed;
CREATE INDEX idx_video_sources_feed
    ON video_sources (priority DESC, created_at DESC)
    WHERE visibility = 'FEED' AND status = 'PUBLISHED';

ALTER TABLE video_sources DROP COLUMN is_hide;

-- Visibility chosen in the Import form, copied onto video_sources when the row is created.
ALTER TABLE video_import_jobs
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'FEED';
ALTER TABLE video_import_jobs
    ADD CONSTRAINT chk_video_import_jobs_visibility
        CHECK (visibility IN ('FEED', 'COLLECTION_ONLY'));

-- saveInfo / upsertInfo look the job up by its Drive file id.
CREATE INDEX idx_video_import_jobs_drive_file_id
    ON video_import_jobs (drive_file_id)
    WHERE drive_file_id IS NOT NULL;

--rollback DROP INDEX IF EXISTS idx_video_import_jobs_drive_file_id;
--rollback ALTER TABLE video_import_jobs DROP CONSTRAINT chk_video_import_jobs_visibility;
--rollback ALTER TABLE video_import_jobs DROP COLUMN visibility;
--rollback ALTER TABLE video_sources ADD COLUMN is_hide BOOLEAN NOT NULL DEFAULT false;
--rollback UPDATE video_sources SET is_hide = (visibility = 'HIDDEN');
--rollback DROP INDEX IF EXISTS idx_video_sources_feed;
--rollback CREATE INDEX idx_video_sources_feed ON video_sources (priority DESC, created_at DESC) WHERE is_hide = false AND status = 'PUBLISHED';
--rollback ALTER TABLE video_sources DROP CONSTRAINT chk_video_sources_visibility;
--rollback ALTER TABLE video_sources DROP COLUMN visibility;
```

> **Assumption:** a rollback maps `COLLECTION_ONLY` back to `is_hide = false`, so those videos would reappear in the feed. That is acceptable for an emergency rollback.

---

## Security Requirements

### Authentication

Unchanged. Admin endpoints need a JWT. `/video-stream/**` stays public.

### Authorization

- Only ADMIN and SUPER_ADMIN (class-level `hasRole('ADMIN')` on `AdminController` and `AdminVideoImportController`) can change visibility or set it on an import.
- `COLLECTION_ONLY` is not access control. Anyone with a collection link, a `?v=` link or a stream URL can play the video. The admin control tooltip must say so.

### Data Validation

| Field | Rule | Error |
|-------|------|-------|
| `visibility` (PATCH) | One of `FEED`, `COLLECTION_ONLY`, `HIDDEN`; mutually exclusive with `hidden` | 400 |
| `hidden` (PATCH, legacy) | Boolean; mutually exclusive with `visibility` | 400 |
| `visibility` (import) | Null, `FEED` or `COLLECTION_ONLY` | 400 subCode 4104 `INVALID_VISIBILITY` |

### Sensitive Data

None. `@AuditLog` on `/visibility` records the id and body, which hold no secrets.

---

## Caching Impact

| Cache | Impact | Action |
|-------|--------|--------|
| `FeedCache` | Invalidated | On every admin visibility change (already done today in `updateHidden`). `saveInfo` and `upsertInfo` already invalidate after creating a row. |
| `VideoCollectionServiceImpl` `publicCache` (30 s) | Reads | No explicit eviction. A visibility change reaches public collection lists within 30 s, as today for `is_hide`. |
| React Query `['videos', category]` (staleTime 5 min) | Reads | The admin mutation already invalidates the admin list. The feed in other browsers refreshes on its stale time, as today. |

---

## Frontend Changes

### New Routes

None.

### Modified Components

| Component | File | Change |
|-----------|------|--------|
| `AdminVideoTable` | `webapp/src/components/admin/AdminVideoTable.jsx` | Three-state visibility control replaces the hidden checkbox; `COLLECTION_ONLY` badge; "Add to collection" row action |
| `AddToCollectionModal` | `webapp/src/components/library/AddToCollectionModal.jsx` | Reused from the admin table; no behaviour change expected |
| `ImportForm` | `webapp/src/components/admin/ImportForm.jsx` | "Collection only" checkbox |
| `ImportTable` | `webapp/src/components/admin/ImportTable.jsx` | `COLLECTION_ONLY` badge |
| `PublicFeed` | `webapp/src/components/video/VideoFeed.jsx` | FR-11 fallback fetch and insert at index 0 |
| admin API | `webapp/src/api/admin.js` | `updateVideoVisibility(id, visibility)` sends `{visibility}`; import create sends `visibility` |
| videos API | `webapp/src/api/videos.js` | New `get(id)` for `GET /video-stream/{id}`, next to `list` |
| i18n | `webapp/src/i18n` (vi, en) | Labels for Feed, Collection only, Hidden, the tooltip and the import checkbox |

### State Management

```javascript
// Admin visibility mutation (useAdmin.js): same keys as today
mutationFn: ({ id, visibility }) => adminApi.updateVideoVisibility(id, visibility)
onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'videos'] })

// Deep-link fallback (VideoFeed.jsx): one-off fetch, cached per id
['video', id]   // enabled only when deepLinkId is set and not found in the feed list
```

---

## Event / Job Changes

### Domain Events

| Event | Change | Schema |
|-------|--------|--------|
| `VideoIngestedEvent` | No change | Published by `saveInfo` as today |
| `SuggestionReviewedEvent` | No change | — |

### Scheduled Jobs

No new job. The existing Drive scheduler (`downloadFileFromFolder` → `saveInfo`) and the force sync (`upsertInfo`) apply Rule 6.

---

## Non-Functional Requirements

### Performance

| Operation | Target | Notes |
|-----------|--------|-------|
| `GET /video-stream/list` (cache miss) | No regression vs. today | Partial index on `visibility = 'FEED'`; check with `EXPLAIN` |
| `PATCH /admin/videos/{id}/visibility` | < 200 ms p99 | One update + cache invalidate |
| `saveInfo` / `upsertInfo` | + 1 indexed lookup | `findFirstByDriveFileId` on the new `idx_video_import_jobs_drive_file_id` |

### Availability

- Deploy needs a short backend downtime (owner-approved, ADR-0035).
- Take a DB backup before deploy.
- Rollback: `liquibase rollback` of the changeset, then redeploy the previous JAR. Redeploying the old JAR alone fails, because `is_hide` is gone.

### Scalability

No concern: `video_sources` has hundreds of rows. The feed index keeps the hot query small.

---

## Edge Cases

### EC-1: Legacy frontend after backend deploy

**Condition:** the old Admin page sends `{"hidden": true}`.
**Expected:** 200, visibility becomes `HIDDEN`. `{"hidden": false}` becomes `FEED`, even if the video was `COLLECTION_ONLY`.

### EC-2: Both fields sent

**Condition:** body `{"visibility": "FEED", "hidden": true}`.
**Expected:** 400 `send either visibility or hidden, not both`; nothing changes.

### EC-3: Import with HIDDEN

**Condition:** `POST /admin/video-imports` with `visibility: "HIDDEN"`.
**Expected:** 400 subCode 4104 `INVALID_VISIBILITY`; no job is created.

### EC-4: Re-import of an existing video

**Condition:** a `COLLECTION_ONLY` import for a Drive file that already has a `FEED` row.
**Expected:** the row stays `FEED` (Rule 7). The admin changes it on the Admin page.

### EC-5: Deep link to a COLLECTION_ONLY video

**Condition:** `?v=42`, a watch-history play or a notification open, where video 42 is `COLLECTION_ONLY`.
**Expected:** `GET /video-stream/42` returns 200. Video 42 is slide 0 and plays (from `startAt` if set). Swiping continues into the normal feed.

### EC-6: Deep link to a HIDDEN or unknown video

**Condition:** `?v=` for a `HIDDEN` or missing id.
**Expected:** `GET /video-stream/{id}` returns 400. The feed opens at `initialIndex ?? 0` with no message (unchanged from today). Nothing is inserted, and `onDeepLinkResolved()` is called.

### EC-7: Same value set again

**Condition:** PATCH with the current visibility.
**Expected:** 200; the row is saved unchanged and `FeedCache` is invalidated (today's behaviour).

> **Assumption:** EC-7 keeps today's idempotent behaviour; the owner did not ask for a no-op shortcut.

### EC-8: Video changed to HIDDEN while in collections

**Condition:** a `COLLECTION_ONLY` video in two collections becomes `HIDDEN`.
**Expected:** it disappears from both collection lists (public lists within 30 s). The items stay in the DB and reappear when it is no longer `HIDDEN` (Rule 10).

### EC-9: Concurrent admin changes

**Condition:** two admins PATCH the same video at once with different values.
**Expected:** last write wins; both calls return 200 and both are audited. No optimistic lock (unchanged from today).

### EC-10: Suggestion approved

**Condition:** an admin approves a suggestion, which creates an import job.
**Expected:** the job is `FEED`. When the video goes live, `SUGGESTION_LIVE` links to it. If the admin later sets it to `COLLECTION_ONLY`, the link still opens it through FR-11.

---

## Acceptance Criteria

- [ ] Migration maps `is_hide` true/false to `HIDDEN`/`FEED`, drops `is_hide`, adds both CHECKs, recreates `idx_video_sources_feed`, and `liquibase rollback` restores `is_hide` (Rule 4, FR-1)
- [ ] Feed returns `FEED` videos only; `COLLECTION_ONLY` and `HIDDEN` are excluded (Rule 2, FR-3)
- [ ] Collections (owner, public, `?l=`), watch history (record, list, summary), `GET /video-stream/{id}`, views, reactions and HLS accept `COLLECTION_ONLY` and reject `HIDDEN` (Rule 3, FR-4)
- [ ] No `is_hide` / `isHide` reference remains in `api/src/main` or `webapp/src`, except the migration and the legacy `hidden` compatibility code
- [ ] `PATCH /admin/videos/{id}/visibility` accepts each of the three values and the legacy `{hidden}`, and rejects both fields together, an unknown value and a missing body (FR-5, EC-1, EC-2)
- [ ] Every visibility change invalidates `FeedCache` (Rule 9)
- [ ] `GET /admin/videos` returns `visibility` and the derived `hidden` (FR-6)
- [ ] Import with `COLLECTION_ONLY` stores it on every job; `HIDDEN` returns 400/4104; the default is `FEED`; retry keeps it (Rules 5 and 8, EC-3)
- [ ] `saveInfo` and `upsertInfo` create the row with the job's visibility, or `FEED` without a job; an existing row keeps its visibility (Rules 6 and 7, EC-4)
- [ ] Admin table: three-state control, `COLLECTION_ONLY` badge, "Add to collection" adds the video to the chosen collection (FR-9)
- [ ] Import form checkbox sends `COLLECTION_ONLY`; the import table shows the badge (FR-10)
- [ ] A deep link, history play or notification for a `COLLECTION_ONLY` video plays it as slide 0; a `HIDDEN` or unknown id falls back to the feed (FR-11, EC-5, EC-6)
- [ ] Feed search, the Explore hero and the Library "Add videos" picker do not show `COLLECTION_ONLY` videos (frontend test)
- [ ] Existing flows are unaffected (regression)
- [ ] API responses match the contracts above
- [ ] `mvn verify` passes with the 90% coverage gate
- [ ] `npm run test` and `npm run lint` pass

---

## Open Questions

None blocking. Decided during `/spec` (2026-10-30):
- Deep-link fallback inserts the video at slide 0 of the feed (owner).
- `SUGGESTION_LIVE` keeps linking to `COLLECTION_ONLY` videos (owner).
- Import accepts only `FEED` / `COLLECTION_ONLY` (owner).

---

## Version History

| Version | Date | Author | Change |
|---------|------|--------|--------|
| 1.0 | 2026-10-30 | nguyenhuuca | Initial draft |
| 1.1 | 2026-10-30 | nguyenhuuca | FR-11 / EC-6: no not-found message exists today; resolve the deep link on failure too (found while planning) |
