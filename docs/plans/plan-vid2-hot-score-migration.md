# Plan: VID-2 — DB Migration: add hot_score, is_hot, hot_rank to video_sources

**Jira:** [VID-2](https://canh-labs.atlassian.net/browse/VID-2)
**Epic:** VID-34 Trending Videos
**Size:** Small (< 1 day)
**Decision type:** Two-Way Door (columns with defaults, reversible via DROP COLUMN)
**Depends on:** nothing
**Blocks:** VID-4 (HotVideoService), VID-1 (recordAccess)

---

## Exploration Findings

| Finding | Detail |
|---------|--------|
| Active video table | `video_sources` (not `youtube_video`) |
| Current entity fields | id, videoId, sourceType, sourceId, sourceUrl, credentialsRef, title, desc, isHide, thumbnailPath, status |
| Mapper: VideoSource → VideoDto | `StreamVideoServiceImpl.toDto()` line 108 |
| Mapper: VideoSource → AdminVideoDto | `AdminVideoServiceImpl.toDto()` line 68 — NOT in scope |
| Migration style | Pure SQL, alphabetical ordering, naming: `YYYYMMDDNNNN-description.sql` |
| BigDecimal usage | None yet in codebase — new import needed |
| `video_access_stats.video_id` index | Does not yet exist — add it here |

---

## Implementation Steps

### Step 1 — Liquibase migration

**File:** `api/src/main/resources/db/changelog/sql/202605160001-add-hot-score-to-video-sources.sql`

```sql
ALTER TABLE video_sources
    ADD COLUMN IF NOT EXISTS hot_score DECIMAL(5,4) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS is_hot    BOOLEAN      DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS hot_rank  INT          DEFAULT NULL;

CREATE INDEX IF NOT EXISTS idx_video_sources_hot_score
    ON video_sources(hot_score DESC) WHERE is_hot = TRUE;

CREATE INDEX IF NOT EXISTS idx_video_access_stats_video_id
    ON video_access_stats(video_id);
```

### Step 2 — Update `VideoSource.java` entity

**File:** `api/src/main/java/com/canhlabs/funnyapp/entity/VideoSource.java`

Add after `thumbnailPath` field:

```java
@Column(name = "hot_score", precision = 5, scale = 4)
private BigDecimal hotScore = BigDecimal.ZERO;

@Column(name = "is_hot", nullable = false)
private boolean isHot = false;

@Column(name = "hot_rank")
private Integer hotRank;
```

Add import: `java.math.BigDecimal`

### Step 3 — Update `VideoDto.java`

**File:** `api/src/main/java/com/canhlabs/funnyapp/dto/VideoDto.java`

Add fields:

```java
private BigDecimal hotScore;
private boolean isHot;
private Integer rank;
```

Add import: `java.math.BigDecimal`

### Step 4 — Update `StreamVideoServiceImpl.toDto()` mapper

**File:** `api/src/main/java/com/canhlabs/funnyapp/service/impl/StreamVideoServiceImpl.java` line 108

Map the new fields so `VideoDto` is populated correctly:

```java
dto.setHotScore(source.getHotScore());
dto.setIsHot(source.isHot());
dto.setRank(source.getHotRank());
```

> `AdminVideoServiceImpl.toDto()` maps to `AdminVideoDto` (not `VideoDto`) — out of scope.

---

## Acceptance Criteria

- [ ] Migration file `202605160001-add-hot-score-to-video-sources.sql` exists and runs cleanly
- [ ] Migration is idempotent (`IF NOT EXISTS` guards on columns and indexes)
- [ ] `VideoSource.java` compiles with `hotScore`, `isHot`, `hotRank`
- [ ] `VideoDto.java` compiles with `hotScore`, `isHot`, `rank`
- [ ] `StreamVideoServiceImpl.toDto()` maps new fields
- [ ] `./unittest.sh` passes — no regressions
- [ ] Existing queries on `video_sources` unaffected (new columns are nullable/defaulted)

---

## Out of Scope

- Hot score computation logic (VID-4)
- Trending API endpoints (VID-5, VID-6)
- Scheduler for recomputing scores (VID-7)
- Frontend changes (VID-8, VID-9)
- `AdminVideoDto` / `AdminVideoServiceImpl` — uses separate DTO

---

## Handoff

Ready for `/swarm-execute` or direct implementation. All 4 files are known:

| # | File | Action |
|---|------|--------|
| 1 | `db/changelog/sql/202605160001-add-hot-score-to-video-sources.sql` | Create |
| 2 | `entity/VideoSource.java` | Edit — add 3 fields |
| 3 | `dto/VideoDto.java` | Edit — add 3 fields |
| 4 | `service/impl/StreamVideoServiceImpl.java` | Edit — update toDto() |
