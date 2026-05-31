# Plan: Bookmark Feature

<!--
Implementation Plan
Filename: docs/plans/plan-bookmark-feature.md
Owner: Builder (/builder)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
-->

## Overview

**Status:** Approved
**Author:** nguyenhuuca
**Date:** 2026-05-31
**Related PRD:** [PRD-bookmark-feature](../prd/PRD-bookmark-feature.md)
**Related ADR:** [ADR-0012: Bookmark Feature Design](../adr/0012-bookmark-feature-design.md)

## Objective

Implement a full-stack bookmark feature: authenticated users can save videos for later, organise them into named collections, and see bookmark state on every video card — without affecting existing video list caching.

## Scope

### In Scope

- Liquibase migration: 3 new tables (`bookmarks`, `bookmark_collections`, `bookmark_collection_items`)
- Backend: entities, repositories, service, controller, DTOs
- Frontend: `api/bookmarks.js`, React Query hook `useBookmarks`, bookmark toggle in `VideoSwiper`, `/bookmarks` page in `AppShell`, collections CRUD UI

### Out of Scope

- Public/shareable bookmark lists
- Guest (unauthenticated) bookmarks
- Notifications for bookmarked videos
- Bulk import/export

---

## Technical Approach

### Architecture

```
[VideoSwiper action-col]           [AppShell side nav → BookmarksPage]
        ↓                                         ↓
[api/bookmarks.js]  ←→  [useBookmarks hook / bookmarkedIds Set]
        ↓
[GET /v1/funny-app/bookmarks/ids]   ← page-load, React Query cache
[POST /v1/funny-app/bookmarks]      ← add (idempotent)
[DELETE /v1/funny-app/bookmarks?videoId=X]  ← remove

        ↓ Spring Boot
[BookmarkController]
[BookmarkService  (@Service)]
[BookmarkRepository + BookmarkCollectionRepository]
        ↓
[PostgreSQL: bookmarks + bookmark_collections + bookmark_collection_items]
```

### Key Decisions (from ADR-0012)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Bookmark state delivery | `GET /bookmarks/ids` at page load, client-side `Set.has()` | Preserves Guava LRU cache; max ~4 KB payload at 500-cap |
| FK strategy | Actual DB FK constraints with `@ManyToOne` | Bookmarks require referential integrity; no guest-user flexibility needed |
| ON DELETE video_id | SET NULL + `source_video_id` immutable column | Maintains unique constraint after video deletion |
| Toggle API | `POST` (idempotent) + `DELETE ?videoId=X` | Video-centric; client never manages internal bookmark IDs |
| ID type | UUID (matches VideoComment) | Most recent pattern; no sequential ID leakage |
| No service interface | `@Service` directly on impl | Matches VideoComment/existing codebase pattern |

### Schema Refinement (vs ADR sketch)

The ADR proposed `UNIQUE(user_id, video_id)` but `video_id` can become NULL via `ON DELETE SET NULL`, breaking the constraint. Solution: add `source_video_id BIGINT NOT NULL` — an immutable copy of the original video ID used for uniqueness. The FK `video_id` can go null; `source_video_id` never changes.

```sql
bookmarks (
  id              UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         BIGINT  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  video_id        BIGINT           REFERENCES video_sources(id) ON DELETE SET NULL,
  source_video_id BIGINT  NOT NULL,   -- immutable; used for uniqueness
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_bookmark_user_video UNIQUE (user_id, source_video_id)
)
```

---

## Implementation Steps

### Phase 1: Database Migration (0.5 day)

- [ ] **1.1** Create migration file `api/src/main/resources/db/changelog/sql/202605310001-create-bookmark-tables.sql`
  - Create `bookmarks` table with `source_video_id` column (see schema above)
  - Create `bookmark_collections` table
  - Create `bookmark_collection_items` junction table
  - Create all indexes
  - Details:
    ```sql
    CREATE TABLE bookmark_collections (
        id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        name       VARCHAR(100) NOT NULL,
        created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
    );
    CREATE INDEX idx_bk_collections_user ON bookmark_collections(user_id);

    CREATE TABLE bookmarks (
        id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        video_id        BIGINT               REFERENCES video_sources(id) ON DELETE SET NULL,
        source_video_id BIGINT      NOT NULL,
        created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
        CONSTRAINT uq_bookmark_user_video UNIQUE (user_id, source_video_id)
    );
    CREATE INDEX idx_bookmarks_user ON bookmarks(user_id);

    CREATE TABLE bookmark_collection_items (
        collection_id UUID NOT NULL REFERENCES bookmark_collections(id) ON DELETE CASCADE,
        bookmark_id   UUID NOT NULL REFERENCES bookmarks(id) ON DELETE CASCADE,
        PRIMARY KEY (collection_id, bookmark_id)
    );
    ```

- [ ] **1.2** Register migration in `api/src/main/resources/db/changelog/db.changelog-master.yaml`
  - Add `include file: sql/202605310001-create-bookmark-tables.sql` in correct order

---

### Phase 2: Backend Entities (0.5 day)

- [ ] **2.1** Create `api/src/main/java/com/canhlabs/funnyapp/entity/Bookmark.java`
  - Fields: `UUID id`, `User user` (@ManyToOne LAZY, nullable=false), `VideoSource video` (@ManyToOne LAZY, optional=true), `Long sourceVideoId` (NOT NULL, updatable=false), `Instant createdAt` (@CreationTimestamp)
  - Annotations: `@Entity @Table(name="bookmarks")`, Lombok `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`
  - `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "source_video_id"}))`

- [ ] **2.2** Create `api/src/main/java/com/canhlabs/funnyapp/entity/BookmarkCollection.java`
  - Fields: `UUID id`, `Long userId` (column: user_id, NOT NULL), `String name`, `Instant createdAt` (@CreationTimestamp)
  - Same Lombok annotations

- [ ] **2.3** Create `api/src/main/java/com/canhlabs/funnyapp/entity/BookmarkCollectionItem.java`
  - Composite PK via `@Embeddable BookmarkCollectionItemId { UUID collectionId; UUID bookmarkId; }`
  - `@EmbeddedId`, `@ManyToOne` to `BookmarkCollection` and `Bookmark`

---

### Phase 3: Backend Repositories (0.5 day)

- [ ] **3.1** Create `api/src/main/java/com/canhlabs/funnyapp/repo/BookmarkRepository.java`
  - `extends JpaRepository<Bookmark, UUID>`
  - Methods:
    ```java
    List<Long> findSourceVideoIdsByUserId(Long userId);
    Optional<Bookmark> findByUserIdAndSourceVideoId(Long userId, Long sourceVideoId);
    List<Bookmark> findByUserIdOrderByCreatedAtDesc(Long userId);
    boolean existsByUserIdAndSourceVideoId(Long userId, Long sourceVideoId);
    long countByUserId(Long userId);
    void deleteByUserIdAndSourceVideoId(Long userId, Long sourceVideoId);
    ```
  - Note: `findSourceVideoIdsByUserId` needs a `@Query("SELECT b.sourceVideoId FROM Bookmark b WHERE b.user.id = :userId")`

- [ ] **3.2** Create `api/src/main/java/com/canhlabs/funnyapp/repo/BookmarkCollectionRepository.java`
  - `extends JpaRepository<BookmarkCollection, UUID>`
  - Methods:
    ```java
    List<BookmarkCollection> findByUserIdOrderByCreatedAtAsc(Long userId);
    long countByUserId(Long userId);
    Optional<BookmarkCollection> findByIdAndUserId(UUID id, Long userId);
    ```

- [ ] **3.3** Create `api/src/main/java/com/canhlabs/funnyapp/repo/BookmarkCollectionItemRepository.java`
  - Methods to add/remove items from collections

---

### Phase 4: DTOs (0.5 day)

- [ ] **4.1** Create DTOs in `api/src/main/java/com/canhlabs/funnyapp/dto/`:
  - `BookmarkDto` — `{ id: UUID, sourceVideoId: Long, videoId: Long|null, title: String|null, createdAt: Instant }`
  - `BookmarkIdsDto` — `{ videoIds: List<Long> }`
  - `AddBookmarkRequest` — `{ videoId: Long }` with `@NotNull` validation
  - `BookmarkCollectionDto` — `{ id: UUID, name: String, bookmarkCount: int }`
  - `CreateCollectionRequest` — `{ name: String }` with `@NotBlank @Size(max=100)`
  - `RenameCollectionRequest` — `{ name: String }` with `@NotBlank @Size(max=100)`
  - `AddToCollectionRequest` — `{ bookmarkId: UUID }`

---

### Phase 5: Backend Service (1.5 days)

- [ ] **5.1** Create `api/src/main/java/com/canhlabs/funnyapp/service/impl/BookmarkService.java`
  - `@Service @RequiredArgsConstructor @Slf4j`
  - Inject: `BookmarkRepository`, `BookmarkCollectionRepository`, `BookmarkCollectionItemRepository`, `VideoSourceRepository` (for video title lookup)
  - Auth helper:
    ```java
    private UserDetailDto currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new UnauthorizedException("...");
        return (UserDetailDto) auth.getDetails();
    }
    ```

- [ ] **5.2** Implement core bookmark methods:
  ```java
  @Transactional(readOnly = true)
  public BookmarkIdsDto getBookmarkIds()          // returns all source_video_ids for current user

  @Transactional(readOnly = true)
  public List<BookmarkDto> getBookmarks()          // full list, newest first

  @Transactional
  public BookmarkDto addBookmark(Long videoId)     // idempotent; throws 409 if cap reached

  @Transactional
  public void removeBookmark(Long videoId)         // delete by (userId, sourceVideoId)
  ```
  - Cap enforcement: `if (repo.countByUserId(userId) >= 500) throw new LimitExceededException("...")`
  - Idempotent add: check `existsByUserIdAndSourceVideoId` first; return existing if found

- [ ] **5.3** Implement collection methods:
  ```java
  @Transactional(readOnly = true)
  public List<BookmarkCollectionDto> getCollections()

  @Transactional
  public BookmarkCollectionDto createCollection(String name)   // cap: 50 collections/user

  @Transactional
  public BookmarkCollectionDto renameCollection(UUID id, String name)

  @Transactional
  public void deleteCollection(UUID id)    // items cascade; bookmarks NOT deleted

  @Transactional
  public void addToCollection(UUID collectionId, UUID bookmarkId)

  @Transactional
  public void removeFromCollection(UUID collectionId, UUID bookmarkId)
  ```
  - All methods: verify ownership via `findByIdAndUserId` before modifying

---

### Phase 6: Backend Controller (1 day)

- [ ] **6.1** Create `api/src/main/java/com/canhlabs/funnyapp/web/BookmarkController.java`
  - `@RestController @RequestMapping(AppConstant.API.BASE_URL + "/bookmarks")`
  - `@AuditLog` at class level (matches `UserController` pattern)
  - Endpoints:
    ```java
    @GetMapping("/ids")
    ResultObjectInfo<BookmarkIdsDto> getIds()

    @GetMapping
    ResultListInfo<BookmarkDto> getBookmarks()

    @PostMapping
    @RateLimited(permit = 10)
    ResponseEntity<ResultObjectInfo<BookmarkDto>> addBookmark(@Valid @RequestBody AddBookmarkRequest req)

    @DeleteMapping
    @RateLimited(permit = 10)
    ResponseEntity<Void> removeBookmark(@RequestParam Long videoId)

    @GetMapping("/collections")
    ResultListInfo<BookmarkCollectionDto> getCollections()

    @PostMapping("/collections")
    @RateLimited(permit = 5)
    ResponseEntity<ResultObjectInfo<BookmarkCollectionDto>> createCollection(@Valid @RequestBody CreateCollectionRequest req)

    @PutMapping("/collections/{id}")
    ResultObjectInfo<BookmarkCollectionDto> renameCollection(@PathVariable UUID id, @Valid @RequestBody RenameCollectionRequest req)

    @DeleteMapping("/collections/{id}")
    ResponseEntity<Void> deleteCollection(@PathVariable UUID id)

    @PostMapping("/collections/{id}/items")
    ResponseEntity<Void> addToCollection(@PathVariable UUID id, @Valid @RequestBody AddToCollectionRequest req)

    @DeleteMapping("/collections/{id}/items/{bookmarkId}")
    ResponseEntity<Void> removeFromCollection(@PathVariable UUID id, @PathVariable UUID bookmarkId)
    ```

- [ ] **6.2** Verify `JWTAuthenticationFilter.shouldNotFilter()` does NOT whitelist `/bookmarks/**`
  - No change needed if not in whitelist — JWT filter auto-blocks unauthenticated requests
  - Add `400/401/409` error codes to `@ControllerAdvice` if not already handled

---

### Phase 7: Backend Tests (1 day)

- [ ] **7.1** Create `BookmarkServiceTest.java` (unit test)
  - Test: `addBookmark` idempotent (same videoId twice → returns existing, no duplicate)
  - Test: cap at 500 → 501st throws exception
  - Test: `removeBookmark` on non-existent → no error (idempotent)
  - Test: ownership check on collection ops → throws for wrong user
  - Test: `deleteCollection` → bookmarks still exist in `getBookmarks()`
  - Mock: `BookmarkRepository`, `BookmarkCollectionRepository`

- [ ] **7.2** Create `BookmarkControllerTest.java` (integration/slice test)
  - Test: unauthenticated `GET /bookmarks` → 401
  - Test: `POST /bookmarks { videoId: X }` → 201 first time, 200 second time
  - Test: `DELETE /bookmarks?videoId=X` → 204
  - Test: collection CRUD flow end-to-end

- [ ] **7.3** Run `mvn verify` and update coverage threshold in `api/.coverage-threshold` (+1%)

---

### Phase 8: Frontend API Module (0.5 day)

- [ ] **8.1** Create `webapp/src/api/bookmarks.js`
  ```javascript
  import { api } from './client'

  export const bookmarksApi = {
    ids:              ()           => api.get('/bookmarks/ids'),
    list:             ()           => api.get('/bookmarks'),
    add:              (videoId)    => api.post('/bookmarks', { videoId }),
    remove:           (videoId)    => api.delete(`/bookmarks?videoId=${videoId}`),
    collections: {
      list:           ()           => api.get('/bookmarks/collections'),
      create:         (name)       => api.post('/bookmarks/collections', { name }),
      rename:         (id, name)   => api.put(`/bookmarks/collections/${id}`, { name }),
      delete:         (id)         => api.delete(`/bookmarks/collections/${id}`),
      addItem:        (id, bmId)   => api.post(`/bookmarks/collections/${id}/items`, { bookmarkId: bmId }),
      removeItem:     (id, bmId)   => api.delete(`/bookmarks/collections/${id}/items/${bmId}`),
    }
  }
  ```

---

### Phase 9: Frontend State Hook (0.5 day)

- [ ] **9.1** Create `webapp/src/hooks/useBookmarks.js`
  ```javascript
  import { useQuery } from '@tanstack/react-query'
  import { bookmarksApi } from '../api/bookmarks'

  // Returns a Set<number> of bookmarked video IDs — O(1) per-card lookup
  export function useBookmarkedIds() {
    const { data } = useQuery({
      queryKey: ['bookmarks', 'ids'],
      queryFn: () => bookmarksApi.ids(),
      staleTime: Infinity,           // only invalidated by mutations
      enabled: !!localStorage.getItem('jwt'),
    })
    return new Set(data?.videoIds ?? [])
  }

  export function useBookmarksList() {
    return useQuery({
      queryKey: ['bookmarks', 'list'],
      queryFn: () => bookmarksApi.list(),
      enabled: !!localStorage.getItem('jwt'),
    })
  }

  export function useCollections() {
    return useQuery({
      queryKey: ['bookmarks', 'collections'],
      queryFn: () => bookmarksApi.collections.list(),
      enabled: !!localStorage.getItem('jwt'),
    })
  }
  ```

- [ ] **9.2** Create `webapp/src/hooks/useBookmarkActions.js`
  - `addBookmark(videoId)` — calls API, then `queryClient.invalidateQueries(['bookmarks', 'ids'])`
  - `removeBookmark(videoId)` — calls API, then invalidates same key
  - Use optimistic update for toggle: flip Set state immediately, revert on error
  - Export `queryClient` from `webapp/src/main.jsx` or use `useQueryClient()`

---

### Phase 10: BookmarkButton Component (0.5 day)

- [ ] **10.1** Create `webapp/src/components/video/BookmarkButton.jsx`
  ```jsx
  // Props: videoId (number)
  // Reads bookmarkedIds Set from useBookmarkedIds()
  // Calls addBookmark/removeBookmark on click
  // Shows filled icon when bookmarked, outline when not
  ```
  - Style: match `.action-btn` class (56px circular glass button) from `VideoSwiper.jsx`
  - Icon: FontAwesome `faBookmark` (solid) / `faBookmark` (regular) from `@fortawesome/react-fontawesome`
  - Hide when user not logged in

- [ ] **10.2** Add `<BookmarkButton videoId={video.id} />` to `.action-col` in `VideoSwiper.jsx`
  - Place between share and delete buttons
  - Conditional: only render when `user` context is authenticated

---

### Phase 11: Bookmarks Page (1 day)

- [ ] **11.1** Create `webapp/src/pages/BookmarksPage.jsx`
  - Uses `useBookmarksList()` for data
  - Grid layout matching existing video list style
  - Each card: thumbnail (poster), title, play button, remove-bookmark button
  - Handle `videoId: null` case — show "Video no longer available" placeholder card
  - Empty state: "No bookmarks yet — save videos to watch later"
  - Loading skeleton matching existing feed skeleton

- [ ] **11.2** Add `bookmarks` to side nav in `AppShell.jsx`
  - Add to `SIDE_NAV` array: `{ key: 'bookmarks', label: 'Bookmarks', icon: faBookmark }`
  - Add conditional render: `activeNav === 'bookmarks' && <BookmarksPage />`
  - Import `BookmarksPage`, `faBookmark`

---

### Phase 12: Collections UI (1.5 days)

- [ ] **12.1** Create `webapp/src/components/bookmark/CollectionManager.jsx`
  - Shown in `BookmarksPage` as a sidebar or top panel
  - Lists existing collections via `useCollections()`
  - Create new collection: text input + submit
  - Rename collection: inline edit on double-click
  - Delete collection: button with confirmation
  - Active collection filter: clicking a collection filters the bookmark list

- [ ] **12.2** Create `webapp/src/components/bookmark/AddToCollectionModal.jsx`
  - Triggered from bookmark card menu (ellipsis button)
  - Shows list of existing collections with checkboxes
  - Add/remove bookmark from collection via API calls
  - Invalidates `['bookmarks', 'collections']` on change

- [ ] **12.3** Wire up: bookmark card in `BookmarksPage` shows collection badges + ellipsis menu
  - Ellipsis menu → "Add to collection" → opens `AddToCollectionModal`

---

## Files to Create / Modify

| File | Action | Description |
|------|--------|-------------|
| `api/src/main/resources/db/changelog/sql/202605310001-create-bookmark-tables.sql` | Create | DB migration |
| `api/src/main/resources/db/changelog/db.changelog-master.yaml` | Modify | Register migration |
| `api/.../entity/Bookmark.java` | Create | JPA entity |
| `api/.../entity/BookmarkCollection.java` | Create | JPA entity |
| `api/.../entity/BookmarkCollectionItem.java` | Create | JPA entity |
| `api/.../repo/BookmarkRepository.java` | Create | JPA repository |
| `api/.../repo/BookmarkCollectionRepository.java` | Create | JPA repository |
| `api/.../repo/BookmarkCollectionItemRepository.java` | Create | JPA repository |
| `api/.../dto/BookmarkDto.java` | Create | Response DTO |
| `api/.../dto/BookmarkIdsDto.java` | Create | Response DTO |
| `api/.../dto/AddBookmarkRequest.java` | Create | Request DTO |
| `api/.../dto/BookmarkCollectionDto.java` | Create | Response DTO |
| `api/.../dto/CreateCollectionRequest.java` | Create | Request DTO |
| `api/.../dto/RenameCollectionRequest.java` | Create | Request DTO |
| `api/.../dto/AddToCollectionRequest.java` | Create | Request DTO |
| `api/.../service/impl/BookmarkService.java` | Create | Service |
| `api/.../web/BookmarkController.java` | Create | REST controller |
| `api/.../web/VideoSwiper.jsx` | Modify | Add BookmarkButton to action col |
| `webapp/src/api/bookmarks.js` | Create | API module |
| `webapp/src/hooks/useBookmarks.js` | Create | React Query hooks |
| `webapp/src/hooks/useBookmarkActions.js` | Create | Mutation helpers |
| `webapp/src/components/video/BookmarkButton.jsx` | Create | Toggle button |
| `webapp/src/pages/BookmarksPage.jsx` | Create | Bookmarks list page |
| `webapp/src/components/bookmark/CollectionManager.jsx` | Create | Collection CRUD UI |
| `webapp/src/components/bookmark/AddToCollectionModal.jsx` | Create | Add-to-collection modal |
| `webapp/src/AppShell.jsx` | Modify | Add bookmarks nav item + route |

---

## Dependencies

### Code Dependencies

| Package | Already Present | Notes |
|---------|----------------|-------|
| `@fortawesome/react-fontawesome` | Yes | Use `faBookmark` from free-solid / free-regular |
| Spring Security | Yes | JWT filter auto-blocks unauthenticated requests |
| Liquibase | Yes | Standard migration |
| `@tanstack/react-query` | Yes | `useQueryClient` for invalidation |

### Service Dependencies

| Service | Status | Notes |
|---------|--------|-------|
| PostgreSQL | Available | Tables created via migration |
| `VideoSourceRepository` | Existing | Needed to validate videoId on bookmark add |

---

## Testing Strategy

### Unit Tests

| Component | Test Cases |
|-----------|------------|
| `BookmarkService.addBookmark` | Idempotent add; cap enforcement (500); videoId not found |
| `BookmarkService.removeBookmark` | Removes correctly; no-op on missing |
| `BookmarkService.deleteCollection` | Bookmarks remain after collection delete |
| `BookmarkService.addToCollection` | Ownership check; bookmark belongs to user |

### Integration Tests

| Scenario | Expected Outcome |
|----------|------------------|
| Unauthenticated `GET /bookmarks` | 401 |
| `POST /bookmarks` twice with same videoId | 201 then 200 (idempotent) |
| `POST /bookmarks` on 501st video | 409 with cap error message |
| `DELETE /bookmarks?videoId=X` | 204; subsequent `GET /ids` excludes X |
| Create → rename → delete collection | Collections list reflects changes |
| Delete collection → `GET /bookmarks` | Bookmarks still present |

### Manual Testing

- [ ] Bookmark a video → icon fills immediately (optimistic)
- [ ] Refresh page → icon still filled (persisted via `/ids` endpoint)
- [ ] Un-bookmark → icon clears immediately; removed from `/bookmarks` page
- [ ] Browse to `/bookmarks` (side nav) → saved videos appear
- [ ] Create collection, add bookmark, delete collection → bookmark still in "All Bookmarks"
- [ ] Null video card (deleted video) → placeholder renders without crash

---

## Dependency Graph (Task Order)

```
Phase 1 (Migration)
  └── Phase 2 (Entities)
        └── Phase 3 (Repos)
              └── Phase 4 (DTOs) ──┐
                    └── Phase 5 (Service) ──┐
                          └── Phase 6 (Controller)
                                └── Phase 7 (BE Tests)

Phase 8 (Frontend API module)  ← can start after Phase 6 API contract is stable
  └── Phase 9 (Hooks)
        └── Phase 10 (BookmarkButton)    ← can parallel with Phase 11
        └── Phase 11 (BookmarksPage)
              └── Phase 12 (Collections UI)
```

Backend phases 1–7 must complete before frontend can be tested end-to-end.
Frontend Phase 8 can start once the API contract is finalised (after Phase 6).

---

## Rollback Plan

1. Revert AppShell.jsx changes (remove bookmarks nav entry)
2. Revert VideoSwiper.jsx (remove BookmarkButton)
3. Drop migration: `DROP TABLE bookmark_collection_items; DROP TABLE bookmark_collections; DROP TABLE bookmarks;`
4. Remove new files (entities, repos, service, controller, DTOs, frontend modules)
5. No data loss to existing features — all changes are additive

---

## Risks

| Risk | Mitigation |
|------|------------|
| `source_video_id` not populated on entity create | Set `sourceVideoId = videoId` in service before save; add `@Column(updatable = false)` |
| `BookmarkButton` renders for unauthenticated users | Check `AuthContext.user` in component; render null if not logged in |
| N+1 on `getBookmarks()` (loading User/VideoSource per bookmark) | Use `@EntityGraph` or `JOIN FETCH` in repo query |
| Coverage gate fails | Run `mvn verify` after Phase 7; update `api/.coverage-threshold` |
| Font Awesome bookmark icon not in current icon set | Check imports; add `faBookmark` from `@fortawesome/free-solid-svg-icons` and `@fortawesome/free-regular-svg-icons` |

---

## Checklist

### Before Starting

- [x] PRD approved (Draft — pending formal sign-off)
- [x] ADR-0012 written and reviewed
- [x] Branch created from main

### Before PR

- [ ] `mvn verify` passes (all tests + coverage gate)
- [ ] `npm run lint` passes
- [ ] `npm run test` passes
- [ ] Manual test checklist complete
- [ ] No hardcoded user IDs or secrets
- [ ] Null `videoId` handled in frontend

### Before Merge

- [ ] Code review approved
- [ ] Coverage threshold updated in `api/.coverage-threshold`
- [ ] Migration file reviewed for correctness

---

## Progress Log

| Date | Update |
|------|--------|
| 2026-05-31 | Plan created from PRD + ADR-0012 |
