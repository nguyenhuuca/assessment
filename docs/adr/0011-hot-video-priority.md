# ADR-0011: Hot Video Priority Scoring

## Status
Accepted

## Date
2026-02-28

## Context

All videos are displayed in a flat, unordered list with no signal of trending or popularity. Users must manually browse without any curation, leading to poor content discovery and lower engagement.

**Existing infrastructure available for reuse:**

| Component | Notes |
|-----------|-------|
| `VideoSource` entity | Active video table (`video_sources`) — supports YouTube, Google Drive, S3 via `sourceType` |
| `VideoAccessStats` entity | Tracks `videoId` (= `VideoSource.sourceId`), `hitCount`, `lastAccessedAt` |
| `VideoAccessServiceImpl.recordAccess()` | Exists but commented out — needs activation |
| `ShareLink` table | Tracks share events per video |
| `VideoCacheImpl` | Already promotes videos with ≥5 cache hits |
| `AppScheduler` | Has `cleanUpOldVideos()` scaffold — can host new scheduled jobs |

**Key constraint:** `VideoSource` has **no** `upCount`/`downCount` fields. The hot score formula must rely only on access hits, shares, and recency.

**Alternatives considered:**

| Approach | Pros | Cons |
|----------|------|------|
| Simple hit-count sort | Trivial to implement | Biased to old videos, ignores recency |
| YouTube-style engagement score | Rich signals (likes, comments) | Signals don't exist in `VideoSource` |
| **Weighted multi-signal score with decay** ✅ | Balances hits, shares, recency; configurable weights | Slightly more complex; requires scheduler |
| External recommendation engine | High quality | Overkill for current scale |
| Collaborative filtering (ML) | Personalized | Out of scope for v1 |

## Decision

Implement a **weighted multi-signal hot score** computed on a 30-minute schedule and stored on the `video_sources` table.

**Formula:**
```
hotScore = (0.60 × normalizedHits) + (0.25 × normalizedShares) + (0.15 × recencyBoost)

recencyBoost = e^(-0.1 × hoursAgo)
```

All inputs are min-max normalized to `[0, 1]` across active videos. Weights are externalised in `application.yaml` under `app.hot-video.*` for tuning without redeploy.

**Storage:** Three new columns added to `video_sources` via Liquibase migration:
```sql
hot_score  DECIMAL(5,4) DEFAULT 0
is_hot     BOOLEAN      DEFAULT FALSE
hot_rank   INT          DEFAULT NULL
```

**API surface:**
- New: `GET /api/videos/trending?limit=N` — returns top N hot videos from cache
- Extended: `GET /api/videos?sort=hot` — existing endpoint with opt-in sort param (backward compatible)

**Scheduler:** `AppScheduler.recomputeHotScores()` runs every 30 minutes, updates scores in DB, refreshes `StatsCacheImpl`, and pre-warms `VideoCacheImpl` for top 10 videos.

## Consequences

### Positive
- Content discovery improves — trending videos surface automatically
- Pre-warming cache for hot videos reduces first-frame latency from ~2s to <500ms
- Configurable weights allow tuning without code changes
- Backward compatible — no breaking change to existing `/api/videos`
- Formula is transparent and auditable (no black-box ML)

### Negative
- Scores are stale up to 30 minutes (acceptable for v1)
- Additional DB columns and index on `video_sources`
- `video_access_stats` table will grow over time — requires periodic cleanup job
- `recordAccess()` adds a small async DB write on every stream (fire-and-forget, non-blocking)

### Trade-offs accepted
- 30-minute TTL is acceptable for v1; real-time scoring deferred to v2
- No personalization — same trending list for all users (v2 can add per-user signals)
- `is_hot` threshold (top 20% by score) is a configurable approximation

## Related

- [PRD-hot-video-priority](../prd/PRD-hot-video-priority.md) — Full product requirements
- [plan-hot-video-priority](../plans/plan-hot-video-priority.md) — Implementation plan
- [ADR-0003](0003-use-cache.md) — Guava cache strategy (StatsCacheImpl, VideoCacheImpl)
- [ADR-0005](0005-use_lru_cache_video_streaming.md) — LRU cache for video streaming
- `VideoAccessStats` entity: `api/src/main/java/com/canhlabs/funnyapp/entity/VideoAccessStats.java`
- `VideoSource` entity: `api/src/main/java/com/canhlabs/funnyapp/entity/VideoSource.java`
