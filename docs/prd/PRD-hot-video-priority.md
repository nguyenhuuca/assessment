# PRD: Hot Video Priority & Trending Feed

<!--
Product Requirements Document
Filename: docs/prd/PRD-hot-video-priority.md
Owner: nguyenhuuca
Handoff to: Architect (/architect), UI/UX Designer (/ui-ux-designer)
Related Skills: writing-prds, decomposing-tasks, requirements-analysis
-->

## Overview

**Status:** Approved
**Author:** nguyenhuuca
**Date:** 2026-02-28
**Version:** 1.0
**Beads Issue:** N/A
**PR-FAQ:** N/A
**Stakeholders:** Backend Team, Frontend Team, Product

---

## Problem Statement

All videos are displayed in a flat, unordered list. Users have no signal about which videos are trending or popular — leading to poor content discovery and lower engagement. Users who want to watch something good must manually browse without any curation.

### Evidence

**Quantitative Evidence:**
- No trending signal exists — 100% of content discovery is manual browsing
- `VideoAccessStats` table exists and tracks `hitCount` + `lastAccessedAt`, but `recordAccess()` is **commented out** — zero access data is being written
- `ShareLink` table tracks share events but is not used for ranking
- `VideoCacheImpl` already promotes videos with ≥5 cache hits — evidence that popularity signals are valuable

**Qualitative Evidence:**
- Users who want to watch "something good" have no starting point other than scrolling
- Hot content buried in a flat list reduces the chance of viral spread
- Pre-caching popular videos is already partially designed in `VideoCacheImpl` — the infrastructure anticipates this need

---

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Improve content discovery | Average session duration | +15% |
| Increase video consumption | Videos watched per session | +20% |
| Reduce hot video latency | Time-to-first-frame for hot videos | < 500ms (from ~2s via pre-cache) |
| Drive trending engagement | User click-through on hot badges | > 30% |

---

## User Stories

### Viewer

- As a **viewer**, I want to see a "Trending Now" section at the top of the home page so that I can quickly find popular content without browsing.
  - Acceptance: A "🔥 Trending Now" section shows top 5 hot videos above the main feed.
- As a **viewer**, I want hot videos to be visually marked so that I know at a glance which content is currently popular.
  - Acceptance: Video cards with `isHot = true` display a 🔥 badge.
- As a **viewer**, I want hot videos to start playing faster so that I don't wait when choosing trending content.
  - Acceptance: Top 10 hot videos are pre-warmed in cache; first-frame latency < 500ms.

### Content Viewer (API consumer)

- As an **API consumer**, I want a dedicated `/api/videos/trending` endpoint so that I can build trending feeds in any client.
  - Acceptance: `GET /api/videos/trending?limit=N` returns top N videos sorted by `hotScore DESC`.
- As an **API consumer**, I want to sort the main video list by hotness so that I can offer multiple sort options without a separate endpoint.
  - Acceptance: `GET /api/videos?sort=hot` returns all videos sorted by `hotScore DESC`; existing calls without `sort` are unchanged.

---

## Requirements

### Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | Activate `recordAccess()` — async upsert of `VideoAccessStats` on every stream | Must Have | Must not block stream response |
| FR-2 | Implement hot score formula: weighted hits (60%) + shares (25%) + recency decay (15%) | Must Have | Min-max normalised across active videos |
| FR-3 | `GET /api/videos/trending?limit=N` endpoint (default 10, max 50) | Must Have | Served from `StatsCacheImpl`, TTL 30 min |
| FR-4 | Extend `GET /api/videos?sort=hot` — backward compatible | Must Have | No change to calls without `sort` param |
| FR-5 | Scheduled job: recompute hot scores every 30 minutes | Must Have | Runs in `AppScheduler` |
| FR-6 | Pre-warm `VideoCacheImpl` for top 10 hot videos after each recomputation | Should Have | Goal: first-frame < 500ms |
| FR-7 | Frontend: "🔥 Trending Now" section at top of home page (top 5 videos) | Must Have | |
| FR-8 | Frontend: "🔥 Hot" badge on video cards where `isHot = true` | Must Have | Show when hotScore ≥ top 20% threshold |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | `recordAccess()` latency overhead | < 5ms (async, fire-and-forget) |
| NFR-2 | Trending API response time | < 100ms (served from cache) |
| NFR-3 | Score recomputation time | < 10s for 10,000 videos |
| NFR-4 | Additional memory overhead | < 50MB |
| NFR-5 | Backward compatibility | `GET /api/videos` without `sort` param — unchanged behaviour |
| NFR-6 | Resilience | DB write failure in `recordAccess()` must not fail the stream request |

---

## Scope

### In Scope

- Activate `VideoAccessServiceImpl.recordAccess()` (upsert `VideoAccessStats`)
- Hot score algorithm with configurable weights (`app.hot-video.*` in `application.yaml`)
- New `GET /api/videos/trending` endpoint
- `sort=hot` parameter on existing `GET /api/videos`
- Liquibase migration: `hot_score`, `is_hot`, `hot_rank` columns on `video_sources`
- Scheduled recomputation job every 30 minutes
- Cache pre-warming for top 10 hot videos
- Frontend: Trending Now section + 🔥 badge on video cards

### Out of Scope

- Personalised recommendations (per-user hot scores) — v2
- Real-time score updates via WebSocket — v2
- A/B testing framework for ranking algorithms — v2
- Machine learning-based scoring — v3
- Like/dislike counts (not present on `VideoSource` entity)

---

## Dependencies

| Dependency | Owner | Status | Risk |
|------------|-------|--------|------|
| `VideoAccessStats` entity + repo | Backend | ✅ Exists | Low — just needs `recordAccess()` uncommented |
| `ShareLink` table | Backend | ✅ Exists | Low — read-only query for share count |
| `StatsCacheImpl` | Backend | ✅ Exists | Low — already used for stats |
| `VideoCacheImpl` | Backend | ✅ Exists | Low — `preWarm()` needs to be called |
| `AppScheduler` | Backend | ✅ Exists | Low — add new `@Scheduled` method |
| Liquibase migration | Backend | ❌ Missing | Medium — must land before entity changes |
| `VideoSource` entity | Backend | ✅ Active table | Low — add 3 columns |

---

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| `recordAccess()` creates DB bottleneck under high load | Medium | High | Keep `@Async` + catch all exceptions; add write queue in v2 if needed |
| Hot score biased toward old high-hit videos | Medium | Medium | Apply 24h rolling window + exponential recency decay (`e^(-0.1 × hoursAgo)`) |
| `video_access_stats` table grows unbounded | Medium | Medium | Activate `cleanUpOldVideos()` in `AppScheduler` to purge rows older than window |
| Scores stale up to 30 minutes after new viral video | Low | Low | TTL is configurable; acceptable for v1 |
| Cache invalidation on new video added | Low | Low | TTL-based invalidation (30 min) sufficient for v1 |

---

## Open Questions

- [ ] Should `/api/videos/trending` be a public endpoint (no auth required)?  — **Owner:** Backend — **Due:** Before FR-3 implementation
- [ ] What threshold defines `isHot` — top 20% by score or a fixed score cutoff? — **Owner:** Product — **Due:** Before FR-2 implementation
- [ ] Should shares from private share-links count toward the hot score? — **Owner:** Backend — **Due:** Before FR-2 implementation
- [ ] Do we need a `hot_score_history` table for analytics / score trending over time? — **Owner:** Data — **Due:** v2 planning

---

## Appendix

### Data Model

```sql
-- Liquibase migration: 202602280001-add-hot-score.sql
ALTER TABLE video_sources ADD COLUMN hot_score  DECIMAL(5,4) DEFAULT 0;
ALTER TABLE video_sources ADD COLUMN is_hot     BOOLEAN      DEFAULT FALSE;
ALTER TABLE video_sources ADD COLUMN hot_rank   INT          DEFAULT NULL;

CREATE INDEX idx_video_sources_hot_score
    ON video_sources(hot_score DESC) WHERE is_hot = TRUE;

CREATE INDEX IF NOT EXISTS idx_video_access_stats_video_id
    ON video_access_stats(video_id);
```

### Hot Score Formula

```
hotScore = (0.60 × normalizedHits) + (0.25 × normalizedShares) + (0.15 × recencyBoost)

recencyBoost = e^(-0.1 × hoursAgo)
```

All inputs min-max normalised to `[0, 1]` across active videos.

### API Contract

**`GET /api/videos/trending`**
```
Query params: limit (int, default=10, max=50)
Response: ResultListInfo<VideoDto>
  data[].videoId     String
  data[].title       String
  data[].embedLink   String
  data[].hotScore    Double
  data[].isHot       Boolean
  data[].rank        Integer  (1 = hottest)
```

**`GET /api/videos?sort=hot`**
```
Same VideoDto structure, ordered by hotScore DESC
```

### New Components

```
service/
├── HotVideoService.java
└── impl/HotVideoServiceImpl.java

jobs/
└── AppScheduler.java  ← add recomputeHotScores()
```

### Research

- `VideoAccessStats` entity: `api/src/main/java/com/canhlabs/funnyapp/entity/VideoAccessStats.java`
- `VideoSource` entity: `api/src/main/java/com/canhlabs/funnyapp/entity/VideoSource.java`
- `VideoCacheImpl`: `api/src/main/java/com/canhlabs/funnyapp/cache/`

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | 2026-02-28 | Approved |
| Engineering | nguyenhuuca | 2026-02-28 | Approved |
| Design | N/A | N/A | N/A |

---

## Next Steps & Handoffs

After PRD approval:

1. [x] **Architect Review**: Technical feasibility assessment
   - Output: [ADR-0011](../adr/0011-hot-video-priority.md)

2. [ ] **UI/UX Designer**: Wireframes for Trending Now section + Hot badge
   - Trigger: `/ui-ux-designer`
   - Output: Design Spec (`artifacts/design_spec_hot-video.md`)

3. [x] **Engineering Estimate**: Effort estimation and decomposition
   - Output: [plan-hot-video-priority](../plans/plan-hot-video-priority.md)

4. [ ] **Create Beads Issues**: Decompose into trackable work items
   - Command: `bd create "Hot Video Priority" -t feature`

**Related Artifacts:**
- ADR: [ADR-0011 — Hot Video Priority Scoring](../adr/0011-hot-video-priority.md)
- Implementation Plan: [plan-hot-video-priority](../plans/plan-hot-video-priority.md)
- Cache Strategy: [ADR-0003](../adr/0003-use-cache.md)
- LRU Cache: [ADR-0005](../adr/0005-use_lru_cache_video_streaming.md)

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-28 | nguyenhuuca | Initial draft |
| 1.1 | 2026-05-31 | nguyenhuuca | Restructured to official PRD template; removed non-existent `upCount` field; fixed entity name `YouTubeVideo` → `VideoSource`; fixed doc paths |
