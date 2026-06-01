# Feature Specification: Watch History

<!-- Model-comparison artifact — generated with Claude Opus 4.8. Sibling: spec-watch-history.md (same feature, different model). Keep separate. -->

## Metadata

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-06-01
**Related PRD:** [PRD-watch-history-opus](../prd/PRD-watch-history-opus.md)
**Related ADR:** [ADR-0014: Watch History Design (Opus)](../adr/0014-watch-history-design-opus.md)

> **Note:** Generated with Claude Opus 4.8 as a model-comparison exercise against [spec-watch-history.md](./spec-watch-history.md). Same feature, independently derived from the Opus-branch PRD/ADR. Honors the Opus design decisions: **BIGINT IDENTITY** primary key (not UUID), **transactional** auto-eviction, JWT-derived ownership, upsert on `(user_id, source_video_id)`.

---

## Overview

When a logged-in user plays any video (local or YouTube-hosted), the system records it in that user's private watch history. The user can view their history newest-first on the `/history` page, see a "watched" badge on video cards they have already seen, delete a single entry, or clear their entire history. All access is scoped to the authenticated user; no user can read or mutate another user's history.

---

## Business Rules

### Rule 1 — One entry per (user, video)
A video appears at most once in a user's history. Recording a play for a video already present updates that entry's `watched_at` to `now()` (upsert on `(user_id, source_video_id)`); it never creates a duplicate.

### Rule 2 — Hard cap of 500 entries, enforced transactionally
A user holds at most **500** history entries. When recording a *new* entry would exceed 500, the oldest entries by `watched_at` are deleted down to 500 **within the same transaction as the upsert**, so the ≤ 500 invariant holds even under concurrent plays. Eviction is silent — no error is returned.

### Rule 3 — Entries survive video deletion
History entries persist if the source `VideoSource` is deleted. `video_id` becomes `NULL` (`ON DELETE SET NULL`); the immutable `source_video_id` is retained, preserving the unique constraint and the badge lookup. The UI must render `video_id: null` entries gracefully.

### Rule 4 — Strictly private, JWT-derived ownership
A user may only read, record, or delete their own history. The owning `user_id` is resolved **server-side from the JWT subject** — no endpoint accepts a client-supplied `userId`. All queries are scoped `WHERE user_id = <authenticated user>`.

### Rule 5 — Fire-and-forget recording
Recording a play is fire-and-forget: the frontend does not await the response, and a failed `POST` never blocks playback or surfaces an error.

### Rule 6 — Re-watch moves to top
Because re-watch updates `watched_at`, a re-watched video rises to the top of the newest-first list. The first-watch timestamp is not retained.

---

## Functional Requirements

### FR-1: Auto-record on play (upsert + transactional eviction)
The system must accept `POST /watch-history { videoId }` and upsert an entry for the authenticated user keyed by `(user_id, source_video_id)`. If the upsert creates a new row and the user then holds > 500 entries, the system must delete the oldest rows (by `watched_at`) down to 500 in the same transaction.

### FR-2: Watched-IDs endpoint
The system must expose `GET /watch-history/ids` returning the list of `source_video_id` values for the authenticated user, for client-side badge lookup. No per-card API call is permitted.

### FR-3: History list endpoint
The system must expose `GET /watch-history` returning the authenticated user's entries sorted by `watched_at` DESC, wrapped in `ResultListInfo<WatchHistoryDto>`.

### FR-4: Watched badge on video cards
Each video card must display a "watched" indicator when the video's ID is in the user's history, derived from the `GET /watch-history/ids` response via a client-side `Set.has()` — zero extra network calls per card.

### FR-5: Delete a single entry
The system must accept `DELETE /watch-history?videoId=X`, removing the authenticated user's entry for that video. Deleting a non-existent entry is idempotent (204).

### FR-6: Clear all history
The system must accept `DELETE /watch-history/all`, removing all entries for the authenticated user. The frontend must show a confirmation dialog before calling it.

### FR-7: History page
The `/history` route must replace the `ComingSoon` placeholder with a real `HistoryPage` component listing all entries newest-first, with an empty state when the user has no history.

---

## API Changes

> Base path is `AppConstant.API.BASE_URL` = `/v1/funny-app` (verified in `AppConstant.java`). All endpoints below are mounted under it. **This corrects the `/api/v1/...` assumption in the sibling ADR-0013, which does not match the codebase.**

### New Endpoints

#### GET `/v1/funny-app/watch-history/ids`

**Description:** All watched video IDs for the current user, for client-side card badges (fetched once at page load).

**Auth:** Required (JWT Bearer)
**Rate limit:** None

**Response — Success (200)**
```json
{
  "status": "SUCCESS",
  "data": { "videoIds": [123, 456, 789] }
}
```
> `ResultObjectInfo<WatchHistoryIdsDto>`. Values are `source_video_id` (BIGINT).

---

#### GET `/v1/funny-app/watch-history`

**Description:** Full history list for the current user, newest first.

**Auth:** Required (JWT Bearer)
**Rate limit:** None

**Response — Success (200)**
```json
{
  "status": "SUCCESS",
  "data": [
    {
      "id": 1001,
      "sourceVideoId": 123,
      "videoId": 123,
      "title": "Funny Cat Video",
      "poster": "https://...",
      "watchedAt": "2026-06-01T10:00:00Z"
    }
  ],
  "total": 42
}
```
> `ResultListInfo<WatchHistoryDto>`. `id` is a **BIGINT** (not UUID). When the source video was deleted, `videoId`, `title`, and `poster` are `null` while `sourceVideoId` and `watchedAt` remain.

---

#### POST `/v1/funny-app/watch-history`

**Description:** Record/update a watch event. Fire-and-forget — the frontend does not await.

**Auth:** Required (JWT Bearer)
**Rate limit:** **30 requests/minute/user** (`@RateLimited(permit = 30)`)

**Request**
```json
{ "videoId": 123 }
```

**Response — Success**

| Condition | HTTP | Body |
|-----------|------|------|
| New entry created | 201 | `ResultObjectInfo<WatchHistoryDto>` |
| Existing entry updated (re-watch) | 200 | `ResultObjectInfo<WatchHistoryDto>` |
| `videoId` not found in `video_sources` | 200 | `ResultObjectInfo<WatchHistoryDto>` recorded with `videoId: null`, `sourceVideoId: <input>` |

**Response — Error**

| HTTP | Code | Condition |
|------|------|-----------|
| 400 | `INVALID_REQUEST` | `videoId` missing or not a positive Long |
| 401 | `UNAUTHORIZED` | Missing or invalid JWT |
| 429 | `RATE_LIMITED` | > 30 requests/minute for the user |
| 500 | `INTERNAL_ERROR` | Unexpected DB/server failure |

> Per the user decision, an unknown `videoId` is **not** a 404 — it is silently recorded with `video_id = null` and `source_video_id = videoId`, preserving the fire-and-forget contract (the frontend never inspects the body). Such entries render as "Video unavailable".

---

#### DELETE `/v1/funny-app/watch-history?videoId=X`

**Description:** Remove a single entry for the current user.

**Auth:** Required (JWT Bearer) · **Rate limit:** None · **Query param:** `videoId` (Long, required)

| Condition | HTTP |
|-----------|------|
| Entry deleted | 204 |
| Entry not found (idempotent) | 204 |
| `videoId` param missing/invalid | 400 |
| Unauthenticated | 401 |

---

#### DELETE `/v1/funny-app/watch-history/all`

**Description:** Clear all history for the current user.

**Auth:** Required (JWT Bearer) · **Rate limit:** None

| Condition | HTTP |
|-----------|------|
| All entries deleted (or none existed) | 204 |
| Unauthenticated | 401 |

---

## Database Changes

### New Table

```sql
-- Migration: api/src/main/resources/db/changelog/sql/202606010001-create-watch-history-table.sql

CREATE TABLE watch_history (
    id              BIGINT      GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_id        BIGINT               REFERENCES video_sources(id) ON DELETE SET NULL,
    source_video_id BIGINT      NOT NULL,
    watched_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_watch_history_user_video UNIQUE (user_id, source_video_id)
);

CREATE INDEX idx_watch_history_user_watched ON watch_history(user_id, watched_at DESC);
```

**Column notes:**
- `id` — **BIGINT IDENTITY**, matching `users.id` and `video_sources.id`. Never exposed as an addressable key in any endpoint (the API addresses entries by `videoId`).
- `video_id` — nullable FK; set `NULL` if the video is deleted.
- `source_video_id` — immutable; uniqueness anchor and badge key; survives `video_id` nulling.
- `watched_at` — updated on re-watch; drives both sort order and eviction selection.
- The single `(user_id, watched_at DESC)` index serves the list query and oldest-row eviction.

### No Changes to Existing Tables

---

## Security Requirements

### Authentication
All five endpoints require a valid JWT Bearer token via `JWTAuthenticationFilter`. `/v1/funny-app/watch-history/**` must NOT be added to the auth whitelist.

### Authorization
The owning user is resolved in the service layer from `SecurityContextHolder.getContext().getAuthentication().getDetails()` → `UserDetailDto`. Every query and mutation is scoped `WHERE user_id = currentUser.getId()`. **No endpoint accepts a `userId` from the request body or params** — this closes IDOR (CWE-639) by construction.

### Data Validation
| Field | Rule | On violation |
|-------|------|--------------|
| `videoId` (POST body) | Not null, positive Long | 400 `INVALID_REQUEST` |
| `videoId` (DELETE param) | Not null, positive Long | 400 `INVALID_REQUEST` |
| `videoId` value not in `video_sources` (POST) | — | 200, recorded with `video_id = null` |

### Sensitive Data
No sensitive fields. `@AuditLog` is **not** applied to `WatchHistoryController` — the per-play POST volume would flood the audit log. Rate limiting (30/min) provides abuse protection instead.

---

## Caching Impact

| Cache | Impact | Action |
|-------|--------|--------|
| `VideoCacheImpl` | None | Watch history neither reads nor writes video chunk cache |
| `ChunkIndexCacheImpl` | None | Unrelated |
| `StatsCacheImpl` | None | `VideoAccessStats` is aggregate per-video; watch history is per-user — independent systems |

> No cache impact. No new cache is introduced.

---

## Frontend Changes

### Modified Routes

| Path | Component | Change |
|------|-----------|--------|
| `/history` (side nav) | `HistoryPage.jsx` | Replace `<ComingSoon page="history" />` in `AppShell.jsx` with `<HistoryPage />` when `activeNav === 'history'` |

### New Components

| Component | File | Purpose |
|-----------|------|---------|
| `HistoryPage` | `webapp/src/pages/HistoryPage.jsx` | Lists entries newest-first; empty state when none; delete + clear-all controls |
| `WatchedBadge` | `webapp/src/components/video/WatchedBadge.jsx` | Overlay "watched" indicator on a video card |

### New API Module

**File:** `webapp/src/api/watchHistory.js`
```javascript
export const watchHistoryApi = {
  ids:      ()        => api.get('/watch-history/ids'),
  list:     ()        => api.get('/watch-history'),
  record:   (videoId) => api.post('/watch-history', { videoId }), // fire-and-forget
  remove:   (videoId) => api.delete(`/watch-history?videoId=${videoId}`),
  clearAll: ()        => api.delete('/watch-history/all'),
}
```
> `record()` must be called without `await`; errors swallowed.

### React Query Keys & State
```javascript
['watchHistory', 'ids']   // GET /watch-history/ids → Set<Long> for badges (page-load)
['watchHistory', 'list']  // GET /watch-history → history page

// After any mutation (record / remove / clearAll):
queryClient.invalidateQueries(['watchHistory', 'ids'])
queryClient.invalidateQueries(['watchHistory', 'list'])
```

**Watched badge per card:** `watchedIds.has(video.id)` — O(1), no extra request.

**Fire-and-forget on play:**
```javascript
watchHistoryApi.record(video.id).catch(() => {})   // do NOT await
queryClient.invalidateQueries(['watchHistory', 'ids'])
```

---

## Event / Job Changes

### Domain Events
| Event | Change |
|-------|--------|
| — | No new domain events |

### Scheduled Jobs
No `AppScheduler` changes. The 500-entry cap is enforced inline at write time (transactional eviction), not by a background job.

---

## Non-Functional Requirements

### Performance

| Operation | Target | Notes |
|-----------|--------|-------|
| `GET /watch-history` (page load) | < 500 ms p99 (≤ 500 rows) | Served by `(user_id, watched_at DESC)` index |
| `GET /watch-history/ids` | < 100 ms p99 | Index-only scan over the user's rows |
| `POST /watch-history` (recording) | adds 0 ms to playback | Fire-and-forget; not awaited by the player |
| Watched badge per card | 0 extra API calls | Client-side `Set.has()` |

### Availability
No degradation to playback: a `POST` timeout or 5xx is swallowed by the frontend. The feature is additive — disabling the `/history` route restores prior behavior.

### Scalability
Per-user data is bounded at 500 rows by Rule 2. The cap invariant holds under concurrent writes because eviction and upsert share one transaction (see EC-3, EC-8).

---

## Edge Cases

### EC-1: Re-watch same video (same session)
**Condition:** User plays video A, then A again.
**Expected:** One entry; `watched_at` updated to latest; entry moves to top of list.

### EC-2: Unknown videoId recorded
**Condition:** `POST /watch-history { videoId: 999999 }` where 999999 is not in `video_sources`.
**Expected:** 200; entry stored with `video_id = null`, `source_video_id = 999999`; renders as "Video unavailable" in the list.

### EC-3: New play at exactly 500 entries
**Condition:** User holds 500 entries; plays a not-yet-watched video.
**Expected:** Upsert inserts the new row; within the same transaction the oldest row by `watched_at` is deleted; total returns to 500. No error.

### EC-4: Re-watch at exactly 500 entries
**Condition:** User holds 500 entries; re-watches a video already present.
**Expected:** Upsert updates `watched_at` only; no eviction; total stays 500.

### EC-5: Delete non-existent entry
**Condition:** `DELETE /watch-history?videoId=999` with no matching entry for the user.
**Expected:** 204, idempotent.

### EC-6: Source video deleted while in history
**Condition:** A `VideoSource` referenced by history is deleted.
**Expected:** `video_id → NULL`; `source_video_id` unchanged; `GET /watch-history` returns the entry with `videoId/title/poster = null`; frontend shows "Video unavailable" without crashing.

### EC-7: Clear all — confirmation
**Condition:** User clicks "Clear all".
**Expected:** Frontend shows a confirm dialog; only on confirm does it call `DELETE /watch-history/all`; on cancel, no API call.

### EC-8: Concurrent plays of distinct new videos at the cap
**Condition:** User at 500 entries plays two different new videos near-simultaneously (two tabs).
**Expected:** Both upserts succeed; transactional eviction runs per write; final total is ≤ 500 (self-healing — converges even if writes interleave). No constraint violation, no > 500 state observable.

### EC-9: Concurrent plays of the same new video
**Condition:** Two tabs `POST` the same `videoId` at once.
**Expected:** The unique `(user_id, source_video_id)` constraint + upsert yields exactly one row; `watched_at` is whichever write lands last; no error.

---

## Acceptance Criteria

- [ ] Playing any video (local or YouTube) creates/updates a history entry with no manual action
- [ ] `GET /watch-history` returns entries sorted by `watched_at` DESC, `id` as BIGINT
- [ ] `GET /watch-history/ids` returns the correct set of `source_video_id` values
- [ ] Video cards show the watched badge for videos in history — zero extra API calls per card
- [ ] Re-watching a video moves it to the top of the list
- [ ] New play at 500 entries evicts the oldest; total stays ≤ 500
- [ ] Re-watch at 500 entries does NOT evict (upsert, not insert)
- [ ] Cap holds at ≤ 500 under two concurrent new-video plays (EC-8)
- [ ] Two concurrent POSTs for the same video yield exactly one entry (EC-9)
- [ ] Unknown videoId is recorded with `video_id = null` and renders as "Video unavailable"
- [ ] Delete of a non-existent entry returns 204 (idempotent)
- [ ] Clear-all requires a frontend confirmation before the API call
- [ ] No endpoint accepts a client-supplied `userId`; cross-user access is impossible
- [ ] `POST /watch-history` exceeding 30/min returns 429 and never blocks playback
- [ ] Unauthenticated requests to any endpoint return 401
- [ ] Existing flows unaffected (regression)
- [ ] `mvn verify` passes with coverage gate met
- [ ] `npm run test` passes

---

## Open Questions

- [ ] Resolved — PK type: **BIGINT IDENTITY** (ADR-0014 D2), diverging from the sibling spec's UUID
- [ ] Resolved — cap strategy: **transactional auto-evict oldest** (ADR-0014 D3)
- [ ] Resolved — rate limit: **30 req/min/user** on POST (user decision)
- [ ] Resolved — unknown videoId: **200, silent record with `video_id = null`** (user decision)
- [ ] Deferred to v2 — minimum watch duration before recording (PRD open question)

---

## Version History

| Version | Date | Author | Change |
|---------|------|--------|--------|
| 1.0 | 2026-06-01 | nguyenhuuca | Initial draft (generated with Claude Opus 4.8 for model comparison) |
