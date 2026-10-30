# Sequence Diagrams: Bookmark Feature (Library Collections)

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

- **PRD:** [PRD-bookmark-feature](../prd/PRD-bookmark-feature.md)
- **ADR:** [0012-bookmark-feature-design](../adr/0012-bookmark-feature-design.md) (amended 2026-10-13: collections-first, no `bookmarks` table)
- **Spec:** — (see [plan-library-collections](../plans/plan-library-collections.md), which supersedes [plan-bookmark-feature](../plans/plan-bookmark-feature.md))
- **Status:** As-built (2026-10-21)

> **PRD vs code.** The PRD (still `Draft`) and `plan-bookmark-feature` describe a `bookmarks` table and `/bookmarks` endpoints. None of that exists in the code. A "bookmark" is an item in the built-in private collection "Xem sau" (Watch later), served by `VideoCollectionController` under `/v1/funny-app/collections`. The code also has two things the plan lists as out of scope or later: manual ordering (`PATCH /collections/{id}/videos/{videoId}`, column `position`) and `GET /collections/{id}/video-ids`. Items are inserted at the top (`MIN(position) - 1`) instead of ordered by `added_at`.

All endpoints below are relative to `/v1/funny-app`. Writes are JWT-only and carry `@RateLimited(permit = 30)` (the reorder PATCH `/{id}/videos/{videoId}` uses `permit = 60`) and `@AuditLog`. `GET /collections/public` and `GET /collections/{id}/videos` are in `OPTIONAL_AUTH_PATH` (guests allowed, a bad token is treated as a guest).

## 1. Open the Library tab (public and my collections)

A guest or user opens the Library nav. Public collections load for everyone, "my" collections only when logged in (the first call lazily creates "Xem sau"). Admins also receive the public collections in `/collections` flagged `canManage`.

```mermaid
sequenceDiagram
    autonumber
    actor U as User or Guest
    participant FE as Browser (LibraryView.jsx, useCollections.js)
    participant API as VideoCollectionController
    participant S as VideoCollectionServiceImpl
    participant DB as PostgreSQL (video_collections, video_collection_items)

    U->>FE: open Library nav
    par public list
        FE->>API: GET /collections/public (JWT optional)
        API->>S: getPublic()
        alt caller is not admin and cache hit
            S-->>API: publicCache (Guava, 30s TTL)
        else admin, or cache miss
            S->>DB: findTop100ByVisibilityOrderByUpdatedAtDesc(PUBLIC)
            S->>DB: countByCollection + findCovers + owner names
            S-->>API: CollectionDto list (itemCount greater than 0 unless admin)
        end
        API-->>FE: 200 ResultListInfo of CollectionDto
    and my list (only when logged in)
        FE->>API: GET /collections (JWT)
        API->>S: getMine()
        S->>DB: ensureDefault -> insertDefaultIfMissing("Xem sau")
        S->>DB: findManageable(userId, admin)
        S-->>API: CollectionDto list
        API-->>FE: 200 ResultListInfo of CollectionDto
    end
    FE-->>U: "Noi bat" row and "Collection cua toi" row (guest sees sign-in hint)
```

## 2. Save a video to a collection (feed "Luu" button)

A logged-in user clicks "Luu" in `VideoSwiper`. `AddToCollectionModal` loads membership, then toggles checkboxes (PUT adds, DELETE removes) or creates a new collection and adds the video in one go.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (AddToCollectionModal.jsx)
    participant API as VideoCollectionController
    participant S as VideoCollectionServiceImpl
    participant R as VideoCollectionItemRepository
    participant DB as PostgreSQL

    U->>FE: click Luu on a video
    alt guest
        FE-->>U: modal shows loginToSave hint (no API call)
    else logged in
        FE->>API: GET /collections (JWT)
        FE->>API: GET /collections/membership?videoId=X (JWT)
        API->>S: getMembership(videoId)
        S->>R: findCollectionIdsContaining(videoId, manageable ids)
        API-->>FE: 200 list of collection UUIDs
        U->>FE: tick a collection
        FE->>API: PUT /collections/{id}/videos/{videoId} (JWT)
        API->>S: addVideo(id, videoId)
        S->>DB: findByIdForUpdate (row lock) then requireManageable
        S->>DB: existsByIdAndIsHideAndStatus(videoId, false, PUBLISHED)
        S->>R: countItems then addItem (insert at top, idempotent)
        S->>DB: touch updated_at, evict publicCache if PUBLIC
        API-->>FE: 204 No Content
        FE->>FE: useToggleCollectionItem invalidates lists, videos and membership keys
        FE-->>U: checkbox stays ticked
    end
    opt add fails
        API-->>FE: 404 video not visible or collection not manageable
        API-->>FE: 409 error.collection.full (500 items)
        FE-->>U: app-alert error with message
    end
    opt create new collection from the modal
        FE->>API: POST /collections (name, visibility optional)
        API-->>FE: 201 ResultObjectInfo of CollectionDto
        FE->>API: PUT /collections/{newId}/videos/{videoId}
        API-->>FE: 204 No Content
    end
```

## 3. Create, rename, change visibility, delete a collection

`CollectionFormModal` calls create or update, the library list's delete action calls delete after `useConfirm`. Only ADMIN or SUPER_ADMIN may make a collection PUBLIC, and the role is re-read from the DB (`userRepo.findById`), not from the JWT.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (CollectionFormModal.jsx, useCollections.js)
    participant API as VideoCollectionController
    participant S as VideoCollectionServiceImpl
    participant DB as PostgreSQL

    U->>FE: submit name and visibility
    FE->>API: POST /collections (JWT)
    API->>S: create(CollectionRequest)
    S->>S: requireUser, normalizeName (NFC, 1-60 chars)
    alt PUBLIC requested and caller is not admin
        S-->>API: CustomException 403 error.collection.adminOnlyCreatePublic
        API-->>FE: 403 ResultErrorInfo
    else allowed
        S->>DB: lockOwner then countByUserId (cap 50)
        S->>DB: exists name checks (per owner, global for PUBLIC)
        S->>DB: saveAndFlush (unique indexes are the source of truth)
        alt cap reached or name taken
            API-->>FE: 409 limitReached or nameTaken
        else saved
            S->>S: evictPublicIf(saved)
            API-->>FE: 201 ResultObjectInfo of CollectionDto
        end
    end

    U->>FE: rename or toggle public
    FE->>API: PATCH /collections/{id} (JWT)
    API->>S: update(id, request)
    alt default collection Xem sau
        API-->>FE: 400 error.collection.defaultImmutable
    else not manageable or unknown id
        API-->>FE: 404 error.collection.notFound
    else public to private by a different admin
        S->>DB: actor takes ownership (requireCollectionSlot)
        API-->>FE: 200 CollectionDto
    else ok
        API-->>FE: 200 CollectionDto
    end
    FE->>FE: invalidate collections mine and public keys

    U->>FE: confirm delete
    FE->>API: DELETE /collections/{id} (JWT)
    API->>S: delete(id)
    alt default collection
        API-->>FE: 400 error.collection.defaultUndeletable
    else deleted
        S->>DB: delete (items cascade)
        API-->>FE: 204 No Content
    end
```

## 4. Open a collection and play it (including the ?l= deep link)

A shared link `?l=<collectionId>` is read once by `AppShell`, stripped from the address bar and opens `LibraryView` on that collection. Guests can read public collections. A private collection of another user answers 404 so ids cannot be probed.

```mermaid
sequenceDiagram
    autonumber
    actor U as User or Guest
    participant FE as Browser (AppShell.jsx, LibraryView.jsx)
    participant F as JWTAuthenticationFilter
    participant API as VideoCollectionController
    participant S as VideoCollectionServiceImpl
    participant R as VideoCollectionItemRepository

    U->>FE: open link with ?l=collectionId
    FE->>FE: initialLink read once, URL stripped, activeNav = library
    FE->>F: GET /collections/{id}/videos?page=0&size=24 (JWT optional)
    alt no token or invalid token
        F->>API: continue as guest (OPTIONAL_AUTH_PATH)
    else valid token
        F->>API: continue with user in SecurityContext
    end
    API->>S: getVideos(id, page, size)
    S->>S: findById then PUBLIC or owner check
    alt not public and not the owner
        S-->>API: CustomException 404 error.collection.notFound
        API-->>FE: 404 ResultErrorInfo
        FE-->>U: error state
    else visible
        S->>R: findVisibleVideos(collectionId, page clamped to size 1-50)
        R-->>S: Page of VideoSource (hidden and unpublished excluded)
        S-->>API: Page of VideoDto
        API-->>FE: 200 ResultObjectInfo of Page of VideoDto
        FE-->>U: tile grid, Play loads the whole list in pages of 50 (useCollectionPlaylist)
    end
```

## 5. Reorder items by drag and drop (managers only)

An owner (or an admin for a PUBLIC collection) drags a tile in `LibraryView`. The cache is reordered optimistically and rolled back if the call fails.

```mermaid
sequenceDiagram
    autonumber
    actor U as Owner or Admin
    participant FE as Browser (LibraryView.jsx, useMoveCollectionItem)
    participant API as VideoCollectionController
    participant S as VideoCollectionServiceImpl
    participant R as VideoCollectionItemRepository

    U->>FE: drop tile after another tile
    FE->>FE: onMutate moveAfter on cache key collections, id, videos, all
    FE->>API: PATCH /collections/{id}/videos/{videoId} (body afterVideoId or null)
    API->>S: moveVideo(id, videoId, afterVideoId)
    S->>S: requireManageable (row lock)
    alt videoId equals afterVideoId
        API-->>FE: 400 error.collection.moveInvalid
    else video not in collection
        API-->>FE: 404 error.collection.itemNotFound
    else same order
        API-->>FE: 204 No Content (no write)
    else new order
        S->>R: findOrderedVideoIds then renumber(collectionId, ids)
        S->>S: evictPublicIf(collection)
        API-->>FE: 204 No Content
    end
    alt error
        FE->>FE: onError restores previous cache
    end
    FE->>FE: onSettled refetch paged queries and both lists (cover may change)
```
