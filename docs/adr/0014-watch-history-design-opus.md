# ADR-0014: Watch History — Recording, Storage, and Ownership Design

<!-- Model-comparison artifact — generated with Claude Opus 4.8. Sibling: ADR-0013-watch-history-design.md (same feature, different model). Keep separate. -->

## Metadata

**Status:** Proposed · **Date:** 2026-06-01 · **Deciders:** nguyenhuuca · **Tags:** watch-history, schema, api-design, concurrency, frontend-state  
**Related PRD:** [PRD-watch-history-opus](../prd/PRD-watch-history-opus.md) · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ✅ Follows Golden Path — PostgreSQL + JPA/Liquibase + Spring Boot + React. No new infrastructure (no Redis — see ADR-0003).

> **Note:** Generated with Claude Opus 4.8 as a model-comparison exercise against [ADR-0013](./0013-watch-history-design.md). Same feature, independently derived. Where the two diverge, this ADR documents the divergence explicitly (PK type, eviction atomicity).

---

## Context

The Watch History PRD ([PRD-watch-history-opus](../prd/PRD-watch-history-opus.md)) requires persisting, per logged-in user, the set of videos they have played, surfaced as a newest-first `/history` page and a "watched" badge on video cards. The feature is automatic (no explicit save action) and private.

Four design questions are not settled by the PRD and must be decided before implementation:

1. **Where is a watch event captured?** The app serves two video sources: locally-stored videos via `VideoStreamController`, and YouTube-hosted videos that stream **directly from YouTube** and never touch the app's stream endpoints. Any purely server-side capture point would silently miss all YouTube content.

2. **What is the primary key type?** The existing domain entities `User` (`api/.../entity/User.java`) and `VideoSource` (`api/.../entity/VideoSource.java`) both use `@GeneratedValue(strategy = IDENTITY)` `Long` (BIGINT) keys. A new table must justify any deviation from this convention.

3. **How is the 500-entry cap enforced under concurrency?** History grows without user intent. PRD NFR-5 requires a user never exceeds 500 entries *even under concurrent plays* — so eviction cannot be a best-effort "delete-then-insert" with a race window.

4. **How is ownership enforced?** History is private (FR-8). Ownership must not be spoofable.

This ADR records data model, recording, eviction, and ownership decisions. Implementation steps and code belong in `docs/plans/plan-watch-history-opus.md`.

---

## Decision Drivers

- **Uniform capture** — recording must work identically for YouTube and local videos
- **Zero playback impact** — recording must not add latency to play start (NFR-1)
- **Convention alignment** — match existing `Long`/IDENTITY key strategy unless there is a strong reason not to
- **Hard cap guarantee** — ≤ 500 entries per user must hold under concurrent writes (NFR-5), not just on average
- **Non-spoofable ownership** — a user can only ever read/mutate their own entries (FR-8), derived from the JWT subject
- **No per-card network cost** — watched badges must add 0 API calls after page load (NFR-3)
- **No new infrastructure** — stay within PostgreSQL + JPA (ADR-0001, ADR-0003)

---

## Considered Options

### Decision 1 — Where to capture the watch event

#### Option 1A: Frontend fire-and-forget `POST /watch-history` on play start

The player calls `POST /watch-history { videoId }` when playback begins; the response is not awaited.

| Pros | Cons |
|------|------|
| Works identically for YouTube and local videos | A dropped request (network/ad-blocker) silently misses the watch — no retry |
| Zero playback impact (no await) | Records even a 1-second skip (min-watch-duration deferred to v2) |
| No change to streaming controllers | |

#### Option 1B: Server-side capture in `VideoStreamController` (first chunk served)

| Pros | Cons |
|------|------|
| Cannot be blocked client-side | **Misses every YouTube video** — they bypass the app's stream path entirely |
| Reliable for local content | Couples history to streaming internals; ~half of content unrecorded |

#### Option 1C: Frontend POST gated by a ≥ N-second watch timer

| Pros | Cons |
|------|------|
| Filters accidental plays | Adds client timer state; min-watch-duration is explicitly v2 (PRD open question) |
| Same uniform coverage as 1A | Same drop risk as 1A |

### Decision 2 — Primary key type for `watch_history`

#### Option 2A: `BIGINT` IDENTITY (match `User` / `VideoSource`)

| Pros | Cons |
|------|------|
| Consistent with every existing entity and FK type | Keys are sequential/guessable (irrelevant — keys are never exposed; access is owner-scoped) |
| Smaller index/storage footprint; faster joins on `user_id BIGINT` | |
| No `pgcrypto` / `gen_random_uuid()` dependency | |

#### Option 2B: `UUID` primary key

| Pros | Cons |
|------|------|
| Non-enumerable identifiers | **Inconsistent** with `User`/`VideoSource` BIGINT keys |
| | Larger index; requires `gen_random_uuid()`; mixes key strategies in one schema |

> ADR-0013 chose UUID here. Given `User.id` and `VideoSource.id` are both `Long`/IDENTITY, this ADR treats BIGINT as the convention-aligned choice and the UUID divergence as unjustified for an owner-scoped table whose PK is never exposed in any API.

### Decision 3 — 500-entry cap enforcement

#### Option 3A: Transactional auto-evict (delete oldest within the upsert transaction)

Within a single transaction: upsert the entry, then if `count > 500`, delete the oldest `watched_at` rows down to 500.

| Pros | Cons |
|------|------|
| Cap holds under concurrency (NFR-5) — bounded inside one transaction | Slightly heavier write path (upsert + conditional delete) |
| Invisible to the user — matches YouTube/Netflix/browser history | Oldest entries silently dropped (expected, acceptable) |
| Self-healing — converges to ≤ 500 even if a prior write raced | |

#### Option 3B: Best-effort evict (delete oldest, then insert; no shared transaction)

| Pros | Cons |
|------|------|
| Simplest | Race window: two concurrent inserts can both skip eviction → > 500 (violates NFR-5) |

#### Option 3C: Hard reject at 500 (HTTP 409)

| Pros | Cons |
|------|------|
| Trivial logic | **Invisible failure** — history silently stops recording; wrong for an automatic feature the user can't control |

### Decision 4 — Ownership enforcement

#### Option 4A: Derive `user_id` from the JWT subject server-side

| Pros | Cons |
|------|------|
| Non-spoofable — client cannot address another user's rows | Requires every query/mutation to scope by the authenticated principal |
| No `userId` accepted in any request body or param | |

#### Option 4B: Accept `userId` from the client, validate against token

| Pros | Cons |
|------|------|
| Explicit in the contract | Redundant, and one missed check = IDOR (CWE-639). No benefit over 4A |

---

## Decision Outcome

| # | Decision | Chosen |
|---|----------|--------|
| D1 | Capture point | **1A — Frontend fire-and-forget POST** |
| D2 | Primary key | **2A — BIGINT IDENTITY** |
| D3 | Cap enforcement | **3A — Transactional auto-evict** |
| D4 | Ownership | **4A — JWT-derived `user_id`** |

**Rationale:**

- **D1:** Option 1B is disqualified — it cannot see YouTube videos, missing roughly half of all content. 1C adds timer state for a min-watch-duration that the PRD explicitly defers to v2. 1A is the only point that captures both sources uniformly with zero playback impact. The dropped-request risk is accepted: a missed history entry is low-severity and self-corrects on the next play.
- **D2:** The two entities this table references both use `Long`/IDENTITY keys. The PK is never returned by any endpoint (the API addresses rows by `videoId`, not history `id`), so UUID's non-enumerability buys nothing. BIGINT keeps the schema internally consistent and the `(user_id, …)` index compact. This is the deliberate divergence from ADR-0013.
- **D3:** Because history is automatic, a hard reject (3C) produces silent, unexplained data loss. Best-effort eviction (3B) has a concurrency race that violates NFR-5. Wrapping evict+upsert in one transaction makes the ≤ 500 invariant hold under concurrent plays and is self-healing — directly answering the "not atomic without a transaction" caveat noted in ADR-0013.
- **D4:** Deriving the owner from the JWT subject removes a whole class of IDOR bugs (CWE-639) by construction; accepting a client `userId` adds risk with no upside.

### Quantified Impact

| Metric | Value | Notes |
|--------|-------|-------|
| Extra API calls per card render | 0 | Client-side `Set.has()` after one page-load `/ids` fetch (NFR-3) |
| Playback latency added by recording | 0 ms | Fire-and-forget POST, no await (NFR-1) |
| DB statements on normal re-watch | 1 | Single upsert (`ON CONFLICT … DO UPDATE`) |
| DB statements when cap exceeded | 2 | Upsert + bounded delete-oldest, one transaction |
| Max rows per user | ≤ 500 | Invariant under concurrency (NFR-5) |
| List query p99 (≤ 500 rows) | < 500 ms | Served by `(user_id, watched_at DESC)` index (NFR-2) |

---

## Consequences

**Positive:**
- One capture path covers YouTube and local videos with no streaming-layer coupling.
- Schema is convention-aligned (all BIGINT keys); FK joins on `user_id` stay compact.
- The 500-entry cap is a hard guarantee, not a best-effort average.
- Ownership is non-spoofable by construction — no client-supplied identity.
- Watched badges cost a single fetch per page load regardless of card count.

**Negative:**
- Fire-and-forget recording has no retry: a failed POST loses that one watch (low severity, self-corrects next play).
- First-watch timestamp is lost on re-watch (history = "recent", not a diary — acceptable).
- The transactional evict adds a conditional delete to the write path (bounded, single-digit rows).

**Risks:**
- **Write pressure** on `watch_history` for popular videos watched by many users concurrently → mitigated by keeping each write to one lightweight upsert (+ rare bounded delete).
- **Concurrent multi-tab plays of the same video** → the unique `(user_id, source_video_id)` constraint + upsert resolves to last-write-wins on `watched_at`; no duplicates.
- **Min-watch-duration absent** → accidental plays are recorded in v1; revisit in v2 if history noise is reported (PRD open question).

---

## Data Model (Reference)

```
watch_history
  id               BIGINT       PRIMARY KEY (GENERATED BY DEFAULT AS IDENTITY)
  user_id          BIGINT       NOT NULL  REFERENCES users(id)         ON DELETE CASCADE
  video_id         BIGINT                 REFERENCES video_sources(id) ON DELETE SET NULL
  source_video_id  BIGINT       NOT NULL  -- immutable; survives video deletion; uniqueness anchor
  watched_at       TIMESTAMPTZ  NOT NULL  DEFAULT now()
  CONSTRAINT uq_watch_history_user_video UNIQUE (user_id, source_video_id)
  INDEX idx_watch_history_user_watched (user_id, watched_at DESC)
```

- `source_video_id` is the uniqueness/badge anchor; it remains valid after `video_id` is nulled by `ON DELETE SET NULL` (a deleted `VideoSource` renders as "Video unavailable" without breaking the unique key).
- `(user_id, watched_at DESC)` serves both the newest-first list query (FR-3) and oldest-row selection for eviction (FR-9) — no separate index needed.
- Migration lives in `api/src/main/resources/db/changelog/sql/` per Tech Strategy.

## API Contract (Reference)

| Method | Path | Purpose | Response |
|--------|------|---------|----------|
| `GET` | `/watch-history` | Current user's history, newest-first, paginated | `ResultListInfo<WatchHistoryDto>` |
| `GET` | `/watch-history/ids` | Watched video IDs for badge lookup | `ResultListInfo<Long>` |
| `POST` | `/watch-history` | Fire-and-forget upsert `{ videoId }` | `ResultObjectInfo<WatchHistoryDto>` |
| `DELETE` | `/watch-history?videoId=X` | Remove a single entry | `ResultObjectInfo<Void>` |
| `DELETE` | `/watch-history/all` | Clear the user's history | `ResultObjectInfo<Void>` |

Every endpoint resolves the owner from the JWT subject (D4). No endpoint accepts a `userId`. Base path follows the project's `BASE_URL` controller convention — **to be confirmed against an existing controller during planning** (the sibling ADR-0013 assumed an `/api/v1` prefix; this must be verified, not assumed).

---

## Validation

- [ ] Fire-and-forget POST does not block playback (verify under network throttling)
- [ ] YouTube video records a history entry identically to a local video
- [ ] At 500 entries, a 501st watch evicts the oldest and total stays ≤ 500 — verified under concurrent inserts (NFR-5)
- [ ] Upsert moves a re-watched entry to the top of `GET /watch-history`
- [ ] `source_video_id` uniqueness holds after `video_id` is nulled by `ON DELETE SET NULL`
- [ ] No endpoint accepts a client-supplied `userId`; cross-user access returns empty/forbidden (FR-8)
- [ ] Security review (IDOR / CWE-639) completed
- [ ] Tech Strategy alignment confirmed — no new infrastructure
- [ ] Related plan created: `docs/plans/plan-watch-history-opus.md`

---

## Links

- [PRD: Watch History (Opus)](../prd/PRD-watch-history-opus.md)
- [ADR-0013: Watch History Design](./0013-watch-history-design.md) — sibling (UUID PK, non-transactional evict); this ADR diverges on D2 and D3
- [ADR-0012: Bookmark Feature Design](./0012-bookmark-feature-design.md) — shared patterns (IDs endpoint, `source_video_id`, FK strategy)
- [ADR-0003: Caching Strategy](./0003-use-cache.md) — no Redis until horizontal scaling
- [Implementation Plan](../plans/plan-watch-history-opus.md) — *to be created*

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-06-01 | nguyenhuuca | Initial draft (generated with Claude Opus 4.8 for model comparison) |
