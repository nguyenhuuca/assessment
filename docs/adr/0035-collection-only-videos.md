# ADR-0035: Video Visibility Enum (FEED, COLLECTION_ONLY, HIDDEN) Replaces is_hide

## Metadata

**Status:** Accepted (2026-10-30) · **Date:** 2026-10-30 · **Deciders:** nguyenhuuca · **Tags:** api, database, frontend
**Related PRD:** [PRD-collection-only-videos](../prd/PRD-collection-only-videos.md) · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ✅ Follows Golden Path (JPA/Hibernate, Liquibase, Guava `FeedCache`, explicit admin controller). No new dependency.

---

## Context

- **The owner wants research videos out of the feed but playable from collections** (PRD Problem Statement). Today a video has one visibility switch, `video_sources.is_hide`:
  - `false`: in the feed, search and collections;
  - `true`: invisible everywhere, collections included (`VideoCollectionItemRepository.VISIBLE`).
- **`is_hide` is read in many places.** About 20 references in 10 non-test files, backend and frontend:
  - the feed query `findAllByIsHideAndStatusOrderByPriorityDescCreatedAtDesc` (`StreamVideoServiceImpl.java:87-93`);
  - `getVideoById`, the collection and reaction checks `existsByIdAndIsHideAndStatus`;
  - `incrementViewCount`, `WatchHistoryRepository`, the HLS playlist check;
  - `PATCH /admin/videos/{id}/visibility` with body `{hidden}`, `AdminVideoDto.hidden` and the Admin videos table.
- **The feed is served from `FeedCache`** (Guava, 30 s TTL). Admin changes that affect the feed call `feedCache.invalidate()` (priority, HLS).
- **The feed has a partial index** `idx_video_sources_feed` with `WHERE is_hide = false AND status = 'PUBLISHED'` (`202610240002-video-sources-feed-index.sql`).
- **Imported videos get their row later, in two places.** The import worker only uploads the file to Drive and stores `drive_file_id` on the job. The `video_sources` row is created afterwards:
  - by the Drive scheduler, through `VideoStorageServiceImpl.saveInfo` (builder + `save`, default `is_hide = false`);
  - or by a force sync, through `upsertInfo` → `VideoSourceRepository.upsertDriveSource`, which INSERTs with `is_hide = false` and, on `ON CONFLICT`, keeps the moderation columns (is_hide, status, priority).
- **The PRD requires** (FR-13..FR-16) the setting to come from the Import form and be written at INSERT time, so a collection-only video never appears in the feed.
- **Deployment.** The backend is deployed by copying the JAR to the VPS and restarting it (`funnyapp-ci.yml` deploy job). Old and new backend never run side by side, and **the owner accepts a short downtime** (2026-10-30). Liquibase runs on startup of the new JAR.
- **The frontend deploys separately** (`deploy-web.yml`, triggered by `webapp/**`). For a few minutes the old frontend can talk to the new backend, or the reverse.

The decision: how to model "collection-only" next to "hidden".

---

## Decision Drivers

- **Invalid states should not be representable:** a video is in exactly one visibility state.
- **Hidden must keep meaning "gone everywhere"**, with no regression in the places that read visibility today.
- **Downtime is acceptable** (single VPS, owner-approved). There is no need for a rolling-safe expand/contract.
- **Room to grow:** a future state (for example private videos) should be one more value, not one more column.
- **The feed query stays index-backed** on the 1 vCPU VM.
- **The setting can be chosen at import time** and stored on the import job.
- **The admin UI stays clear:** one control, three states.

---

## Considered Options

### Option 1: New boolean column `collection_only`, `is_hide` unchanged

- Add `video_sources.collection_only BOOLEAN NOT NULL DEFAULT false`. Only the feed query adds `AND collection_only = false`.
- Precedence: `is_hide = true` wins.
- New endpoint `PATCH /admin/videos/{id}/collection-only`.

| Pros | Cons |
|------|------|
| Smallest change: only the feed query and admin code | Two booleans give four combinations; "hidden + collection-only" is meaningless and needs a precedence rule |
| Additive column, safe even for a rolling deploy | Every future visibility query must remember both columns |
| | A future state means a third column |
| | Two admin toggles for one concept |

### Option 2: Replace `is_hide` with a `visibility` enum (`FEED`, `COLLECTION_ONLY`, `HIDDEN`)

- A `visibility VARCHAR NOT NULL DEFAULT 'FEED'` column (Java `VideoVisibility` enum, `@Enumerated(STRING)`), backfilled from `is_hide`: `true` becomes `HIDDEN`, `false` becomes `FEED`. `is_hide` is then dropped in the same changeset.
- Readers switch to:
  - feed: `visibility = 'FEED'`;
  - everywhere a video may play: `visibility <> 'HIDDEN'`.
- `PATCH /admin/videos/{id}/visibility` takes `{visibility}`.

| Pros | Cons |
|------|------|
| One value per video: invalid states cannot exist | Touches about 20 references in 10 files, plus their tests |
| The feed rule and the playable rule each read one column | Not rolling-deploy safe; acceptable here because of the owner-approved downtime |
| A future state is one more enum value | Changes the `/visibility` request body and the admin DTO, so it needs a compatibility window with the separately deployed frontend |
| One admin control with three states | The partial index and the `ON CONFLICT` upsert must be rewritten |

### Option 3: Option 2 in two releases (expand/contract)

- Add `visibility`, dual-write both columns, switch reads, then drop `is_hide` in a second release.

| Pros | Cons |
|------|------|
| Zero downtime | Two releases and temporary dual-write code for a single-VPS app that accepts downtime |

---

## Decision Outcome

**Chosen Option:** Option 2, a `visibility` enum (`FEED`, `COLLECTION_ONLY`, `HIDDEN`) that replaces `is_hide` in one release with a short downtime.

**Rationale:**
- **It is the correct model.** Visibility is a single property with three mutually exclusive values (invalid-state driver). There is no precedence rule to remember, and a fourth state later is one more value (room-to-grow driver).
- **The main cost of Option 2 is the rolling deploy, and it does not apply.** The backend is a single JAR on a VPS, and the owner accepts downtime (downtime driver). One Liquibase changeset adds the column, backfills it from `is_hide`, recreates the index and drops `is_hide`.
- **The remaining cost is touching ~20 references.** That is mechanical and fully covered by existing tests that already assert hidden videos are excluded. Each of those tests is updated to the enum.
- **Option 1 was the cheaper change, but it was rejected.** It encodes one concept in two columns forever.
- **Option 3 was rejected.** It buys zero downtime nobody needs.

**Compatibility window with the frontend.** Backend and frontend deploy separately. For one release the backend therefore:
- also accepts the legacy body `{hidden: true|false}` on `PATCH /admin/videos/{id}/visibility`, mapping it to `HIDDEN|FEED` and rejecting a body that has both fields;
- keeps a derived `hidden` field (`visibility == HIDDEN`) in `AdminVideoDto` next to the new `visibility`.

A follow-up removes both once the new frontend is live. The exact mechanics belong in the plan.

**Import jobs** store a `visibility` limited to `FEED` or `COLLECTION_ONLY` (default `FEED`). When `saveInfo` or `upsertInfo` creates the `video_sources` row, it copies the visibility of the job found by `drive_file_id`, or uses `FEED` when there is no job (plain Drive sync). On `ON CONFLICT` the existing visibility is kept, like the other moderation columns today. Approved suggestions create `FEED` jobs.

### Quantified Impact

| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| Columns describing visibility | 1 (`is_hide`) | 1 (`visibility`) | Option 1 would make it 2 |
| References changed | — | ~20 in 10 files + tests | Mechanical rename of the predicate |
| Releases needed | — | 1 backend + 1 frontend, then a small cleanup | Option 3 would need 2 backend releases with dual-write |
| Downtime | — | Backend restart + migration, expected under a minute | Owner-approved; `video_sources` is small |
| Feed query | Partial index scan | Partial index scan | Index predicate becomes `visibility = 'FEED' AND status = 'PUBLISHED'`; verified with `EXPLAIN` in the plan |
| Time for a change to reach the feed | — | Next request (cache invalidated), ≤ 30 s worst case | Same as priority and HLS changes |

---

## Consequences

**Positive:**
- One column, one meaning.
  - The feed reads `visibility = 'FEED'`.
  - Collections, watch history, direct links, views, reactions and HLS read `visibility <> 'HIDDEN'`, so collection-only videos keep working there.
- Feed search, the Explore hero and the Library "Add videos" picker exclude collection-only videos automatically, because they all read the feed list.
- Import jobs carry the visibility, so a collection-only import is never in the feed, not even between INSERT and an admin change.
- The Admin videos table gets one three-state control instead of a hidden checkbox plus a second toggle.

**Negative:**
- About 20 references and their tests change in one go. Review must check that every former `is_hide = false` becomes `visibility <> 'HIDDEN'`, except the feed, which becomes `visibility = 'FEED'`.
- `is_hide` is dropped. Rollback to the previous JAR needs a reverse migration (Liquibase rollback block) or a DB restore, not just redeploying the old JAR.
- There is a temporary compatibility code path (legacy `{hidden}` body, derived `hidden` DTO field) until the cleanup follow-up.
- The client must handle a video that exists but is not in the feed list (PRD FR-9 deep-link fallback).

**Risks:**
- **A former `is_hide = false` predicate is mapped to `visibility = 'FEED'` by mistake,** and collection-only videos disappear from collections or history. Mitigation:
  - tests per surface (collections, history, `GET /video-stream/{id}`, reactions, views, HLS) using a `COLLECTION_ONLY` fixture;
  - a grep check that no `is_hide`/`isHide` remains.
- **The old frontend sends `{hidden}` after the backend deploys.** Mitigation: the legacy body is accepted for one release, as described above.
- **A bad migration on prod.** Mitigation:
  - take a DB backup before deploy;
  - the changeset has a `rollback` block that recreates `is_hide` from `visibility`;
  - test the migration on a prod-like dump first.
- **The owner reads collection-only as private.** Mitigation: the PRD Out of Scope states it, and the admin control has a tooltip that says so.

---

## Validation

- [ ] Migration test: `is_hide = true` becomes `HIDDEN` and `false` becomes `FEED`; `is_hide` is gone; the rollback block restores it
- [ ] Feed excludes `COLLECTION_ONLY` and `HIDDEN`
- [ ] Collections, watch history, `GET /video-stream/{id}`, reactions, views and HLS accept `COLLECTION_ONLY` and reject `HIDDEN`
- [ ] Import with `COLLECTION_ONLY` creates the row with that value on first INSERT; re-import keeps the existing value
- [ ] `PATCH /admin/videos/{id}/visibility` accepts `{visibility}` and the legacy `{hidden}`, and rejects both together
- [ ] No `is_hide` / `isHide` reference remains in `api/src/main` or `webapp/src` (except the migration and the legacy `hidden` compatibility code)
- [ ] `EXPLAIN` of the feed query uses the recreated partial index
- [ ] Security review: `/visibility` keeps its role guard and `@AuditLog`
- [ ] Related plan document created: `docs/plans/plan-collection-only-videos.md`

---

## Links

- [Related PRD](../prd/PRD-collection-only-videos.md)
- [Implementation Plan](../plans/plan-collection-only-videos.md) (to be written)
- [ADR-0019: video import worker](./0019-admin-video-import.md)
- [ADR-0031: opt-in HLS streaming](./0031-hls-streaming.md) (`FeedCache` invalidation pattern)

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-10-30 | nguyenhuuca | Initial draft (boolean `collection_only`) |
| 2026-10-30 | nguyenhuuca | Switched to the visibility enum: single-VPS deploy, owner accepts downtime |
