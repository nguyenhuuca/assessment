# Plan: HLS Streaming for Selected Videos

<!--
Implementation Plan
Filename: docs/plans/plan-hls-streaming.md
Owner: Builder (/builder)
-->

## Overview

**Status:** Done (2026-10-24, autopilot run `2026-10-24-hls-streaming`)
**Date:** 2026-10-24
**PRD:** [PRD-hls-streaming](../prd/PRD-hls-streaming.md)
**ADR:** [ADR-0031](../adr/0031-hls-streaming.md)
**Spec:** [spec-hls-streaming](../specs/spec-hls-streaming.md) (rules are cited as BR-n / FR-n / EC-n)
**Sequence:** [sequence-hls-streaming](../sequence/sequence-hls-streaming.md) (flows 1–6)
**Size:** Medium. 7 tasks: 5 backend, 2 frontend, about 2,000 LOC including tests.

## Objective

- The SUPER_ADMIN turns HLS on per Drive video.
- A single-job worker stream-copies the video into `video-cache/hls/{fileId}/`.
- The API offers `hlsUrl` only when the Harness flag `hls_streaming_enabled` is on and the rendition is READY.
- The player uses HLS and falls back to Range.

## Scope

### In Scope

- **Backend:**
  - one Liquibase changeset, entity + enum, repository queries, `app.hls.*` config;
  - an ffprobe/ffmpeg client and HLS storage helper;
  - the worker;
  - admin toggle endpoints and admin DTO;
  - the downloader hook and delete cleanup;
  - public playlist and segment endpoints;
  - `hlsUrl` in the DTO.
- **Frontend:** `hls.js`, the `useHlsSource` hook with fallback, the swiper preload rule, and the admin HLS column.
- Tests for every BR, EC and acceptance criterion in the spec that can be automated.

### Out of Scope

- Everything out of scope in the PRD:
  - auto-transcode at ingest, backfill;
  - re-encode, ABR, DRM;
  - per-user flag targeting;
  - FFmpeg in the Docker image.
- Any change to the Range endpoint, `VideoCache` or the download flow beyond the BR-10 hook.

## Technical Approach

### Architecture Changes

See the spec's API, Database and Frontend sections and the sequence diagrams.

| New | Package | Notes |
|-----|---------|-------|
| `HlsStatus` enum | `enums/` | Next to `VideoStatus` |
| `HlsErrorCode` enum | `enums/` | BR-9 codes, stored as their name in `hls_error` |
| `HlsStorage` | `service/impl/` (`@Component`) | Paths, id/segment validation, tmp → publish rename, delete, usable space, directory listing for the sweep |
| `FfmpegHlsClient` | `client/` | ffprobe + ffmpeg through the existing `ProcessFactory` (`client/ProcessFactory.java:8-9`), timeout watchdog, kill |
| `HlsTranscodeWorker` | `service/impl/` | Copies the `VideoImportWorker` pattern |
| `HlsPlaybackService` (+ impl) | `service/` | BR-13 lookup for the playlist endpoint, flag read for DTOs |

### Key Decisions

All recorded in ADR-0031 (D1–D8) and the spec's Open Questions (owner decisions of 2026-10-24).

**Explorer findings that shape the tasks:**
- **Atomic claim to copy.** `VideoImportJobRepository.claimNextDue()` (`:37-50`) is a single native statement: `UPDATE … WHERE id = (SELECT … FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING id`. It returns `Optional<Long>`. Startup reset is `resetInterrupted()` (`:62-75`). The HLS claim must also set `updated_at = now()`: native updates do not fire `@UpdateTimestamp` (`BaseDomain.java:26`), and `incrementViewCount` (`VideoSourceRepository.java:41-45`) does not touch it.
- **Process handling.**
  - Only the start step is shared: `ProcessFactory` / `DefaultProcessFactory` (env allowlist, stdin from the null device).
  - The timeout watchdog and kill live privately in `YtDlpClientImpl` (`:220-289`).
  - `FfmpegHlsClient` takes `ProcessFactory` and has its own small run/kill. We do not refactor `YtDlpClientImpl` (Two Hats rule).
  - Tests use `FakeProcess.finished(...)` / `hanging(...)` (test `client/FakeProcess.java:29-35`).
  - `FfmpegServiceImpl` (thumbnails) calls `ProcessBuilder` directly and stays as it is.
- **Locks.**
  - `DriveVideoDownloaderImpl` does **not** claim `DriveFileLocks`. Its callers do: `VideoStorageServiceImpl.java:141/151` and `DriveSyncServiceImpl.java:193/209`. So the BR-10 hook after `moveIntoPlace` (`DriveVideoDownloaderImpl.java:70`) already runs under the lock.
  - `DriveFileLocks` is non-blocking and non-reentrant: `tryClaim` / `release` / `isHeld` (`cache/DriveFileLocks.java:18-28`).
- **Not-found errors.**
  - `AdminVideoServiceImpl` throws `IllegalArgumentException`, which maps to 400 (`:86-87`).
  - The spec asks for 404, so the new HLS methods use `CustomException.of(HttpStatus.NOT_FOUND, 404, "error.video.notFound")`. That key already exists (`VideoReactionServiceImpl.java:60`).
  - Existing methods are not changed.
- **Security.**
  - `AdminController` has class-level `@PreAuthorize("hasRole('ADMIN')")` (`:51`). The new methods add the method-level SUPER_ADMIN expression from `AdminDriveSyncController.java:55`; the method level wins.
  - `/video-stream/**` is in `ALLOW_ALL_METHOD` (`AppConstant.java:72-84`), so the public HLS paths need no security change.
  - CORS already allows PUT and DELETE (`WebSecurityConfig.java:141`).
- **DTO callers.**
  - `Converter.toVideoDto` is called from `StreamVideoServiceImpl.java:113` (via `toDto`, used at `:89`, `:97` and by the list) and `VideoCollectionServiceImpl.java:190` (method ref).
  - Both must pass the per-request flag value.
  - `ShareServiceImpl` / `YouTubeVideoServiceImpl` build YouTube DTOs and are not touched.
- **Frontend.**
  - `VideoPlayer.jsx` has no `onError` handler (`:189-206`). A failed source spins forever today, so the fallback adds the first error path.
  - The only other `<video>` is the hidden preload in `VideoSwiper.jsx:222-236`.
  - There is no dynamic `import()` in `src` yet. `hls.js` will be the first split chunk; Vite does this without config.
  - `AdminVideoTable.jsx` has no role check yet. Use `useAuth().user.role` + `isSuperAdmin` (`utils/roles.js:5`), as `DriveSyncPanel.jsx:90` does.
  - Tests use the real i18n (`src/test-setup.js`). `VideoSwiper.actions.test.jsx:7` mocks `VideoPlayer`.
- **`hls.js` version:** pin `1.7.3` (published 2026-09-11), exact version, no caret.
- **`BeanConstructorTest`** (`BeanConstructorTest.java:22-40`): `HlsTranscodeWorker` and `FfmpegHlsClient` will have a test constructor, so the public one needs `@Autowired`.

## Implementation Steps

Every task lands on `main` on its own. The order in the dependency graph is the merge order.

| ID | Task | Files | Acceptance criteria | Est. LOC (main + tests) |
|----|------|-------|---------------------|-------------------------|
| **HL-1** | BE: schema, entity, repository, config, flag constant | new `db/changelog/sql/202610240001-video-source-hls.sql`, `enums/HlsStatus.java`, `enums/HlsErrorCode.java`; `entity/VideoSource.java:60-70`, `repo/VideoSourceRepository.java`, `config/AppProperties.java:52,66-80` (+ `Hls` class), `application.yaml:126-136` (+ `app.hls`), `utils/AppConstant.java:37`; tests: new `repo/VideoSourceHlsMigrationTest` (pattern `AppReportMigrationTest`), `AppPropertiesTest` | (1) Changeset = spec SQL: 2 columns, CHECK, partial index. Existing rows read `NONE`/null (BR-3, AC 1). (2) `VideoSource.hlsStatus` `@Enumerated(STRING)` `@Builder.Default NONE` + `hlsError` String (FR-1). (3) Repository: `findHlsQueue()` = PENDING rows ordered by `updated_at, id`; `claimHls(id)` native `UPDATE video_sources SET hls_status='PROCESSING', updated_at=now() WHERE id=:id AND hls_status='PENDING'` → rows updated (1 = this caller won); `resetHlsProcessing()`; `findAllByHlsStatus(HlsStatus)`; `findBySourceId` is reused (BR-3, BR-4, BR-11). A test asserts the conditional `WHERE … hls_status='PENDING'` in the query text: EC-8 (one winner) rests on it, because Mockito cannot prove it and the repo has no Postgres harness. (4) `app.hls.min-free-disk-bytes` 1073741824 and `app.hls.timeout` 10m with env overrides `HLS_MIN_FREE_DISK_BYTES` / `HLS_TIMEOUT` (FR-12). (5) `AppConstant.Flags.HLS_STREAMING = "hls_streaming_enabled"` | ~70 + ~110 |
| **HL-2** | BE: HLS storage + ffprobe/ffmpeg client | new `service/impl/HlsStorage.java`, `client/FfmpegHlsClient.java` (+ result record); tests `HlsStorageTest` (`@TempDir`), `FfmpegHlsClientTest` (`FakeProcess`) | (1) `HlsStorage`: validates `fileId` `^[A-Za-z0-9_-]{1,128}$` and segment `^seg_\d{1,5}\.ts$`; `resolveSegment` / `resolvePlaylist` return a path proven inside `video-cache/hls/{fileId}/` after `normalize()` (CWE-22, EC-13); `tmpDir`, `publish(fileId)` (atomic rename, replaces nothing), `delete(fileId)` (recursive, missing is OK), `usableSpace()`, `listRenditionIds()`, `deleteTmpDirs()`. (2) `FfmpegHlsClient.probe(path)` → codecs from `ffprobe -v error -show_entries stream=codec_type,codec_name -of csv=p=0`; `isSupported` = h264 + (aac or mp3 or none) (BR-5, EC-14). (3) `segment(src, tmpDir, timeout)` runs `ffmpeg -nostdin -y -i src -c copy -f hls -hls_time 6 -hls_playlist_type vod -hls_segment_filename tmp/seg_%d.ts tmp/playlist.m3u8` as a list; drains stderr into a bounded tail; on timeout kills descendants + process → `TIMEOUT`; start `IOException` → `FFMPEG_NOT_FOUND`; non-zero → `FFMPEG_FAILED` (BR-7, BR-9). (4) Tests assert the exact argument lists, the timeout kill with `FakeProcess.hanging`, and traversal rejections | ~180 + ~230 |
| **HL-3** | BE: transcode worker | new `service/impl/HlsTranscodeWorker.java`; test `HlsTranscodeWorkerTest` (sync executor `Runnable::run`, mocks for repo / `HlsStorage` / `FfmpegHlsClient` / `DriveFileLocks`) | (1) `@Autowired` ctor + test ctor with `Executor` (BeanConstructorTest). (2) `wakeUp()` on a virtual thread, `@Scheduled(cron="0 */5 * * * *") poll()`, `Semaphore(1)`; with 3 PENDING rows exactly one runs at a time (BR-4, AC). (3) Job order (peek, lock, claim by id): walk `findHlsQueue()`; for each row `tryClaim(fileId)`, and on busy skip it (EC-7: the row stays PENDING, untouched); on success `claimHls(id)`, and if it returns 0 release and go on. Stop the drain when no row can be taken, so the loop never spins on a locked row; the next `wakeUp()` or poll retries. Then → source exists (`SOURCE_MISSING`) → probe (`UNSUPPORTED_CODEC`) → disk guard `usable < size + reserve` → `DISK_FULL`, ffmpeg never started (BR-6) → segment → re-read status → publish + READY, or delete tmp (FR-3, FR-4). Lock busy → the row stays PENDING and the job is not claimed (EC-7). (4) Status NONE after FFmpeg → tmp deleted, no publish, NONE kept (EC-6). (5) `onApplicationReady`: `resetHlsProcessing`, `deleteTmpDirs`, READY with no directory → ERROR `OUTPUT_MISSING`, orphan directories (row missing or not READY) deleted, then `markReady` + `wakeUp` (BR-11, EC-2, EC-9). (6) Every failure releases the lock (`finally`) and logs `fileId`, duration and output size (FR-13) | ~170 + ~280 |
| **HL-4** | BE: admin toggles, admin DTO, delete cleanup, re-download hook | `web/AdminController.java:74-133` (+ PUT/DELETE `/videos/{id}/hls`), `service/AdminVideoService.java`, `service/impl/AdminVideoServiceImpl.java:85-95,113-124`, `dto/admin/AdminVideoDto.java:18-26` (+ new `AdminHlsStatusDto`), `service/impl/DriveVideoDownloaderImpl.java:43-81`, `i18n/messages.properties` + `messages_vi.properties` (`error.hls.notDriveVideo`); tests `AdminVideoServiceImplTest`, `AdminControllerTest`, `AdminControllerPermissionTest`, `DriveVideoDownloaderImplTest` | (1) PUT: NONE/ERROR → PENDING (clears error) + `worker.wakeUp()`; PENDING/PROCESSING/READY → 200 unchanged (BR-3, EC-5). Non-Drive → 400 `error.hls.notDriveVideo`; unknown → 404 `error.video.notFound` (AC 3). (2) DELETE: any → NONE; if `tryClaim` succeeds, delete the directory and release; never 409 (BR-12, EC-6). (3) Both methods: `@PreAuthorize("hasRole('SUPER_ADMIN') and @permissionServiceImpl.hasPermission(authentication.details.permissions, ADMIN)")` + `@AuditLog("enableVideoHls"/"disableVideoHls")`; the permission test gets ADMIN 403, SUPER_ADMIN 200 (FR-2). (4) `AdminVideoDto` `hlsStatus` (null for non-Drive) + `hlsError` (FR-7). (5) `deleteVideo` always deletes `hls/{fileId}` (EC-16). (6) Downloader after `moveIntoPlace` (`DriveVideoDownloaderImpl.java:70`): delete `hls/{fileId}`; `findBySourceId(fileId)` → READY/ERROR → PENDING + `wakeUp()`; NONE/PENDING/PROCESSING unchanged; no row (first download, the row is saved later by `saveInfo`) → nothing (BR-10, EC-4). New constructor deps `VideoSourceRepository`, `HlsStorage`, `HlsTranscodeWorker` go into both the `@Autowired` ctor (`:43-47`) and the test ctor (`:49-57`); `BeanConstructorTest` stays green; no bean cycle (the worker does not depend on the downloader) | ~120 + ~260 |
| **HL-5** | BE: public HLS endpoints + `hlsUrl` in DTOs | `web/VideoStreamController.java:29-110` (+ 2 GETs), new `service/HlsPlaybackService.java` + impl, `dto/video/VideoDto.java:16-23`, `service/impl/Converter.java:65-76`, `service/impl/StreamVideoServiceImpl.java:75-113`, `service/impl/VideoCollectionServiceImpl.java:190`; tests `VideoStreamControllerTest`, new `HlsPlaybackServiceImplTest`, `ConverterTest:120`, `StreamVideoServiceImplTest:304,317`, `VideoCollectionServiceImplTest` | (1) Playlist: 200 `application/vnd.apple.mpegurl` + `Cache-Control: no-cache` only if READY + PUBLISHED + not hidden + file exists; else 404; bad id → 400 (BR-13, EC-3, EC-13). (2) Segment: 200 `video/mp2t`, `Content-Length`, `Cache-Control: public, max-age=31536000, immutable`, `FileSystemResource`, no DB call; bad name → 400; missing → 404. (3) `VideoDto.hlsUrl`; `Converter.toVideoDto(source, hlsEnabled)` sets `https://canh-labs.com/api/v1/funny-app/video-stream/hls/{sourceId}/playlist.m3u8` only when `hlsEnabled && READY` (BR-8, FR-6). (4) List, detail and collection call `featureFlagService.isEnabled(HLS_STREAMING, false)` **once per request**; a test verifies 1 call for a list of N (NFR). (5) Flag off / Harness default → every `hlsUrl` null (EC-10, EC-11) | ~150 + ~260 |
| **HL-6** | FE: hls.js player with Range fallback | `webapp/package.json` (+ `"hls.js": "1.7.3"`), `src/utils/videoModel.js:30-45`, new `src/hooks/useHlsSource.js`, `src/components/video/VideoPlayer.jsx:21,71-86,112-121,189-206`, `src/components/video/VideoSwiper.jsx:222-236`, `.claude/rules/tech-strategy.md` (JS table row, ADR-0031); tests new `src/hooks/__tests__/useHlsSource.test.jsx`, `VideoPlayer.hls.test.jsx`, `videoModel.test.js`, new swiper preload test | (1) `mapApiVideo` keeps `hlsUrl` (null by default). (2) No `hlsUrl` → identical behaviour to today; existing VideoPlayer tests pass unchanged. (3) `hlsUrl` + native support (`canPlayType` non-empty) → `src = hlsUrl`, no `import('hls.js')`. (4) Else `import('hls.js')`, `Hls.isSupported()` → `loadSource` + `attachMedia`; not supported → Range src (FR-9). (5) Fatal `Hls.Events.ERROR` or a media `error` while on HLS → destroy, `src` = Range URL, restore the last `currentTime` and the play state on `loadedmetadata` (EC-12). (6) Destroy on unmount and on video change. (7) `startAt` resume, view count and progress still work on HLS (tests reuse `playTo()`). (8) Swiper renders no hidden preload `<video>` for an upcoming video with `hlsUrl` (FR-10). (9) `npm run build`: hls.js in its own chunk; the main chunk grows by < 1 KB | ~140 + ~220 |
| **HL-7** | FE: admin HLS column and toggle | `src/components/admin/AdminVideoTable.jsx:126-134,138-243`, `src/api/admin.js:15-24`, `src/hooks/useAdmin.js:7-58`, `src/styles/index.css:2467-2494` (HLS badge variants), `src/i18n/locales/{en,vi}/admin.json:56-100`; test `AdminVideoTable.test.jsx` | (1) `enableVideoHls(id)` → `put('/admin/videos/{id}/hls')`, `disableVideoHls(id)` → `delete(...)`; hooks invalidate `['admin','videos']`. (2) HLS column: badge per status for rows with a non-null `hlsStatus`, "—" otherwise; ERROR shows the localized `hlsError` reason as a tooltip (FR-11). (3) Toggle only when `isSuperAdmin(user.role)`; ADMIN sees the badge only (test mocks `useAuth`). (4) `useAdminVideos` gets `refetchInterval: data => any row PENDING/PROCESSING ? 5000 : false`. (5) EN/VI keys: `colHls`, `hlsStatus.*`, `hlsError.*` (7 codes), `hlsToggleOn/Off`; the badge passes the existing contrast test if covered | ~110 + ~150 |

### Dependency graph

```
HL-1 ──┬──> HL-3 ──> HL-4 ──> HL-7
HL-2 ──┘      ▲
HL-1 + HL-2 ──┴─────> HL-5
HL-6   (independent)
```

- HL-1, HL-2 and HL-6 can start in parallel.
- HL-3 needs HL-1 (repository, enums, config) and HL-2 (storage, client).
- HL-5 needs HL-1 and HL-2 (`HlsStorage` validation). It can run in parallel with HL-3.
- HL-4 needs HL-3 (`wakeUp()`) and HL-2 (`HlsStorage.delete`).
- HL-7 calls the HL-4 endpoints, so it lands after HL-4.
- HL-6 only reads `hlsUrl`. It is safe before the backend, because the field is absent or null.
- **Rollout rule:**
  1. Create the Harness flag `hls_streaming_enabled` **OFF** before deploying HL-5.
  2. Turn it on only after a test video reaches READY and its playlist plays in Chrome and Safari through the public URL.
  3. While the flag is off, the toggles and the worker can be used freely: nothing reaches viewers.

## Files to Modify

See the Files column above. New files:
- `api/src/main/resources/db/changelog/sql/202610240001-video-source-hls.sql`
- `api/src/main/java/com/canhlabs/funnyapp/enums/HlsStatus.java`, `enums/HlsErrorCode.java`
- `api/src/main/java/com/canhlabs/funnyapp/service/impl/HlsStorage.java`, `HlsTranscodeWorker.java`
- `api/src/main/java/com/canhlabs/funnyapp/client/FfmpegHlsClient.java`
- `api/src/main/java/com/canhlabs/funnyapp/service/HlsPlaybackService.java` + `service/impl/HlsPlaybackServiceImpl.java`
- `api/src/main/java/com/canhlabs/funnyapp/dto/admin/AdminHlsStatusDto.java`
- `webapp/src/hooks/useHlsSource.js`

## Dependencies

### Code Dependencies

- `ProcessFactory` / `DefaultProcessFactory` (ADR-0019 import client)
- `VideoImportWorker` pattern (ADR-0019 D3)
- `DriveFileLocks`, `DriveVideoDownloaderImpl` (Drive force sync)
- `FeatureFlagService` (Harness), `CustomException`, `@AuditLog`

### Service Dependencies

- `ffmpeg` + `ffprobe` on the prod VM (verified 2026-10-24)
- Harness flag `hls_streaming_enabled` (to be created, default OFF)
- npm `hls.js@1.7.3`

## Testing Strategy

### Unit Tests

- `HlsStorageTest`, `FfmpegHlsClientTest`: BR-5, BR-7, BR-9, EC-13, EC-14, EC-15
- `HlsTranscodeWorkerTest`: BR-3, BR-4, BR-6, BR-11, FR-3, FR-4, EC-1, EC-2, EC-6, EC-7, EC-9
- `AdminVideoServiceImplTest`, `DriveVideoDownloaderImplTest`: BR-10, BR-12, EC-4, EC-5, EC-16
- `HlsPlaybackServiceImplTest`, `ConverterTest`, `StreamVideoServiceImplTest`, `VideoCollectionServiceImplTest`: BR-8, BR-13, EC-3, EC-10, EC-11
- `VideoSourceHlsMigrationTest`, `AppPropertiesTest`
- Vitest: `useHlsSource`, `VideoPlayer.hls`, `videoModel`, swiper preload, `AdminVideoTable`

### Integration Tests

- `AdminControllerPermissionTest` (`@WebMvcTest`): ADMIN 403, SUPER_ADMIN 200 on PUT/DELETE
- `VideoStreamControllerTest`: headers, status codes, traversal → 400

### Manual Testing

Owner, after deploy, with the flag still OFF:
1. In the admin table, turn HLS on for one H.264 video. It should reach READY in under 30 s. Open `…/video-stream/hls/{fileId}/playlist.m3u8` in Safari: it plays.
2. Turn HLS on for an iPhone HEVC video. It should end in ERROR "unsupported codec".
3. Turn the flag ON in Harness and reload the feed. Network shows `.m3u8` + `.ts` for the READY video and `/stream/` for the others. Resume works.
4. Delete one segment on the VM during playback. The player keeps playing through Range.
5. Turn HLS off for the video: the directory is gone. Turn the flag OFF: the feed is back to Range.
6. Run `./api/mock-test.sh` (read-only).

## Rollback Plan

- Turn the Harness flag OFF first. Viewers are back on Range at their next list fetch.
- `git revert` the task commits in reverse order (HL-7 … HL-1).
- Columns left by the HL-1 changeset are harmless; they have defaults and nothing reads them after the revert.
- Remove `video-cache/hls/` on the VM to reclaim disk.

## Risks

| Risk | Mitigation |
|------|------------|
| FFmpeg starves the single vCPU | Stream copy only, `Semaphore(1)`, never on a request thread (HL-3) |
| Disk fills during a job | Disk guard before FFmpeg; tmp deleted on failure (HL-3) |
| Path traversal on the public endpoints | Regex + normalize-and-prefix check, tests for `..`, `%2F`, absolute paths (HL-2, HL-5) |
| Segments of a hidden video stay fetchable when the `fileId` is known | Accepted by the spec (BR-13: no DB call per segment); the playlist is refused, and the Range endpoint has the same exposure today |
| Player regression on Range videos | HL-6 (2): existing player tests unchanged; HLS code runs only when `hlsUrl` is set |
| Harness evaluated per video, slowing the feed | Once per request, verified by a test (HL-5 (4)) |
| HL-1 changes a Liquibase file, which the headless auto-merge refuses | Land HL-1 through a local run; the owner pushes |

## Checklist

### Before Starting

- [x] PRD, ADR-0031, spec, sequence diagrams written
- [x] Explorer findings recorded (Key Decisions)
- [x] TASKS.md section added

### Before PR

- [x] `mvn -f api/pom.xml verify` (coverage ≥ `api/.coverage-threshold`)
- [x] `npm --prefix webapp run test`, `npm --prefix webapp run lint`, `npm --prefix webapp run build` (chunk check)
- [x] `BeanConstructorTest` green (`HlsTranscodeWorker`, `FfmpegHlsClient`)

### Before Merge

- [x] Security review (focus `security`: public file serving, process arguments, SUPER_ADMIN checks)
- [x] `docs/tracking.md` updated
- [ ] Harness flag `hls_streaming_enabled` created OFF

## Notes

- **Seven tasks**, within the `/autopilot` plan-mode cap of 8 open tasks (raised from 6 on 2026-10-24), so `/autopilot docs/plans/plan-hls-streaming.md` can run it in one round.
- `/swarm-execute` can run it in waves instead:
  1. HL-1 ∥ HL-2 ∥ HL-6;
  2. HL-3 ∥ HL-5;
  3. HL-4;
  4. HL-7.
- Keep the sequence diagrams in sync if the as-built flow differs.

## Progress Log

| Date | Task | Commit | Note |
|------|------|--------|------|
| 2026-10-24 | — | — | Plan written from spec + 4 explorer reports |
| 2026-10-24 | — | — | Autopilot plan review: approve, 3 advisories folded in (peek-lock-claim job order, downloader deps, EC-8 rests on the conditional UPDATE) |
| 2026-10-24 | HL-1..HL-7 | 5c485b5f..64d66721 | Built by autopilot (4 worker-builders in 2 waves); plan review approve; code review FE approve, BE revise → approve (publish-guard race fixed); QA ×2 347/347; mvn verify 1788 tests, line coverage 96.37%; vitest 610/610 |
