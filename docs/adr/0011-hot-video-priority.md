# ADR-0011: Hot Video Priority Scoring

## Metadata

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-02-28 |
| **Deciders** | nguyenhuuca |
| **Related PRD** | [PRD-hot-video-priority](../prd/PRD-hot-video-priority.md) |
| **Domain Tags** | data, api, infrastructure |
| **Supersedes** | N/A |
| **Superseded By** | N/A |

**Tech Strategy Alignment:**
- [x] Decision follows Golden Path in `.claude/rules/tech-strategy.md`

---

## Context

All videos are displayed in a flat, unordered list with no popularity or recency signal. Users must manually browse to find good content, reducing discovery and engagement.

**Existing infrastructure available for reuse:**

| Component | Status | Notes |
|-----------|--------|-------|
| `VideoSource` entity | ✅ Active | Active video table — no `upCount`/`downCount` fields |
| `VideoAccessStats` entity | ✅ Exists | Tracks `videoId`, `hitCount`, `lastAccessedAt` |
| `VideoAccessServiceImpl.recordAccess()` | ⚠️ Commented out | Needs activation |
| `ShareLink` table | ✅ Exists | Tracks share events per video |
| `VideoCacheImpl` | ✅ Exists | Already promotes videos with ≥5 cache hits |
| `AppScheduler` | ✅ Exists | Has scaffold for new scheduled jobs |

**Key constraint:** `VideoSource` has **no** `upCount`/`downCount` — the scoring formula can only use access hits, shares, and recency.

---

## Decision Drivers

- Hot score must not block the streaming response (`recordAccess` must be async)
- Backward compatible — existing `GET /api/videos` must not change without `sort` param
- Score weights must be configurable without redeploy
- Must reuse existing cache infrastructure (`StatsCacheImpl`, `VideoCacheImpl`)
- First-frame latency for hot videos should drop below 500ms via pre-warming

---

## Considered Options

### Option 1: Simple hit-count sort

Sort `video_sources` by total `hitCount` DESC.

| Pros | Cons |
|------|------|
| Trivial to implement — one `ORDER BY` | Biased toward old videos with accumulated hits |
| No new columns or scheduler needed | Ignores recency — a viral new video stays buried |
| | No way to tune or weight signals |

### Option 2: Weighted multi-signal score with recency decay ✅

Compute `hotScore = w1×hits + w2×shares + w3×recencyBoost` on a schedule, persist to DB, serve from cache.

| Pros | Cons |
|------|------|
| Balances hits, shares, and freshness | More implementation complexity |
| Configurable weights without redeploy | Scores stale up to 30 minutes |
| Reuses `StatsCacheImpl` and `VideoCacheImpl` | Requires Liquibase migration for 3 new columns |
| Transparent and auditable formula | |

### Option 3: External recommendation engine (e.g., Apache Mahout, ML model)

| Pros | Cons |
|------|------|
| High quality personalised ranking | Massive infrastructure overhead |
| Handles cold-start and edge cases well | Requires training data we don't have |
| | Out of scope for current team size |

---

## Decision Outcome

**Chosen Option:** Option 2 — Weighted multi-signal score with recency decay

**Rationale:** Option 1 is too simple — it permanently favours old content and ignores the viral nature of recent hits. Option 3 is overkill for current scale. Option 2 gives a transparent, tunable formula using only the signals available in `VideoSource` + `VideoAccessStats` + `ShareLink`, and reuses all existing cache infrastructure.

**Formula:**
```
hotScore = (0.60 × normalizedHits) + (0.25 × normalizedShares) + (0.15 × recencyBoost)
recencyBoost = e^(-0.1 × hoursAgo)
```

All inputs min-max normalised to `[0, 1]` across active videos. Weights externalised in `application.yaml` under `app.hot-video.*`.

**Storage:** Three new columns on `video_sources` via Liquibase migration:
```sql
hot_score  DECIMAL(5,4) DEFAULT 0
is_hot     BOOLEAN      DEFAULT FALSE
hot_rank   INT          DEFAULT NULL
```

**API surface:**
- New: `GET /api/videos/trending?limit=N`
- Extended: `GET /api/videos?sort=hot` (backward compatible)

**Scheduler:** `AppScheduler.recomputeHotScores()` every 30 minutes → update DB → refresh `StatsCacheImpl` → pre-warm `VideoCacheImpl` top 10.

### Quantified Impact

| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| First-frame latency (hot videos) | ~2s | < 500ms | Via `VideoCacheImpl` pre-warming |
| Trending API response time | N/A | < 100ms | Served from `StatsCacheImpl` |
| Score freshness | N/A | ≤ 30 min stale | Configurable TTL |

---

## Consequences

**Positive:**
- Trending content surfaces automatically — no manual curation needed
- Pre-warming reduces first-frame latency for popular videos
- Configurable weights allow formula tuning without code changes
- No breaking change to existing `GET /api/videos`

**Negative:**
- Scores are stale up to 30 minutes (acceptable for v1)
- Three additional DB columns + index on `video_sources`
- `video_access_stats` table grows over time — requires periodic cleanup

**Risks:**
- `recordAccess()` async write could become a bottleneck under high load → mitigate with `@Async` + catch-all exception handler; add write queue in v2 if needed
- `video_access_stats` unbounded growth → activate `cleanUpOldVideos()` in `AppScheduler`

---

## Validation

- [ ] `recordAccess()` does not add measurable latency to stream response
- [ ] `GET /api/videos` without `sort` param returns unchanged results
- [x] Tech Strategy alignment confirmed — uses Spring Scheduler, Guava cache, PostgreSQL (all Golden Path)
- [x] Related plan document created: [plan-hot-video-priority](../plans/plan-hot-video-priority.md)

---

## Links

- [PRD-hot-video-priority](../prd/PRD-hot-video-priority.md)
- [plan-hot-video-priority](../plans/plan-hot-video-priority.md)
- [ADR-0003 — Cache Strategy](0003-use-cache.md)
- [ADR-0005 — LRU Cache for Video Streaming](0005-use_lru_cache_video_streaming.md)

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-28 | nguyenhuuca | Initial draft |
| 2026-05-31 | nguyenhuuca | Restructured to new ADR template format |
