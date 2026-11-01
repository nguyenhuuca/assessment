# Sequence Diagrams: Admin Bulk Actions

- **PRD:** [PRD-admin-bulk-actions](../prd/PRD-admin-bulk-actions.md)
- **ADR:** [0036-admin-bulk-actions](../adr/0036-admin-bulk-actions.md)
- **Spec:** —
- **Status:** Draft

## 1. Bulk change video visibility (happy path with mixed rows)

Admin selects rows on Video Vault and picks a visibility in the bulk bar. Each id runs the existing single-row method in its own transaction.

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    participant FE as Browser (AdminVideoTable + useBulkSelection)
    participant API as AdminController
    participant B as AdminVideoBulkServiceImpl
    participant S as AdminVideoServiceImpl (@Transactional)
    participant DB as PostgreSQL
    participant C as FeedCache

    A->>FE: tick 3 rows, choose COLLECTION_ONLY
    FE->>API: POST /admin/videos/bulk {ids:[1,2,9], action:VISIBILITY, visibility}
    Note over API: @HasPermission(ADMIN) · @AuditLog · @RateLimited · @Valid (1–100 ids)
    API->>B: bulk(request)
    loop each id
        B->>S: updateVisibility(id, visibility)
        alt row exists, value differs
            S->>DB: UPDATE video_sources SET visibility
            S->>C: invalidate()
            S-->>B: true (updated)
        else already that value
            S-->>B: false (unchanged)
        else not found
            S-->>B: NotFound
        end
    end
    B-->>API: BulkResultDto {updated:1, unchanged:1, notFound:[9]}
    API-->>FE: 200 ResultObjectInfo<BulkResultDto>
    FE->>FE: invalidate ['admin','videos'], clear selection
    FE-->>A: notice "1 updated · 1 unchanged · 1 not found"
```

## 2. Bulk delete videos – one row locked by Drive sync

A failure in one row is reported as skipped; the other rows are already committed.

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    participant FE as Browser (AdminVideoTable)
    participant API as AdminController
    participant B as AdminVideoBulkServiceImpl
    participant S as AdminVideoServiceImpl
    participant L as DriveFileLocks
    participant DB as PostgreSQL

    A->>FE: Delete 2 selected (confirm with count, local-file option)
    FE->>API: POST /admin/videos/bulk {ids:[4,5], action:DELETE, deleteLocalFile:true}
    API->>B: bulk(request)
    B->>S: deleteVideo(4, local=true, drive=false)
    S->>L: tryClaim(file 4)
    L-->>S: claimed
    S->>DB: DELETE video_sources id=4 (tx 1 commits)
    B->>S: deleteVideo(5, local=true, drive=false)
    S->>L: tryClaim(file 5)
    L-->>S: busy
    S-->>B: AppException(code=video.fileBusy)
    B->>B: skipped += {5, video.fileBusy}
    B-->>API: {updated:1, skipped:[{5, video.fileBusy}]}
    API-->>FE: 200
    FE-->>A: "1 deleted · 1 skipped (file busy – Drive sync running)"
```

## 3. Bulk add videos to a collection

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    participant FE as Browser (BulkAddToCollectionModal)
    participant API as VideoCollectionController
    participant B as CollectionBulkServiceImpl
    participant S as VideoCollectionServiceImpl (@Transactional)
    participant DB as PostgreSQL

    A->>FE: pick collection "Research" for 3 selected videos
    FE->>API: POST /collections/{id}/videos/bulk {videoIds:[1,2,3]}
    Note over API: @RateLimited · @AuditLog
    API->>B: addVideos(id, videoIds, user)
    B->>S: requireManageable(id, user) once (own or PUBLIC-as-admin), else 403/404 for the whole request
    loop each videoId (own tx via proxy)
        B->>S: addVideo(id, videoId)
        alt PUBLISHED and not HIDDEN, not yet in collection
            S->>DB: INSERT video_collection_items
            S-->>B: added
        else already in collection
            S-->>B: unchanged
        else PENDING/FLAGGED/HIDDEN
            S-->>B: NotFound → skipped (collection.videoNotEligible)
        else cap 500 reached
            S-->>B: 409 → skipped (collection.full)
        end
    end
    B-->>API: BulkResultDto
    API-->>FE: 200 BulkResultDto
    FE->>FE: invalidate collection lists + ['admin','videos']
```

## 4. Bulk reject suggestions – validation before any row

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    participant FE as Browser (AdminSuggestionTable)
    participant API as AdminVideoSuggestionController
    participant B as SuggestionBulkServiceImpl
    participant S as VideoSuggestionServiceImpl

    A->>FE: Reject 5 selected, reason OTHER, empty note
    FE->>API: POST /admin/video-suggestions/bulk {ids, action:REJECT, reason:OTHER}
    API->>B: bulk(request)
    B-->>API: 400 (note required for OTHER) – no row touched
    API-->>FE: 400 ResultErrorInfo
    FE-->>A: error shown, selection kept
    A->>FE: add note, submit again
    FE->>API: POST ... {reason:OTHER, note}
    loop each id
        B->>S: reject(id, reason, note)
        S-->>B: ok / notPending (4093) → skipped
    end
    B-->>API: BulkResultDto
    API-->>FE: 200
```

## 5. Bulk account role change – guards per row

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin (ADMIN)
    participant FE as Browser (AdminAccountTable)
    participant API as AdminController
    participant B as AdminAccountBulkServiceImpl
    participant S as AdminAccountServiceImpl
    participant DB as PostgreSQL

    A->>FE: select 3 accounts (self/outranked checkboxes disabled), role CONTRIBUTOR
    FE->>API: POST /admin/accounts/bulk {ids, action:ROLE, role:CONTRIBUTOR}
    API->>B: bulk(request, actorId)
    loop each id
        B->>S: updateRole(id, actorId, CONTRIBUTOR)
        alt allowed
            S->>DB: UPDATE users SET role, credentials_version+1
        else self / outranked
            S-->>B: IllegalArgument/Forbidden → skipped {id, code}
        end
    end
    B-->>API: BulkResultDto
    API-->>FE: 200
```
