# Plan: Admin Video Import (YouTube / Facebook → Google Drive)

<!--
Implementation Plan
Filename: docs/plans/plan-admin-video-import.md
Owner: Builder (/builder)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer), Security Auditor (/security-auditor)
-->

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-10-10
**Related PRD:** [PRD-admin-video-import](../prd/PRD-admin-video-import.md)
**Related ADR:** [ADR-0019](../adr/0019-admin-video-import.md) (proposed)

## Verified Facts (exploration 2026-10-10)

| Area | Fact | Impact |
|------|------|--------|
| Drive client | `ConfigBean.googleDrive()` Service Account, scope `DriveScopes.DRIVE` (full) | Upload API available; quota risk → VI-0 PoC |
| Folder | `AppConstant.FOLDER_ID` hardcoded | Upload into the same id |
| Cron | `AppScheduler.syncDriveVideos` `0 */15 * * * *`, window `createdTime >= now-60m`, skip if `video-cache/{id}.full` exists | Uploaded file is ingested by the next run |
| Title | `saveInfo(id, file.getName().replaceFirst("[.][^.]+$", ""), …)` | Drive file name = title contract |
| Dedupe | `videoSourceRepository.existsBySourceId(driveFileId)` | Use for "Đã lên app" |
| Runtime | Prod = JAR on host via `startServer.sh` (no container); yt-dlp installed on host by owner | `ProcessBuilder("yt-dlp", …)` works |
| Process pattern | `FfmpegServiceImpl` uses `ProcessBuilder`, **no timeout** | New runner adds timeouts / destroy |
| Scheduling | `@EnableScheduling` + `@EnableAsync`, no ShedLock, no job table | New table + `FOR UPDATE SKIP LOCKED` claim |
| Admin BE | `AdminController` class-level `@PreAuthorize("hasRole('ADMIN')")`, `@HasPermission(perm = Permission.ADMIN)`, `@AuditLog`, `Page<T>` + `@PageableDefault` | Same conventions |
| Admin FE | `AdminView` TABS (VIDEO_VAULT, USER_ACCOUNTS, FLAGGED_LOGS, COMMENTS); `api/admin.js`; `hooks/useAdmin.js` (no polling yet); `.admin-*` CSS | New tab `IMPORT`, first `refetchInterval` use |
| Last migration | `202610070001-users-password-login.sql` | New `202610100001-create-video-import-jobs.sql` |

---

## Implementation Steps

### Phase 0 — PoC Drive upload (0.25 d) — `VI-0` ⚠️ gate
- [ ] Small test (dev profile / one-off runner) uploading a 1 MB file into `FOLDER_ID` with the Service Account
- [ ] Success → `ServiceAccountDriveUploader`; `403 storageQuotaExceeded` → `OAuthDriveUploader` (owner creates OAuth client, consents once, stores refresh token in `.env`) — record result in ADR-0019 D2 and accept the ADR
- [ ] Delete the test file afterwards

### Phase 1 — Data & config (0.5 d) — `VI-1`
- [ ] Migration `202610100001-create-video-import-jobs.sql` (ADR-0019 D3 columns, indexes, partial unique on active `normalized_url`)
- [ ] Entity `VideoImportJob` (+ `@Builder.Default` for status/progress/attempts), enum `ImportStatus`, `ImportPlatform`; `VideoImportJobRepository` with native `claimNextDue()` (`FOR UPDATE SKIP LOCKED … RETURNING`), throttled `updateProgress`, `resetInterrupted`
- [ ] `AppProperties.videoImport`: `enabled`, `ytDlpPath` (`yt-dlp`), `workDir` (`/opt/data/imports`), `maxConcurrent` (1), `maxFileSize` (`1G`), `downloadTimeout` (30m), `metadataTimeout` (60s), `minFreeDisk` (2GB), `cookiesPath` (empty), `bulkMax` (20)
- [ ] `ImportUrlValidator`: https, host allowlist, length, normalization (YouTube id from `watch?v=`, `youtu.be/`, `/shorts/`; Facebook `reel/{id}`, `videos/{id}`, `watch?v=`, `fb.watch/…` kept as-is) + unit tests (incl. `javascript:`, `http:`, `file:`, `youtube.com.evil.com`, userinfo `@`, IP hosts)
- [ ] `TitleSanitizer` + tests

### Phase 2 — Process runner & uploader (1 d) — `VI-2` (after VI-0, VI-1)
- [ ] `YtDlpClient` (interface + impl): `fetchMetadata(url)` (`-J --skip-download`, 60 s) → `{id, title, duration, filesize?}`; `download(url, dir, listener, cancelToken)` with ADR-0019 D4 args; parse `download:` progress lines; bounded stderr ring buffer; map exit/stderr → `error_code` (`UNSUPPORTED_URL`, `LOGIN_REQUIRED`, `TOO_LARGE`, `EXTRACTOR_ERROR`, `TIMEOUT`, `CANCELLED`, `NOT_INSTALLED`)
- [ ] `DriveUploader` interface + impl chosen in VI-0: resumable upload (8 MB chunks), `parents=[FOLDER_ID]`, name `{title}.mp4`, `appProperties.importJobId`, progress listener → returns file id
- [ ] Startup check: `yt-dlp --version` logged; missing binary → feature reports `NOT_INSTALLED` instead of crashing
- [ ] Tests: argument list (no shell, `--` before URL, `--ignore-config`, `--use-extractors`), progress parsing, timeout → destroy, error mapping (fake process via injectable `ProcessFactory`)

### Phase 3 — Service, worker, API (1 d) — `VI-3` (after VI-1, VI-2)
- [ ] `VideoImportService`: `create(items, scheduledAt, adminId)` (≤ bulkMax, per-line result `{line, jobId | errorCode}`), `list(status, page)`, `preview(url)`, `runNow(id)`, `cancel(id)`, `retry(id)`; `ingested` flag via `existsBySourceId(drive_file_id)` (batched `findSourceIdsIn`)
- [ ] `VideoImportWorker`: `@Scheduled(fixedDelay = 15000)` + `wakeUp()` on virtual thread; `Semaphore(maxConcurrent)`; flow: claim → disk guard → resolve title (if none) → download (status DOWNLOADING) → UPLOADING → DONE + `drive_file_id` → delete work dir (always, `finally`); cancel = flag + destroy process; startup `resetInterrupted` (attempts < 3)
- [ ] Endpoints under `AdminController` or new `AdminVideoImportController` (`/admin/video-imports`): `POST` (create, `@RateLimited(permit = 10)`), `GET` (page, status filter), `POST /preview`, `POST /{id}/run-now`, `POST /{id}/cancel`, `POST /{id}/retry` — all `@HasPermission(ADMIN)` + `@AuditLog`; errors via `CustomException` (400 `INVALID_URL`, 409 `DUPLICATE_ACTIVE`, 409 `INVALID_STATE`)
- [ ] No retention: job history is kept forever (owner, 2026-10-10); list is paginated, newest first
- [ ] Tests: service rules, state transitions, claim (repository `@DataJpaTest` if available, else mocked), worker happy path / failure / cancel / interrupted reset, controller security (non-admin 403)

### Phase 4 — Frontend (1.25 d) — `VI-4` (contract-first, parallel with VI-2/3)
- [ ] `api/admin.js`: `createImports`, `listImports`, `previewImport`, `runImportNow`, `cancelImport`, `retryImport`
- [ ] `hooks/useAdmin.js`: `useVideoImports(page, status)` with `refetchInterval: data => hasActive(data) ? 2000 : false`; mutations invalidate `['admin','imports']`
- [ ] `AdminView` new tab **Import Video** → `AdminImportPanel`:
  - Form: textarea (one URL per line, optional `URL | Tiêu đề`), single-URL mode shows a title input + "Lấy tiêu đề" (preview); radio **Tải ngay / Hẹn giờ** + `datetime-local` (Asia/Ho_Chi_Minh, must be future); submit shows per-line results
  - Table: title, platform icon, status badge, phase + progress bar (%, MB), scheduled/started time, "Đã lên app"/"Chờ đồng bộ", error message (Vietnamese map), actions run-now / cancel / retry
- [ ] Tests: line parsing, validation, schedule-in-past blocked, polling on/off by status, actions, error mapping
- [ ] `npm run lint && npx vitest run && npm run build`

### Phase 5 — Ops, verification, docs (0.5 d) — `VI-5`
- [ ] Host: `ffmpeg`, `yt-dlp` present; `/opt/data/imports` owned by the app user; daily `yt-dlp -U` cron; optional cookies file `chmod 600`
- [ ] Config `.env`: `VIDEO_IMPORT_ENABLED=true` (+ OAuth vars if VI-0 chose OAuth)
- [ ] Prod E2E: 1 YouTube now → Drive file appears → within 15 min in app with the right title; bulk 3 (1 FB reel, 1 Short, 1 invalid) → per-line result; schedule +5 min; cancel during download; retry failed
- [ ] `/security-auditor` on the diff (process execution, SSRF, authz)
- [ ] `docs/tracking.md`, mkdocs nav (ADR-0019, PRD, plan), `TASKS.md`

---

## Dependency Graph

```
VI-0 (PoC upload) ─┐
VI-1 (data/config) ┼──► VI-2 (yt-dlp + uploader) ──► VI-3 (service/worker/API) ──┐
                   │                                                            ├──► VI-5
VI-4 (FE, contract-first) ──────────────────────────────────────────────────────┘
```

Suggested swarm: BE builder VI-0 → VI-1 → VI-2 → VI-3 (shared `AppProperties`, `AdminController`); FE builder VI-4 in parallel from the API contract below.

**Estimate:** ≈ 4.5 days (critical path ≈ 3 days).

## API Contract (for VI-4)

```
POST /admin/video-imports
  { "items": [{ "url": "...", "title": "optional" }], "scheduledAt": "2026-10-11T02:00:00Z" | null }
  → 200 { data: { results: [{ "line": 1, "jobId": 12 } | { "line": 2, "errorCode": "INVALID_URL" }] } }

GET  /admin/video-imports?status=&page=&size=   → Page<VideoImportJobDto>
POST /admin/video-imports/preview { "url": "..." } → { title, platform, durationSec }
POST /admin/video-imports/{id}/run-now | /cancel | /retry → VideoImportJobDto

VideoImportJobDto {
  id, sourceUrl, platform, title, status, phase, progressPct, downloadedBytes, totalBytes,
  scheduledAt, startedAt, finishedAt, driveFileId, ingested, errorCode, errorMessage, attempts, createdAt
}
```

## Risks

| Risk | Mitigation |
|------|-----------|
| Upload rejected by Drive quota | VI-0 gate before building the uploader |
| Import starves streaming (CPU/disk/network) | Concurrency 1, 720p cap, ffmpeg merge only |
| Stuck child process | Timeouts + `destroyForcibly`, `finally` cleanup |
| Option / command injection via URL | Allowlist + normalization, `--`, arg list, no shell |
| Title with path chars breaks Drive name / cron title | `TitleSanitizer` |
| Cron 60-min window misses a file if the cron is down > 1 h | Out of scope (same as manual uploads today); note in ops |
