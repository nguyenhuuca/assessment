# Feature Specification: Watch History

## Metadata

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-05-31
**Related PRD:** [PRD-watch-history](../prd/PRD-watch-history.md)
**Related ADR:** [ADR-0013: Watch History Design](../adr/0013-watch-history-design.md)

---

## Overview

When a logged-in user plays any video, the system automatically records it in their personal watch history. Users can view their history on a dedicated page (sorted newest first), see a "watched" indicator on video cards, and delete individual entries or clear all history. History is private and scoped to the authenticated user.

---

## Business Rules

### Rule 1
Each user may have at most 500 watch history entries. When a new entry would exceed this cap, the oldest entry (by `watched_at`) is automatically deleted before the new one is inserted. No error is returned to the caller.

### Rule 2
A video can appear at most once per user in watch history. Re-watching the same video updates the existing entry's `watched_at` timestamp (upsert) — it does not create a duplicate.

### Rule 3
Watch history entries persist even if the source video is deleted. The `video_id` foreign key becomes `NULL` on video deletion; the entry remains with `source_video_id` intact. The UI must handle `video_id: null` gracefully.

### Rule 4
Watch history is strictly private. A user can only read, record, or delete their own history. No cross-user access is permitted.

### Rule 5
Recording a watch event is fire-and-forget. The frontend does not await the response. A failed POST does not affect video playback or surface an error to the user.

---

## Functional Requirements

### FR-1: Auto-record on play
The system must accept a `POST /watch-history` request when a user plays a video. If the video is already in the user's history, update `watched_at` to now (upsert). If it is a new entry and the user has 500 entries, delete the oldest before inserting.

### FR-2: History IDs endpoint
The system must expose `GET /watch-history/ids` returning the list of `source_video_id` values for the authenticated user. Used by the frontend for client-side watched-indicator lookup.

### FR-3: History list endpoint
The system must expose `GET /watch-history` returning full history entries for the authenticated user, sorted by `watched_at` DESC.

### FR-4: Watched indicator on video cards
Each video card must display a visual indicator if the video's ID is present in the user's history. The frontend must derive this from the `GET /watch-history/ids` response using a client-side `Set.has()` lookup — no per-card API call.

### FR-5: Delete single entry
The system must accept `DELETE /watch-history?videoId=X` to remove a single entry. If the entry does not exist, return 204 (idempotent).

### FR-6: Clear all history
The system must accept `DELETE /watch-history/all` to remove all entries for the authenticated user. The frontend must show a confirmation dialog before calling this endpoint.

### FR-7: History page
The `/history` route must replace the current `ComingSoon` placeholder with a real `HistoryPage` component displaying all history entries.

---

## API Changes

### New Endpoints

#### GET `/v1/funny-app/watch-history/ids`

**Description:** Returns all watched video IDs for the current user. Used at page load for client-side card indicators.

**Auth:** Required (JWT Bearer)
**Rate limit:** None

**Response — Success (200)**
```json
{
  "status": "SUCCESS",
  "data": {
    "videoIds": [123, 456, 789]
  }
}
```

> Uses `ResultObjectInfo<WatchHistoryIdsDto>`.

---

#### GET `/v1/funny-app/watch-history`

**Description:** Returns full history list, newest first.

**Auth:** Required (JWT Bearer)
**Rate limit:** None

**Response — Success (200)**
```json
{
  "status": "SUCCESS",
  "data": [
    {
      "id": "uuid",
      "sourceVideoId": 123,
      "videoId": 123,
      "title": "Funny Cat Video",
      "poster": "https://...",
      "watchedAt": "2026-05-31T10:00:00Z"
    }
  ],
  "total": 42
}
```

> Uses `ResultListInfo<WatchHistoryDto>`. `videoId` may be `null` if source video was deleted — `title` and `poster` will also be `null` in that case.

---

#### POST `/v1/funny-app/watch-history`

**Description:** Record or update a watch event. Fire-and-forget — frontend does not await.

**Auth:** Required (JWT Bearer)
**Rate limit:** 30 requests/minute/user (`@RateLimited(permit = 30)`)

**Request**
```json
{
  "videoId": 123
}
```

**Response — Success**

| Condition | HTTP | Body |
|-----------|------|------|
| New entry created | 201 | `ResultObjectInfo<WatchHistoryDto>` |
| Existing entry updated (re-watch) | 200 | `ResultObjectInfo<WatchHistoryDto>` |
| `videoId` not found in `video_sources` | 200 | `ResultObjectInfo<WatchHistoryDto>` with `videoId: null` — silent, recorded with null video_id |

**Response — Error**

| HTTP | Code | Condition |
|------|------|-----------|
| 401 | `UNAUTHORIZED` | Missing or invalid JWT |
| 500 | `INTERNAL_ERROR` | Unexpected DB or server failure |

> Note: Invalid `videoId` is NOT a 404. It is silently recorded with `video_id = null` and `source_video_id = videoId`. This supports the fire-and-forget contract — the frontend never inspects the response.

---

#### DELETE `/v1/funny-app/watch-history`

**Description:** Remove a single history entry by video ID.

**Auth:** Required (JWT Bearer)
**Rate limit:** None

**Query param:** `?videoId=X` (Long, required)

**Response**

| Condition | HTTP |
|-----------|------|
| Entry deleted | 204 |
| Entry not found (idempotent) | 204 |
| `videoId` param missing | 400 |
| Unauthenticated | 401 |

---

#### DELETE `/v1/funny-app/watch-history/all`

**Description:** Clear all history entries for the authenticated user.

**Auth:** Required (JWT Bearer)
**Rate limit:** None

**Response**

| Condition | HTTP |
|-----------|------|
| All entries deleted (or none existed) | 204 |
| Unauthenticated | 401 |

---

## Database Changes

### New Table

```sql
-- Migration: api/src/main/resources/db/changelog/sql/202605310002-create-watch-history-table.sql

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

**Column notes:**
- `video_id` — nullable FK; becomes `NULL` if video is deleted (`ON DELETE SET NULL`)
- `source_video_id` — immutable copy of the original video ID; used for uniqueness and delete-by-videoId operations
- `watched_at` — updated on re-watch (upsert); drives sort order

### No Changes to Existing Tables

---

## Security Requirements

### Authentication
All five endpoints require a valid JWT Bearer token. The `JWTAuthenticationFilter` enforces this for any path not in the whitelist. `/v1/funny-app/watch-history/**` must NOT be added to the whitelist.

### Authorization
User identity is extracted in the service layer via `SecurityContextHolder.getContext().getAuthentication().getDetails()` → `UserDetailDto`. All queries are scoped to `WHERE user_id = currentUser.getId()`. No cross-user access is possible.

### Data Validation
| Field | Rule | Behaviour on violation |
|-------|------|----------------------|
| `videoId` (POST body) | Not null, Long | 400 if missing |
| `videoId` (DELETE param) | Not null, Long | 400 if missing |
| `videoId` value | Must be positive | 200 silent ignore if not found in DB |

### Sensitive Data
No sensitive fields. `@AuditLog` is NOT applied to `WatchHistoryController` — POST volume would generate excessive audit log noise.

---

## Caching Impact

| Cache | Impact |
|-------|--------|
| `VideoCacheImpl` | No impact — watch history does not read from or write to video metadata cache |
| `StatsCacheImpl` | No impact — watch history is separate from aggregate view stats (`VideoAccessStats`) |

> `VideoAccessStats` tracks aggregate hit counts per video (not per-user). Watch history is per-user. These are independent systems.

---

## Frontend Changes

### Modified Routes

| Path | Component | Change |
|------|-----------|--------|
| `/history` (side nav) | `HistoryPage.jsx` | Replace `ComingSoon` with real component |

**How to replace:** In `AppShell.jsx`, the `history` nav key currently renders `<ComingSoon page="history" />`. Change to render `<HistoryPage />` when `activeNav === 'history'`.

### New Components

| Component | File | Purpose |
|-----------|------|---------|
| `HistoryPage` | `webapp/src/pages/HistoryPage.jsx` | Lists all watch history entries, newest first |
| `WatchedBadge` | `webapp/src/components/video/WatchedBadge.jsx` | Overlay indicator on watched video cards |

### New API Module

**File:** `webapp/src/api/watchHistory.js`

```javascript
export const watchHistoryApi = {
  ids:      ()         => api.get('/watch-history/ids'),
  list:     ()         => api.get('/watch-history'),
  record:   (videoId)  => api.post('/watch-history', { videoId }),   // fire-and-forget
  remove:   (videoId)  => api.delete(`/watch-history?videoId=${videoId}`),
  clearAll: ()         => api.delete('/watch-history/all'),
}
```

> `record()` must be called without `await`. Errors must be silently swallowed.

### React Query Keys & State

```javascript
// Page-load fetch — cached for entire session
['watchHistory', 'ids']    → GET /watch-history/ids  → Set<Long>

// History page fetch
['watchHistory', 'list']   → GET /watch-history

// Invalidate after any mutation
queryClient.invalidateQueries(['watchHistory', 'ids'])
queryClient.invalidateQueries(['watchHistory', 'list'])
```

**Watched indicator per card:** `watchedIds.has(video.id)` — O(1), no extra API call.

**Fire-and-forget pattern:**
```javascript
// On video play event — do NOT await
watchHistoryApi.record(video.id).catch(() => {})
// Then invalidate to keep indicator in sync
queryClient.invalidateQueries(['watchHistory', 'ids'])
```

---

## Non-Functional Requirements

### Performance

| Operation | Target | Notes |
|-----------|--------|-------|
| `GET /watch-history` (history page load) | < 500 ms p99 | Index on `(user_id, watched_at DESC)` covers this query |
| All other endpoints | No specific target | Upsert + delete are single-row operations; expected < 50 ms |

### Availability
No degradation to video playback. POST is fire-and-forget — a timeout or 5xx does not surface to the user.

---

## Edge Cases

### EC-1: Re-watch same video (same tab)
**Condition:** User plays video A, then plays video A again in the same session.
**Expected:** Single entry in history. `watched_at` updated to latest play time. Entry appears at top of list.

### EC-2: Two browser tabs play same video simultaneously
**Condition:** User has two tabs open and plays the same video at the same moment.
**Expected:** `UPSERT ON CONFLICT (user_id, source_video_id) DO UPDATE SET watched_at = now()` handles concurrency. Exactly one entry exists. `watched_at` reflects whichever write lands last. No error.

### EC-3: History at exactly 500 entries, new video played
**Condition:** User has 500 entries. Plays a new (not-yet-watched) video.
**Expected:** Oldest entry (min `watched_at`) is deleted. New entry is inserted. Total remains 500. No error returned to frontend.

### EC-4: History at exactly 500 entries, re-watching an existing video
**Condition:** User has 500 entries. Re-watches a video already in history.
**Expected:** Upsert updates `watched_at` only. No deletion. Total remains 500.

### EC-5: Delete entry that does not exist
**Condition:** `DELETE /watch-history?videoId=999` where no entry exists for this user + videoId.
**Expected:** 204 — idempotent, no error.

### EC-6: Source video deleted while in history
**Condition:** Admin deletes a `VideoSource` record. One or more users have it in watch history.
**Expected:** `video_id` → `NULL` via `ON DELETE SET NULL`. `source_video_id` unchanged. `GET /watch-history` returns the entry with `videoId: null`, `title: null`, `poster: null`. Frontend renders "Video no longer available" placeholder — does not crash.

### EC-7: Clear all history — confirmation
**Condition:** User clicks "Clear all" button.
**Expected:** Frontend shows confirmation dialog ("Are you sure? This cannot be undone."). Only on confirm does it call `DELETE /watch-history/all`. On cancel, no API call is made.

---

## Acceptance Criteria

- [ ] Playing any video automatically creates or updates a history entry (no manual action required)
- [ ] `GET /watch-history` returns entries sorted by `watched_at` DESC
- [ ] `GET /watch-history/ids` returns correct set of `source_video_id` values
- [ ] Video cards show watched indicator for videos in history — zero extra API calls per card
- [ ] Re-watching a video moves it to the top of the history list
- [ ] 500th entry: success; 501st (new video): oldest entry evicted, total stays 500
- [ ] Re-watching at cap 500 does NOT trigger eviction (upsert, not insert)
- [ ] Delete non-existent entry returns 204 (idempotent)
- [ ] Clear all requires frontend confirmation dialog before API call
- [ ] Deleted video appears as "Video no longer available" in history — frontend does not crash
- [ ] Two concurrent POSTs for same video result in exactly one history entry
- [ ] POST /watch-history does not block or delay video playback
- [ ] Unauthenticated requests to any endpoint return 401
- [ ] `mvn verify` passes with coverage gate met
- [ ] `npm run test` passes

---

## Open Questions

- [ ] Resolved: min-watch-duration threshold — deferred to v2 (fire-and-forget on play start, no timer)
- [ ] Resolved: cap strategy — auto-evict oldest (not hard reject)
- [ ] Resolved: re-watch — upsert (update watched_at, move to top)

---

## Version History

| Version | Date | Author | Change |
|---------|------|--------|--------|
| 1.0 | 2026-05-31 | nguyenhuuca | Initial draft |
