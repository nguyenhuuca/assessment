# ADR-0031: Opt-in HLS Streaming per Video, Behind a Feature Flag

## Metadata

**Status:** Accepted (2026-10-24) · **Date:** 2026-10-24 · **Deciders:** nguyenhuuca · **Tags:** api, infrastructure, frontend
**Related PRD:** [PRD-hls-streaming](../prd/PRD-hls-streaming.md) · **Supersedes:** N/A (the earlier ADR-0006 draft was deleted unaccepted in `196e9f54`) · **Superseded By:** N/A

**Tech Strategy:** ✅ Follows Golden Path (Spring Boot, Liquibase, FFmpeg already in the stack for thumbnails, Harness FF, Virtual Threads). One new frontend dependency: `hls.js`.

---

## Context

- **Drive videos play only through HTTP Range.**
  - The DTO sets `embedLink` to `…/video-stream/stream/{sourceId}` (`Converter.java:73-74`).
  - `StreamVideoServiceImpl` serves byte ranges of `video-cache/{fileId}.full` through the Guava `VideoCache`.
- **The owner wants to try HLS on the real app** (PRD Problem Statement). This is a learning feature, not a fix.
- **Disk is nearly full on the prod VM.** An HLS copy of a video costs about 1× its `.full` size, so only videos the SUPER_ADMIN picks may get one (PRD).
- **The VM has 1 vCPU.** An earlier incident came from starving the virtual-thread scheduler (memory: prod VT deadlock). Heavy or parallel FFmpeg work is not acceptable.
- **Two download paths write `.full` files:**
  - the scheduler (`VideoStorageServiceImpl.downloadFileFromFolder`);
  - the admin force sync (`DriveSyncServiceImpl:199`).

  Both go through `DriveVideoDownloader` and claim the file in `DriveFileLocks`.
- **Admin delete (#58)** removes the `.full` file through `VideoStorageServiceImpl.deleteIfEligible`, under the same lock (`AdminVideoServiceImpl.java:85-92`).
- **There is already an in-process job pattern:** `VideoImportWorker` (ADR-0019). It uses:
  - a DB status as the queue;
  - a `Semaphore` to bound concurrency;
  - `wakeUp()` on a virtual thread;
  - a 5-minute poll;
  - startup recovery of interrupted jobs.
- **Feature flags** are read through `FeatureFlagService.isEnabled(flag, default)` (Harness, `FeatureFlagServiceImpl.java:46`). The default is used when Harness is unreachable.
- **Every `/v1/funny-app/video-stream/**` path is public** (`AppConstant.java:74-75`). No token goes in a stream URL (`videoModel.js` `buildStreamUrl`, CWE-598).
- `ffmpeg` and `ffprobe` are both installed on the VM (owner checked 2026-10-24). The Docker image has neither.

---

## Decision Drivers

1. **Disk:** HLS output only for selected videos, and never start a job that could fill the disk.
2. **CPU:** at most one FFmpeg process; no re-encoding; nothing on the request thread.
3. **Safe rollback:** one switch turns HLS off for everyone without a deploy, and Range keeps working in every failure case.
4. **Fit the existing code:** reuse the import-worker pattern, `DriveFileLocks`, the public stream path and `FeatureFlagService`; no new infrastructure.
5. **Security:** no path traversal through file names, no shell injection through FFmpeg arguments, toggles limited to the SUPER_ADMIN.

---

## Considered Options

### Option 1: Transcode every video at ingest (the old ADR-0006 plan)

Hook FFmpeg into the download pipeline, then backfill every existing video.

| Pros | Cons |
|------|------|
| Every video gets HLS, no admin step | About 2× disk for every video: does not fit the current disk |
| No per-video UI | A bulk backfill on 1 vCPU competes with requests |
| | Has to hook both download paths and the force sync locks |

### Option 2: Segment on the fly per request

Run FFmpeg when a playlist or segment is requested, and keep nothing on disk (or cache it briefly).

| Pros | Cons |
|------|------|
| No extra disk | One FFmpeg per viewer per seek: unbounded CPU on 1 vCPU |
| No job queue | Slow first frame; hard to make segments stable and cacheable |

### Option 3: Per-video opt-in, transcoded by an in-process single-job worker, global flag checked by the server *(chosen)*

The SUPER_ADMIN turns HLS on for one video. A worker built like `VideoImportWorker` stream-copies it into segments, one job at a time. The API offers `hlsUrl` only when the Harness flag is on and the video is READY.

| Pros | Cons |
|------|------|
| Disk is spent only on chosen videos, and a guard refuses a job that does not fit | Manual step per video |
| One FFmpeg at a time, stream copy only: low CPU | Videos with HEVC/VP9 etc. stay Range-only (no re-encode) |
| Flag off or any error → Range, which is untouched | Two serving paths to keep working |
| Reuses an existing, tested worker pattern | Flag changes reach clients at the next list fetch, not instantly |

### Option 4: External transcoding / object storage (e.g. a cloud media service + bucket + CDN)

| Pros | Cons |
|------|------|
| No load on the VM; ABR and CDN for free | New paid infrastructure and credentials; data leaves the system boundary |
| | Far beyond a learning feature |

---

## Decision Outcome

**Chosen Option:** Option 3. HLS is opt-in per video and made by an in-process single-job worker. A global flag decides whether the server offers it.

**Rationale:**
- Options 1 and 2 break drivers 1 and 2 on this VM.
- Option 4 breaks driver 4 and the data-routing rule.
- Option 3 limits disk and CPU use to what the owner chooses.
- It keeps Range as the fallback for every case.
- It is built from patterns the codebase already has.

The trade-off we accept is a manual step per video and Range-only playback for codecs that cannot be stream-copied.

### D1. HLS state lives on `video_sources`

- Add two columns to `video_sources` in one SQL changeset (`includeAll`):
  - `hls_status`: `NONE | PENDING | PROCESSING | READY | ERROR`, `NOT NULL`, default `NONE`;
  - `hls_error`: short text with the reason.
- There is no separate table: one video has at most one HLS rendition in v1.
- The DB status is the job queue.

### D2. Storage layout and atomic publish

- Output goes to `video-cache/hls/{fileId}/` and holds `playlist.m3u8` + `seg_N.ts`.
- FFmpeg writes into `video-cache/hls/{fileId}.tmp/`. The worker renames the directory to its final name only after:
  - FFmpeg exits with code 0;
  - the playlist exists.

  Then it sets READY. A reader never sees a half-written rendition.
- On failure, the worker deletes the temp directory.
- `.full` files and their layout do not change.

### D3. One in-process worker, one job at a time

- `HlsTranscodeWorker` follows `VideoImportWorker` (ADR-0019):
  - `Semaphore(1)`;
  - `wakeUp()` on a virtual thread after a toggle;
  - a 5-minute poll as a fallback.
- **Startup recovery** resets `PROCESSING` to `PENDING` and deletes leftover `*.tmp` directories.
- The worker claims the file in `DriveFileLocks` for the whole job. If the file is busy (download or delete), the job stays `PENDING` for the next poll.
- FFmpeg has a hard timeout. When it expires, the worker kills the process and sets ERROR "timeout".

### D4. Probe, guard, then stream copy

The order of steps is fixed:
1. **Probe:** run `ffprobe`. Accept only H.264 video plus AAC or MP3 audio (or no audio). Anything else → ERROR "unsupported codec".
2. **Disk guard:** free space must be at least `.full` size + 1 GB. Otherwise → ERROR "not enough disk".
3. **Segment:** run `ffmpeg -c copy -f hls`, 6-second segments, VOD playlist.

Rules for both commands:
- Build them with `ProcessBuilder` from a list of arguments, never a shell string (CWE-78).
- The `fileId` is validated with the existing safe-id check before any path is built.

### D5. The server decides, the client follows

- The video DTO gets one field, `hlsUrl`. It is set only when both hold:
  - `FeatureFlagService.isEnabled("hls_streaming_enabled", false)`;
  - `hls_status = READY`.
- The flag is read **once per list or detail request**, not once per video.
- The client never reads the flag. When `hlsUrl` is null, it plays `embedLink` (Range) as today.
- If Harness is unreachable, the default `false` keeps everyone on Range.
- The flag is global. The Harness target is the caller or `system` (guest), and that does not change the result.

### D6. Endpoints

| Path | Access | Notes |
|------|--------|-------|
| `GET /v1/funny-app/video-stream/hls/{fileId}/playlist.m3u8` | Public (existing `/video-stream/**` rule) | `application/vnd.apple.mpegurl`, short cache |
| `GET /v1/funny-app/video-stream/hls/{fileId}/{segment}` | Public | Name must match `^seg_\d{1,5}\.ts$`; `video/mp2t`; `Cache-Control: public, max-age=31536000, immutable` |
| `PUT /v1/funny-app/admin/videos/{id}/hls` | SUPER_ADMIN (same expression as the Drive force sync run, `AdminDriveSyncController.java:55`) | Drive videos only; sets `PENDING`, wakes the worker |
| `DELETE /v1/funny-app/admin/videos/{id}/hls` | SUPER_ADMIN | Sets `NONE`, removes the directory |
| `GET /v1/funny-app/admin/videos` (existing) | ADMIN+ | Adds `hlsStatus`, `hlsError` to the admin DTO |

- Files are served only when they resolve inside `video-cache/hls/{fileId}/` after normalisation (CWE-22).
- They are served even when the flag is off. Players that already loaded a playlist can then finish, and nobody new is offered one (D5).

### D7. Cleanup

- `DELETE …/hls` and the admin video delete (#58) both remove `video-cache/hls/{fileId}/` under the file lock.
- If a job is running, it re-reads the status after FFmpeg exits. If the status is no longer `PROCESSING`, it deletes its output instead of publishing it.

### D8. Frontend playback

- `VideoPlayer` keeps its single `<video>` element.
- When `hlsUrl` is set:
  - on Safari (`canPlayType('application/vnd.apple.mpegurl')`), it uses native HLS;
  - otherwise, it loads `hls.js` with a dynamic `import()` and attaches it.
- On a fatal HLS error, it destroys the instance, switches `src` to the Range URL and restores `currentTime`.
- Resume position and watch history read the same element, so both paths behave the same.
- `VideoSwiper` attaches HLS only to the active slide and destroys it when the slide changes.

### Quantified Impact

| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| Disk per selected video | 1× (`.full`) | ≈ 2× | Only for selected videos; toggle off returns it |
| Transcode CPU | none | seconds per video | Stream copy, one job at a time |
| Main JS bundle | — | +0 KB | `hls.js` (~150 KB gzip) loads only when an HLS video plays |
| Playback with the flag off | Range | Range | No change |

---

## Consequences

**Positive:**
- The owner can try HLS on chosen videos in prod, and one switch turns it off.
- Range playback, the Guava cache and the download paths do not change.
- The worker, the locks and the flag reuse code that already runs in prod.

**Negative:**
- Each selected video uses about twice its disk space while HLS is on.
- Two serving paths and two player paths must be tested.
- Videos with HEVC, VP9 or other codecs cannot use HLS in v1.
- A flag change reaches open clients only at the next video-list fetch (up to the 5-minute `staleTime` in `useVideos`).

**Risks:**
- *Disk fills during a job*: mitigated by the guard (D4), one job at a time, and the temp directory deleted on failure.
- *FFmpeg hangs*: mitigated by the timeout and kill (D3); the job goes to ERROR, playback stays Range.
- *Delete races a running job*: mitigated by the shared `DriveFileLocks` claim and the status re-check after FFmpeg exits (D7).
- *K8s/Helm deploy has no FFmpeg*: jobs end in ERROR "ffmpeg not found" and Range keeps working. Adding FFmpeg to the image is out of scope.

---

## Validation

- [ ] Security review of the public HLS endpoints (path traversal, MIME types) and the FFmpeg arguments
- [ ] Transcode of a real prod-size H.264 video stays under the PRD target (< 30 s for 500 MB)
- [ ] Flag off → every DTO has `hlsUrl = null`; flag on → only READY videos have it
- [ ] Tech Strategy alignment confirmed (`hls.js` added to the frontend table)
- [ ] Related plan document created: `docs/plans/plan-hls-streaming.md`

---

## Links

- [PRD-hls-streaming](../prd/PRD-hls-streaming.md)
- [Implementation Plan](../plans/plan-hls-streaming.md) (to be written by `/swarm-plan`)
- [ADR-0019 Admin Video Import](0019-admin-video-import.md): the worker pattern reused in D3
- [ADR-0015 Bitwise Permissions](0015-bitwise-permission-system.md): the SUPER_ADMIN check in D6
- [RFC 8216: HTTP Live Streaming](https://datatracker.ietf.org/doc/html/rfc8216)

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-10-24 | nguyenhuuca | Initial draft |
| 2026-10-24 | nguyenhuuca | Accepted |
