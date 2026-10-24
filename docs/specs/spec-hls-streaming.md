# Feature Specification: HLS Streaming for Selected Videos

<!--
Feature Specification
Filename: docs/specs/spec-hls-streaming.md
Owner: Builder (/builder) or Architect (/architect)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
-->

## Metadata

**Status:** Approved
**Author:** nguyenhuuca
**Date:** 2026-10-24
**Related PRD:** [PRD-hls-streaming](../prd/PRD-hls-streaming.md)
**Related ADR:** [ADR-0031](../adr/0031-hls-streaming.md)
**Sequence diagrams:** [sequence-hls-streaming](../sequence/sequence-hls-streaming.md)

---

## Overview

- **Opt-in per video:** the SUPER_ADMIN turns HLS on or off for one Drive video at a time.
- **Transcoding:** an in-process worker stream-copies the video's `.full` file into `video-cache/hls/{fileId}/`, one job at a time.
- **Playback:**
  - Video DTOs carry `hlsUrl` only when the Harness flag `hls_streaming_enabled` is on and the rendition is READY.
  - The player uses `hlsUrl` when it is set and falls back to the existing Range URL (`embedLink`) on any failure.
  - Range playback, the Guava cache and the download paths keep working as today.

---

## Business Rules

### BR-1: Drive videos only
- HLS can be turned on only for `VideoSource` rows with `source_type = 'google_drive'`.
- Every other row keeps `hls_status = NONE` and its admin DTO shows `hlsStatus = null`.

### BR-2: SUPER_ADMIN only
- Only the SUPER_ADMIN can turn HLS on or off.
- ADMIN can see the status but cannot change it.

### BR-3: Status machine
`hls_status` moves only along these edges. Any other change is a bug.

| From | To | Trigger |
|------|----|---------|
| NONE, ERROR | PENDING | PUT (turn on / retry) |
| PENDING | PROCESSING | Worker claims the job (atomic `UPDATE … WHERE hls_status = 'PENDING'`) |
| PROCESSING | READY | FFmpeg exit 0, playlist exists, temp directory renamed |
| PROCESSING | ERROR | Any failure listed in BR-9 |
| PROCESSING | PENDING | Startup recovery (BR-11) |
| READY, ERROR | PENDING | `.full` replaced by a new download (BR-10) |
| READY | ERROR | Startup check finds the output directory missing (BR-11) |
| any | NONE | DELETE (turn off) |

### BR-4: One job at a time
- At most one FFmpeg or ffprobe process runs at any moment (`Semaphore(1)`).
- Jobs are taken oldest first, ordered by `updated_at`.

### BR-5: Codec allowlist
- `ffprobe` must report video codec `h264`.
- Audio must be `aac` or `mp3`, or there must be no audio stream.
- Anything else ends the job with `UNSUPPORTED_CODEC`. No re-encoding is done.

### BR-6: Disk guard
- Before FFmpeg starts, the usable space on the `video-cache` file store must be at least `size(.full) + app.hls.min-free-disk-bytes`. The default reserve is 1 GB.
- If it is lower, the job ends with `DISK_FULL`.

### BR-7: Output and timeout
- FFmpeg runs with `-c copy -f hls -hls_time 6 -hls_playlist_type vod -hls_segment_filename seg_%d.ts` and writes into `video-cache/hls/{fileId}.tmp/`.
- The job is READY only after all of these:
  - FFmpeg exits 0;
  - `playlist.m3u8` exists;
  - the directory has been renamed to `video-cache/hls/{fileId}/`.
- A run longer than `app.hls.timeout` (default 10 min) is killed and ends with `TIMEOUT`.

### BR-8: `hlsUrl` offer rule
- `hlsUrl` is set when both hold:
  - `FeatureFlagService.isEnabled("hls_streaming_enabled", false)` is true;
  - `hls_status = READY`.
- Otherwise it is `null`.
- The flag is read once per request.
- The rule applies to every place that builds a feed-shaped DTO (`Converter.toVideoDto`): the feed list, video detail and public collection videos.

### BR-9: Error codes
`hls_error` holds one of these codes. It is `null` unless the status is ERROR.

| Code | Condition |
|------|-----------|
| `SOURCE_MISSING` | `video-cache/{fileId}.full` does not exist when the job starts |
| `FFMPEG_NOT_FOUND` | `ffprobe` or `ffmpeg` cannot be started |
| `UNSUPPORTED_CODEC` | BR-5 fails |
| `DISK_FULL` | BR-6 fails |
| `TIMEOUT` | BR-7 timeout |
| `FFMPEG_FAILED` | Non-zero exit code, or no playlist after exit 0 |
| `OUTPUT_MISSING` | Startup check: READY but the directory is gone |

### BR-10: Source replaced
When `DriveVideoDownloaderImpl` writes a new `.full`, still holding the file lock, it:
- always deletes `video-cache/hls/{fileId}/` if it exists;
- if the status was READY or ERROR, sets it to PENDING and wakes the worker.

### BR-11: Startup recovery
On `ApplicationReadyEvent`, the worker:
1. sets every PROCESSING row to PENDING;
2. deletes every `video-cache/hls/*.tmp/` directory;
3. sets every READY row whose directory is missing to ERROR `OUTPUT_MISSING`;
4. deletes every `video-cache/hls/{fileId}/` directory whose row is missing or not READY (orphans left by a DELETE that ran while a download held the lock);
5. then wakes up.

### BR-12: Cleanup
`video-cache/hls/{fileId}/` is deleted in each of these cases:
- DELETE on the HLS endpoint;
- admin video delete (`AdminVideoServiceImpl.deleteVideo`, always, whatever the `deleteLocalFile` flag);
- BR-10.

The deletion runs under `DriveFileLocks`. The `.full` file is touched only by the existing delete-local-file option.

### BR-13: Public serving
- **Playlist:** served only when all of these hold:
  - status READY;
  - video `PUBLISHED`;
  - video not hidden;
  - the file exists.
- **Segment:** served when the name is valid and the file exists in the READY directory. There is no DB lookup per segment.

---

## Functional Requirements

### FR-1: Schema
`video_sources` must get the columns `hls_status` and `hls_error` (see Database Changes). The `VideoSource` entity must map them with a new `HlsStatus` enum.

### FR-2: Admin toggle endpoints
The system must expose `PUT` and `DELETE /v1/funny-app/admin/videos/{id}/hls`, as described in API Changes.
- Both are idempotent.
- Both are annotated with `@AuditLog("enableVideoHls")` and `@AuditLog("disableVideoHls")`.
- A PUT that sets PENDING must call `HlsTranscodeWorker.wakeUp()`.

### FR-3: Worker
`HlsTranscodeWorker` must follow `VideoImportWorker`:
- `wakeUp()` on a virtual thread;
- a `@Scheduled(cron = "0 */5 * * * *")` poll;
- startup recovery (BR-11);
- `Semaphore(1)`.

For each job it must take these steps in this order:
1. claim the file lock in `DriveFileLocks`; if the file is busy, leave the job PENDING;
2. claim the DB row;
3. check that the source exists;
4. run ffprobe;
5. run the disk guard;
6. run FFmpeg;
7. re-read the status;
8. publish, or clean up;
9. release the lock.

Every command must be built with `ProcessBuilder(List<String>)`, never a shell string. `fileId` must pass the existing safe-id check before any path is built.

### FR-4: Publish guard
After FFmpeg exits, the worker must re-read the row.
- If the status is no longer PROCESSING (the owner turned it off, or the video was deleted), it must delete the temp directory and not publish.
- It must never overwrite NONE with READY.

### FR-5: Public HLS endpoints
`VideoStreamController` must serve the playlist and the segments (see API Changes). It must:
- validate path variables before touching the file system;
- check that the resolved path stays inside `video-cache/hls/{fileId}/` (CWE-22).

### FR-6: DTO field
- `VideoDto` must get `hlsUrl` (String, nullable).
- The value is `https://canh-labs.com/api/v1/funny-app/video-stream/hls/{fileId}/playlist.m3u8`, the same host and prefix as `embedLink`, built in `Converter`.
- `Converter.toVideoDto` must take the request's flag value as a parameter. It must not read the flag itself, per video.

### FR-7: Admin DTO
`AdminVideoDto` must get `hlsStatus` (`HlsStatus`, null for non-Drive videos) and `hlsError` (String).

### FR-8: Source replaced hook
`DriveVideoDownloaderImpl` must apply BR-10 after it moves a new `.full` into place. This covers both the scheduler and the force sync, because both go through it.

### FR-9: Player
`VideoPlayer` must keep its single `<video>` element.
- **When `video.hlsUrl` is set:**
  - If `canPlayType('application/vnd.apple.mpegurl')` is non-empty (Safari/iOS), it sets `src = hlsUrl`.
  - Otherwise, it loads `hls.js` with `import('hls.js')`, checks `Hls.isSupported()`, and attaches it.
  - If HLS is not supported, it uses the Range URL.
- **On a fatal HLS error, or a native `error` event while on HLS:** it destroys the instance, sets `src` to the Range URL, and on `loadedmetadata` restores the last `currentTime` and the play state.
- It destroys the instance on unmount and whenever the video changes.
- Resume (`startAt`), view counting and watch-history progress keep using the same element, with no other change.

### FR-10: Swiper preload
`VideoSwiper` must not render the hidden Range preload `<video>` for upcoming videos that have `hlsUrl`.
> **Assumption:** preloading the `.full` bytes for an HLS video wastes bandwidth. hls.js starts quickly enough from the playlist.

### FR-11: Admin table
`AdminVideoTable` must add an HLS column for Drive videos:
- a status badge (`NONE`, `PENDING`, `PROCESSING`, `READY`, `ERROR`);
- the localized error reason as a tooltip on ERROR;
- for the SUPER_ADMIN only (`isSuperAdmin(role)`), a toggle (on = PUT, off = DELETE).

While any row on the page is PENDING or PROCESSING, the admin videos query refetches every 5 s.

### FR-12: Config
`AppProperties` must get an `Hls` section:
- `app.hls.min-free-disk-bytes`, default `1073741824`;
- `app.hls.timeout`, default `10m`.

Both are bound in `application.yaml` with env overrides `HLS_MIN_FREE_DISK_BYTES` and `HLS_TIMEOUT`.

### FR-13: Tech strategy
Add `hls.js` (exact version pinned in `webapp/package.json`) to the JavaScript table in `.claude/rules/tech-strategy.md`, citing ADR-0031.

---

## API Changes

### New Endpoints

#### PUT /v1/funny-app/admin/videos/{id}/hls

| | |
|---|---|
| Auth | JWT; `@PreAuthorize("hasRole('SUPER_ADMIN') and @permissionServiceImpl.hasPermission(authentication.details.permissions, ADMIN)")` (same as `AdminDriveSyncController.java:55`) |
| Body | none |
| Effect | NONE or ERROR → PENDING (clears `hls_error`) and wakes the worker. PENDING, PROCESSING or READY → no change |

Response `200`:
```json
{ "status": "SUCCESS", "data": { "id": 42, "hlsStatus": "PENDING", "hlsError": null } }
```

| Status | Code | Condition |
|--------|------|-----------|
| 400 | `error.hls.notDriveVideo` | The row's `source_type` is not `google_drive` |
| 401 | — | No or invalid JWT |
| 403 | — | Caller is not SUPER_ADMIN, or lacks the ADMIN bit while enforcement is on |
| 404 | `error.video.notFound` | No `video_sources` row with this id (`CustomException` with `NOT_FOUND`) |

#### DELETE /v1/funny-app/admin/videos/{id}/hls

| | |
|---|---|
| Auth | Same as PUT |
| Effect | Any status → NONE, `hls_error = null`. If `DriveFileLocks.tryClaim(fileId)` succeeds, deletes `video-cache/hls/{fileId}/` and releases the lock. If the lock is held, the holder cleans up: a running job discards its output (FR-4), and a running download deletes the directory (BR-10). A running job is not killed |

Response `200`: `{ "status": "SUCCESS", "data": { "id": 42, "hlsStatus": "NONE", "hlsError": null } }`

| Status | Code | Condition |
|--------|------|-----------|
| 400 | `error.hls.notDriveVideo` | Not a Drive video |
| 401 / 403 | — | As for PUT |
| 404 | `error.video.notFound` | Unknown id |

#### GET /v1/funny-app/video-stream/hls/{fileId}/playlist.m3u8

| | |
|---|---|
| Auth | Public (`/v1/funny-app/video-stream/**` in `ALLOW_ALL_METHOD`) |
| `fileId` | Must match `^[A-Za-z0-9_-]{1,128}$` |

| Status | Headers / body | Condition |
|--------|----------------|-----------|
| 200 | `Content-Type: application/vnd.apple.mpegurl`, `Cache-Control: no-cache`, the playlist file | BR-13 holds |
| 400 | empty | `fileId` fails the regex |
| 404 | empty | Unknown `fileId`, status not READY, video hidden or not PUBLISHED, or file missing |

#### GET /v1/funny-app/video-stream/hls/{fileId}/{segment}

| | |
|---|---|
| Auth | Public |
| `segment` | Must match `^seg_\d{1,5}\.ts$` |

| Status | Headers / body | Condition |
|--------|----------------|-----------|
| 200 | `Content-Type: video/mp2t`, `Cache-Control: public, max-age=31536000, immutable`, `Content-Length`, file body via `FileSystemResource` | File exists in `video-cache/hls/{fileId}/` |
| 400 | empty | `fileId` or `segment` fails its regex |
| 404 | empty | File missing |

### Modified Endpoints

#### GET /v1/funny-app/video-stream/list, GET /v1/funny-app/video-stream/{id}, GET /v1/funny-app/collections/{id}/videos

Each `VideoDto` gets one more field:
```json
{ "id": 42, "fileId": "1AbC…", "embedLink": "https://canh-labs.com/api/v1/funny-app/video-stream/stream/1AbC…",
  "hlsUrl": "https://canh-labs.com/api/v1/funny-app/video-stream/hls/1AbC…/playlist.m3u8" }
```
`hlsUrl` is `null` unless BR-8 holds. No other field changes.

#### GET /v1/funny-app/admin/videos

Each `AdminVideoDto` gets `"hlsStatus": "READY" | … | null` and `"hlsError": "UNSUPPORTED_CODEC" | null`.

---

## Database Changes

### New Tables

None.

### Modified Tables

`api/src/main/resources/db/changelog/sql/202610240001-video-source-hls.sql` (picked up by `includeAll`):

```sql
-- HLS rendition state per video (ADR-0031). NONE until the SUPER_ADMIN turns HLS on.
ALTER TABLE video_sources
    ADD COLUMN hls_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN hls_error  VARCHAR(40);

ALTER TABLE video_sources
    ADD CONSTRAINT chk_video_sources_hls_status
        CHECK (hls_status IN ('NONE', 'PENDING', 'PROCESSING', 'READY', 'ERROR'));

-- Worker queue lookups touch only the few in-flight rows.
CREATE INDEX idx_video_sources_hls_queue
    ON video_sources (updated_at)
    WHERE hls_status IN ('PENDING', 'PROCESSING');
```

The `ALTER TABLE` with a constant default is a metadata-only change in PostgreSQL 11+ (no table rewrite).

### No Changes

`video_access_stats`, `video_collection*`, `watch_history` and every other table.

---

## Security Requirements

### Authentication

- The toggle endpoints need a valid JWT.
- The public HLS endpoints need none, like the Range endpoint. No token is ever put in an HLS URL (CWE-598).

### Authorization

- PUT and DELETE are for the SUPER_ADMIN only, using the expression in API Changes.
- `GET /admin/videos` keeps its ADMIN+ access.
- The playlist endpoint enforces BR-13, so a hidden or unpublished video is not served even if its `fileId` is known.

### Data Validation

- `fileId` must match `^[A-Za-z0-9_-]{1,128}$` and `segment` must match `^seg_\d{1,5}\.ts$`.
- After resolving, the path must still start with the normalized `video-cache/hls/{fileId}/` (CWE-22).
- FFmpeg and ffprobe arguments are a fixed list; only the validated path is inserted (CWE-78).
- Toggles are written to the audit log (`@AuditLog`).

### Sensitive Data

None. HLS files are the same public video bytes the Range endpoint already serves.

---

## Caching Impact

| Cache | Impact |
|-------|--------|
| `VideoCacheImpl` / `ChunkIndexCacheImpl` | None. HLS files are served straight from disk via `FileSystemResource`, and the OS page cache keeps hot segments |
| `StatsCacheImpl` | None. HLS requests do not record cache hits or misses |
| HTTP / browser | Segments are immutable for a year. The playlist uses `no-cache`, so a re-transcode (BR-10) is picked up |
| Harness SDK | The flag is evaluated through the SDK's local cache, with no network call per request |

---

## Frontend Changes

### New Routes

None.

### Modified Components

| File | Change |
|------|--------|
| `webapp/src/utils/videoModel.js` | `mapApiVideo` adds `hlsUrl: raw.hlsUrl || null` |
| `webapp/src/components/video/VideoPlayer.jsx` | FR-9; HLS attach and fallback live in a new hook `webapp/src/hooks/useHlsSource.js` |
| `webapp/src/components/video/VideoSwiper.jsx` | FR-10 |
| `webapp/src/components/admin/AdminVideoTable.jsx` | FR-11: HLS column, badge, SUPER_ADMIN toggle |
| `webapp/src/api/admin.js` | `enableVideoHls(id)` → `PUT /admin/videos/{id}/hls`; `disableVideoHls(id)` → `DELETE` |
| `webapp/src/hooks/useAdmin.js` | `useEnableVideoHls`, `useDisableVideoHls` invalidate `['admin', 'videos']`; the admin videos query gets `refetchInterval: 5000` while any row is PENDING or PROCESSING |
| `webapp/src/i18n/locales/{en,vi}/admin.json` | `colHls`, `hlsStatus.{NONE,PENDING,PROCESSING,READY,ERROR}`, `hlsError.{…BR-9 codes}`, `hlsToggleOn`, `hlsToggleOff` |
| `webapp/package.json` | Add `hls.js` (exact version) |

### State Management

| Query key | Change |
|-----------|--------|
| `['videos', …]` (`useVideos`, `staleTime` 5 min) | Unchanged. Carries `hlsUrl`. A flag flip reaches a client at its next refetch |
| `['admin', 'videos', page, status]` | Unchanged key; conditional 5 s `refetchInterval` |

---

## Event / Job Changes

### Domain Events

None. The worker is woken by a direct `wakeUp()` call from the admin service and the downloader.

### Scheduled Jobs

| Job | Schedule | Behaviour |
|-----|----------|-----------|
| `HlsTranscodeWorker.poll()` | `0 */5 * * * *` | Drains PENDING jobs one by one (BR-4). It is a fallback for a missed `wakeUp()` or a job left PENDING because its file was locked |
| `HlsTranscodeWorker.onApplicationReady()` | Once at startup | BR-11 recovery, then `wakeUp()` |

---

## Non-Functional Requirements

### Performance

| Operation | Target |
|-----------|--------|
| Transcode, 500 MB H.264 on the prod VM | < 30 s end to end (probe + guard + copy + rename) |
| Playlist request | p95 < 50 ms (one indexed row read + static file) |
| Segment request, time to first byte | p95 < 50 ms (static file, no DB) |
| Feed list with the flag | +< 5 ms vs today (one SDK evaluation per request) |
| PUT / DELETE toggle | p95 < 200 ms (excludes the transcode itself) |

### Availability

- Range playback never depends on HLS. The flag off, ERROR, a missing file or an unsupported browser all lead to Range.
- A Harness outage means the flag reads `false`, so everyone plays through Range.

### Scalability

- One job at a time on one instance; this is a single-replica app (HPA 1–1).
- Disk is the limit, and the owner controls it per video. There is no automatic growth.

---

## Edge Cases

### EC-1: `.full` missing
PUT on a video whose `.full` has not been downloaded, or was deleted, results in PENDING, then ERROR `SOURCE_MISSING`. Playback stays Range.

### EC-2: READY but the directory is gone
- The playlist returns 404, so the player falls back to Range.
- At the next startup the row becomes ERROR `OUTPUT_MISSING`.
- There is no self-healing: the owner retries with PUT.

### EC-3: Hidden or unpublished video
- PUT is allowed.
- The feed already leaves the video out, and the playlist returns 404 while it is hidden or not PUBLISHED.
- Unhiding it makes the existing READY rendition available again with no re-transcode.

### EC-4: Force sync or scheduler replaces `.full`
- Any old directory is deleted.
- For a READY or ERROR row, the status becomes PENDING and the worker is woken (BR-10).
- For NONE, PENDING or PROCESSING, the status does not change.

### EC-5: PUT while PENDING, PROCESSING or READY
200 with the current status; there is no second job.

### EC-6: DELETE while PROCESSING
- The status becomes NONE at once.
- FFmpeg runs to the end or to the timeout. FR-4 then deletes `.tmp` and does not publish.
- The final status stays NONE.

### EC-7: File locked when the worker picks a job
The download or delete holds `DriveFileLocks`, so the job stays PENDING and is retried at the next wake-up or poll.

### EC-8: Two concurrent PUTs
Both return 200 PENDING. The atomic claim `UPDATE … WHERE hls_status = 'PENDING'` lets exactly one worker run move it to PROCESSING.

### EC-9: Restart during PROCESSING
BR-11: the row goes back to PENDING, the leftover `.tmp` is deleted, and the job runs again.

### EC-10: Flag turned off while someone is watching
- The current playback continues, because files are still served.
- The next list fetch returns `hlsUrl = null`, so that client uses Range from then on.

### EC-11: Harness unreachable
`isEnabled` returns the default `false`, so `hlsUrl` is null everywhere.

### EC-12: Fatal HLS error during playback
The player switches to the Range URL and resumes at the last `currentTime`. If it was playing, it keeps playing.

### EC-13: Path traversal and bad names
`fileId` with `..`, `/`, `%2F` or other characters outside the regex, or a segment like `../x.ts` or `seg_1.m3u8`, returns 400 with no file system access.

### EC-14: Video without an audio stream
Allowed by BR-5. FFmpeg copies the video stream only.

### EC-15: FFmpeg or ffprobe missing (e.g. K8s image)
ERROR `FFMPEG_NOT_FOUND`; Range keeps working.

### EC-16: Delete a video with an HLS rendition
`deleteVideo` always removes `video-cache/hls/{fileId}/` (BR-12), even when `deleteLocalFile = false`, so no orphan directory is left.

---

## Acceptance Criteria

- [ ] Changeset `202610240001-video-source-hls.sql` applies on an existing DB. Every existing row reads `hls_status = 'NONE'`, `hls_error = NULL`.
- [ ] PUT as SUPER_ADMIN on a Drive video with status NONE → 200 PENDING. The worker runs, and the status reaches READY with `video-cache/hls/{fileId}/playlist.m3u8` + `seg_*.ts`.
- [ ] PUT as ADMIN → 403. PUT on a non-Drive row → 400 `error.hls.notDriveVideo`. PUT on an unknown id → 404.
- [ ] PUT on ERROR → PENDING with `hls_error` cleared. PUT on PENDING, PROCESSING or READY → 200, unchanged.
- [ ] DELETE → NONE and the directory is gone. DELETE during PROCESSING → the final status is NONE and no directory is published. DELETE never returns 409.
- [ ] HEVC source → ERROR `UNSUPPORTED_CODEC`. Low disk (reserve set above free space in a test) → ERROR `DISK_FULL`, and FFmpeg is not started.
- [ ] A timeout (set to 1 s in a test with a stub process) kills the process → ERROR `TIMEOUT`, and `.tmp` is removed.
- [ ] Only one FFmpeg process at a time, with 3 videos queued.
- [ ] Restart with a PROCESSING row → it becomes PENDING, then READY. A READY row with a deleted directory → ERROR `OUTPUT_MISSING`.
- [ ] Re-download of a READY video's `.full` → the status becomes PENDING and the old segments are deleted.
- [ ] Flag on + READY → `hlsUrl` is set in the list, the detail and the collection videos. Flag off, or not READY → `hlsUrl` is null.
- [ ] Playlist for a hidden or unpublished READY video → 404. Segment with `..` → 400. Segment response has `video/mp2t` and the immutable `Cache-Control`.
- [ ] Chrome desktop: an HLS video plays through hls.js (network shows `.m3u8` + `.ts`, no `/stream/`). Safari/iOS: native HLS plays.
- [ ] Forcing a fatal HLS error (e.g. deleting a segment mid-play) → playback continues through Range at about the same time.
- [ ] Resume (`startAt`) and watch-history progress work for an HLS video like for a Range video.
- [ ] `hls.js` is in a separate chunk. The main bundle size is unchanged (±1 KB).
- [ ] Admin table: SUPER_ADMIN sees the toggle and ADMIN sees only the badge. The status refreshes on its own until READY or ERROR. Error reasons appear in EN and VI.
- [ ] `mvn verify` green with coverage ≥ the threshold. `npm run test` and `npm run lint` green.

---

## Open Questions

None. All PRD and ADR questions are closed. Decisions from the spec Q&A (2026-10-24):
- the toggles are idempotent, and PUT on ERROR retries;
- the `app.hls.*` config is separate from video-import;
- the four edge cases EC-1..EC-4 are confirmed;
- `@AuditLog` on the toggles;
- the playlist is served only for PUBLISHED, non-hidden videos.

> **Assumption:** there is no `app.hls.enabled` kill switch for the worker. The Harness flag controls playback, and the owner controls transcoding per video.

---

## Version History

| Date | Author | Change |
|------|--------|--------|
| 2026-10-24 | nguyenhuuca | Initial spec from PRD-hls-streaming + ADR-0031 |
