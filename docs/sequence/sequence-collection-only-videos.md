# Sequence Diagrams: Collection-Only Videos

- **PRD:** [PRD-collection-only-videos](../prd/PRD-collection-only-videos.md)
- **ADR:** [0035-collection-only-videos](../adr/0035-collection-only-videos.md)
- **Spec:** [spec-collection-only-videos](../specs/spec-collection-only-videos.md)
- **Status:** Draft (to be marked As-built after implementation)

All paths are under `/v1/funny-app`. `visibility` is one of `FEED`, `COLLECTION_ONLY`, `HIDDEN` (it replaces `is_hide`). Feed rule: `visibility = 'FEED'`. Playable rule: `visibility <> 'HIDDEN'`. Both also require `status = 'PUBLISHED'`.

## 1. Admin changes a video's visibility

The admin picks Feed, Collection only or Hidden in the Admin videos table. The cache is invalidated, so the next feed request reflects the change.

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    participant FE as Browser (AdminVideoTable.jsx, useAdmin.js)
    participant API as AdminController
    participant S as AdminVideoServiceImpl
    participant DB as PostgreSQL (video_sources)
    participant FC as FeedCache

    A->>FE: choose Collection only on a row
    FE->>API: PATCH /admin/videos/{id}/visibility {visibility COLLECTION_ONLY} (JWT)
    API->>API: PreAuthorize hasRole ADMIN, AuditLog updateVideoVisibility
    alt body null, no field, both fields, or unknown value
        API-->>FE: 400
    else legacy body {hidden true or false}
        API->>API: map to HIDDEN or FEED
    end
    API->>S: updateVisibility(id, visibility)
    S->>DB: findById(id)
    alt unknown id
        S-->>API: IllegalArgumentException Video not found
        API-->>FE: 400
    else found
        S->>DB: save(visibility)
        S->>FC: invalidate()
        S-->>API: ok
        API-->>FE: 200 Visibility updated
        FE->>FE: invalidate ['admin','videos']
        FE-->>A: row shows the Collection only badge
    end
```

## 2. Feed excludes COLLECTION_ONLY videos

Feed search, the Explore hero and the Library "Add videos" picker filter this same list on the client, so they exclude the video too.

```mermaid
sequenceDiagram
    autonumber
    participant FE as Browser (useVideos, SearchModal, ExploreView, AddVideosModal)
    participant API as VideoStreamController
    participant S as StreamVideoServiceImpl
    participant FC as FeedCache (Guava, 30 s)
    participant DB as PostgreSQL

    FE->>API: GET /video-stream/list
    API->>S: getVideosToStream()
    S->>FC: get(hlsFlag, loader)
    alt cache hit
        FC-->>S: cached list
    else miss
        FC->>DB: findAllByVisibilityAndStatusOrderByPriorityDescCreatedAtDesc(FEED, PUBLISHED)
        Note over DB: partial index idx_video_sources_feed<br/>WHERE visibility = 'FEED' AND status = 'PUBLISHED'
        DB-->>FC: FEED rows only
        FC-->>S: list
    end
    S-->>API: List of VideoDto
    API-->>FE: 200 ResultListInfo<VideoDto>
    FE->>FE: feed, search, Explore hero and Add videos picker read this list
```

## 3. Collection plays a COLLECTION_ONLY video

```mermaid
sequenceDiagram
    autonumber
    actor U as Viewer (owner, public or ?l= link)
    participant FE as Browser (LibraryView, useCollectionPlaylist)
    participant API as VideoCollectionController
    participant S as VideoCollectionServiceImpl
    participant DB as PostgreSQL

    U->>FE: open a collection
    FE->>API: GET /collections/{id}/videos
    API->>S: getVideos(id, page, size)
    S->>DB: items JOIN video_sources WHERE VISIBLE
    Note over DB: VISIBLE = v.visibility <> 'HIDDEN' AND v.status = 'PUBLISHED'
    DB-->>S: FEED and COLLECTION_ONLY videos
    S-->>API: page of videos
    API-->>FE: 200
    FE-->>U: playlist includes the research video
    FE->>API: GET /video-stream/stream/{fileId} (Range) or HLS playlist
    Note over API: HlsPlaybackServiceImpl and views, reactions, watch history<br/>all use the playable rule, so COLLECTION_ONLY works
```

## 4. Admin adds a COLLECTION_ONLY video to a collection

The video is not in the feed or the "Add videos" picker, so the admin adds it from the Admin videos table.

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    participant FE as Browser (AdminVideoTable, AddToCollectionModal)
    participant API as VideoCollectionController
    participant S as VideoCollectionServiceImpl
    participant DB as PostgreSQL

    A->>FE: row action Add to collection
    FE->>FE: open AddToCollectionModal(videoId)
    A->>FE: pick a collection
    FE->>API: PUT /collections/{id}/videos/{videoId} (JWT)
    API->>S: addVideo(collectionId, videoId)
    S->>DB: existsByIdAndVisibilityNotAndStatus(videoId, HIDDEN, PUBLISHED)
    alt HIDDEN or not published
        S-->>API: error (unchanged collection error)
        API-->>FE: 4xx
    else playable
        S->>DB: insert item
        S-->>API: ok
        API-->>FE: 200
        FE-->>A: added
    end
```

## 5. Import with Collection only

The worker only uploads to Drive. The `video_sources` row is created later, by the Drive scheduler (`saveInfo`) or a force sync (`upsertInfo`). Both copy the visibility from the import job found by `drive_file_id`.

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    participant FE as Browser (ImportForm, ImportTable)
    participant API as AdminVideoImportController
    participant IS as VideoImportServiceImpl
    participant W as VideoImportWorker
    participant ST as VideoStorageServiceImpl
    participant DB as PostgreSQL
    participant FC as FeedCache

    A->>FE: paste URLs, tick Collection only
    FE->>API: POST /admin/video-imports {items, scheduledAt, visibility COLLECTION_ONLY}
    API->>IS: create(request)
    alt visibility HIDDEN or unknown
        IS-->>API: 400 subCode 4104 INVALID_VISIBILITY
        API-->>FE: 400
    else FEED, COLLECTION_ONLY or absent (FEED)
        IS->>DB: INSERT video_import_jobs (visibility) per item
        IS->>W: wakeUp()
        API-->>FE: 200 jobs with visibility
    end
    W->>W: download, upload to Drive
    W->>DB: job DONE, drive_file_id set
    Note over ST: later: scheduler downloadFileFromFolder or force sync
    ST->>DB: findFirstByDriveFileId(fileId)
    DB-->>ST: job (visibility COLLECTION_ONLY) or none (FEED)
    alt saveInfo, row does not exist
        ST->>DB: INSERT video_sources (visibility from job)
    else upsertInfo
        ST->>DB: upsertDriveSource(..., visibility)
        Note over DB: INSERT writes visibility<br/>ON CONFLICT keeps the existing visibility
    end
    ST->>FC: invalidate()
    Note over DB: the video never appears in the feed
```

## 6. Deep link, history or notification opens a COLLECTION_ONLY video

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant SH as Browser (AppShell)
    participant PF as PublicFeed (VideoFeed.jsx)
    participant API as VideoStreamController
    participant S as StreamVideoServiceImpl
    participant DB as PostgreSQL

    U->>SH: open ?v=42, a history card or a notification
    SH->>PF: deepLinkId 42, deepLinkStartAt
    PF->>PF: videos.findIndex(id 42)
    alt found in feed list
        PF-->>U: jump to that slide (unchanged)
    else not in feed list
        PF->>API: GET /video-stream/42
        API->>S: getVideoById(42)
        S->>DB: findById, filter visibility <> HIDDEN and PUBLISHED
        alt COLLECTION_ONLY or FEED
            S-->>API: VideoDto
            API-->>PF: 200
            PF->>PF: insert at index 0, start there (startAt if set)
            PF-->>U: plays video 42, swiping continues into the feed
        else HIDDEN, not published or unknown
            S-->>API: IllegalArgumentException
            API-->>PF: 400
            PF-->>U: feed at index 0 with the not-found message
        end
    end
```
