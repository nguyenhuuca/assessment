# Sequence Diagrams: HLS Streaming for Selected Videos

- **PRD:** [PRD-hls-streaming](../prd/PRD-hls-streaming.md)
- **ADR:** [0031-hls-streaming](../adr/0031-hls-streaming.md)
- **Spec:** [spec-hls-streaming](../specs/spec-hls-streaming.md)
- **Status:** As-built (2026-10-24)

## 1. SUPER_ADMIN turns HLS on, worker transcodes (happy path)

The SUPER_ADMIN toggles HLS on in the admin table. The worker stream-copies the `.full` file and publishes the rendition atomically (spec FR-2, FR-3, BR-3..BR-7).

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

    SA->>FE: toggle HLS on
    FE->>API: PUT /v1/funny-app/admin/videos/42/hls
    API->>S: enableHls(42)
    S->>DB: status NONE or ERROR to PENDING, clear hls_error
    S->>W: wakeUp() on a virtual thread
    API-->>FE: 200 hlsStatus PENDING
    W->>L: tryClaim(fileId)
    L-->>W: true
    W->>DB: UPDATE set PROCESSING where hls_status is PENDING
    W->>FS: check fileId.full exists
    W->>FF: ffprobe streams (argument list)
    FF-->>W: video h264, audio aac
    W->>FS: usable space vs size of .full plus 1 GB
    W->>FF: ffmpeg -c copy -f hls -hls_time 6 into hls/fileId.tmp
    FF-->>W: exit 0, playlist.m3u8 written
    W->>DB: re-read status (still PROCESSING)
    W->>FS: rename hls/fileId.tmp to hls/fileId
    W->>DB: status READY
    W->>L: release(fileId)
    loop every 5 s while PENDING or PROCESSING
        FE->>API: GET /v1/funny-app/admin/videos
        API-->>FE: hlsStatus READY
    end
```

## 2. Transcode failures

Each check fails fast and leaves Range untouched (spec BR-9, EC-1, EC-15).

```mermaid
sequenceDiagram
    autonumber
    participant W as HlsTranscodeWorker
    participant DB as PostgreSQL (video_sources)
    participant FF as ffprobe / ffmpeg
    participant FS as Disk (video-cache)

    W->>DB: claim job, status PROCESSING
    alt .full missing
        W->>DB: ERROR SOURCE_MISSING
    else ffprobe or ffmpeg cannot start
        W->>DB: ERROR FFMPEG_NOT_FOUND
    else codec is not h264 plus aac or mp3
        FF-->>W: video hevc
        W->>DB: ERROR UNSUPPORTED_CODEC
    else free disk below size plus reserve
        W->>DB: ERROR DISK_FULL (ffmpeg never started)
    else ffmpeg runs longer than app.hls.timeout
        W->>FF: destroyForcibly()
        W->>FS: delete hls/fileId.tmp
        W->>DB: ERROR TIMEOUT
    else non-zero exit or no playlist
        W->>FS: delete hls/fileId.tmp
        W->>DB: ERROR FFMPEG_FAILED
    end
    Note over W,DB: the file lock is released in a finally block, and the next PENDING job is taken
```

## 3. Viewer plays an HLS video (flag on)

The server offers `hlsUrl` only when the flag is on and the rendition is READY. The player attaches HLS to its single video element (spec BR-8, BR-13, FR-9).

```mermaid
sequenceDiagram
    autonumber
    actor V as Viewer (guest or signed in)
    participant FE as Browser (VideoPlayer.jsx + useHlsSource)
    participant API as VideoStreamController
    participant S as StreamVideoServiceImpl
    participant FF as FeatureFlagService (Harness)
    participant DB as PostgreSQL (video_sources)
    participant FS as Disk (video-cache/hls)

    V->>FE: open feed
    FE->>API: GET /v1/funny-app/video-stream/list
    API->>S: getVideosToStream()
    S->>FF: isEnabled(hls_streaming_enabled, false), once per request
    FF-->>S: true
    S->>DB: published, visible videos
    S-->>API: VideoDto list, hlsUrl set where hls_status is READY
    API-->>FE: 200 list
    alt Safari or iOS (native HLS)
        FE->>FE: video.src = hlsUrl
    else other browsers
        FE->>FE: import hls.js, attach to the video element
    end
    FE->>API: GET /video-stream/hls/fileId/playlist.m3u8
    API->>DB: READY and PUBLISHED and not hidden
    API->>FS: read playlist.m3u8
    API-->>FE: 200 application/vnd.apple.mpegurl, no-cache
    loop each segment
        FE->>API: GET /video-stream/hls/fileId/seg_N.ts
        API->>FS: FileSystemResource (no DB)
        API-->>FE: 200 video/mp2t, immutable
    end
    FE->>FE: resume at startAt, report views and progress as today
```

## 4. Fallback to Range

The player falls back on any HLS problem, and the flag off sends everyone to Range (spec EC-2, EC-10, EC-11, EC-12).

```mermaid
sequenceDiagram
    autonumber
    participant FE as Browser (VideoPlayer.jsx + useHlsSource)
    participant API as VideoStreamController
    participant S as StreamVideoServiceImpl
    participant FF as FeatureFlagService (Harness)

    alt flag off or Harness unreachable
        FE->>API: GET /v1/funny-app/video-stream/list
        S->>FF: isEnabled(hls_streaming_enabled, false)
        FF-->>S: false (default when unreachable)
        API-->>FE: every hlsUrl is null
        FE->>API: GET /video-stream/stream/fileId with Range header
        API-->>FE: 206 video/mp4
    else fatal HLS error or playlist 404
        FE->>FE: save currentTime, destroy the hls instance
        FE->>API: GET /video-stream/stream/fileId with Range header
        API-->>FE: 206 video/mp4
        FE->>FE: on loadedmetadata restore currentTime and play state
    end
```

## 5. Turn HLS off or delete the video during a job

DELETE wins over a running job: the worker re-reads the status before publishing (spec FR-4, BR-12, EC-6, EC-16).

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

    Note over W: job running for fileId, holds the file lock
    SA->>API: DELETE /v1/funny-app/admin/videos/42/hls
    API->>S: disableHls(42)
    S->>DB: status NONE, hls_error null
    S->>L: tryClaim(fileId)
    alt lock held (running job or download)
        L-->>S: false
        Note over S: the lock holder cleans up, no 409
    else lock free
        L-->>S: true
        S->>FS: delete hls/fileId
        S->>L: release(fileId)
    end
    API-->>SA: 200 hlsStatus NONE
    W->>DB: re-read status after ffmpeg exits
    DB-->>W: NONE (not PROCESSING)
    W->>FS: delete hls/fileId.tmp, do not publish
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
    participant D as DriveVideoDownloaderImpl
    participant DB as PostgreSQL (video_sources)
    participant FS as Disk (video-cache)

    App->>W: onApplicationReady()
    W->>DB: PROCESSING to PENDING
    W->>FS: delete every hls/*.tmp
    W->>FS: check the directory of each READY row
    W->>DB: missing directory to ERROR OUTPUT_MISSING
    W->>FS: delete orphan hls directories (row missing or not READY)
    W->>W: wakeUp(), drain PENDING one by one

    Note over D: scheduler or admin force sync downloads fileId again
    D->>FS: move the new .full into place
    D->>FS: delete hls/fileId if it exists
    D->>DB: read hls_status for fileId
    alt READY or ERROR
        D->>DB: status PENDING, hls_error null
        D->>W: wakeUp()
    else NONE, PENDING or PROCESSING
        Note over D: no change
    end
```
