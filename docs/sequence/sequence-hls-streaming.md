# Sequence Diagrams: HLS Streaming for Selected Videos

- **PRD:** [PRD-hls-streaming](../prd/PRD-hls-streaming.md)
- **ADR:** [0031-hls-streaming](../adr/0031-hls-streaming.md)
- **Spec:** [spec-hls-streaming](../specs/spec-hls-streaming.md)
- **Status:** As-built (2026-10-24; all six flows re-checked against the code after the review fixes, the perf runs and the feed cache)

## 1. SUPER_ADMIN turns HLS on, worker transcodes (happy path)

The SUPER_ADMIN toggles HLS on in the admin table. The worker takes one job at a time (peek the queue, lock the file, win the row), stream-copies the `.full` file and publishes it with a conditional READY update (spec FR-2, FR-3, BR-3..BR-7).

```mermaid
sequenceDiagram
    autonumber
    actor SA as SUPER_ADMIN
    participant FE as Browser (AdminVideoTable.jsx)
    participant API as AdminController
    participant S as AdminVideoServiceImpl
    participant W as HlsTranscodeWorker
    participant L as DriveFileLocks
    participant DB as PostgreSQL (video_sources)
    participant FF as ffprobe / ffmpeg
    participant FS as Disk (video-cache)
    participant FC as FeedCache

    SA->>FE: toggle HLS on
    FE->>API: PUT /v1/funny-app/admin/videos/42/hls
    API->>S: enableHls(42)
    alt unknown id
        S-->>API: CustomException 404 error.video.notFound
    else source is not a Drive video
        S-->>API: CustomException 400 error.hls.notDriveVideo
    else status NONE or ERROR
        S->>DB: status to PENDING, clear hls_error
        S->>W: wakeUp() on a virtual thread
    else already PENDING, PROCESSING or READY
        Note over S: status kept as is
    end
    S->>FC: invalidate()
    API-->>FE: 200 hlsStatus (PENDING after a new request)

    W->>W: Semaphore(1).tryAcquire()
    W->>DB: findHlsQueue() (PENDING, oldest first)
    loop each queued row until one is taken
        W->>L: tryClaim(fileId)
        alt lock free
            L-->>W: true
            W->>DB: claimHls(id) = UPDATE set PROCESSING where hls_status is PENDING
            alt 1 row updated
                Note over W: job taken
            else 0 rows (someone else won)
                W->>L: release(fileId), try the next row
            end
        else lock busy (download or delete)
            L-->>W: false, row stays PENDING, try the next row
        end
    end

    W->>FS: check fileId.full exists
    W->>FF: ffprobe streams (argument list, 30 s timeout)
    FF-->>W: video h264, audio aac
    W->>FS: usable space vs size of .full plus app.hls.min-free-disk-bytes
    W->>FS: delete a leftover hls/fileId.tmp
    W->>FF: ffmpeg -c copy -f hls -hls_time 6 into hls/fileId.tmp (app.hls.timeout)
    FF-->>W: exit 0, playlist.m3u8 written
    W->>DB: re-read status (still PROCESSING)
    W->>FS: delete a stale hls/fileId, rename hls/fileId.tmp to hls/fileId
    W->>DB: markHlsReady(id) = UPDATE set READY where hls_status is PROCESSING
    DB-->>W: 1 row
    W->>FC: invalidate()
    W->>L: release(fileId) in finally
    W->>W: release the permit, drain() for the next job

    loop every 5 s while PENDING or PROCESSING
        FE->>API: GET /v1/funny-app/admin/videos
        API-->>FE: hlsStatus READY
    end
```

## 2. Transcode failures

Each check fails fast and leaves Range untouched. A failure writes ERROR only while the row is still PROCESSING, so a status the SUPER_ADMIN set meanwhile (NONE or PENDING) is never overwritten (spec BR-9, EC-1, EC-15).

```mermaid
sequenceDiagram
    autonumber
    participant W as HlsTranscodeWorker
    participant L as DriveFileLocks
    participant DB as PostgreSQL (video_sources)
    participant FF as ffprobe / ffmpeg
    participant FS as Disk (video-cache)

    alt fileId is not a valid id (seen while taking the job)
        W->>DB: claimHls(id), then ERROR SOURCE_MISSING, take the next row
    else job taken (row PROCESSING, file locked)
        alt .full missing
            W->>W: HlsException SOURCE_MISSING
        else ffprobe or ffmpeg cannot start
            W->>W: HlsException FFMPEG_NOT_FOUND
        else codec is not h264 plus aac, mp3 or no audio
            FF-->>W: e.g. video vp9
            W->>W: WARN log with video and audio codecs, HlsException UNSUPPORTED_CODEC
        else free disk below size plus reserve
            W->>W: HlsException DISK_FULL (ffmpeg never started)
        else ffmpeg runs longer than app.hls.timeout
            W->>FF: destroyForcibly() child processes, then the process
            W->>W: HlsException TIMEOUT
        else non-zero exit or no playlist
            W->>W: HlsException FFMPEG_FAILED
        else unexpected exception or the executor rejects the job
            W->>W: treated as FFMPEG_FAILED
        end
        W->>FS: delete hls/fileId.tmp (every failure path)
        W->>DB: markHlsError(id, code) = UPDATE set ERROR where hls_status is PROCESSING
        Note over W,DB: 0 rows when the owner turned HLS off or re-queued it meanwhile, which is kept as is
        W->>L: release(fileId) in finally, then drain() takes the next PENDING job
    end
```

## 3. Viewer plays an HLS video (flag on)

The server offers `hlsUrl` only when the flag is on and the rendition is READY. The flag is read once per request and the feed list comes from a 30 s cache keyed by the flag value. The player attaches HLS to its single video element (spec BR-8, BR-13, FR-9).

```mermaid
sequenceDiagram
    autonumber
    actor V as Viewer (guest or signed in)
    participant FE as Browser (VideoPlayer.jsx + useHlsSource)
    participant HJ as hls.js (lazy chunk)
    participant API as VideoStreamController
    participant S as StreamVideoServiceImpl
    participant P as HlsPlaybackServiceImpl
    participant FF as FeatureFlagService (Harness)
    participant FC as FeedCache (30 s, key = flag)
    participant DB as PostgreSQL (video_sources)
    participant FS as Disk (video-cache/hls)

    V->>FE: open feed
    FE->>API: GET /v1/funny-app/video-stream/list
    API->>S: getVideosToStream()
    S->>P: hlsEnabled()
    P->>FF: isEnabled(hls_streaming_enabled, false), once per request
    FF-->>P: true
    P-->>S: true
    S->>FC: get(true, loader)
    alt cache miss
        FC->>DB: published, visible videos (partial feed index)
        FC-->>S: VideoDto list, hlsUrl set where hls_status is READY
    else cache hit
        FC-->>S: cached unmodifiable list
    end
    S-->>API: VideoDto list
    API-->>FE: 200 list
    alt Safari or iOS (native HLS)
        FE->>FE: video.src = hlsUrl
        FE->>API: GET /video-stream/hls/fileId/playlist.m3u8
    else other browsers
        FE->>HJ: import hls.js, Hls.isSupported(), loadSource + attachMedia
        HJ->>API: GET /video-stream/hls/fileId/playlist.m3u8
    end
    alt fileId is not a valid id
        API-->>FE: 400
    end
    API->>P: findPlaylist(fileId)
    P->>DB: READY and PUBLISHED and not hidden
    P->>FS: playlist.m3u8 exists
    API-->>FE: 200 application/vnd.apple.mpegurl, no-cache
    loop each segment
        HJ->>API: GET /video-stream/hls/fileId/seg_N.ts (on Safari the video element fetches it)
        opt invalid fileId or segment name
            API-->>HJ: 400
        end
        API->>FS: FileSystemResource (no DB)
        API-->>HJ: 200 video/mp2t, immutable
    end
    FE->>FE: resume at startAt, report views and progress as today
```

## 4. Fallback to Range

Two ways a viewer ends up on Range: the server offers no `hlsUrl` (flag off, Harness unreachable, rendition not READY), or the player gives up on HLS for this video and switches the same `<video>` element to the Range URL (spec EC-2, EC-10, EC-11, EC-12; `webapp/src/hooks/useHlsSource.js`).

```mermaid
sequenceDiagram
    autonumber
    participant FE as Browser (VideoPlayer.jsx + useHlsSource)
    participant HJ as hls.js (lazy chunk)
    participant API as VideoStreamController
    participant S as StreamVideoServiceImpl
    participant P as HlsPlaybackServiceImpl
    participant FF as FeatureFlagService (Harness)
    participant FC as FeedCache (30 s, key = flag)

    alt server offers no hlsUrl
        FE->>API: GET /v1/funny-app/video-stream/list
        API->>S: getVideosToStream()
        S->>P: hlsEnabled()
        P->>FF: isEnabled(hls_streaming_enabled, false)
        FF-->>P: false (also the default when Harness is unreachable)
        P-->>S: false
        S->>FC: get(false, loader)
        FC-->>S: list built with hlsUrl = null
        S-->>API: VideoDto list
        API-->>FE: 200, every hlsUrl is null (READY or not)
        Note over FE: useHlsSource returns the Range URL, no hls.js is loaded
    else player gives up on HLS for this video
        alt hls.js path
            FE->>HJ: import hls.js
            HJ-->>FE: import fails, or Hls.isSupported() is false, or a fatal Hls.Events.ERROR (e.g. playlist 404, segment missing)
        else native HLS (Safari, iOS)
            FE->>API: GET /video-stream/hls/fileId/playlist.m3u8
            API-->>FE: 404 or a broken segment
            Note over FE: video element fires error
        end
        FE->>FE: fallback() once: keep currentTime and play state, destroy hls.js, remove the error listener
        FE->>FE: failedUrl = hlsUrl, so HLS is not retried for this video (no loop)
        Note over FE: src switches to the Range URL. If position > 0 or it was playing, restore on loadedmetadata. A video change drops a pending restore.
    end

    loop while the video plays or seeks
        FE->>API: GET /video-stream/stream/fileId, Range bytes=N- or bytes=N-M
        API->>API: getFileSize, then cap the range at 1 MB (first chunk 256 KB)
        alt valid range
            API-->>FE: 206 video/mp4, Content-Range, body read from disk inside the response
        else malformed or unsatisfiable range
            API-->>FE: 416, Content-Range bytes */size
        end
    end
```

## 5. Turn HLS off or delete the video during a job

The owner always wins over a running job. The worker checks twice: it re-reads the status before publishing, and the READY update itself only matches a PROCESSING row (spec FR-4, BR-12, EC-6, EC-16).

```mermaid
sequenceDiagram
    autonumber
    actor SA as SUPER_ADMIN
    participant API as AdminController
    participant S as AdminVideoServiceImpl
    participant W as HlsTranscodeWorker
    participant L as DriveFileLocks
    participant DB as PostgreSQL (video_sources)
    participant FS as Disk (video-cache)
    participant FC as FeedCache

    Note over W: job running for fileId, holds the file lock
    alt turn HLS off
        SA->>API: DELETE /v1/funny-app/admin/videos/42/hls
        API->>S: disableHls(42)
        Note over S: unknown id gives 404 error.video.notFound, a non-Drive source gives 400 error.hls.notDriveVideo
        S->>DB: status NONE, hls_error null
    else delete the video
        SA->>API: DELETE /v1/funny-app/admin/videos/42
        API->>S: deleteVideo(42, deleteLocalFile, deleteDriveFile)
        S->>S: optional Drive file and local .full delete
    end
    S->>L: tryClaim(fileId) to delete hls/fileId
    alt lock held (running job or download)
        L-->>S: false
        Note over S: the lock holder cleans up, no 409
    else lock free
        L-->>S: true
        S->>FS: delete hls/fileId
        S->>L: release(fileId)
    end
    opt delete the video
        S->>DB: deleteById(42)
    end
    S->>FC: invalidate()
    API-->>SA: 200

    W->>DB: re-read status after ffmpeg exits
    alt status no longer PROCESSING (NONE, or row deleted)
        W->>FS: delete hls/fileId.tmp, do not publish
    else still PROCESSING at the re-read, but changed right after
        W->>FS: publish hls/fileId
        W->>DB: markHlsReady(id) where hls_status is PROCESSING
        DB-->>W: 0 rows
        W->>FS: delete the published hls/fileId, READY is never written over NONE
    end
    W->>L: release(fileId)
```

> DELETE never returns 409. If a running job holds the lock, it discards its output (FR-4). If a download holds it, the downloader deletes the directory (BR-10). Anything still left is removed by the startup orphan sweep (BR-11).

## 6. Startup recovery and a replaced source

Recovery on startup, and a re-download that invalidates the rendition (spec BR-10, BR-11, EC-4, EC-9).

```mermaid
sequenceDiagram
    autonumber
    participant App as Spring (ApplicationReadyEvent)
    participant W as HlsTranscodeWorker
    participant C as Caller (scheduler sync or admin force sync)
    participant L as DriveFileLocks
    participant D as DriveVideoDownloaderImpl
    participant DB as PostgreSQL (video_sources)
    participant FS as Disk (video-cache)
    participant FC as FeedCache

    App->>W: onApplicationReady()
    Note over W: wakeUp and poll do nothing until ready is set
    W->>DB: resetHlsProcessing(), PROCESSING to PENDING
    W->>FS: delete every hls/*.tmp
    loop each READY row
        W->>FS: does hls/fileId exist
        opt directory missing
            W->>DB: markHlsOutputMissing(id) where hls_status is READY
            W->>FC: invalidate()
        end
    end
    W->>FS: delete orphan hls directories (row missing or not READY)
    Note over W: any recovery error is logged, startup goes on
    W->>W: markReady(), wakeUp(), drain PENDING one by one
    loop every 5 min (@Scheduled cron 0 */5 * * * *)
        W->>W: poll() then drain(), a fallback for a missed wake-up or a row left PENDING by a busy file
    end

    C->>L: tryClaim(fileId)
    C->>D: download(fileId) again
    D->>FS: write .part, move the new .full into place, invalidate cached chunks
    D->>FS: delete hls/fileId if it exists
    D->>DB: findBySourceId(fileId)
    alt READY or ERROR
        D->>DB: status PENDING, hls_error null
        D->>FC: invalidate()
        D->>W: wakeUp()
    else NONE, PENDING, PROCESSING or no row yet
        Note over D: status unchanged
    end
    Note over D: a failure in this hook is only logged, the download still succeeds
    C->>L: release(fileId)
```
