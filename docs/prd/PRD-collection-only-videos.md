# PRD: Collection-Only Videos (hidden from Feed and Search)

<!--
Product Requirements Document
Filename: docs/prd/PRD-collection-only-videos.md
Owner: nguyenhuuca
Handoff to: Architect (/architect), UI/UX Designer (/ui-ux-designer)
Related Skills: scope, planning-artifacts
-->

## Overview

**Status:** Approved
**Author:** nguyenhuuca
**Date:** 2026-10-30
**Version:** 1.2
**Tracking:** N/A (add a `docs/tracking.md` row once approved)
**PR-FAQ:** N/A (small feature that extends existing video moderation and collections)
**Stakeholders:** nguyenhuuca (owner, admin)

## Problem Statement

Some videos in the library are pure scientific-research content. They are worth keeping and watching on purpose, but they do not fit the entertainment feed: they break the swipe flow on Home/Explore and show up in feed search.

Today an admin has only two choices per video:
- **Visible**: `is_hide = false`, so the video is in the feed, search and collections.
- **Hidden**: `is_hide = true`, so the video is gone everywhere, including collections (`VideoCollectionItemRepository.VISIBLE`).

There is no "keep it out of the feed, but let me play it from my playlist" state.

### Evidence

**Quantitative Evidence:**
- The feed (`GET /video-stream/list`, `StreamVideoServiceImpl.getVideosToStream`) returns every `is_hide = false AND status = PUBLISHED` video. Every research video the owner imports therefore lands in the feed.
- Feed search (`SearchModal.jsx`) and the Library "Add videos" picker (`AddVideosModal.jsx`) both filter the client-side feed list from `useVideos`, so they show the same set.

**Qualitative Evidence:**
- Owner request (2026-10-30): "there are videos that are purely scientific research; I only want to see them when I open the playlist; the feed and the feed's search list don't need them."

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Collection-only videos never appear in the feed | Videos flagged collection-only in `GET /video-stream/list` (automated test) | 0 |
| Collection-only videos never appear in feed search or the Explore hero | Covered by the feed test, because both read the feed list (frontend test) | 0 |
| They still play from a collection | A collection containing the video lists and plays it (owner's and public/shared link) | 100% (test) |
| The flag takes effect quickly | Time from toggling the flag to the feed reflecting it | ≤ 30 s (`FeedCache` TTL), immediate when the cache is invalidated on toggle |
| Watch history and direct links keep working | A history entry or `?v=` link for a collection-only video opens and plays it | 100% (test) |

## User Stories

### Admin (owner)

- As an admin, I want to mark a video as "collection-only" on the Admin videos page so that research videos stay out of the feed.
  - Acceptance: toggling the flag on a row updates it, the next feed load (≤ 30 s) no longer contains the video, and toggling it back returns it to the feed.
- As an admin, I want an "Add to collection" action on each Admin videos row so that I can put a collection-only video into my playlists even though it is no longer in the feed.
  - Acceptance: the action opens the existing collection picker (`AddToCollectionModal`), the video is added to the chosen collection, and the add works for collection-only videos.
- As an admin, I want a "Collection only" option in the Import form so that a research video never reaches the feed, not even for a moment after import.
  - Acceptance: with the option on, every video created by that import job is collection-only from the first row insert, the job list shows the option, and the default is off.
- As an admin, I want to see which videos are collection-only in the Admin videos table so that I can review them.
  - Acceptance: the row shows a clear badge or state, and the table can be filtered by it (Should Have).

### Viewer (any user or guest who opens a collection)

- As a viewer, I want collection-only videos to play normally inside a collection so that the playlist is complete.
  - Acceptance: an owner's collection, a public collection and a `?l=` shared link list the video and play it; Range and HLS streaming behave as for any other video.
- As a viewer, I want my watch history and "Continue watching" to include collection-only videos I watched so that I can resume them.
  - Acceptance: the history entry shows, and clicking it plays the video from the saved position although the video is not in the feed.
- As a viewer, I want a direct `?v=` link to a collection-only video to open it so that sharing still works.
  - Acceptance: the link plays the video even though the feed list does not contain it.

## Requirements

### Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | Replace `is_hide` on `VideoSource` with a `visibility` enum: `FEED`, `COLLECTION_ONLY`, `HIDDEN`. `HIDDEN` keeps today's meaning (invisible everywhere). | Must Have | ADR-0035. One Liquibase changeset in `api/src/main/resources/db/changelog/sql/` backfills `is_hide = true` to `HIDDEN` and `false` to `FEED`, then drops `is_hide`; it has a rollback block. Every former `is_hide = false` reader becomes `visibility <> 'HIDDEN'`, and the feed becomes `visibility = 'FEED'`. |
| FR-2 | The feed (`GET /video-stream/list`) excludes collection-only videos. | Must Have | Changes the `findAllByIsHideAndStatusOrderByPriorityDescCreatedAtDesc` query or adds a filter, plus a check of the `video_sources` feed index (202610240002). |
| FR-3 | Feed search, the Explore hero/Trending badge and the Library "Add videos" picker exclude collection-only videos. | Must Have | No extra code expected: all three read the `useVideos` feed list. Verify with tests. |
| FR-4 | Collection endpoints (`/collections/{id}/videos`, `/video-ids`, public collections, `?l=` links) keep returning collection-only videos. | Must Have | `VideoCollectionItemRepository.VISIBLE` becomes `visibility <> 'HIDDEN' AND status = PUBLISHED`. |
| FR-5 | Adding a collection-only video to a collection is allowed. | Must Have | `VideoCollectionServiceImpl` (~:209) checks `existsByIdAndIsHideAndStatus(false, PUBLISHED)`, which already passes. Keep it that way. |
| FR-6 | `PATCH /admin/videos/{id}/visibility` takes `{visibility}` (one of the three values), keeps its role guard and `@AuditLog`, and calls `feedCache.invalidate()`. | Must Have | ADR-0035. For one release it also accepts the legacy `{hidden}` body (mapped to `HIDDEN`/`FEED`), because the frontend deploys separately. |
| FR-7 | `GET /admin/videos` returns `visibility` (plus the derived legacy `hidden` for one release), and the Admin videos table replaces the hidden checkbox with a three-state control (Feed / Collection only / Hidden) and a badge. | Must Have | `AdminVideoTable.jsx`, `AdminVideoDto`. |
| FR-8 | "Add to collection" action on each Admin videos row, reusing `AddToCollectionModal`. | Must Have | Without it a collection-only video can only be added before the flag is set. |
| FR-9 | Deep link fallback: when `?v=`, a watch-history play or a notification asks for a video id that is not in the feed list, the client fetches `GET /video-stream/{id}` and plays it on its own. | Must Have | Today `PublicFeed` resolves `deepLinkId` only with `videos.findIndex(...)` (`VideoFeed.jsx:11-12`), so a collection-only video would silently not open. `getVideoById` accepts any non-`HIDDEN`, `PUBLISHED` video after FR-1. |
| FR-10 | Watch history keeps recording and listing collection-only videos. | Must Have | `WatchHistoryRepository` filters on `is_hide` and `status` only, so no change is expected. Add a test. |
| FR-11 | Views, reactions and comments work for collection-only videos as for any visible video. | Should Have | `incrementViewCount` and `VideoReactionServiceImpl` check `is_hide` only. |
| FR-12 | Admin table filter "collection-only" (show only flagged videos). | Nice to Have | |
| FR-13 | Import form (`ImportForm.jsx`) has a "Collection only" option, default off. `POST /admin/video-imports` accepts it per request (applies to every line of that request), it is stored on `VideoImportJob` as `visibility` (`FEED` or `COLLECTION_ONLY`, default `FEED`), and shown in `ImportTable`. | Must Have | New column on the import job table (same Liquibase changeset as FR-1). |
| FR-14 | The `video_sources` row is created with the job's visibility, so the video is never in the feed, even briefly. | Must Have | The import worker only uploads to Drive. The row is created later by `VideoStorageServiceImpl.saveInfo` (Drive scheduler) or `upsertInfo` → `upsertDriveSource` (force sync); both look the job up by `drive_file_id`, and use `FEED` when there is none. On `ON CONFLICT` (the file already exists) the existing moderation is kept (is_hide, status, priority), and `visibility` follows the same rule: re-importing an existing video does not change it. |
| FR-15 | Retry of a FAILED import job keeps the job's collection-only option. | Must Have | `retryIfFinal` reuses the job row. |
| FR-16 | Other paths that create videos (Drive sync cron `sync-drive-videos`, approved user suggestions via `createImportJob`) default to not collection-only. | Must Have | No UI change. The admin can flip it afterwards (FR-6). |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Performance | The feed query keeps using an index, with no measurable slowdown of `GET /video-stream/list` (cache miss path). |
| NFR-2 | Freshness | The feed reflects a flag change on the next request after toggling (`FeedCache` invalidated), ≤ 30 s in the worst case. |
| NFR-3 | Security | Only admins can change the flag (role + permission bit), every change is audited, and the collection-only flag is not access control (see Out of Scope). |
| NFR-4 | Compatibility | One backend release with a short downtime (owner-approved, single VPS). The backend accepts the legacy `{hidden}` body and keeps a derived `hidden` DTO field until the new frontend is live. |
| NFR-5 | Coverage | Backend LINE coverage stays ≥ 90% (`api/.coverage-threshold`). |

## Scope

### In Scope

- Collection-only flag on `VideoSource` + migration.
- Feed excludes flagged videos. Search, Explore hero and the "Add videos" picker follow automatically.
- Admin API + Admin videos table three-state visibility control and badge.
- "Add to collection" action in the Admin videos table.
- "Collection only" option in the Import form, stored on the import job and applied when the video row is created.
- Client fallback that opens a video not present in the feed (deep link, watch history, notification).
- Tests: feed exclusion, collection inclusion, history, deep-link fallback.

### Out of Scope

- **Access control**: collection-only is "not promoted", not "private". Anyone with the collection link, a `?v=` link or the stream URL can still play the video. Real privacy (owner-only collections or videos) is a separate feature.
- A separate search inside collections, or a "Research" category or tab.
- Letting non-admin collection owners mark videos collection-only.
- Setting the flag from the Drive sync panel or on user suggestions. Those paths default to off; the admin flips it on the Admin videos page.
- Per-line options inside one import request (the option applies to the whole request).
- Hiding collection-only videos from watch history or direct links (the owner chose to keep both).
- Bulk toggle of many videos at once.

## Dependencies

| Dependency | Owner | Status | Risk |
|------------|-------|--------|------|
| `FeedCache` invalidation on toggle (exists, used by priority and HLS changes) | nguyenhuuca | Available | Low |
| `AddToCollectionModal` reuse in the Admin page | nguyenhuuca | Available (used by `VideoSwiper`) | Low |
| `GET /video-stream/{id}` for the deep-link fallback | nguyenhuuca | Available (`StreamVideoServiceImpl.getVideoById`) | Low |
| Liquibase migration + `video_sources` feed index (202610240002) | nguyenhuuca | To review in the ADR | Med |
| [ADR-0035](../adr/0035-collection-only-videos.md): visibility enum replaces `is_hide` | nguyenhuuca | Proposed | Med |

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Deep links, history or notifications to a collection-only video silently do nothing, because the client looks the id up only in the feed list | H (certain without FR-9) | M | FR-9 fallback via `GET /video-stream/{id}`, plus a frontend test. |
| Admin confuses "hidden" and "collection-only" | M | M | One three-state control (Feed / Collection only / Hidden) with a tooltip; the enum makes the states exclusive. |
| A former `is_hide = false` check is mapped to `visibility = 'FEED'` by mistake, so collection-only videos vanish from collections or history | M | H | A `COLLECTION_ONLY` fixture in the tests of every surface, plus a grep that no `is_hide` is left. |
| Owner expects collection-only to be private | M | M | Out of Scope states it is not access control, and the admin tooltip says so. |
| The feed query loses its index after the filter changes | L | M | Check the plan with `EXPLAIN` in the plan phase; extend the partial index if needed. |
| Migration fails or must be undone on prod | L | H | DB backup before deploy, a rollback block that recreates `is_hide`, and a test run on a prod-like dump. Redeploying the old JAR alone is not enough once `is_hide` is dropped. |

## Open Questions

- [x] Storage: `visibility` enum (`FEED`, `COLLECTION_ONLY`, `HIDDEN`) replaces `is_hide`, in one release with an owner-approved downtime ([ADR-0035](../adr/0035-collection-only-videos.md))
- [x] API: `PATCH /admin/videos/{id}/visibility` takes `{visibility}`, and accepts the legacy `{hidden}` for one release ([ADR-0035](../adr/0035-collection-only-videos.md))
- [ ] Should a SUGGESTION_LIVE notification for a video the admin marked collection-only still link to it? The FR-9 fallback makes it work; confirm it is wanted.
- [ ] When the deep-link fallback plays a single video, should swiping continue into the feed, or show only that one video? (design spec)

## Appendix

### Mockups/Wireframes

TBD (design spec): a three-state visibility control and badge in `AdminVideoTable`, an "Add to collection" row action, and the single-video player state for the deep-link fallback.

### Research

Code paths read on 2026-10-30:
- Feed: `StreamVideoServiceImpl.java:87-93` (`FeedCache` + `findAllByIsHideAndStatusOrderByPriorityDescCreatedAtDesc`).
- Single video: `StreamVideoServiceImpl.getVideoById` (~:97-102) accepts `!isHide && PUBLISHED`.
- Collections: `VideoCollectionItemRepository.VISIBLE`, `VideoCollectionServiceImpl` (~:209).
- Admin: `AdminController` `PATCH /videos/{id}/visibility` (~:111), `/priority`, `/hls`.
- Client: `useVideos` feed list, used by `SearchModal.jsx`, `AddVideosModal.jsx` and `ExploreView.jsx`.
- Deep link: `AppShell.jsx:63-82, 125-137` and `VideoFeed.jsx:11-12`.

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | | Pending |
| Engineering | nguyenhuuca | | Pending |
| Design | nguyenhuuca | | Pending |

---

## Next Steps & Handoffs

After PRD approval:

1. [ ] **Architect Review**: storage shape, admin endpoint, flag precedence
   - Trigger: `/architect`
   - Output: ADR (`docs/adr/0035-collection-only-videos.md`)

2. [ ] **UI/UX Designer**: admin visibility control, badge, row action, single-video fallback state
   - Trigger: `/ui-ux-designer`
   - Output: Design Spec (`docs/specs/design-spec-collection-only-videos.md`)

3. [ ] **Spec + sequence diagrams**: `/spec` → `docs/specs/spec-collection-only-videos.md`, `docs/sequence/sequence-collection-only-videos.md`

4. [ ] **Engineering Estimate**: Implementation Plan (`docs/plans/plan-collection-only-videos.md`)

5. [ ] **Track it**: add the feature row to `docs/tracking.md`
   - Then decompose: `/swarm-plan docs/prd/PRD-collection-only-videos.md`

**Related Artifacts**:
- ADR: [ADR-0035](../adr/0035-collection-only-videos.md)
- Design Spec: TBD
- Implementation Plan: TBD

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-10-30 | nguyenhuuca | Initial draft from /scope |
| 1.1 | 2026-10-30 | nguyenhuuca | Import form gets a "Collection only" option (FR-13..FR-16), moved out of Out of Scope |
| 1.2 | 2026-10-30 | nguyenhuuca | Visibility enum replaces `is_hide` (ADR-0035); downtime accepted |
