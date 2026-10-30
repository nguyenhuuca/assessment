# Plan: Collection-Only Videos

<!--
Implementation Plan
Filename: docs/plans/plan-collection-only-videos.md
Owner: Builder (/builder)
-->

## Overview

**Status:** Approved (2026-10-30)
**Author:** nguyenhuuca
**Date:** 2026-10-30
**Tracking:** `docs/tracking.md` row "Collection-Only Videos"
**Related PRD:** [PRD-collection-only-videos](../prd/PRD-collection-only-videos.md)
**Related ADR:** [ADR-0035](../adr/0035-collection-only-videos.md)
**Spec:** [spec-collection-only-videos](../specs/spec-collection-only-videos.md) (rules cited as Rule n / FR-n / EC-n)
**Sequence:** [sequence-collection-only-videos](../sequence/sequence-collection-only-videos.md) (flows 1–6)
**Size:** Medium. 7 tasks + 1 follow-up: 3 backend, 3 frontend, 1 land. About 1,700 LOC including tests.

## Objective

- `video_sources.is_hide` becomes `visibility` (`FEED`, `COLLECTION_ONLY`, `HIDDEN`) in one backend release, with a short downtime.
- The feed shows only `FEED`. Collections, watch history, direct links, views, reactions and HLS accept any non-`HIDDEN` video.
- The admin sets visibility in the Admin videos table or in the Import form, and can add any video to a collection from the admin table.
- A deep link to a video that is not in the feed opens it at slide 0.

## Scope

### In Scope

- Everything in the spec: FR-1 to FR-12, every Rule and EC, every acceptance criterion that can be automated.
- Local migration run against a prod-like dump (there is no DB test harness in the repo).

### Out of Scope

- Everything out of scope in the PRD: access control, a research category, per-line import options, bulk changes, setting visibility from Drive sync or suggestions.
- FR-13 compatibility cleanup: tracked as follow-up **CO-F1**, after the new frontend is live.

## Technical Approach

### Architecture Changes

See the spec's API, Database and Frontend sections and the sequence diagrams (flows 1–6).

| New | Package / path | Notes |
|-----|----------------|-------|
| `VideoVisibility` enum | `enums/` | Next to `VideoStatus`, `HlsStatus` |
| `202610300001-video-visibility.sql` | `db/changelog/sql/` | First Liquibase **formatted** SQL file (`--liquibase formatted sql` + `--rollback`), per the spec SQL |
| `videosApi.get(id)` | `webapp/src/api/videos.js` | `GET /video-stream/{id}` for the deep-link fallback |

### Key Decisions

| Decision | Rationale |
|----------|-----------|
| CO-1 is one atomic backend unit: migration + enum + entity + every `is_hide` reader + their tests | The migration drops `is_hide`, so the entity and all readers must change in the same commit, or the app fails to start. About 20 main references and 12 test files; it stays reviewable because each change is the same mechanical rule (feed → `= FEED`, everything else → `<> HIDDEN`). |
| CO-1 keeps the legacy admin API working (`{hidden}` body, `hidden` DTO field, derived) | CO-1 can land and deploy alone without breaking the admin page. CO-2 adds the new body. |
| Visibility at row creation is looked up by `drive_file_id` (CO-3) | The import worker only uploads to Drive; `saveInfo` / `upsertInfo` create the row later and see only the file id (spec Rule 6). |
| Push in two batches: backend CO-1..CO-3 in one push, frontend CO-4..CO-6 in one push | Every `api/**` push to `main` deploys the backend (`funnyapp-ci.yml`). One backend push means one downtime and one migration run. The legacy compatibility in CO-1 makes the backend-first order safe. |
| Verify the migration locally on a prod-like dump before pushing | The repo has no `@DataJpaTest` / Testcontainers harness (`AppReportMigrationTest.java:38`); migration tests only string-match SQL files. Mixing a formatted SQL file into `includeAll` is supported by Liquibase but new to this repo, so it must be proven on a real Postgres. |

## Implementation Steps

Every task lands on `main` on its own and leaves the build green. The dependency graph gives the merge order.

| ID | Task | Files | Acceptance criteria | Est. LOC (main + tests) |
|----|------|-------|---------------------|-------------------------|
| **CO-1** | BE: schema, enum, entity and every `is_hide` reader | new `db/changelog/sql/202610300001-video-visibility.sql`, `enums/VideoVisibility.java`; `entity/VideoSource.java:53-55`, `entity/VideoImportJob.java` (+ `visibility`), `repo/VideoSourceRepository.java:31,36,45,54-71`, `repo/VideoCollectionItemRepository.java:17,21`, `repo/WatchHistoryRepository.java:52,86,94`, `service/impl/StreamVideoServiceImpl.java:90,100`, `service/impl/HlsPlaybackServiceImpl.java:44`, `service/impl/VideoCollectionServiceImpl.java:209`, `service/impl/VideoReactionServiceImpl.java:59`, `service/impl/AdminVideoServiceImpl.java:109-115,211`, `service/impl/VideoStorageServiceImpl.java:264-266`, `dto/admin/AdminVideoDto.java:24`; tests: new `repo/VideoVisibilityMigrationTest` (pattern `VideoSourceFeedIndexMigrationTest`), `StreamVideoServiceImplTest`, `AdminVideoServiceImplTest`, `AdminControllerTest`, `VideoCollectionServiceImplTest`, `VideoReactionServiceImplTest`, `HlsPlaybackServiceImplTest`, `VideoSourceDefaultsTest`, `WatchHistoryServiceImplTest`, `VideoViewServiceImplTest`, `JsonContractTest` + `json-contract/dto-admin-video.json` | (1) Changeset = spec SQL: header `--liquibase formatted sql`, one `--changeset nguyenhuuca:202610300001-video-visibility`, backfill, 2 CHECKs, recreated `idx_video_sources_feed`, new `idx_video_import_jobs_drive_file_id`, `is_hide` dropped, `--rollback` lines; the migration test asserts each of them in the file text (Rule 1, Rule 4, FR-1). (2) `VideoVisibility { FEED, COLLECTION_ONLY, HIDDEN }`; `VideoSource.visibility` and `VideoImportJob.visibility` are `@Enumerated(STRING) @Column(length=20, nullable=false) @Builder.Default FEED` (FR-2). (3) Feed query `findAllByVisibilityAndStatusOrderByPriorityDescCreatedAtDesc(FEED, PUBLISHED)` (Rule 2, FR-3). (4) Every other reader uses the playable rule: `VISIBLE` = `v.visibility <> 'HIDDEN' AND v.status = 'PUBLISHED'`, `existsByIdAndVisibilityNotAndStatus(id, HIDDEN, PUBLISHED)`, `incrementViewCount` / `WatchHistoryRepository` `visibility <> 'HIDDEN'`, `getVideoById` and `findPlaylist` `!= HIDDEN` (Rule 3, FR-4). Each service test gets a `COLLECTION_ONLY` case that passes and a `HIDDEN` case that is rejected. (5) `upsertDriveSource` INSERT writes `'FEED'` (its param comes in CO-3); `ON CONFLICT` does not touch `visibility` (Rule 7). (6) `updateHidden(id, hidden)` maps to `HIDDEN` / `FEED` and still invalidates `FeedCache`; `AdminVideoDto` gets `visibility` and a derived `hidden` (FR-6). (7) `grep -rn "is_hide\|isHide\|IsHide\|setHide" api/src/main` returns only the migration files. (8) `mvn verify` green, coverage ≥ 90%. (9) **Local run:** restore a prod-like dump into local Postgres, `./startLocal.sh`, Liquibase applies the changeset, hidden rows read `HIDDEN`, the app boots and `/video-stream/list` answers; then running the file's `--rollback` statements with `psql` restores `is_hide` and the old index, and deleting the `databasechangelog` row lets the next boot apply it again (memory: green CI ≠ app boots) | ~120 + ~260 |
| **CO-2** | BE: admin visibility endpoint takes `{visibility}` | `web/AdminController.java:111-122`, `service/AdminVideoService.java`, `service/impl/AdminVideoServiceImpl.java` (`updateVisibility`); tests `AdminControllerTest`, `AdminVideoServiceImplTest` | (1) `PATCH /admin/videos/{id}/visibility` accepts `{visibility}` with each of the 3 values and the legacy `{hidden}` (FR-5, EC-1). (2) 400 for a null body, neither field, both fields (EC-2) and an unknown value, with the spec messages; the service is not called. (3) Unknown id → 400 `Video not found: {id}` (unchanged). (4) Every call saves and invalidates `FeedCache`, including the same value again (Rule 9, EC-7). (5) Role guard and `@AuditLog("updateVideoVisibility")` unchanged | ~50 + ~110 |
| **CO-3** | BE: import visibility and row creation | `dto/admin/CreateVideoImportRequest.java` (+ `visibility`), `dto/admin/VideoImportJobDto.java` (+ `visibility`), `service/VideoImportService.java`, `service/impl/VideoImportServiceImpl.java:60-126,242-` (`create`, `createResolved`, `createJob`, `toDto`), `service/impl/VideoSuggestionServiceImpl.java:327-330`, `service/impl/VideoStorageServiceImpl.java:59-103,248-292` (inject `VideoImportJobRepository` with an `@Autowired` setter like the others), `repo/VideoSourceRepository.java:58-71` (+ `visibility` param); tests `VideoImportServiceImplTest`, `VideoStorageServiceImplTest:207-275,500-545`, `DriveSyncServiceImplTest:289-327`, `VideoSuggestionServiceImplTest`, `AdminVideoImportControllerTest`, `JsonContractTest:92`, `BeanConstructorTest` | (1) `visibility` null → `FEED`; `COLLECTION_ONLY` stored on every job of the request; `HIDDEN` or unknown → 400 subCode 4104 `INVALID_VISIBILITY`, no job saved (Rule 5, EC-3, FR-7). (2) `createResolved` takes a visibility; suggestion approval passes `FEED` (EC-10). (3) Retry keeps the job's visibility: `retryIfFinal` does not touch the column, and a test asserts it (Rule 8). (4) `VideoImportJobDto.visibility` is set by `toDto`. (5) `saveInfo` sets the builder's visibility from the **latest** job for the Drive file, new repository method `findFirstByDriveFileIdOrderByIdDesc(fileId)` (several jobs can share a file after a re-import; the existing `findFirstByDriveFileId` at `VideoImportJobRepository.java:29` has no order), else `FEED`; `upsertInfo` passes the same value to `upsertDriveSource`; an existing row is unchanged (Rules 6–7, EC-4, FR-8). (6) `BeanConstructorTest` green | ~90 + ~220 |
| **CO-4** | FE: admin three-state control, badge, Add to collection | `webapp/src/api/admin.js:18`, `webapp/src/hooks/useAdmin.js:61-67`, `webapp/src/components/admin/AdminVideoTable.jsx:167,176,198-220,248-284`, `webapp/src/styles/index.css` (badge), `webapp/src/i18n/locales/{en,vi}/admin.json:57,116-119`; tests `AdminVideoTable.test.jsx`, `api/__tests__/admin.test.js`, `i18n/__tests__/parity.test.js` | (1) `updateVideoVisibility(id, visibility)` sends `{visibility}`; the hook invalidates `['admin','videos']` (FR-5). (2) The hidden checkbox becomes a 3-option control (Feed / Collection only / Hidden) with the existing busy state and `SaveIndicator`; the tooltip says collection-only is not private (FR-9, spec Security). (3) Row class `is-hidden` follows `visibility === 'HIDDEN'`; a `COLLECTION_ONLY` badge is shown. (4) A row action "Add to collection" opens `AddToCollectionModal` with `{ id }` (it only reads `video.id`). (5) New EN/VI keys pass the parity test | ~150 + ~160 |
| **CO-5** | FE: import "Collection only" option | `webapp/src/api/admin.js:44-45`, `webapp/src/components/admin/ImportForm.jsx:47-71,98-110`, `webapp/src/components/admin/ImportTable.jsx:99`, `webapp/src/i18n/locales/{en,vi}/admin.json`; tests `AdminImportPanel.test.jsx` (no ImportForm/ImportTable tests exist) | (1) A "Collection only" checkbox, off by default; checked → the body has `visibility: "COLLECTION_ONLY"`, unchecked → no `visibility` field (FR-10). (2) `ImportTable` shows a badge on `COLLECTION_ONLY` jobs. (3) The 400/4104 error shows through the existing error path. (4) EN/VI parity | ~40 + ~90 |
| **CO-6** | FE: deep-link fallback in the feed | `webapp/src/api/videos.js:3-6` (+ `get(id)`), `webapp/src/components/video/VideoFeed.jsx:6-45`, `webapp/src/components/video/VideoSwiper.jsx:241` (+ `searchVideos` prop); tests `PublicFeed.resume.test.jsx`, `AppShell.deeplink.test.jsx`, new `components/video/__tests__/feedSurfaces.test.jsx` (SearchModal, ExploreView, AddVideosModal) | (1) Id found in the feed → unchanged behaviour (existing tests pass). (2) Id not found → `GET /video-stream/{id}` once (query key `['video', id]`); on 200 the video is inserted at index 0, the swiper starts there and `deepLinkStartAt` resumes it (FR-11, EC-5). (3) On error → feed at `initialIndex ?? 0`, nothing inserted, no message (EC-6). (4) `onDeepLinkResolved()` is called in all three outcomes, so `AppShell` clears `deepLinkId` (today a missing id is never resolved). (5) The inserted video is not added to the cached `['videos', category]` list. (6) Feed search keeps using the feed list **without** the inserted video: `VideoFeed` passes the pre-insert list to `VideoSwiper` as a separate `searchVideos` prop, which `VideoSwiper.jsx:241` hands to `SearchModal` (today it passes the slide list). `SearchModal` returns indexes into the list it gets (`SearchModal.jsx:86,92,109`), so `VideoSwiper` must map **by id** both ways: `onSelect(i)` → `videos.findIndex(v => v.id === searchVideos[i].id)` before `setIndex` (`VideoSwiper.jsx:244-248`), and `currentIndex` = position of `videos[index].id` in `searchVideos`, or -1 on the inserted slide (`:242`). Tests: after a deep-link insert, (a) search does not list the inserted video, (b) selecting the second feed result shows that video's slide, (c) no row is marked current while the inserted slide plays. (8) Implementation notes from plan review: while `deepLinkId` is set, not in the feed and the `['video', id]` query is pending, render `LoadingState` instead of the swiper (`VideoSwiper.jsx:12` reads `initialIndex` once); the inserted video and a `resolved` flag live in `PublicFeed` state, because `AppShell` clears `deepLinkId` right after `onDeepLinkResolved`, and the callback fires exactly once per outcome (test); `VideoSwiper` defaults `searchVideos = videos` so `LibraryView.jsx:206` and the existing swiper tests are unchanged. Known limitation: the slide counter (`VideoSwiper.jsx:216`) shows N+1 while the inserted slide exists. (7) Surfaces that read the feed list do not show a video absent from it: a test renders `SearchModal`, `ExploreView` and `AddVideosModal` with a mocked `useVideos` list and asserts each one shows only the list items (no other source of videos); together with the CO-1 feed-query test this covers the spec AC "feed search, Explore hero and Add videos picker do not show `COLLECTION_ONLY`" | ~60 + ~130 |
| **CO-7** | Land: deploy, prod smoke, docs | `docs/sequence/sequence-collection-only-videos.md` (Status As-built), `docs/tracking.md`, `TASKS.md`, `session.txt` | (1) Before the backend push: `pg_dump` backup of prod. (2) Push 1 = CO-1..CO-3 (owner approves the push); from push 1 until push 2 is live, **no visibility edits on the Admin page**, because the old page sends `{hidden:false}` and would reset a `COLLECTION_ONLY` row to `FEED` (EC-1); `funnyapp-ci` green; prod log shows the changeset applied and the app started. (3) Push 2 = CO-4..CO-6; `Deploy Frontend` green. (4) Prod smoke: set a video to Collection only → gone from the feed within 30 s, still plays in a collection and via `?v=`; import one URL with Collection only → never appears in the feed; Hidden still hides everywhere. (5) Diagrams marked As-built, tracking ✅ | docs only |
| **CO-F1** | Follow-up: remove the legacy `{hidden}` body and the `hidden` DTO field | `web/AdminController.java`, `dto/admin/AdminVideoDto.java`, `json-contract/dto-admin-video.json`, tests | After CO-7 push 2 is live: legacy body → 400, `hidden` gone from the DTO and the contract JSON (FR-13) | ~-30 + ~40 |

### Dependency graph

```
CO-1 ──┬──> CO-2 ──> CO-4 ──┐
       └──> CO-3 ──> CO-5 ──┼──> CO-7 ──> CO-F1
CO-6 (independent) ─────────┘
```

- CO-1 first: it is the schema and every reader, and it keeps the legacy admin API so it can deploy alone.
- CO-2 and CO-3 both need CO-1 and can be built in parallel (different files, except `VideoSourceRepository`, where CO-3 only adds a parameter).
- CO-4 needs CO-2 (new body), CO-5 needs CO-3 (import field). They are pushed only after the backend batch is live.
- CO-6 only calls the existing `GET /video-stream/{id}`. It works before or after the backend, but it is pushed with the frontend batch.
- **Push rule:** the owner approves each push. Push 1 = CO-1..CO-3 after the local migration run (CO-1 AC 9) and the prod backup. Push 2 = CO-4..CO-6 after push 1 is live.

## Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `api/src/main/resources/db/changelog/sql/202610300001-video-visibility.sql` | Create | Formatted SQL changeset with rollback (CO-1) |
| `api/src/main/java/com/canhlabs/funnyapp/enums/VideoVisibility.java` | Create | Enum (CO-1) |
| `entity/VideoSource.java`, `entity/VideoImportJob.java` | Modify | `visibility` field (CO-1) |
| `repo/VideoSourceRepository.java`, `repo/VideoCollectionItemRepository.java`, `repo/WatchHistoryRepository.java` | Modify | Feed / playable predicates; `upsertDriveSource` param (CO-1, CO-3) |
| `service/impl/StreamVideoServiceImpl.java`, `HlsPlaybackServiceImpl.java`, `VideoCollectionServiceImpl.java`, `VideoReactionServiceImpl.java` | Modify | Playable rule (CO-1) |
| `service/impl/AdminVideoServiceImpl.java`, `web/AdminController.java`, `dto/admin/AdminVideoDto.java` | Modify | Visibility endpoint + DTO (CO-1, CO-2) |
| `service/impl/VideoImportServiceImpl.java`, `VideoSuggestionServiceImpl.java`, `VideoStorageServiceImpl.java`, `dto/admin/CreateVideoImportRequest.java`, `VideoImportJobDto.java` | Modify | Import visibility and row creation (CO-3) |
| `webapp/src/api/admin.js`, `api/videos.js`, `hooks/useAdmin.js` | Modify | API calls (CO-4, CO-5, CO-6) |
| `webapp/src/components/admin/AdminVideoTable.jsx`, `ImportForm.jsx`, `ImportTable.jsx`, `components/video/VideoFeed.jsx` | Modify | UI (CO-4, CO-5, CO-6) |
| `webapp/src/i18n/locales/{en,vi}/admin.json`, `webapp/src/styles/index.css` | Modify | Labels, badge (CO-4, CO-5) |

## Dependencies

### Code Dependencies

None new. Liquibase comes from the Spring Boot 4.1.2 BOM; formatted SQL with `--rollback` is a core Liquibase feature (checked in the Liquibase docs).

### Service Dependencies

| Service | Status | Notes |
|---------|--------|-------|
| Prod PostgreSQL backup (`pg_dump`) | Needed before push 1 | Rollback path once `is_hide` is dropped |
| Local PostgreSQL with a prod-like dump | Needed for CO-1 AC 9 | No DB test harness in the repo |

## Testing Strategy

### Unit Tests

| Component | Test Cases | Status |
|-----------|------------|--------|
| Migration file (`VideoVisibilityMigrationTest`) | Header, changeset id, backfill, CHECKs, both indexes, drop, rollback lines | Pending |
| Feed / playable readers (CO-1 service tests) | `FEED` in feed; `COLLECTION_ONLY` out of feed but in collections, history, by-id, views, reactions, HLS; `HIDDEN` out everywhere | Pending |
| `AdminController` / `AdminVideoServiceImpl` | 3 values, legacy body, both fields, unknown value, missing body, unknown id, cache invalidation | Pending |
| `VideoImportServiceImpl` / `VideoStorageServiceImpl` | Default FEED, COLLECTION_ONLY stored, HIDDEN 4104, retry keeps it, row created from job, existing row unchanged, no job → FEED | Pending |
| `AdminVideoTable`, `AdminImportPanel`, `PublicFeed`, `AppShell` deep link | 3-state control, badge, Add to collection, import checkbox body, fallback insert / error / resolve | Pending |

### Integration Tests

| Scenario | Expected Outcome | Status |
|----------|------------------|--------|
| Local migration on a prod-like dump (CO-1 AC 9) | Changeset applies, hidden rows `HIDDEN`, app boots, rollback restores `is_hide` | Pending |
| Prod smoke (CO-7 AC 4) | Feed, collection, deep link and import behave per spec | Pending |

## Rollback Plan

Roll back in this order: the frontend first, because the old JAR rejects the new `{visibility}` body with 400.

1. **Frontend:** redeploy the previous `webapp/dist` (re-run `Deploy Frontend` on the previous commit). The backend keeps the legacy body until CO-F1, so the old page keeps working.
2. **Backend schema:** there is no Liquibase CLI on the VM; Liquibase runs only inside Spring Boot. Run the changeset's `--rollback` statements by hand with `psql` on the VM (they are in `202610300001-video-visibility.sql`, in order), then `DELETE FROM databasechangelog WHERE id = '202610300001-video-visibility' AND author = 'nguyenhuuca';`. `COLLECTION_ONLY` videos become visible in the feed again. The same statements are rehearsed locally in CO-1 AC 9.
3. **Backend JAR:** redeploy the previous JAR.
4. If step 2 fails: restore the pre-deploy `pg_dump`, then the previous JAR.

Redeploying the previous JAR alone does not work once `is_hide` is dropped.

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| A former `is_hide = false` reader is mapped to `= FEED` and collection-only videos vanish from collections or history | M | H | A `COLLECTION_ONLY` case in every reader's test (CO-1 AC 4) and the grep check (AC 7) |
| The formatted SQL file behaves differently under `includeAll` | L | H | CO-1 AC 9 runs it on a real Postgres before any push |
| The migration fails on prod | L | H | Backup first; the changeset is small (`video_sources` has hundreds of rows) |
| The old admin page talks to the new backend between pushes | H (certain, briefly) | L | Legacy `{hidden}` body and `hidden` DTO field kept until CO-F1 |
| `VideoStorageServiceImpl` gains a dependency and breaks bean wiring | L | M | Setter injection like its other dependencies; `BeanConstructorTest`; the local boot in CO-1 / CO-3 |
