# ADR-0013: Watch History — Recording, Cap, and Re-watch Design

## Metadata

**Status:** Proposed · **Date:** 2026-05-31 · **Deciders:** nguyenhuuca · **Tags:** watch-history, api-design, schema, frontend-state  
**Related PRD:** [PRD-watch-history](../prd/PRD-watch-history.md) · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ✅ Follows Golden Path — PostgreSQL + Spring Boot + React, no new infrastructure

---

## Context

The Watch History feature requires three architectural decisions that are not answered by the PRD and are not covered by ADR-0012 (Bookmark):

1. **Recording trigger** — How does a watch event reach the backend? The app serves both locally-stored videos (via `VideoStreamController`) and YouTube-hosted videos (streamed directly from YouTube, never touching the app's stream endpoints). Any server-side recording approach would miss YouTube videos.

2. **Cap enforcement** — History grows automatically without user intent, unlike bookmarks which are deliberate saves. The cap strategy for bookmarks (hard reject at 500) does not translate to history.

3. **Re-watch behavior** — When a user watches a video they have already seen, should the entry move to the top of the list (update timestamp) or stay at its original position?

Patterns shared with ADR-0012 and applied without re-analysis:
- Watched state delivery: `GET /watch-history/ids` at page load → client-side `Set.has()` per card
- Schema: `source_video_id BIGINT NOT NULL` for uniqueness stability under `ON DELETE SET NULL`
- FK strategy: actual FK constraints (`user_id CASCADE`, `video_id SET NULL`)
- ID type: UUID

---

## Decision Drivers

- Recording must not impact video playback latency (NFR-1: fire-and-forget)
- Recording must work for both local and YouTube-hosted videos
- History grows automatically — cap UX must be invisible to the user
- History should reflect "recently watched" order, not "discovery" order
- Watched indicator must work across all video cards without extra API calls per card

---

## Decision 1: Recording Trigger

### Option A: Frontend POST on play start (fire-and-forget)

Frontend calls `POST /watch-history { videoId }` immediately when play begins. No `await` — response is ignored.

| Pros | Cons |
|------|------|
| Works for YouTube and local videos equally | Can be blocked by aggressive ad-blockers (low risk for auth'd internal feature) |
| Zero playback impact (async, no await) | Records even if user skips immediately (min-watch-duration deferred to v2) |
| No change needed to VideoStreamController | |

### Option B: Backend records on first chunk request (VideoStreamController)

`VideoStreamController` creates a history entry when it serves the first video chunk.

| Pros | Cons |
|------|------|
| Server-side — cannot be blocked | **Only covers local videos** — YouTube videos stream from YouTube directly, bypassing this server entirely |
| Reliable — chunk requested = user played | Misses ~50%+ of content |

### Option C: Frontend POST after N seconds of playback

Timer fires after user has watched ≥ N seconds (e.g., 5s).

| Pros | Cons |
|------|------|
| More accurate — user actually watched | Adds JS timer complexity |
| Filters accidental plays | Still fire-and-forget; same ad-blocker risk as Option A |

---

## Decision 2: Cap Enforcement Strategy

### Option A: Hard reject (409) when cap reached

Return HTTP 409 when `count >= 500` and the entry is new.

| Pros | Cons |
|------|------|
| Simple logic | **Invisible failure** — history silently stops recording new entries |
| Correct for bookmarks (intentional saves) | Wrong for history (automatic, user has no control over when cap is reached) |
| | User sees incomplete history with no explanation |

### Option B: Auto-evict oldest entry (sliding window)

Before inserting a new entry, if `count >= 500`, delete the entry with the oldest `watched_at`.

| Pros | Cons |
|------|------|
| History always shows the 500 most recent watches | Oldest entries are silently dropped (expected and acceptable) |
| Invisible to user — no error, no truncated list | Slightly more complex: delete-oldest before insert |
| Matches YouTube, Netflix, browser history behavior | |

### Option C: No cap

Unbounded growth.

| Pros | Cons |
|------|------|
| Simplest | Unbounded DB growth at scale |

---

## Decision 3: Re-watch Behavior

### Option A: Upsert — update `watched_at`, entry moves to top

`INSERT ... ON CONFLICT (user_id, source_video_id) DO UPDATE SET watched_at = now()`

| Pros | Cons |
|------|------|
| History reflects "most recently watched" | First-watch date is lost (acceptable — history ≠ diary) |
| Single SQL statement | |
| Matches all major platforms (YouTube, Netflix) | |

### Option B: Keep original — no-op if entry exists

Check existence; if found, do nothing.

| Pros | Cons |
|------|------|
| Preserves "discovery" order | Entry buried in list after re-watch — confusing |
| | Two DB operations (check + conditional insert) |

---

## Decision Outcome

### D1 — Recording Trigger: **Option A — Frontend fire-and-forget POST**

**Rationale:** Option B is eliminated because it misses YouTube-hosted videos entirely. Option C adds complexity for a min-watch-duration that is explicitly deferred to v2 (PRD open question). Option A is the only approach that works uniformly for all video sources. Fire-and-forget ensures zero playback impact.

### D2 — Cap Strategy: **Option B — Auto-evict oldest (sliding window)**

**Rationale:** History is automatic — users never consciously "fill" it. A hard reject (Option A) would silently stop recording without the user knowing, creating invisible data loss. Auto-evict matches universal platform behavior and keeps the cap invisible. Contrasts with bookmarks (ADR-0012) where hard reject is appropriate because bookmarks are intentional.

### D3 — Re-watch Behavior: **Option A — Upsert, update `watched_at`**

**Rationale:** History answers "what did I watch recently?" — not "what did I first discover?". Upsert is a single SQL statement vs. check-then-no-op. Aligns with all major platforms.

### Quantified Impact

| Metric | Value | Notes |
|--------|-------|-------|
| Extra API calls per video card render | 0 | Client-side `Set.has()` after page-load `/ids` fetch |
| Playback latency added | 0 ms | Fire-and-forget POST, no await |
| DB operations on re-watch | 1 | Single `UPSERT` |
| DB operations when cap full (new entry) | 2 | DELETE oldest + INSERT new |

---

## Consequences

**Positive:**
- Recording works identically for YouTube and local videos
- No playback latency impact
- History cap is invisible to users — graceful degradation
- Re-watch moves entry to top — history stays sorted by recency naturally

**Negative:**
- Fire-and-forget means if the POST fails (network error), the watch is not recorded — no retry
- First-watch timestamp is lost on re-watch (acceptable trade-off)
- Auto-evict requires an extra DELETE query when cap is full; not atomic with INSERT without a transaction

**Risks:**
- Concurrent play events (two tabs): both fire POST simultaneously → UPSERT handles gracefully (last write wins for watched_at)
- If fire-and-forget POST rate becomes high (popular video + many users), write pressure on `watch_history` table → mitigated by keeping writes lightweight (single UPSERT)

---

## Schema Reference

```sql
-- Migration: 202605310002-create-watch-history-table.sql

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

## API Contract Reference

```
GET    /api/v1/watch-history/ids          → { videoIds: [Long] }         (page-load)
GET    /api/v1/watch-history              → ResultListInfo<WatchHistoryDto> (history page)
POST   /api/v1/watch-history              body: { videoId: Long }          (fire-and-forget upsert)
DELETE /api/v1/watch-history?videoId=X   → 204                            (remove single entry)
DELETE /api/v1/watch-history/all         → 204                            (clear all)
```

---

## Validation

- [ ] Fire-and-forget POST does not block video playback (test with network throttling)
- [ ] Auto-evict: at 500 entries, 501st watch removes oldest and adds new — verified by test
- [ ] Upsert: re-watching moves entry to top — `GET /watch-history` returns correct order
- [ ] YouTube video history recorded same as local video
- [ ] `source_video_id` uniqueness holds after `video_id` is nulled by `ON DELETE SET NULL`
- [ ] Tech Strategy alignment: ✅ no new infrastructure

---

## Links

- [PRD: Watch History](../prd/PRD-watch-history.md)
- [ADR-0012: Bookmark Feature Design](./0012-bookmark-feature-design.md) — shared patterns (IDs endpoint, source_video_id, FK strategy)

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-05-31 | nguyenhuuca | Initial draft |
