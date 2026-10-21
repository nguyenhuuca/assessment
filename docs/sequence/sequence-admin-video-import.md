# Sequence Diagrams: Admin Video Import

<!--
One file per feature that has a PRD: docs/sequence/sequence-<feature>.md (slug = PRD slug).
- Mermaid `sequenceDiagram` only (rendered by mkdocs via pymdownx.superfences); no PNG/PlantUML.
- Draw what the code does (or will do, per ADR/spec). Cite the class/method in each participant or note.
- One diagram per main flow: the happy path first, then the important alternative or failure paths
  (auth failure, validation error, async/event, scheduled job). 2-6 diagrams is typical.
- Participants: Browser (React page/hook) → Controller → Service → Repository/DB, plus external
  systems (YouTube, GitHub, SMTP, SSE) and async listeners/jobs. Use `autonumber`.
- Keep in sync: when the as-built flow changes, update the diagram in the same commit.
-->

- **PRD:** [PRD-admin-video-import](../prd/PRD-admin-video-import.md)
- **ADR:** [0019-admin-video-import](../adr/0019-admin-video-import.md)
- **Spec:** — (plans: [plan-admin-video-import](../plans/plan-admin-video-import.md), [plan-admin-drive-force-sync](../plans/plan-admin-drive-force-sync.md))
- **Status:** As-built (2026-10-21)

Notes (code vs plan/PRD):

- Base path for every endpoint is `/v1/funny-app`. Admin endpoints need `hasRole('ADMIN')` plus `@HasPermission(ADMIN)`. In the diagrams `:id` stands for the path variable `{id}`.
- The plan lists `409 DUPLICATE_ACTIVE` and `400 INVALID_URL` as request-level errors for create. In the code `VideoImportServiceImpl.create` returns `200` with a per-line `errorCode` (`INVALID_URL`, `DUPLICATE_ACTIVE`) in `CreateVideoImportResultDto.results`. Only request-level problems are HTTP errors: `503 IMPORT_DISABLED`, `400 EMPTY_ITEMS`, `400 TOO_MANY_ITEMS`, `400 INVALID_SCHEDULE`. `retry` returns `409 DUPLICATE_ACTIVE` when the partial unique index rejects it.
- The import job never writes `video_sources`. It only uploads to the Drive folder, and the Drive sync (cron `sync-drive-videos` or admin force sync) ingests the file.

## 1. Create imports (preview title, run now or schedule)

An admin pastes one or many YouTube or Facebook URLs in the Import tab (`ImportForm.jsx`). With a single valid URL the admin can fetch the title through yt-dlp first. Each line becomes a `PENDING` row in `video_import_jobs`. "Run now" wakes the worker immediately, "Later" stores a `scheduledAt` that must sit on a 5-minute slot.

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    participant FE as Browser (ImportForm.jsx, useAdmin.js)
    participant API as AdminVideoImportController
    participant S as VideoImportServiceImpl
    participant R as ImportUrlResolver
    participant YT as YtDlpClient (yt-dlp on host)
    participant REPO as VideoImportJobRepository
    participant W as VideoImportWorker
    participant DB as PostgreSQL

    A->>FE: paste URLs (one per line)
    FE->>FE: parseLines() local validation
    opt exactly one valid URL and admin clicks "fetch title"
        FE->>API: POST /v1/funny-app/admin/video-imports/preview (RateLimited 20)
        API->>S: preview(url)
        S->>R: resolve(url)
        alt URL not supported
            S-->>API: CustomException 400 INVALID_URL
            API-->>FE: 400 ResultErrorInfo
        else URL ok
            S->>YT: fetchMetadata(normalizedUrl)
            alt yt-dlp error
                S-->>API: CustomException 503 NOT_INSTALLED, 504 TIMEOUT, 422 UNSUPPORTED_URL or LOGIN_REQUIRED, else 502
                API-->>FE: error status
            else metadata ok
                S-->>API: ImportPreviewDto (title, platform, durationSec)
                API-->>FE: 200 ResultObjectInfo of ImportPreviewDto
                FE-->>A: title field filled
            end
        end
    end
    A->>FE: choose "now" or "later" (datetime on 5-minute step), submit
    FE->>API: POST /v1/funny-app/admin/video-imports (items, scheduledAt) (RateLimited 10)
    API->>S: create(request, adminId)
    alt app.video-import.enabled is false
        S-->>API: CustomException 503 IMPORT_DISABLED
    else items empty
        S-->>API: CustomException 400 EMPTY_ITEMS
    else more than bulkMax items
        S-->>API: CustomException 400 TOO_MANY_ITEMS
    else scheduledAt in the past, over 30 days ahead or off the 5-minute slot
        S-->>API: CustomException 400 INVALID_SCHEDULE
    else request accepted
        loop each item
            S->>R: resolve(item.url)
            alt URL invalid
                S->>S: LineResult errorCode INVALID_URL
            else active job with same normalizedUrl exists
                S->>REPO: existsByNormalizedUrlAndStatusIn(url, ACTIVE)
                S->>S: LineResult errorCode DUPLICATE_ACTIVE
            else new job
                S->>REPO: save(VideoImportJob PENDING, scheduledAt, createdBy)
                REPO->>DB: INSERT video_import_jobs
                Note over S,DB: a lost race on the partial unique index is also reported as DUPLICATE_ACTIVE
                S->>S: LineResult jobId
            end
        end
        opt at least one job created and scheduledAt was null
            S->>W: wakeUp() (virtual thread runs drain())
        end
        S-->>API: CreateVideoImportResultDto (results per line)
        API-->>FE: 200 ResultObjectInfo of CreateVideoImportResultDto
        FE-->>A: per-line result list (job created or error text)
    end
    API-->>FE: 4xx or 5xx ResultErrorInfo for request-level errors
    FE-->>A: serverError alert via errorText()
```

## 2. Worker runs a job (download, upload to Drive, progress polling)

`VideoImportWorker.drain()` claims the oldest due job with `claimNextDue()` (UPDATE ... FOR UPDATE SKIP LOCKED, sets `DOWNLOADING`), bounded by a Semaphore of `maxConcurrent`. It runs on a virtual thread: disk guard, title resolution, yt-dlp download, upload through `DriveUploader` (`OAuthDriveUploader`, resumable 8 MB chunks), then `DONE` with `drive_file_id`. The work directory is always deleted. The Import tab polls every 2 s while any job is active.

```mermaid
sequenceDiagram
    autonumber
    participant TRG as Trigger (wakeUp, poll cron every 5 min, onApplicationReady)
    participant W as VideoImportWorker
    participant REPO as VideoImportJobRepository
    participant YT as YtDlpClient
    participant UP as DriveUploader (OAuthDriveUploader)
    participant GD as Google Drive folder
    participant DB as PostgreSQL
    participant FE as Browser (useVideoImports, refetchInterval 2000)
    participant API as AdminVideoImportController

    TRG->>W: drain()
    loop while a permit is free
        W->>REPO: claimNextDue()
        REPO->>DB: UPDATE status DOWNLOADING, attempts plus 1 WHERE PENDING and due (SKIP LOCKED)
        DB-->>REPO: job id or none
        alt no due job
            W->>W: release permit and stop
        else job claimed
            W->>W: running.put(id, CancelToken), executor.execute(runJob)
        end
    end
    W->>REPO: findById(id)
    W->>UP: isConfigured()
    W->>W: ensureDiskSpace() (minFreeDiskBytes)
    W->>W: resolveTitle(job)
    opt no requestedTitle
        W->>YT: fetchMetadata(normalizedUrl)
        W->>REPO: save resolvedTitle (fallback title platform-videoId on error)
    end
    W->>YT: download(normalizedUrl, jobDir, progress, cancelToken)
    YT-->>W: progress(done, total)
    W->>REPO: updateProgress(id, pct, done, total) (at most every 2 s)
    YT-->>W: file path (.mp4)
    W->>REPO: save status UPLOADING, totalBytes
    W->>UP: upload(file, title plus ".mp4", jobId, progress, cancelToken)
    UP->>GD: files.create parents FOLDER_ID (resumable upload)
    GD-->>UP: drive file id
    UP-->>W: driveId
    W->>REPO: save status DONE, progress 100, driveFileId, finishedAt
    W->>W: finally running.remove(id), deleteRecursively(jobDir), release permit, drain()
    loop every 2 s while a job is DOWNLOADING or UPLOADING
        FE->>API: GET /v1/funny-app/admin/video-imports?page&size=20&status
        API-->>FE: 200 Page of VideoImportJobDto (status, phase, progressPct, ingested)
    end
    Note over FE,API: ingested is true once video_sources has a row with source_id = driveFileId
    alt ImportException (NOT_INSTALLED, DRIVE_NOT_CONFIGURED, DISK_LOW, TIMEOUT, DRIVE_AUTH_FAILED, DRIVE_UPLOAD_FAILED ...)
        W->>REPO: fail(): status FAILED, errorCode, errorMessage cut to 500 chars
    else any other exception
        W->>REPO: fail(): status FAILED, errorCode INTERNAL_ERROR
    else CANCELLED
        W->>REPO: fail(): status CANCELLED, error fields cleared
    end
```

## 3. Run now, cancel, retry and restart recovery

Row actions in `ImportTable.jsx` call three POST endpoints. Each one is a conditional UPDATE, so a stale click gets `409 INVALID_STATE` instead of corrupting a job. After a JVM restart interrupted jobs go back to `PENDING` (attempts below 3) or `FAILED` with `INTERRUPTED`.

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    participant FE as Browser (ImportTable.jsx, useAdmin.js)
    participant API as AdminVideoImportController
    participant S as VideoImportServiceImpl
    participant REPO as VideoImportJobRepository
    participant W as VideoImportWorker
    participant DB as PostgreSQL

    A->>FE: click "Run now" on a PENDING job
    FE->>API: POST /v1/funny-app/admin/video-imports/:id/run-now
    API->>S: runNow(id)
    S->>REPO: findById(id)
    alt id unknown
        S-->>API: CustomException 404 IMPORT_NOT_FOUND
    else status is not PENDING or runNowIfPending returns 0
        S-->>API: CustomException 409 INVALID_STATE
    else updated
        S->>REPO: runNowIfPending(id) sets scheduled_at to now()
        S->>W: wakeUp()
        S-->>API: VideoImportJobDto
        API-->>FE: 200 ResultObjectInfo of VideoImportJobDto
    end

    A->>FE: click "Cancel"
    FE->>API: POST /v1/funny-app/admin/video-imports/:id/cancel
    API->>S: cancel(id)
    alt status PENDING
        S->>REPO: cancelIfPending(id) (status CANCELLED)
    else status DOWNLOADING or UPLOADING
        S->>W: requestCancel(id)
        W->>W: CancelToken.cancel() then runJob ends with CANCELLED
    else other status or job not running on this node
        S-->>API: CustomException 409 INVALID_STATE
    end
    S-->>API: VideoImportJobDto
    API-->>FE: 200 or 409 ResultErrorInfo

    A->>FE: click "Retry" on a FAILED or CANCELLED job
    FE->>API: POST /v1/funny-app/admin/video-imports/:id/retry
    API->>S: retry(id)
    S->>REPO: retryIfFinal(id) (PENDING, attempts 0, driveFileId cleared)
    alt unique index on active normalizedUrl violated
        S-->>API: CustomException 409 DUPLICATE_ACTIVE
    else no row updated
        S-->>API: CustomException 409 INVALID_STATE
    else updated
        S->>W: wakeUp()
        S-->>API: VideoImportJobDto
        API-->>FE: 200 ResultObjectInfo of VideoImportJobDto
    end
    FE->>FE: useImportMutation invalidates the admin imports query and the list refetches

    Note over W,DB: JVM restart
    W->>W: onApplicationReady() (only when enabled)
    W->>REPO: resetInterrupted()
    REPO->>DB: DOWNLOADING or UPLOADING to PENDING (attempts below 3) or FAILED INTERRUPTED
    W->>W: cleanLeftoverDirs(), markReady(), wakeUp()
```

## 4. Cron ingest of the uploaded Drive file

The uploaded file is ingested by the same scheduled job as a manual upload: `sync-drive-videos` (registered in `JobRegistry` as `SYNC_DRIVE_VIDEOS`, runs `AppScheduler.syncDriveVideos`). It looks at files created in the last 60 minutes, so the video appears in the app within the cron interval after the upload.

```mermaid
sequenceDiagram
    autonumber
    participant J as Dynamic job sync-drive-videos (AppScheduler.syncDriveVideos)
    participant VS as VideoStorageServiceImpl
    participant GD as Google Drive API
    participant L as DriveFileLocks
    participant DL as DriveVideoDownloaderImpl
    participant FF as FfmpegService
    participant AI as ChatGptService
    participant DB as PostgreSQL

    J->>VS: downloadFileFromFolder(FOLDER_ID, now minus 60 min)
    VS->>GD: files.list q 'folder' in parents and createdTime >= iso
    GD-->>VS: files (id, name, size)
    loop each file in StructuredTaskScope
        alt cached .full file for the fileId already exists
            VS->>VS: skip
        else locks.tryClaim(fileId) fails (admin force sync running)
            VS->>VS: skip
        else claimed
            VS->>DL: download(fileId, size, noop)
            DL->>GD: files.get executeMediaAndDownloadTo (.part then move)
            DL->>FF: generateThumbnail(file, thumbnailPath)
            DL-->>VS: thumbnailUrl
            VS->>VS: saveInfo(fileId, titleOf(file name), thumbnailUrl)
            alt video_sources row with sourceId exists
                VS->>VS: skip save
            else new
                VS->>AI: makePoem(title) (failure only logged, desc stays null)
                VS->>DB: INSERT video_sources (source_type google_drive, source_id = fileId, title)
                VS->>VS: publish VideoIngestedEvent
            end
            VS->>L: release(fileId)
        end
    end
```

## 5. Admin Drive force sync (SUPER_ADMIN)

Any ADMIN can view the Drive folder against the DB and local cache. Only SUPER_ADMIN can start a force sync of up to `MAX_BATCH` files. One run at a time, executed on a virtual thread, and the page polls every 2 s while the run is active. Each file is claimed with the same `DriveFileLocks` the cron uses, so the two never process one file together.

```mermaid
sequenceDiagram
    autonumber
    actor SA as Super admin
    participant FE as Browser (DriveSyncPanel.jsx, useAdmin.js)
    participant API as AdminDriveSyncController
    participant S as DriveSyncServiceImpl
    participant GD as Google Drive API
    participant L as DriveFileLocks
    participant DL as DriveVideoDownloaderImpl
    participant ST as VideoStorageServiceImpl
    participant DB as PostgreSQL

    FE->>API: GET /v1/funny-app/admin/drive-sync/files?from&to (ADMIN)
    alt from is after to
        API-->>FE: 400 error.driveSync.invalidRange
    else valid
        API->>S: listFolder(from, to)
        S->>GD: files.list (paged)
        S->>DB: videoSourceRepository rows (all, or by ids when a range is used)
        S-->>API: DriveSyncListingDto (files with state, orphans, maxBatch)
        API-->>FE: 200 ResultObjectInfo
    end
    SA->>FE: select files, confirm modal, click Force Sync
    FE->>API: POST /v1/funny-app/admin/drive-sync/runs (fileIds) (SUPER_ADMIN, RateLimited 5)
    API->>S: startRun(fileIds)
    alt role is ADMIN only
        API-->>FE: 403
    else fileIds empty
        S-->>API: CustomException 400 EMPTY_FILE_IDS
    else more than MAX_BATCH ids
        S-->>API: CustomException 400 TOO_MANY_FILES
    else a run is still active
        S-->>API: CustomException 409 SYNC_RUNNING
    else some id not in the folder listing
        S-->>API: CustomException 400 NOT_IN_FOLDER
    else Drive list fails
        S-->>API: CustomException 502 DRIVE_UNAVAILABLE
    else accepted
        S->>S: current.compareAndSet(previous, RunState)
        S-->>API: DriveSyncRunDto (snapshot)
        API-->>FE: 202 ResultObjectInfo of DriveSyncRunDto
        S--)S: executor.execute(execute(run)) on virtual thread drive-sync
    end
    loop each RunItem in the run
        S->>L: tryClaim(fileId)
        alt lock held by cron
            S->>S: status SKIPPED_BUSY
        else claimed
            S->>DL: download(fileId, totalBytes, bytes callback)
            DL->>GD: files.get media download
            DL-->>S: thumbnailUrl
            S->>ST: upsertInfo(fileId, titleOf(name), thumbnailUrl)
            ST->>DB: upsertDriveSource (unique source_type plus source_id)
            S->>S: status DONE
        end
        S->>L: release(fileId)
        Note over S: exception sets FAILED with errorCode DISK_FULL, DRIVE_ERROR, DB_ERROR or INTERNAL
    end
    loop every 2 s while run.active
        FE->>API: GET /v1/funny-app/admin/drive-sync/runs/current
        API->>S: currentRun()
        S-->>FE: DriveSyncRunDto (items status, downloadedBytes)
    end
    FE->>FE: when the run ends, invalidate the drive-sync files query
```
