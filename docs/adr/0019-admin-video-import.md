# ADR-0019: Admin Video Import (YouTube / Facebook → Google Drive)

**Status:** Proposed
**Date:** 2026-10-10
**Deciders:** nguyenhuuca
**Related:** [PRD-admin-video-import](../prd/PRD-admin-video-import.md) · [plan-admin-video-import](../plans/plan-admin-video-import.md)

## Context

Videos enter the app only one way today: someone drops a file into the shared Google Drive folder (`AppConstant.FOLDER_ID`), and `AppScheduler.syncDriveVideos` (cron `0 */15 * * * *`) picks it up:

1. lists files with `createdTime >=` now − 60 min
2. skips if `video-cache/{driveFileId}.full` exists, otherwise downloads it
3. FFmpeg thumbnail → `saveInfo(driveFileId, fileName without extension, …)` → `video_sources` row (`source_type = google_drive`, title = file name, ChatGPT poem as description)

The admin wants to paste one or many YouTube / Facebook URLs into an admin page and have the server download them (yt-dlp, already installed on the host), upload them into that same Drive folder, and let the existing cron bring them into the app. Downloads can run immediately or at a scheduled time, and progress is shown by polling.

Constraints:
- Single VM, 1 vCPU, prod runs the JAR directly on the host (not in Docker) → `yt-dlp` and `ffmpeg` on the host `PATH` are usable.
- Drive client: Service Account, scope `DriveScopes.DRIVE` (full), folder shared with the Service Account (reads work today).
- No job/queue infrastructure exists (no ShedLock, no job table); scheduling is `@Scheduled` in `AppScheduler`.

## Decision

### D1 — The Drive folder stays the single entry point; the cron does the ingest

The import job only **downloads → uploads to the Drive folder → deletes the local temp file**. It never writes `video_sources` itself. The existing cron ingests the file within ≤ 15 min, exactly like a manually uploaded file (owner decision 2026-10-10).

- The Drive **file name is the contract for the title**: the job uploads `"{title}.mp4"`; the cron strips the extension and stores it as `video_sources.title`.
- The job records `drive_file_id`; the admin UI shows "Đã lên app" once a `video_sources` row with `source_id = drive_file_id` exists, "Chờ đồng bộ" before that.
- Trade-off accepted: the bytes travel twice (server → Drive → server) and the video appears up to 15 min after upload. In return there is one ingest path, no duplicate-ingest race, and Drive remains the source of truth.

### D2 — Upload with the existing Service Account; OAuth refresh token as fallback

Upload uses the existing `Drive` bean: `files.create` with `parents = [FOLDER_ID]`, resumable media upload (8 MB chunks) and a progress listener.

**Known risk:** Service Accounts have no storage quota. Creating a file in a folder that lives in a *personal* My Drive is commonly rejected with `403 storageQuotaExceeded`, even when the folder is shared with the Service Account (reads still work). Shared Drives (Workspace) do not have this limit.

→ Phase 0 of the plan is a **PoC upload** of a small file with the Service Account. If it fails, switch the uploader (only the uploader) to an OAuth 2.0 user credential of the folder owner: a refresh token obtained once (consent screen, scope `drive.file`) and stored in env `GOOGLE_OAUTH_CLIENT_ID / _SECRET / _REFRESH_TOKEN`. The cron keeps using the Service Account for reads. `DriveUploader` is an interface so either implementation can be wired by config.

**PoC result (VI-0, 2026-10-10):** Service Account lists the folder (200) and has `canAddChildren = true`, but `files.create` returns **`403 storageQuotaExceeded`** (folder is in a personal My Drive, not a Shared Drive). → **Decision: `OAuthDriveUploader`** with the folder owner's refresh token, scope `https://www.googleapis.com/auth/drive` (`drive.file` cannot add children to a folder the app did not create). The OAuth consent screen must be **published "In production"** (unverified is fine for the owner's own account) — in "Testing" mode refresh tokens expire after 7 days. Helper: `scripts/google/get-drive-refresh-token.py`.

### D3 — Persistent job table + in-process worker (no new infrastructure)

Table `video_import_jobs` (Liquibase):

| Column | Notes |
|--------|-------|
| `id` bigserial | |
| `source_url` text, `normalized_url` text | normalized = canonical form (YouTube `watch?v=ID`, Facebook reel/video id) |
| `platform` varchar(16) | `YOUTUBE` / `FACEBOOK` |
| `requested_title` varchar(200) null | admin input — wins |
| `resolved_title` varchar(200) null | from yt-dlp metadata |
| `status` varchar(16) | `PENDING`, `DOWNLOADING`, `UPLOADING`, `DONE`, `FAILED`, `CANCELLED` |
| `progress_pct` smallint, `downloaded_bytes`, `total_bytes` bigint | updated at most every 2 s |
| `scheduled_at` timestamptz | `now()` for "run now" |
| `started_at`, `finished_at` timestamptz | |
| `drive_file_id` varchar(128) null | |
| `error_code` varchar(48), `error_message` varchar(500) | sanitized, never raw stderr |
| `attempts` smallint default 0 | |
| `created_by` bigint, `created_at`, `updated_at` | |

Indexes: `(status, scheduled_at)`; partial unique `normalized_url WHERE status IN ('PENDING','DOWNLOADING','UPLOADING')` (no duplicate active import).

Worker (`VideoImportWorker`):
- `@Scheduled(cron = "0 */5 * * * *")` (every 5 minutes on the clock; scheduled times must be on a 5-minute slot, claim allows 1 min of DB clock slack) **and** an immediate wake-up after "run now" / create-with-no-schedule / retry.
- Claims one due job atomically: `UPDATE … SET status='DOWNLOADING', started_at=now(), attempts=attempts+1 WHERE id = (SELECT id … WHERE status='PENDING' AND scheduled_at <= now() ORDER BY scheduled_at, id LIMIT 1 FOR UPDATE SKIP LOCKED) RETURNING id`.
- **Concurrency 1** by default (`app.video-import.max-concurrent`, guarded by a `Semaphore`, no `synchronized`): 1 vCPU, disk and bandwidth shared with streaming.
- Runs on a virtual thread; the job itself is blocking I/O + one child process.
- On startup, jobs left in `DOWNLOADING`/`UPLOADING` (crash/deploy) go back to `PENDING` if `attempts < 3`, else `FAILED (INTERRUPTED)`.

### D4 — yt-dlp invocation (hardened)

`ProcessBuilder` with an argument list (never a shell):

```
yt-dlp --ignore-config --no-playlist --use-extractors youtube,facebook
       --newline --progress-template "download:%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s"
       -f "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720]/b" --merge-output-format mp4
       --max-filesize 1G -o "{workDir}/{jobId}/video.%(ext)s" -- {url}
```

- URL validated before it is stored: `https` only, host allowlist (`youtube.com`, `www.youtube.com`, `m.youtube.com`, `youtu.be`, `facebook.com`, `www.facebook.com`, `m.facebook.com`, `fb.watch`), length ≤ 2048. `--use-extractors` disables the generic extractor (no fetching arbitrary hosts → SSRF guard); `--` stops option injection.
- Progress parsed from stdout lines; stderr kept in a bounded ring buffer (last 20 lines) for the log, mapped to an `error_code` for the UI.
- Timeouts: metadata 60 s, download 30 min → `destroyForcibly()`. Cancel destroys the process.
- Disk guard: refuse to start if free space in `app.video-import.work-dir` (default `/opt/data/imports`) < 2 GB.
- Optional cookies file (`app.video-import.cookies-path`, for login-only Facebook videos), file mode 600, never logged.

### D5 — Title resolution

1. Admin title, if given.
2. Else `yt-dlp -J --skip-download` metadata `title` (also exposed as `POST /admin/video-imports/preview` so the UI can fill the field before submit).
3. Else `"{platform}-{videoId}"`.

Sanitized before upload: strip control chars and `/\:*?"<>|`, collapse whitespace, max 120 chars.

### D6 — Progress by polling

FE polls `GET /admin/video-imports` with `refetchInterval: 2000` **only while at least one job is `PENDING` (due) / `DOWNLOADING` / `UPLOADING`**, stops otherwise; React Query already pauses when the tab is hidden. Progress = phase + percent (download 0–100 %, then upload 0–100 %). SSE (ADR-0017) is not reused: admin-only, few users, polling every 2 s is cheap and simpler.

## Consequences

**Positive**
- No change to the ingest path, thumbnails, poems or streaming; a manually uploaded Drive file and an imported one behave the same.
- Jobs survive restarts/deploys; scheduled imports work across deploys.
- Hardened process execution (allowlist, no shell, timeouts, extractor allowlist).

**Negative / risks**
- Up to 15 min extra latency, double transfer (D1, accepted).
- Service Account upload may be rejected → OAuth fallback (D2), one more secret to manage.
- yt-dlp breaks when YouTube/Facebook change → host cron `yt-dlp -U` daily; failures surface as `FAILED (EXTRACTOR_ERROR)`, admin can retry after update.
- Legal: downloading from YouTube/Facebook may break their Terms of Service and copyright; the owner is responsible for importing only content they have rights to.
- K8s/Helm image does not contain `yt-dlp`/`ffmpeg` (same as today for ffmpeg); VM is the supported runtime.

## Alternatives considered

| Option | Why not |
|--------|---------|
| Ingest locally right after upload (shared helper with cron) | Faster, but owner chose a single ingest path (D1) |
| Spring Batch / Quartz | Heavy for one job type on one VM |
| In-memory queue only | Loses scheduled jobs on deploy |
| SSE for progress | Admin-only, low volume; polling is simpler (D6) |
| Run yt-dlp in a separate container | Prod is not containerized |
