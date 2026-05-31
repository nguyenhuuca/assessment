# ADR-0012: Bookmark Feature — Storage and API Design

<!--
Architecture Decision Record
Filename: docs/adr/0012-bookmark-feature-design.md
Owner: Architect (/architect)
Handoff to: Builder (/builder), Security Auditor (/security-auditor)
Related Skills: designing-systems, designing-apis, domain-driven-design
-->

## Metadata

**Status:** Proposed · **Date:** 2026-05-31 · **Deciders:** nguyenhuuca · **Tags:** bookmark, api-design, schema, frontend-state  
**Related PRD:** [PRD-bookmark-feature](../prd/PRD-bookmark-feature.md) · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ✅ Follows Golden Path — PostgreSQL + Spring Boot + React + Guava LRU, no new infrastructure

---

## Context

The Funny Movies app has no mechanism for authenticated users to save videos for later. The bookmark feature (PRD-bookmark-feature) requires:

1. Per-user persistent bookmark storage with optional named collections
2. A visual bookmark indicator on every video card in the browse UI
3. A `/bookmarks` page listing all saved videos

Two architectural questions have non-obvious answers and warrant an explicit decision record:

**Q1 — Bookmark state delivery**: How does each video card know whether the current user has bookmarked it? The naive approach (enriching video list responses) conflicts with the existing Guava LRU cache, which stores video metadata without user context. Introducing user-specific data into cached responses would either break caching or require user-keyed cache variants.

**Q2 — FK strategy**: The existing `VideoComment` entity stores `userId` and `videoId` as `String` columns (no FK constraints) to support guest commenting. Should bookmarks follow the same pattern for consistency, or use actual FK constraints given that bookmarks have different integrity requirements?

Additional decisions made (rationale in Decision Outcome):
- ID type: UUID (matches VideoComment pattern)
- Collection storage: three-table relational
- Toggle API: video-centric (POST add + DELETE by videoId, not bookmark ID)
- ON DELETE video_id: SET NULL

---

## Decision Drivers

- Must not invalidate or complicate the existing Guava LRU video-metadata cache
- Bookmark state must be visually correct on every video card without per-card API calls
- Authenticated-only feature — no guest bookmark support
- 500-bookmark cap per user (enforced at API layer) — payload size is bounded and small
- Schema must enforce referential integrity appropriate to a user-owned resource
- API must feel natural to the frontend — video-centric, not internal-ID-centric

---

## Decision 1: Bookmark State Delivery to Frontend

### Option 1A: Enrich video list response with `isBookmarked`

Each video list endpoint (`GET /videos`, `GET /videos/popular`, etc.) returns an additional `isBookmarked: boolean` field per video, resolved via a JOIN to the bookmarks table using the authenticated user's ID.

| Pros | Cons |
|------|------|
| Single network request per page | Breaks Guava LRU: video lists become user-specific, cannot be shared across users |
| State is always fresh per response | Every video list endpoint needs modification |
| No extra client-side logic | Requires cache bypass or user-keyed cache (contradicts ADR-0003 and ADR-0011) |

### Option 1B: Separate bulk status endpoint `GET /bookmarks/status?videoIds=…`

Frontend fetches video list (cached), then issues a second request with the visible video IDs to retrieve bookmark status as a `{ videoId → boolean }` map.

| Pros | Cons |
|------|------|
| Video list cache untouched | Two sequential network round-trips per page (video list → status check) |
| Only enriches visible cards | Client must batch video IDs and re-fetch on scroll |
| Scoped to currently visible items | More complex client code |

### Option 1C: Fetch all bookmarked video IDs at page load (chosen)

A lightweight `GET /bookmarks/ids` endpoint returns the complete set of bookmarked video IDs for the authenticated user as `{ videoIds: [Long] }`. React Query caches this response. Each video card performs a client-side `Set.has(videoId)` lookup — no per-card API call.

| Pros | Cons |
|------|------|
| Video list cache completely untouched | Requires page-load fetch (mitigated: runs in parallel with video list) |
| O(1) per-card lookup with `Set` | Initial response is slightly larger than per-page status (bounded: max ~4 KB at 500 bookmarks) |
| Single invalidation point: any bookmark mutation triggers `invalidateQueries(['bookmarkIds'])` | State is eventually consistent if another tab modifies bookmarks (acceptable; no real-time sync required) |
| No modification to existing video endpoints | |
| Works correctly even as user scrolls / loads more video cards | |

---

## Decision 2: FK Strategy — Actual Constraints vs String Columns

### Option 2A: String columns (matching VideoComment)

Store `userId` and `videoId` as `VARCHAR`/`String` in the bookmark entity, no DB-level FK constraints. Application code manages consistency.

| Pros | Cons |
|------|------|
| Consistent with VideoComment entity pattern | No DB-level integrity — orphan bookmarks possible if user/video deleted |
| Simpler JPA mapping | Application must defensively handle missing referenced entities |
| No accidental cascade risk | Misses the reason VideoComment uses strings (guest tokens, optional userId) |

### Option 2B: Actual FK constraints (chosen)

Store `user_id BIGINT REFERENCES users(id) ON DELETE CASCADE` and `video_id BIGINT REFERENCES video_sources(id) ON DELETE SET NULL`. JPA maps these as `@ManyToOne` relationships.

| Pros | Cons |
|------|------|
| DB enforces integrity — no orphan rows possible | Slightly tighter coupling to users/video_sources tables |
| `ON DELETE CASCADE` on user: bookmarks deleted cleanly with account | Must handle nullable `video_id` in JPA entity and API response |
| `ON DELETE SET NULL` on video: bookmark persists as "video unavailable" | Deviates from VideoComment pattern (intentional and documented here) |
| Uniqueness constraint `(user_id, video_id)` is enforceable at DB level | |

**Why VideoComment uses strings**: Comments support guest users (null/token userId) and are intended as permanent historical records even if a video or user is removed. Bookmarks are always owned by an authenticated user and have no meaning without that user — they should cascade-delete. The VideoComment rationale does not apply to bookmarks.

---

## Decision Outcome

### D1 — Bookmark State Delivery: Option 1C (bookmarked video IDs at page load)

**Rationale**: The 500-bookmark cap bounds the payload to ≤ 4 KB (500 × 8 bytes + JSON overhead). This fits easily in a single HTTP response and is negligible alongside a typical video list payload. The Guava LRU cache (ADR-0003) is left untouched — video list responses remain user-agnostic and fully cacheable. React Query's mutation invalidation (`invalidateQueries`) ensures UI consistency after any add/remove operation. Options 1A and 1B both introduce complexity that grows with the number of video list endpoints.

### D2 — FK Strategy: Option 2B (actual FK constraints)

**Rationale**: Bookmarks are a user-owned resource. The database should enforce that a bookmark cannot exist without its owning user — `ON DELETE CASCADE` is the correct model. For videos, `ON DELETE SET NULL` is preferred over `ON DELETE CASCADE` (silent bookmark loss) because it lets the UI surface a "video no longer available" state rather than silently shrinking the user's bookmark list. The VideoComment string-column pattern exists specifically for guest-user flexibility, which bookmarks do not have.

### Additional Decisions (no alternatives considered necessary)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Bookmark ID type | UUID | Matches VideoComment (most recent entity); no sequential ID leakage risk |
| Collection storage | Three-table relational | Proper M:N integrity; 500-bookmark cap makes scale irrelevant; aligns with JPA @ManyToMany |
| Toggle API shape | `POST /bookmarks` (idempotent add) + `DELETE /bookmarks?videoId=X` | Frontend is video-centric; client never needs to manage internal bookmark IDs |
| ON DELETE video_id | SET NULL | Preserves bookmark entry; UI shows "Video no longer available"; VideoSource `status` field provides primary soft-delete layer |

### Quantified Impact

| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| Bookmark state per card | N/A (no feature) | 0 extra API calls after initial load | O(1) Set.has() lookup |
| Page-load additional request | 0 | +1 (GET /bookmarks/ids, ~4 KB max) | Parallel with video list fetch |
| Video list cache hit rate | Unchanged | Unchanged | No enrichment of cached responses |
| Bookmark toggle latency (p99 target) | N/A | < 200 ms | Simple INSERT/DELETE by (user_id, video_id) |

---

## Consequences

**Positive:**
- Guava LRU cache for video lists is completely unaffected — no cache architecture changes
- Bookmark state is correct for all video cards, including paginated / infinite-scroll results, without extra requests
- DB integrity is enforced at the schema level — no defensive application logic needed for orphan rows
- Video-centric API (`?videoId=X`) matches how the frontend naturally thinks about the feature

**Negative:**
- Bookmark entity deviates from VideoComment's string-FK pattern — developers must be aware of the distinction
- Nullable `video_id` (after `ON DELETE SET NULL`) requires null handling in the JPA entity (`@ManyToOne(optional = true)`) and in the API response DTO
- `GET /bookmarks/ids` result is stale if bookmarks change in another browser tab — acceptable for this use case

**Risks:**
- If the 500-bookmark cap is removed in future, the "all IDs at page load" approach must be re-evaluated. Cap enforcement in the service layer is the guard. → If removed, migrate to Option 1B (bulk status endpoint).
- `ON DELETE SET NULL` on video_id requires the frontend to handle `videoId: null` bookmarks gracefully (show placeholder, not crash). → API contract must document this; frontend must treat null videoId as a special case.

---

## Schema Reference

```sql
-- Migration: 202605310001-create-bookmark-tables.sql

CREATE TABLE bookmarks (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_id    BIGINT               REFERENCES video_sources(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_bookmark_user_video UNIQUE (user_id, video_id)
);
CREATE INDEX idx_bookmarks_user_id ON bookmarks(user_id);

CREATE TABLE bookmark_collections (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_bookmark_collections_user_id ON bookmark_collections(user_id);

CREATE TABLE bookmark_collection_items (
    collection_id UUID NOT NULL REFERENCES bookmark_collections(id) ON DELETE CASCADE,
    bookmark_id   UUID NOT NULL REFERENCES bookmarks(id) ON DELETE CASCADE,
    PRIMARY KEY (collection_id, bookmark_id)
);
```

## API Contract Reference

```
GET    /api/v1/bookmarks/ids              → { videoIds: [Long] }          (page-load fetch)
GET    /api/v1/bookmarks                  → ResultListInfo<BookmarkDto>    (bookmarks page)
POST   /api/v1/bookmarks                  body: { videoId: Long }          (idempotent add)
DELETE /api/v1/bookmarks?videoId=X        → 204                            (remove by video)

GET    /api/v1/bookmarks/collections      → ResultListInfo<CollectionDto>
POST   /api/v1/bookmarks/collections      body: { name }
PUT    /api/v1/bookmarks/collections/{id} body: { name }
DELETE /api/v1/bookmarks/collections/{id} → 204

POST   /api/v1/bookmarks/collections/{id}/items   body: { bookmarkId }
DELETE /api/v1/bookmarks/collections/{id}/items/{bookmarkId} → 204
```

Notes:
- All endpoints require `Authorization: Bearer <jwt>` — Spring Security filter enforces this
- `BookmarkDto.videoId` may be `null` when source video has been deleted (ON DELETE SET NULL)
- `POST /bookmarks` returns 200 if already exists (idempotent), 201 if created
- `POST /bookmarks` returns 409 if user has reached the 500-bookmark cap

---

## Validation

- [ ] Performance: bookmark toggle p99 < 200 ms under load with 500 bookmarks/user
- [ ] Correctness: `GET /bookmarks/ids` returns consistent results before/after add/remove
- [ ] Security review: authorization check in service layer (userId from SecurityContext, never from request body)
- [ ] Null safety: `BookmarkDto` with `videoId: null` renders gracefully in the frontend
- [ ] Tech Strategy alignment: ✅ no new infrastructure, follows Spring Boot + PostgreSQL + React + Guava golden path
- [ ] Related plan document: `docs/plans/plan-bookmark-feature.md`

---

## Links

- [PRD: Bookmark Feature](../prd/PRD-bookmark-feature.md)
- [ADR-0003: Use Cache](./0003-use-cache.md) — Guava LRU cache that must remain unaffected
- [ADR-0009: Unified Video List API](./0009-unified-video-list-api.md) — video list endpoints that must not be enriched

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-05-31 | nguyenhuuca | Initial draft |
