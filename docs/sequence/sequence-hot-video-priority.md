# Sequence Diagrams: Hot Video Priority and Trending Feed

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

- **PRD:** [PRD-hot-video-priority](../prd/PRD-hot-video-priority.md)
- **ADR:** [0011-hot-video-priority](../adr/0011-hot-video-priority.md)
- **Spec:** — (see [plan-hot-video-priority](../plans/plan-hot-video-priority.md) and [plan-vid2-hot-score-migration](../plans/plan-vid2-hot-score-migration.md))
- **Status:** Draft (the hot score is NOT implemented. Flows 1-3 are as-built, flow 4 is planned only)

> **Planned vs built.**
> - **Built:** manual `priority` column (0-9999) on `video_sources`, set by an admin and used for feed ordering. The `VideoAccessStats` entity and repo, the `VideoAccessService.recordAccess(String)` signature, and a `cleanUpOldVideos` job whose delete step is commented out.
> - **Not built:** `recordAccess()` body (commented out with a TODO), the Liquibase migration for `hot_score`, `is_hot`, `hot_rank`, `HotVideoService` / `HotVideoServiceImpl`, `HotVideoProperties` (`app.hot-video.*`), the `recomputeHotScores()` job, `GET /videos/trending`, `sort=hot`, cache pre-warming, and the frontend Hot badge. There is no server trending endpoint or section. `ExploreView.jsx` shows a "Trending Now" hero badge, but it is client side only: the hero is the first video of the list from `useVideos`, sorted by upvotes, views or newest (default upvotes). A grep for `hotScore` / `hot_score` in `api/src` returns nothing.
> - **PRD vs code.** The PRD uses paths `/api/videos`, `/api/videos/trending`. The real feed endpoint is `GET /v1/funny-app/video-stream/list`, and there is no `/videos` list endpoint. `recordAccess` is called only inside `getFileRangeFromDisk`, so even once implemented it would count cache misses, not every stream request as the PRD (FR-1) intends. The web client (`useVideos`) re-sorts the list by `upvotes` descending, which can override the server order except for ties.

## 1. Stream a video chunk (access recording is a no-op today)

The browser requests byte ranges. A chunk-cache hit never touches disk. On a miss the service reads the file, records a cache miss in `StatsCache`, and calls `recordAccess`, which does nothing yet.

```mermaid
sequenceDiagram
    autonumber
    participant FE as Browser (VideoPlayer.jsx)
    participant API as VideoStreamController
    participant S as StreamVideoServiceImpl
    participant C as VideoCache (Guava LRU)
    participant ST as VideoStorageServiceImpl
    participant SC as StatsCache
    participant VA as VideoAccessServiceImpl

    FE->>API: GET /v1/funny-app/video-stream/stream/{fileId} (Range header)
    API->>S: getFileSize(fileId)
    alt malformed or unsatisfiable Range
        API-->>FE: 416 Range Not Satisfiable (Content-Range bytes */size)
    end
    API->>S: getPartialFileUsingRAF(fileId, start, end)
    S->>C: getChunkStream(fileId, start, end, loader)
    alt chunk cached
        C-->>S: InputStream from memory
    else cache miss
        C->>ST: getFileRangeFromDisk(fileId, start, end)
        ST->>SC: recordMiss(fileId)
        ST-)VA: recordAccess(fileId) (@Async)
        Note over VA: body is commented out (TODO)<br/>no DB write, no hit count
        ST-->>C: bytes via RandomAccessFile
    end
    API-->>FE: 206 Partial Content (video/mp4, Content-Range)
```

## 2. Home feed ordering (manual priority, then newest)

The feed is ordered by the `priority` column, not by a hot score. There is no `sort` parameter.

```mermaid
sequenceDiagram
    autonumber
    actor U as Viewer
    participant FE as Browser (useVideos.js, VideoFeed.jsx)
    participant API as VideoStreamController
    participant S as StreamVideoServiceImpl
    participant P as HlsPlaybackServiceImpl
    participant FC as FeedCache (Guava, 30 s)
    participant R as VideoSourceRepository
    participant DB as PostgreSQL

    U->>FE: open Home
    FE->>API: GET /v1/funny-app/video-stream/list
    API->>S: getVideosToStream()
    S->>P: hlsEnabled()
    P-->>S: flag
    S->>FC: get(flag, loader)
    alt cache hit (within 30 s)
        FC-->>S: cached unmodifiable list
    else cache miss
        FC->>R: findAllByIsHideAndStatusOrderByPriorityDescCreatedAtDesc(false, PUBLISHED)
        R->>DB: SELECT ... ORDER BY priority DESC, created_at DESC
        DB-->>R: rows
        FC-->>S: List of VideoDto (no hotScore field)
    end
    API-->>FE: 200 ResultListInfo of VideoDto
    FE->>FE: mapApiVideo then sort by upvotes desc (client side, queryKey videos + category, staleTime 5 min)
    FE-->>U: swipe feed
```

## 3. Admin pins a video with a manual priority

This is the only "hot" lever that exists. An admin edits the priority cell in the video table.

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    participant FE as Browser (AdminVideoTable.jsx, useUpdateVideoPriority)
    participant API as AdminController (hasRole ADMIN)
    participant S as AdminVideoServiceImpl
    participant R as VideoSourceRepository
    participant FC as FeedCache

    A->>FE: type priority and blur the input
    FE->>API: PATCH /v1/funny-app/admin/videos/{id}/priority (JWT, body priority)
    API->>S: updatePriority(id, priority)
    alt priority outside 0..9999
        S-->>API: Contract.require fails
        API-->>FE: error ResultErrorInfo
    else unknown id
        S-->>API: IllegalArgumentException Video not found
        API-->>FE: error ResultErrorInfo
    else ok
        S->>R: save(source with new priority)
        S->>FC: invalidate()
        API-->>FE: 200 ResultObjectInfo "Priority updated" (@AuditLog updateVideoPriority)
    end
    FE-->>A: SaveIndicator, admin videos query refreshed
```

## 4. PLANNED: scheduled hot score recompute and trending read

Not implemented. Drawn from PRD FR-2 to FR-8, ADR-0011 and `plan-hot-video-priority`. Names such as `HotVideoServiceImpl` and `recomputeHotScores` are the planned names and do not exist in the code.

```mermaid
sequenceDiagram
    autonumber
    participant J as AppScheduler.recomputeHotScores (PLANNED, every 30 min)
    participant H as HotVideoServiceImpl (PLANNED)
    participant AS as VideoAccessStatsRepo
    participant DB as PostgreSQL (video_sources, share_link)
    participant SC as StatsCache
    participant VC as VideoCache
    actor U as Viewer
    participant FE as Browser (PLANNED Trending Now section)
    participant API as Video controller (PLANNED GET trending)

    J->>H: recompute()
    H->>AS: hits per video in 24h window
    H->>DB: share counts per video
    H->>H: hotScore = 0.60 hits + 0.25 shares + 0.15 recency (min-max normalised)
    H->>DB: UPDATE hot_score, is_hot, hot_rank (new columns, migration missing)
    H->>SC: put top N trending list (TTL 30 min)
    H->>VC: preWarm top 10 videos
    U->>FE: open Home
    FE->>API: GET trending?limit=5
    API->>SC: read cached list
    SC-->>API: List of VideoDto with hotScore, isHot, rank
    API-->>FE: 200 ResultListInfo of VideoDto
    FE-->>U: Trending Now row and Hot badge on cards
```
