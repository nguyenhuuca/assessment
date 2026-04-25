# PRD: Hot Video Priority Feature

**Status:** Draft
**Author:** Team
**Created:** 2026-02-28
**Version:** 1.0

---

## 1. Overview

### Problem Statement

Currently, all videos are displayed in a flat, unordered list. Users have no signal about which videos are trending or popular — leading to poor content discovery and lower engagement. Users who want to watch something good have to manually browse without any curation.

### Goal

Surface "hot" videos (trending, high-engagement content) at the top of the feed and mark them with a visual indicator. Hot videos should also be pre-cached so their streaming starts faster.

### Success Metrics

| Metric | Baseline | Target |
|--------|----------|--------|
| Average session duration | TBD | +15% |
| Videos watched per session | TBD | +20% |
| Time-to-first-frame for hot videos | ~2s | <500ms (via pre-cache) |
| User click-through on hot badges | N/A | >30% |

---

## 2. Background & Current State

### Existing Infrastructure (reuse)

| Component | Status | Notes |
|-----------|--------|-------|
| `VideoSource` entity | ✅ Active | Maps to `video_sources` table — the **active** video table |
| `VideoAccessStats` entity | ✅ Exists | Tracks `videoId` (= `VideoSource.sourceId`), `hitCount`, `lastAccessedAt` |
| `VideoAccessServiceImpl.recordAccess()` | ⚠️ Commented out | TODO comment — needs to be activated |
| `ShareLink` table | ✅ Exists | Tracks which videos are shared |
| `VideoCacheImpl` (adaptive cache) | ✅ Exists | Already promotes videos with ≥5 cache hits |
| `StatsCacheImpl` | ✅ Exists | In-memory stats cache |
| `AppScheduler` | ✅ Exists | Has `cleanUpOldVideos()` scaffold (currently no-op) |

> ⚠️ **Note:** `YouTubeVideo` / `youtube_video` table is **no longer the active table**.
> The system now uses `VideoSource` / `video_sources` (supports Google Drive, YouTube, S3 sources via `sourceType` field).
> `VideoSource` does **not** have `upCount`/`downCount` — hot score formula adjusted accordingly.

### Gap Analysis

1. `recordAccess()` is disabled — no access data is being written
2. No "hot score" computation logic exists
3. No API endpoint returns videos sorted by hotness
4. No cache pre-warming for hot videos
5. Frontend has no "hot" badge / trending section

---

## 3. Scope

### In Scope (v1)

- [x] Activate `recordAccess()` in `VideoAccessServiceImpl`
- [x] Implement Hot Score algorithm
- [x] New API: `GET /videos/trending` — returns top N hot videos
- [x] Extend existing `GET /videos` to accept `sort=hot` param
- [x] Scheduled job: recompute hot scores every 30 minutes
- [x] Pre-warm cache for top 10 hot videos
- [x] Frontend: "🔥 Hot" badge on trending videos
- [x] Frontend: "Trending" section at top of home page

### Out of Scope (v2+)

- Personalized recommendations (per-user hot scores)
- Real-time score updates (WebSocket push)
- A/B testing framework for ranking algorithms
- Machine learning-based scoring

---

## 4. Functional Requirements

### FR-1: Access Tracking

**When:** A user streams any video (calls the stream endpoint)
**Then:** System records the access asynchronously (non-blocking)

```
POST /stream/{videoId}
    → async: VideoAccessService.recordAccess(videoId)
        → upsert VideoAccessStats (hitCount++, lastAccessedAt = now)
```

**Acceptance Criteria:**
- `recordAccess()` must NOT block the streaming response
- Already uses `@Async` annotation — keep it
- Handle upsert: create if not exists, increment if exists
- DB write failure must not fail the stream request (catch + log)

---

### FR-2: Hot Score Calculation

**Formula:**

```
hotScore = (w1 × normalizedHits) + (w2 × normalizedShares) + (w3 × recencyBoost)
```

> `YouTubeVideo.upCount` is no longer available — `VideoSource` table does not have like counts.
> Formula simplified to 3 signals. Weights re-allocated.

**Parameters:**

| Variable | Source | Weight (default) |
|----------|--------|-----------------|
| `normalizedHits` | `VideoAccessStats.hitCount` where `videoId = VideoSource.sourceId` (last 24h) | w1 = 0.60 |
| `normalizedShares` | COUNT from `ShareLink` table | w2 = 0.25 |
| `recencyBoost` | Decay function based on `VideoAccessStats.lastAccessedAt` | w3 = 0.15 |

**Recency Decay:**
```
recencyBoost = e^(-λ × hoursAgo)   where λ = 0.1
```
- Video accessed 1 hour ago → boost ≈ 0.90
- Video accessed 24 hours ago → boost ≈ 0.09

**Normalization:** All inputs normalized to [0, 1] using min-max scaling across all active videos.

**Configuration** (externalizable via `AppProperties`):
```yaml
app:
  hot-video:
    weights:
      hits: 0.60
      shares: 0.25
      recency: 0.15
    decay-lambda: 0.1
    window-hours: 24
    top-n: 20
    score-ttl-minutes: 30
```

---

### FR-3: Trending API Endpoint

**New endpoint:**
```
GET /api/videos/trending?limit=10
```

**Response:**
```json
{
  "status": "SUCCESS",
  "data": [
    {
      "videoId": "abc123",
      "title": "Funny Cat Video",
      "upCount": 12500,
      "embedLink": "...",
      "hotScore": 0.87,
      "isHot": true,
      "rank": 1
    }
  ]
}
```

**Rules:**
- Returns top `limit` videos by hotScore (default 10, max 50)
- Scores served from `StatsCacheImpl` (TTL: 30 minutes)
- Falls back to `upCount` sort if no access stats available

---

### FR-4: Extend Existing Video List

Modify `GET /api/videos` to accept optional sort param:

```
GET /api/videos?sort=hot        → sorted by hotScore DESC
GET /api/videos?sort=latest     → sorted by createdAt DESC (current default)
GET /api/videos?sort=likes      → sorted by upCount DESC
```

**Backward compatible:** `sort` is optional; default behavior unchanged.

---

### FR-5: Scheduled Score Recomputation

In `AppScheduler`, add job:
```
@Scheduled(fixedRate = 30 * 60_000)   // every 30 minutes
public void recomputeHotScores() { ... }
```

**Steps:**
1. Query all videos with access stats in last 24h
2. Compute hotScore for each
3. Store result in `StatsCacheImpl` (key: `hot_scores`, TTL: 35min)
4. Log top 5 scores for observability
5. Trigger cache pre-warm for top 10 videos (FR-6)

---

### FR-6: Pre-warm Cache for Hot Videos

After score recomputation, pre-load video metadata for top 10 hot videos into `VideoCacheImpl`:

```
hotVideoService.getTopN(10)
    .forEach(v -> videoCacheImpl.preWarm(v.getVideoId()))
```

**Goal:** First-frame latency for hot videos drops from ~2s → <500ms

---

### FR-7: Frontend — Hot Badge & Trending Section

**Home Page changes:**
1. Add "🔥 Trending Now" section at the top — shows top 5 hot videos
2. Each video card gets `🔥 Hot` badge when `isHot: true`

**Badge logic:** Show badge if `hotScore >= threshold` (default: top 20% of scores)

---

## 5. Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| `recordAccess()` latency overhead | < 5ms (async, fire-and-forget) |
| Trending API response time | < 100ms (served from cache) |
| Score recomputation time | < 10s for 10,000 videos |
| Cache memory overhead | < 50MB additional |
| Backward compatibility | Existing `/api/videos` unchanged without `sort` param |

---

## 6. Data Model Changes

### New Columns on `video_sources` table

```sql
-- Liquibase migration: 202502280001-add-hot-score.sql
ALTER TABLE video_sources ADD COLUMN hot_score DECIMAL(5,4) DEFAULT 0;
ALTER TABLE video_sources ADD COLUMN is_hot BOOLEAN DEFAULT FALSE;
ALTER TABLE video_sources ADD COLUMN hot_rank INT DEFAULT NULL;
```

### Index for trending query performance

```sql
CREATE INDEX idx_video_sources_hot_score ON video_sources(hot_score DESC)
WHERE is_hot = TRUE;
```

### Activate `VideoAccessStats` writes

```sql
-- Ensure index exists for upsert performance
-- Note: VideoAccessStats.videoId maps to VideoSource.sourceId (String)
CREATE INDEX IF NOT EXISTS idx_video_access_stats_video_id
ON video_access_stats(video_id);
```

---

## 7. Component Design

### New Components

```
service/
├── HotVideoService.java              ← Interface
└── impl/
    └── HotVideoServiceImpl.java      ← Score computation, ranking

jobs/
└── AppScheduler.java                 ← Add recomputeHotScores() job

web/
└── YoutubeController.java            ← Add GET /videos/trending endpoint
```

### Modified Components

| File | Change |
|------|--------|
| `VideoAccessServiceImpl.java` | Uncomment `recordAccess()` body |
| `StreamVideoServiceImpl.java` | Add `sort=hot` support in `getVideosToStream()` |
| `VideoSource.java` entity | Add `hotScore`, `isHot`, `hotRank` fields |
| `VideoDto.java` | Add `hotScore`, `isHot`, `rank` to response |
| `AppProperties.java` | Add `HotVideoProperties` inner class |
| `application.yaml` | Add `app.hot-video.*` config block |

---

## 8. API Contract

### GET /api/videos/trending

```
Request:
  GET /api/videos/trending
  Query Params:
    limit  (optional, int, default=10, max=50)
  Headers:
    Authorization: Bearer <token>  (optional — public endpoint)

Response 200:
  ResultListInfo<VideoDto>
    data[].videoId     String
    data[].title       String
    data[].embedLink   String
    data[].upCount     Long
    data[].hotScore    Double   ← new
    data[].isHot       Boolean  ← new
    data[].rank        Integer  ← new (1 = hottest)
    data[].userShared  String
```

### GET /api/videos?sort=hot

```
Request:
  GET /api/videos?sort=hot&page=0&size=20

Response: same VideoDto structure, ordered by hotScore DESC
```

---

## 9. Implementation Plan

### Phase 1 — Data Foundation (1-2 days)
- [ ] Activate `recordAccess()` — uncomment + test
- [ ] Add DB migration: hot_score column + indexes
- [ ] Update `YouTubeVideo` entity + `VideoDto`

### Phase 2 — Scoring Engine (1-2 days)
- [ ] Implement `HotVideoServiceImpl` with score formula
- [ ] Add config properties (`AppProperties.HotVideoProperties`)
- [ ] Unit tests for score computation (edge cases: no stats, all zeros)

### Phase 3 — API & Scheduler (1 day)
- [ ] Add `GET /videos/trending` endpoint
- [ ] Add `sort=hot` param to existing endpoint
- [ ] Add `recomputeHotScores()` scheduler job
- [ ] Pre-warm cache for top 10

### Phase 4 — Frontend (1 day)
- [ ] Add "Trending Now" section
- [ ] Add 🔥 badge to hot video cards
- [ ] Update API client to call `/trending`

### Phase 5 — Testing & Tuning (1 day)
- [ ] Integration tests for trending endpoint
- [ ] Load test: 100 concurrent users recording access
- [ ] Tune weights based on sample data

**Total estimate: 5-7 days**

---

## 10. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| `recordAccess()` creating DB bottleneck under high load | Medium | High | Keep `@Async`, add circuit breaker, batch writes with queue if needed |
| Hot score bias toward old videos with high hitCount | Medium | Medium | Apply 24h rolling window + recency decay factor |
| Cache invalidation: scores stale after new video added | Low | Low | TTL-based invalidation (30min) is acceptable for v1 |
| DB `video_access_stats` table growth | Medium | Medium | Add scheduled cleanup job (already scaffolded in `AppScheduler.cleanUpOldVideos()`) |

---

## 11. Open Questions

| # | Question | Owner | Due |
|---|----------|-------|-----|
| 1 | Should `/trending` be a public endpoint (no auth)? | Backend | Before Phase 3 |
| 2 | What threshold defines "isHot" — top 20% or fixed score? | Product | Before Phase 2 |
| 3 | Should shares from private share-links count toward score? | Backend | Before Phase 2 |
| 4 | Do we need a `hot_score_history` table for analytics? | Data | v2 |

---

## 12. Related Documents

- `doc/adr/0003-use-cache.md` — Cache strategy (Guava → Redis)
- `doc/adr/0005-use_lru_cache_video_streaming.md` — LRU cache trade-offs
- `VideoAccessStats` entity: `api/src/main/java/com/canhlabs/funnyapp/entity/VideoAccessStats.java`
- `VideoAccessServiceImpl`: `api/src/main/java/com/canhlabs/funnyapp/service/impl/VideoAccessServiceImpl.java`
