# Plan: Watch History

## Overview

**Status:** Approved
**Author:** nguyenhuuca
**Date:** 2026-05-31
**Related PRD:** [PRD-watch-history](../prd/PRD-watch-history.md)
**Related ADR:** [ADR-0013: Watch History Design](../adr/0013-watch-history-design.md)
**Related Spec:** [Spec: Watch History](../specs/spec-watch-history.md)

## Objective

Implement watch history end-to-end: auto-record on play, `/history` page replacing ComingSoon, watched indicator on video cards, delete/clear operations — all scoped to authenticated users.

## Scope

### In Scope
- Liquibase migration: `watch_history` table
- Backend: entity, repository, service (upsert + auto-evict), controller, DTOs
- Frontend: `watchHistory.js` API module, hooks, `WatchedBadge` component, `HistoryPage`, AppShell wiring

### Out of Scope
- Resume playback (watch timestamp)
- Recommendations based on history
- Guest user history

---

## Technical Approach

### Architecture

```
[VideoSwiper — on play event]
        ↓ fire-and-forget (no await)
[api/watchHistory.js → POST /watch-history]
        ↓
[WatchHistoryController]
[WatchHistoryService — upsert + auto-evict]
[WatchHistoryRepository]
        ↓
[PostgreSQL: watch_history table]

[Page load]
        ↓
[GET /watch-history/ids → useWatchedIds hook → Set<Long>]
        ↓
[VideoSwiper card → WatchedBadge — Set.has(video.id)]

[AppShell activeNav === 'history']
        ↓
[HistoryPage → useWatchHistory hook → GET /watch-history]
```

### Key Decisions (from ADR-0013 + Spec)

| Decision | Choice |
|----------|--------|
| Recording trigger | Frontend fire-and-forget POST on play |
| Cap strategy | Auto-evict oldest (sliding window, 500 entries) |
| Re-watch | Upsert — update `watched_at`, move to top |
| Invalid videoId | 200 silent ignore (not 404) |
| State delivery | GET /ids at page load → client-side `Set.has()` |
| Uniqueness | `UNIQUE(user_id, source_video_id)` — stable under `ON DELETE SET NULL` |

### AppShell Change

Current catch-all (renders ComingSoon for all non-home nav):
```javascript
} : activeNav !== 'home' ? (
  <ComingSoon page={activeNav} />
```

Change to add history condition before catch-all:
```javascript
} : activeNav === 'history' ? (
  <HistoryPage />
) : activeNav !== 'home' ? (
  <ComingSoon page={activeNav} />
```

---

## Implementation Steps

### Phase 1: Database Migration (0.5 day)

- [ ] **1.1** Create `api/src/main/resources/db/changelog/sql/202605310002-create-watch-history-table.sql`
  ```sql
  CREATE TABLE watch_history (
      id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      video_id        BIGINT               REFERENCES video_sources(id) ON DELETE SET NULL,
      source_video_id BIGINT      NOT NULL,
      watched_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
      CONSTRAINT uq_watch_history_user_video UNIQUE (user_id, source_video_id)
  );
  CREATE INDEX idx_watch_history_user_watched ON watch_history(user_id, watched_at DESC);
  ```

- [ ] **1.2** Register in `api/src/main/resources/db/changelog/db.changelog-master.yaml`

---

### Phase 2: Backend Entity (0.5 day)

- [ ] **2.1** Create `api/.../entity/WatchHistory.java`
  - Fields: `UUID id`, `@ManyToOne(LAZY) User user`, `@ManyToOne(LAZY, optional=true) VideoSource video`, `Long sourceVideoId` (`@Column(updatable=false)`), `Instant watchedAt` (`@CreationTimestamp`)
  - `@Table(name="watch_history", uniqueConstraints = @UniqueConstraint(columnNames={"user_id","source_video_id"}))`
  - Lombok: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`

---

### Phase 3: Backend Repository (0.5 day)

- [ ] **3.1** Create `api/.../repo/WatchHistoryRepository.java`
  ```java
  extends JpaRepository<WatchHistory, UUID>

  @Query("SELECT w.sourceVideoId FROM WatchHistory w WHERE w.user.id = :userId")
  List<Long> findSourceVideoIdsByUserId(Long userId);

  List<WatchHistory> findByUserIdOrderByWatchedAtDesc(Long userId);

  Optional<WatchHistory> findByUserIdAndSourceVideoId(Long userId, Long sourceVideoId);

  long countByUserId(Long userId);

  @Query("SELECT w FROM WatchHistory w WHERE w.user.id = :userId ORDER BY w.watchedAt ASC LIMIT 1")
  Optional<WatchHistory> findOldestByUserId(Long userId);

  void deleteByUserIdAndSourceVideoId(Long userId, Long sourceVideoId);

  void deleteByUserId(Long userId);
  ```

---

### Phase 4: DTOs (0.5 day)

- [ ] **4.1** Create in `api/.../dto/`:
  - `WatchHistoryDto` — `{ id: UUID, sourceVideoId: Long, videoId: Long|null, title: String|null, poster: String|null, watchedAt: Instant }`
  - `WatchHistoryIdsDto` — `{ videoIds: List<Long> }`
  - `RecordWatchRequest` — `{ videoId: Long }` with `@NotNull`

---

### Phase 5: Backend Service (1 day)

- [ ] **5.1** Create `api/.../service/impl/WatchHistoryService.java`
  - `@Service @RequiredArgsConstructor @Slf4j`
  - Auth helper: `SecurityContextHolder.getContext().getAuthentication().getDetails()` → `UserDetailDto`

- [ ] **5.2** Implement methods:
  ```java
  @Transactional(readOnly = true)
  WatchHistoryIdsDto getWatchedIds()
  // → SELECT source_video_id WHERE user_id = current

  @Transactional(readOnly = true)
  List<WatchHistoryDto> getHistory()
  // → findByUserIdOrderByWatchedAtDesc

  @Transactional
  WatchHistoryDto recordWatch(Long videoId)
  // 1. Check if entry exists for (userId, videoId)
  // 2a. If exists → update watchedAt = now(), return
  // 2b. If new → check count >= 500 → if yes, delete oldest → insert new

  @Transactional
  void removeEntry(Long videoId)
  // deleteByUserIdAndSourceVideoId — no-op if not found (idempotent)

  @Transactional
  void clearAll()
  // deleteByUserId
  ```

- [ ] **5.3** `recordWatch` must NOT throw if `videoId` not found in `video_sources` — set `video = null`, `sourceVideoId = videoId`, persist silently

---

### Phase 6: Backend Controller (0.5 day)

- [ ] **6.1** Create `api/.../web/WatchHistoryController.java`
  ```java
  @RestController
  @RequestMapping(AppConstant.API.BASE_URL + "/watch-history")
  // No @AuditLog — POST volume too high per Spec

  @GetMapping("/ids")
  ResultObjectInfo<WatchHistoryIdsDto> getIds()

  @GetMapping
  ResultListInfo<WatchHistoryDto> getHistory()

  @PostMapping
  @RateLimited(permit = 30)
  ResponseEntity<ResultObjectInfo<WatchHistoryDto>> record(@Valid @RequestBody RecordWatchRequest req)
  // Returns 201 for new, 200 for upsert

  @DeleteMapping
  ResponseEntity<Void> remove(@RequestParam Long videoId)
  // 204 always (idempotent)

  @DeleteMapping("/all")
  ResponseEntity<Void> clearAll()
  // 204
  ```

- [ ] **6.2** Confirm `/v1/funny-app/watch-history/**` is NOT in `JWTAuthenticationFilter.shouldNotFilter()` whitelist

---

### Phase 7: Backend Tests (1 day)

- [ ] **7.1** `WatchHistoryServiceTest.java`
  - `recordWatch`: new entry → 201; re-watch → 200 + watched_at updated
  - `recordWatch`: at cap 500, new video → oldest evicted, count stays 500
  - `recordWatch`: at cap 500, re-watch existing → no eviction, upsert only
  - `recordWatch`: invalid videoId → 200, entry saved with `videoId = null`
  - `removeEntry`: non-existent → no exception
  - `clearAll`: all entries removed for current user only

- [ ] **7.2** `WatchHistoryControllerTest.java`
  - Unauthenticated `GET /ids` → 401
  - `POST /watch-history` → 201; same video again → 200
  - `DELETE /watch-history?videoId=X` (not in history) → 204
  - `DELETE /watch-history/all` → 204; subsequent `GET` → empty list

- [ ] **7.3** Run `mvn verify`, update `api/.coverage-threshold` (+1%)

---

### Phase 8: Frontend API Module (0.5 day)

- [ ] **8.1** Create `webapp/src/api/watchHistory.js`
  ```javascript
  import { api } from './client'

  export const watchHistoryApi = {
    ids:      ()        => api.get('/watch-history/ids'),
    list:     ()        => api.get('/watch-history'),
    record:   (videoId) => api.post('/watch-history', { videoId }),
    remove:   (videoId) => api.delete(`/watch-history?videoId=${videoId}`),
    clearAll: ()        => api.delete('/watch-history/all'),
  }
  ```

---

### Phase 9: Frontend Hooks (0.5 day)

- [ ] **9.1** Create `webapp/src/hooks/useWatchHistory.js`
  ```javascript
  export function useWatchedIds() {
    const { data } = useQuery({
      queryKey: ['watchHistory', 'ids'],
      queryFn: () => watchHistoryApi.ids(),
      staleTime: Infinity,
      enabled: !!localStorage.getItem('jwt'),
    })
    return new Set(data?.videoIds ?? [])
  }

  export function useWatchHistoryList() {
    return useQuery({
      queryKey: ['watchHistory', 'list'],
      queryFn: () => watchHistoryApi.list(),
      enabled: !!localStorage.getItem('jwt'),
    })
  }
  ```

- [ ] **9.2** Create `webapp/src/hooks/useRecordWatch.js`
  ```javascript
  // Fire-and-forget — never await this
  export function useRecordWatch() {
    const queryClient = useQueryClient()
    return (videoId) => {
      watchHistoryApi.record(videoId).catch(() => {})
      queryClient.invalidateQueries(['watchHistory', 'ids'])
    }
  }
  ```

---

### Phase 10: WatchedBadge + VideoSwiper (0.5 day)

- [ ] **10.1** Create `webapp/src/components/video/WatchedBadge.jsx`
  - Small overlay badge: eye icon (`visibility`) or checkmark with "Watched" label
  - Positioned top-left of video card (absolute, inside `.video-card` container)
  - Only renders when `isWatched === true`

- [ ] **10.2** In `VideoSwiper.jsx`:
  - Import `useWatchedIds` and `useRecordWatch`
  - On play event: call `recordWatch(video.id)` (fire-and-forget)
  - Render `<WatchedBadge isWatched={watchedIds.has(video.id)} />` on card

---

### Phase 11: HistoryPage + AppShell (1 day)

- [ ] **11.1** Create `webapp/src/pages/HistoryPage.jsx`
  - Uses `useWatchHistoryList()` for data
  - Grid layout of history cards, newest first
  - Each card: poster, title, `watchedAt` relative timestamp, remove button
  - Handle `videoId: null` → show "Video no longer available" placeholder
  - "Clear all" button → confirmation dialog → `watchHistoryApi.clearAll()` → invalidate queries
  - Empty state: "No watch history yet — play a video to get started"
  - Loading skeleton

- [ ] **11.2** Modify `webapp/src/components/layout/AppShell.jsx`
  - Import `HistoryPage`
  - Add condition before the `activeNav !== 'home'` catch-all:
    ```javascript
    } : activeNav === 'history' ? (
      <HistoryPage />
    ) : activeNav !== 'home' ? (
      <ComingSoon page={activeNav} />
    ```

---

## Files to Create / Modify

| File | Action | Description |
|------|--------|-------------|
| `api/.../db/changelog/sql/202605310002-create-watch-history-table.sql` | Create | DB migration |
| `api/.../db/changelog/db.changelog-master.yaml` | Modify | Register migration |
| `api/.../entity/WatchHistory.java` | Create | JPA entity |
| `api/.../repo/WatchHistoryRepository.java` | Create | JPA repository |
| `api/.../dto/WatchHistoryDto.java` | Create | Response DTO |
| `api/.../dto/WatchHistoryIdsDto.java` | Create | Response DTO |
| `api/.../dto/RecordWatchRequest.java` | Create | Request DTO |
| `api/.../service/impl/WatchHistoryService.java` | Create | Service |
| `api/.../web/WatchHistoryController.java` | Create | REST controller |
| `webapp/src/api/watchHistory.js` | Create | API module |
| `webapp/src/hooks/useWatchHistory.js` | Create | React Query hooks |
| `webapp/src/hooks/useRecordWatch.js` | Create | Fire-and-forget helper |
| `webapp/src/components/video/WatchedBadge.jsx` | Create | Watched indicator |
| `webapp/src/components/video/VideoSwiper.jsx` | Modify | Fire record + WatchedBadge |
| `webapp/src/pages/HistoryPage.jsx` | Create | History list page |
| `webapp/src/components/layout/AppShell.jsx` | Modify | Replace ComingSoon for history |

---

## Testing Strategy

### Unit Tests

| Component | Test Cases |
|-----------|------------|
| `WatchHistoryService.recordWatch` | New entry (201), re-watch (200+upsert), cap eviction, invalid videoId silent |
| `WatchHistoryService.removeEntry` | Remove existing, remove non-existent (no-op) |
| `WatchHistoryService.clearAll` | Removes only current user's entries |

### Integration Tests

| Scenario | Expected |
|----------|---------|
| Unauthenticated POST | 401 |
| POST same videoId twice | First 201, second 200, one DB entry |
| POST at cap 500 (new video) | Oldest evicted, count = 500 |
| POST at cap 500 (re-watch) | No eviction, upsert only |
| DELETE non-existent videoId | 204 |
| DELETE /all → GET | Empty list |

### Manual Testing

- [ ] Play video → watched indicator appears on card immediately
- [ ] Refresh page → indicator still shown (persisted via `/ids`)
- [ ] Open `/history` (side nav) → video appears with timestamp
- [ ] Re-watch → entry moves to top of history list
- [ ] Delete single entry → removed from history, indicator clears on card
- [ ] Clear all → confirm dialog → history empty
- [ ] Deleted video in history → shows "Video no longer available" placeholder, no crash

---

## Dependency Graph

```
Phase 1 (Migration)
  └── Phase 2 (Entity)
        └── Phase 3 (Repository)
              └── Phase 4 (DTOs) ─────────────┐
                    └── Phase 5 (Service) ─────┤
                          └── Phase 6 (Controller)
                                └── Phase 7 (BE Tests)

Phase 8 (API module)  ← after Phase 6 contract stable
  └── Phase 9 (Hooks)
        ├── Phase 10 (WatchedBadge + VideoSwiper)
        └── Phase 11 (HistoryPage + AppShell)
```

Backend phases 1–7 must complete before end-to-end frontend testing.
Frontend phases 8–11 can start once the API contract is finalised (Phase 6).

---

## Rollback Plan

1. Revert `AppShell.jsx` (restore `<ComingSoon page="history" />` catch-all)
2. Revert `VideoSwiper.jsx` (remove fire-and-forget call and WatchedBadge)
3. Drop migration: `DROP TABLE watch_history;`
4. Remove all new files (entity, repo, service, controller, DTOs, frontend modules)
5. No data loss to existing features — fully additive

---

## Risks

| Risk | Mitigation |
|------|------------|
| `source_video_id` not set before save | Set `sourceVideoId = videoId` in service before persist; `@Column(updatable=false)` prevents accidental clear |
| Auto-evict + insert not atomic | Wrap in `@Transactional` — both DELETE oldest and INSERT new run in same transaction |
| Fire-and-forget fails silently | Acceptable per Spec; log warning server-side for monitoring |
| WatchedBadge renders for unauthenticated users | Guard with `AuthContext.user` check — render null if not logged in |
| N+1 on history list (loading User/VideoSource per entry) | Use `@EntityGraph` or `JOIN FETCH` in repo; test with 100+ entries |

---

## Checklist

### Before Starting
- [x] PRD, ADR, Spec approved
- [x] Branch: create `feat/watch-history` from main

### Before PR
- [ ] `mvn verify` passes (tests + coverage gate)
- [ ] `npm run lint` passes
- [ ] `npm run test` passes
- [ ] Manual test checklist complete
- [ ] `videoId: null` entry renders gracefully — no crash
- [ ] Fire-and-forget confirmed: POST does not block video playback

### Before Merge
- [ ] Code review approved
- [ ] Coverage threshold updated in `api/.coverage-threshold`
- [ ] Migration reviewed

---

## Beads

```bash
# Epic
bd create --title="Watch History" --type=feature --priority=2

# Backend
bd create --title="[BE-1] DB migration: watch_history table" --type=task
bd create --title="[BE-2] WatchHistory entity" --type=task
bd create --title="[BE-3] WatchHistoryRepository" --type=task
bd create --title="[BE-4] Watch history DTOs" --type=task
bd create --title="[BE-5] WatchHistoryService (upsert + auto-evict)" --type=task
bd create --title="[BE-6] WatchHistoryController" --type=task
bd create --title="[BE-7] Backend tests + coverage" --type=task

# Frontend
bd create --title="[FE-1] api/watchHistory.js API module" --type=task
bd create --title="[FE-2] useWatchHistory + useRecordWatch hooks" --type=task
bd create --title="[FE-3] WatchedBadge + VideoSwiper fire-and-forget" --type=task
bd create --title="[FE-4] HistoryPage + AppShell wiring" --type=task

# Dependencies
# BE-2→BE-1, BE-3→BE-2, BE-5→BE-3+BE-4, BE-6→BE-5, BE-7→BE-6
# FE-2→FE-1, FE-3→FE-2, FE-4→FE-2
```

---

## Progress Log

| Date | Update |
|------|--------|
| 2026-05-31 | Plan created from Spec + ADR-0013 |
