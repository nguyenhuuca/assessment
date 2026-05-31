# Plan: Hot Video Priority Scoring

**Relates to:** [PRD-hot-video-priority](../prd/PRD-hot-video-priority.md), [ADR-0011](../adr/0011-hot-video-priority.md)
**Size:** Medium (5–7 days)
**Decision type:** Two-Way Door (additive columns, opt-in sort param, backward compatible)
**Blocks nothing** — existing `/api/videos` endpoint unchanged without `sort=hot` param

---

## Problem Summary

Videos are shown in a flat list with no trending signal. Five gaps must be closed:

| # | Gap | Severity |
|---|-----|----------|
| 1 | `recordAccess()` is commented out — no access data is written | High |
| 2 | No hot score computation logic | High |
| 3 | No API endpoint for trending videos | High |
| 4 | No cache pre-warming for hot videos | Medium |
| 5 | Frontend has no hot badge or trending section | Medium |

---

## Architecture Summary

```
Stream request
    → VideoAccessServiceImpl.recordAccess()  [async, fire-and-forget]
        → upsert VideoAccessStats

AppScheduler.recomputeHotScores()  [every 30 min]
    → HotVideoServiceImpl.computeScores()
        → query VideoAccessStats + ShareLink (last 24h)
        → normalize + apply weighted formula
        → update video_sources (hot_score, is_hot, hot_rank)
        → refresh StatsCacheImpl
        → pre-warm VideoCacheImpl for top 10

GET /api/videos/trending
    → VideoController.getTrending()
        → HotVideoService.getTopN()
            → StatsCacheImpl (TTL 30 min)

GET /api/videos?sort=hot
    → VideoController.getVideos()
        → VideoService.getVideosToStream(sort=hot)
            → query ORDER BY hot_score DESC
```

---

## Phase 1 — Data Foundation (Day 1–2)

### Step 1 — Activate `recordAccess()`

**File:** `api/src/main/java/com/canhlabs/funnyapp/service/impl/VideoAccessServiceImpl.java`

Uncomment the body of `recordAccess()`. It should perform an upsert:
```java
// Upsert: increment hitCount, update lastAccessedAt
videoAccessStatsRepo.upsertAccess(videoId, Instant.now());
```

- Must remain `@Async` — non-blocking
- DB failure must be caught + logged, never propagated to caller

**File:** `api/src/main/java/com/canhlabs/funnyapp/service/impl/StreamVideoServiceImpl.java`

Verify that the stream endpoint calls `recordAccess()`. If not, add the call:
```java
videoAccessService.recordAccess(videoId);  // fire-and-forget
```

---

### Step 2 — DB migration

**New file:** `api/src/main/resources/db/changelog/sql/202602280001-add-hot-score.sql`

```sql
-- Add hot score columns to video_sources
ALTER TABLE video_sources ADD COLUMN hot_score  DECIMAL(5,4) DEFAULT 0;
ALTER TABLE video_sources ADD COLUMN is_hot     BOOLEAN      DEFAULT FALSE;
ALTER TABLE video_sources ADD COLUMN hot_rank   INT          DEFAULT NULL;

-- Index for trending query performance
CREATE INDEX idx_video_sources_hot_score
    ON video_sources(hot_score DESC)
    WHERE is_hot = TRUE;

-- Index for upsert performance on VideoAccessStats
CREATE INDEX IF NOT EXISTS idx_video_access_stats_video_id
    ON video_access_stats(video_id);
```

Register in `api/src/main/resources/db/changelog/db.changelog-master.yaml`.

---

### Step 3 — Update `VideoSource` entity + `VideoDto`

**File:** `api/src/main/java/com/canhlabs/funnyapp/entity/VideoSource.java`

Add fields:
```java
@Column(name = "hot_score")
private Double hotScore = 0.0;

@Column(name = "is_hot")
private Boolean isHot = false;

@Column(name = "hot_rank")
private Integer hotRank;
```

**File:** `api/src/main/java/com/canhlabs/funnyapp/dto/VideoDto.java`

Add response fields:
```java
private Double hotScore;
private Boolean isHot;
private Integer rank;
```

---

## Phase 2 — Scoring Engine (Day 2–3)

### Step 4 — Add `HotVideoProperties` config

**File:** `api/src/main/java/com/canhlabs/funnyapp/config/AppProperties.java`

Add inner class:
```java
@Data
public static class HotVideoProperties {
    private double weightHits    = 0.60;
    private double weightShares  = 0.25;
    private double weightRecency = 0.15;
    private double decayLambda   = 0.1;
    private int    windowHours   = 24;
    private int    topN          = 20;
    private int    scoreTtlMinutes = 30;
}
```

**File:** `api/src/main/resources/application.yaml`

```yaml
app:
  hot-video:
    weight-hits: 0.60
    weight-shares: 0.25
    weight-recency: 0.15
    decay-lambda: 0.1
    window-hours: 24
    top-n: 20
    score-ttl-minutes: 30
```

---

### Step 5 — Implement `HotVideoService`

**New file:** `api/src/main/java/com/canhlabs/funnyapp/service/HotVideoService.java`

```java
public interface HotVideoService {
    void recomputeScores();
    List<VideoDto> getTopN(int n);
}
```

**New file:** `api/src/main/java/com/canhlabs/funnyapp/service/impl/HotVideoServiceImpl.java`

Core scoring logic:
```java
double hotScore(double hits, double shares, double hoursAgo, HotVideoProperties cfg) {
    double recency = Math.exp(-cfg.getDecayLambda() * hoursAgo);
    return cfg.getWeightHits()    * hits
         + cfg.getWeightShares()  * shares
         + cfg.getWeightRecency() * recency;
}
```

Min-max normalization applied to all videos before computing weighted sum.

After computation:
- Persist `hot_score`, `hot_rank`, `is_hot` (top 20%) back to `video_sources` via repository
- Push top N results into `StatsCacheImpl`

---

## Phase 3 — API & Scheduler (Day 3–4)

### Step 6 — Add Trending endpoint

**File:** `api/src/main/java/com/canhlabs/funnyapp/web/VideoController.java`

```java
@GetMapping("/trending")
public ResponseEntity<ResultListInfo<VideoDto>> getTrending(
        @RequestParam(defaultValue = "10") @Max(50) int limit) {
    return ok(hotVideoService.getTopN(limit));
}
```

### Step 7 — Extend `GET /api/videos` with `sort=hot`

**File:** `api/src/main/java/com/canhlabs/funnyapp/service/impl/VideoServiceImpl.java`

```java
if ("hot".equalsIgnoreCase(sort)) {
    return videoSourceRepo.findAllOrderByHotScoreDesc(pageable);
}
```

Add corresponding repository method using `@Query` with `ORDER BY hot_score DESC`.

### Step 8 — Scheduler job

**File:** `api/src/main/java/com/canhlabs/funnyapp/jobs/AppScheduler.java`

```java
@Scheduled(fixedRate = 30 * 60_000)
public void recomputeHotScores() {
    hotVideoService.recomputeScores();
}
```

---

## Phase 4 — Frontend (Day 4–5)

**File:** `webapp/src/components/VideoCard.jsx` (or equivalent)

- Add `🔥 Hot` badge when `isHot === true`

**File:** `webapp/src/pages/Home.jsx`

- Add "🔥 Trending Now" section at page top
- Call `GET /api/videos/trending?limit=5`
- Reuse existing `VideoCard` component

---

## Phase 5 — Testing (Day 5–7)

**Unit tests (Sonnet):**

| Test class | Scenarios |
|---|---|
| `HotVideoServiceImplTest` | Score formula, normalization, no-data edge case, all-zero edge case |
| `AppSchedulerTest` | `recomputeHotScores()` is called, delegates to service |

**Integration tests:**

| Test class | Scenarios |
|---|---|
| `VideoControllerTrendingTest` | `GET /trending` returns 200 with sorted list, limit enforced, cache hit |
| `VideoAccessServiceImplTest` | `recordAccess()` upserts correctly, DB failure does not throw |

---

## Acceptance Criteria

- [ ] Streaming a video increments `VideoAccessStats.hitCount` asynchronously
- [ ] `recomputeHotScores()` scheduler runs every 30 minutes
- [ ] `hot_score`, `is_hot`, `hot_rank` are persisted to `video_sources` after each run
- [ ] `GET /api/videos/trending` returns videos ordered by `hotScore DESC`
- [ ] `GET /api/videos?sort=hot` returns all videos ordered by `hotScore DESC`
- [ ] `GET /api/videos` (no sort param) behavior is unchanged
- [ ] Top 10 hot videos are pre-warmed in `VideoCacheImpl`
- [ ] Frontend shows 🔥 badge on `isHot = true` video cards
- [ ] Frontend shows "Trending Now" section with top 5 hot videos
- [ ] Unit tests pass for scoring formula with edge cases
- [ ] `mvn verify` passes (coverage gate +1%)

---

## File Checklist

| # | File | Action |
|---|------|--------|
| 1 | `db/changelog/sql/202602280001-add-hot-score.sql` | New — add hot_score, is_hot, hot_rank columns + indexes |
| 2 | `db/changelog/db.changelog-master.yaml` | Register new migration |
| 3 | `entity/VideoSource.java` | Add hotScore, isHot, hotRank fields |
| 4 | `dto/VideoDto.java` | Add hotScore, isHot, rank fields |
| 5 | `config/AppProperties.java` | Add HotVideoProperties inner class |
| 6 | `application.yaml` | Add app.hot-video.* config block |
| 7 | `service/impl/VideoAccessServiceImpl.java` | Uncomment recordAccess() body |
| 8 | `service/impl/StreamVideoServiceImpl.java` | Call recordAccess() on stream |
| 9 | `service/HotVideoService.java` | New interface |
| 10 | `service/impl/HotVideoServiceImpl.java` | New — scoring engine, getTopN() |
| 11 | `repo/VideoSourceRepo.java` | Add findAllOrderByHotScoreDesc() |
| 12 | `repo/VideoAccessStatsRepo.java` | Add upsertAccess() |
| 13 | `web/VideoController.java` | Add GET /trending endpoint + sort=hot param |
| 14 | `service/impl/VideoServiceImpl.java` | Add sort=hot branch |
| 15 | `jobs/AppScheduler.java` | Add recomputeHotScores() job |
| 16 | `webapp/src/components/VideoCard.jsx` | Add 🔥 Hot badge |
| 17 | `webapp/src/pages/Home.jsx` | Add Trending Now section |

---

## Risks & Mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|-----------|
| `recordAccess()` DB bottleneck under load | Medium | `@Async` + catch all exceptions; add write queue if needed in v2 |
| `video_access_stats` table unbounded growth | Medium | Activate `cleanUpOldVideos()` in `AppScheduler` to purge rows older than window |
| Hot score biased to old high-hit videos | Medium | 24h rolling window + exponential recency decay mitigates this |
| Score stale up to 30 minutes | Low | Acceptable for v1; TTL is configurable |
