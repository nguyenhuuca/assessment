# Plan: Backend Performance, Medium Items

<!--
Implementation Plan
Filename: docs/plans/plan-be-perf-medium.md
Owner: Builder (/builder)
-->

## Overview

**Status:** Done (2026-10-24, autopilot run `2026-10-24-be-perf-medium`)
**Date:** 2026-10-24
**Source:** `/swarm-review` performance pass of 2026-10-24 (backend only). Critical and High items already landed in runs `2026-10-24-stream-range-cap` and `2026-10-24-be-perf-high`.
**PRD / ADR / Spec:** none. These are fixes that follow existing patterns (Guava caches, Liquibase changesets, Spring config); no new architectural decision.
**Size:** Small. 7 tasks, backend only, about 140 LOC non-test.

## Objective

Remove repeated per-request work and needless DB round trips on the hot paths of a 1 vCPU / `-Xmx256m` VM, without changing any API contract.

## Scope

### In Scope

The 7 Medium items listed in the request (PM-1..PM-7 below).

### Out of Scope

- Paging the comment tree and the feed: both change the API and need frontend work.
- `StructuredTaskScope` on platform threads in the Drive sync, and an overall sync deadline.
- `remoteAddr` behind nginx for guest rate limits (follow-up from run `2026-10-24-be-perf-high`).
- Low items of the review.

## Technical Approach

Paths are under `api/src/main/java/com/canhlabs/funnyapp/` unless noted.

### Key facts (read by the orchestrator)

- **JWT:**
  - `filter/JwtProvider.java:119` `getKey()` runs `Keys.hmacShaKeyFor(...)` on every call.
  - It is used by `warmUp` (`:46-49`), `generateToken` (`:62-66`) and `verifyToken` (`:82-83`).
  - `verifyToken` also builds a new `JwtParser` per request.
  - The secret comes from `AppProperties`, which is setter-injected (`injectProps`) before `@PostConstruct`.
- **Permissions:**
  - `service/impl/PermissionServiceImpl.java` calls `isEnforced()` → `featureFlagService.isEnabled(PERMISSION_ENFORCEMENT, false)` on every check.
  - It logs at INFO on every path: `hasPermission`, `hasAllPermissions`, `hasAnyPermission` (about 9 `log.info` calls).
- **Feed:**
  - `service/impl/StreamVideoServiceImpl.java:80-86` `getVideosToStream()` loads every PUBLISHED, non-hidden `VideoSource` per request (`findAllByIsHideAndStatusOrderByPriorityDescCreatedAtDesc`). There is no cache.
  - The HLS flag value is read once per request.
  - Only `idx_video_sources_priority` exists for this table (changeset `20260913001`).
- **Things that change what the feed shows:**
  - `AdminVideoServiceImpl`: `updateStatus` `:83`, `updatePriority` `:91`, `updateHidden` `:100`, `deleteVideo` `:108`, `enableHls` `:122`, `disableHls` `:135`.
  - `VideoStorageServiceImpl.saveInfo` `:241` (scheduler download) and `upsertInfo` (admin force sync, called from `DriveSyncServiceImpl:202`).
  - `HlsTranscodeWorker` `markHlsReady` `:209`.
  - View counts change all the time. They may be up to the TTL stale, which is accepted.
- **Hikari:** `api/src/main/resources/application.yaml` `spring.datasource` (`:69-72`) sets only url, username and password, so the defaults apply: pool 10, `connection-timeout` 30 s.
- **Comments:**
  - `service/impl/VideoCommentServiceImpl.java:190` loads every reply (`repo.findByParentId(...)`, `repo/VideoCommentRepository.java:20`) only to test emptiness.
  - The index `idx_video_comments_parent_id` exists.
- **Watch history:**
  - `service/impl/WatchHistoryServiceImpl.java:113-115` `isIncognito` runs `userSettingsRepository.findById` on every `recordWatch` and `saveProgress`. The frontend sends progress every 30 s while playing.
  - `recordWatch` (`:33-40`) runs `trimToCap` whenever `upsertWatched` returns > 0. The upsert (`repo/WatchHistoryRepository.java:47-54`) returns 1 on both insert and conflict-update, so the trim runs on every view.
  - Settings are written in `UserSettingsServiceImpl.java:110` (update) and `:163` (create defaults). An account is deleted in `UserSettingsServiceImpl.deleteAccount:118` and `AdminAccountServiceImpl.deleteAccount:63`.
- **Hot-path INFO logs:** about 10 `log.info` calls on the Range path in `web/VideoStreamController.java`, `cache/VideoCacheImpl.java` and `service/impl/StreamVideoServiceImpl.java` (thread name, "Incoming stream request", "Streaming bytes", "Range header", cache HIT/MISS/CACHED, "Cache miss: fetching", "getTopVideos Thread").

### Design notes

- New small caches follow the existing Guava pattern: `cache/GuavaAppCache` / `AppCacheFactory` or a plain `CacheBuilder`, like `cache/CredentialsVersionCache`.
- No `synchronized` (tech strategy).
- The feed cache key is the HLS flag value (`true`/`false`). A flag flip therefore takes effect at once, without eviction.
- PostgreSQL `RETURNING (xmax = 0)` tells an insert from a conflict-update in a single statement. The app is PostgreSQL-only (tech strategy).

## Implementation Steps

| ID | Task | Files | Acceptance criteria | Est. LOC (main + tests) |
|----|------|-------|---------------------|-------------------------|
| **PM-1** | JWT key and parser built once | `filter/JwtProvider.java:40-125`; test `JwtProviderTest` | (1) `SecretKey` and `JwtParser` are built once and reused by `warmUp`, `generateToken` and `verifyToken`: held in `volatile` fields, created by an idempotent `ensureInit()` (no `synchronized`; a racing double build is harmless) that `warmUp` calls and every use calls again, so a swallowed warm-up failure cannot leave them null. (2) Behaviour is unchanged: valid, expired and invalid tokens give the same results and error codes (601/602). (3) Existing `JwtProviderTest` passes unchanged. A new test checks `getJwtSecretKey()` is read once across N verifications | ~12 + ~20 |
| **PM-2** | Cache the enforcement flag, quiet logs | `service/impl/PermissionServiceImpl.java`; test `PermissionServiceImplTest` | (1) `isEnforced()` caches the flag value for 15 s in a 1-entry Guava cache (`expireAfterWrite(15 s)`, `get(KEY, loader)`), not `Suppliers.memoizeWithExpiration` (keeps the codebase free of `synchronized` paths we own). (2) Every per-check `log.info` becomes `log.debug`. Keep an INFO or WARN only for a DENY if one exists today; otherwise all go to DEBUG. (3) Tests: N checks within 15 s → `featureFlagService.isEnabled` called once. The decision results are unchanged with the flag on and off | ~12 + ~25 |
| **PM-3** | Feed list cache + partial index | new `cache/FeedCache.java`; `service/impl/StreamVideoServiceImpl.java:80-86`; eviction calls in `service/impl/AdminVideoServiceImpl.java:83,91,100,108,122,135`, `service/impl/VideoStorageServiceImpl.java` (`saveInfo` `:241`, `upsertInfo`), `service/impl/HlsTranscodeWorker.java:209` (+ the `markHlsOutputMissing` call), `service/impl/StreamVideoServiceImpl.java:113` (`updateDesc`), `service/impl/DriveVideoDownloaderImpl.java:119` (`requeueHls`); new changeset `api/src/main/resources/db/changelog/sql/202610240002-video-sources-feed-index.sql`; tests | (1) `FeedCache`: Guava cache, key `Boolean hlsEnabled`, value immutable `List<VideoDto>`, `expireAfterWrite(30 s)`, max 2 entries. Methods `get(hlsEnabled, loader)` (single-flight) and `invalidate()`. (2) `getVideosToStream` reads the flag once, then returns `feedCache.get(flag, …)`. (3) **Eviction rule (plan-review [B]): invalidate whenever what the public feed returns can change** — the set of rows (status, is_hide, delete, new row), their order (priority) or a DTO field (title/desc, `hlsUrl` = flag && READY). Call `invalidate()` after the write returns, in: `AdminVideoServiceImpl` `updateStatus`, `updatePriority`, `updateHidden`, `deleteVideo` (after `deleteById` `:118`), `enableHls`/`disableHls` (end of method, covers the save at `:128`); `VideoStorageServiceImpl.saveInfo`/`upsertInfo`; `StreamVideoServiceImpl.updateDesc` (`:113`, after each save); `HlsTranscodeWorker` after `markHlsReady`==1 and after `markHlsOutputMissing` (READY → ERROR); `DriveVideoDownloaderImpl.requeueHls` (`:119`, READY → PENDING). **No eviction needed (documented):** `claimHls`, `markHlsError`, `resetHlsProcessing` (transitions between non-READY states keep `hlsUrl` null); `AppScheduler.regenerateThumbnails` (`:140`, the DTO has no thumbnail field, the poster is derived from `fileId`); view_count changes (accepted ≤ 30 s staleness). Loader exceptions are not cached (Guava `get(key, loader)` semantics) and the value is `List.copyOf(...)`. `invalidate()` runs after commit when a transaction is active (same `TransactionSynchronizationManager` helper as PM-6), otherwise immediately. (4) Changeset: `CREATE INDEX IF NOT EXISTS idx_video_sources_feed ON video_sources (priority DESC, created_at DESC) WHERE is_hide = false AND status = 'PUBLISHED';`. (5) Tests: two calls within the TTL → repository called once. After `invalidate()` → reloaded. Flag true vs false → separate entries. Each eviction site listed in (3) calls `invalidate()` (one test per class); a loader that throws is not cached (next call reloads); the cached value is unmodifiable. A migration-text test asserts the index definition | ~45 + ~70 |
| **PM-4** | Explicit Hikari limits | `api/src/main/resources/application.yaml:69-72` (`spring.datasource`); test `SqlLoggingProfileTest`-style yaml test | (1) `spring.datasource.hikari.maximum-pool-size: ${DB_POOL_SIZE:10}`, `connection-timeout: ${DB_CONNECTION_TIMEOUT_MS:5000}`, in the shared document. (2) A test binds the real yaml for the default, `dev` and `prod` profiles and asserts both keys and their defaults in each (no profile document overrides them) | ~3 + ~15 |
| **PM-5** | Reply existence check | `repo/VideoCommentRepository.java:20` (add `boolean existsByParentId(String parentId)`), `service/impl/VideoCommentServiceImpl.java:190`; test `VideoCommentServiceImplTest` | (1) Delete uses `existsByParentId`. (2) `findByParentId` stays if other callers use it (grep first), else it is removed. (3) Tests: the existing `VideoCommentServiceImplTest` cases that stub `findByParentId` (around `:236`, `:256`, `:307`) are rewritten to stub `existsByParentId`; own comment without replies → hard delete; with replies → soft-delete placeholder | ~3 + ~10 |
| **PM-6** | Watch history: incognito cache, trim on insert only | new `cache/IncognitoCache.java`; `service/impl/WatchHistoryServiceImpl.java:33-40,113-115`; `repo/WatchHistoryRepository.java:47-54`; eviction in `service/impl/UserSettingsServiceImpl.java:110,163,118` and `service/impl/AdminAccountServiceImpl.java:63`; tests | (1) `IncognitoCache`: Guava `userId → Boolean`, `expireAfterWrite(60 s)`, `maximumSize(10_000)`, with `get(userId, loader)` and `evict(userId)`. `isIncognito` uses it. Every settings save and account delete listed above evicts that user; inside an active transaction the eviction is registered with `TransactionSynchronizationManager` to run **after commit** (otherwise immediately), so a concurrent reload cannot re-cache the pre-commit value. (2) `upsertWatched` returns whether the row was **inserted**: native `INSERT … ON CONFLICT … DO UPDATE … RETURNING (xmax = 0)` as a non-`@Modifying` query returning `Boolean` / `Optional<Boolean>`. Null or empty means not public or nothing written. `recordWatch` calls `trimToCap` only when it was inserted. (3) Tests: N progress pings → one `findById`. A settings update evicts, so the next call reloads. Upsert insert → trim. Upsert update → no trim. Not public → no trim. A query-text test asserts `RETURNING (xmax = 0)` | ~35 + ~60 |
| **PM-7** | Hot-path logs to DEBUG | `web/VideoStreamController.java`, `cache/VideoCacheImpl.java`, `service/impl/StreamVideoServiceImpl.java` | (1) Per-request and per-chunk `log.info` / `log.warn` on the Range path (thread name, incoming request, range header, streaming bytes, cache HIT/MISS/CACHED, cache-miss fetch, getTopVideos thread) become `log.debug`. (2) Errors stay `error`/`warn`, and the body-error warn from run `2026-10-24-stream-range-cap` stays. (3) No behaviour change; existing tests pass | ~12 + ~0 |

### Dependency graph

All 7 tasks touch different code, except two overlaps:
- PM-3 and PM-7 both touch `StreamVideoServiceImpl`;
- PM-3 and PM-6 add new files to `cache/`.

They can be built in one worker in the order PM-1 → PM-7, with one commit per task. Every task can be merged on its own.

## Testing Strategy

- **Unit:** the tests listed per task (Mockito, as the existing tests do). Query and migration text are asserted by reading annotations or classpath SQL, like `VideoSourceHlsMigrationTest`.
- **Gates:** `mvn -f api/pom.xml verify` (coverage ≥ `api/.coverage-threshold`), `BeanConstructorTest` (new caches are injected into existing beans).
- **Manual (owner, after deploy):**
  1. Load the feed twice within 30 s. The second load runs no SQL; check DEBUG logs locally, or the DB stats.
  2. Hide a video in Admin. It disappears from the feed at the next load.
  3. Watch a video. History and resume still work.
  4. Toggle incognito in settings. History stops or resumes at once.
  5. Run `./api/mock-test.sh`.

## Rollback Plan

- `git revert` the task commits.
- The PM-3 index changeset is harmless to keep. To drop it, add a new changeset; never edit the old one.

## Risks

| Risk | Mitigation |
|------|------------|
| Feed shows stale data for up to 30 s after a change that does not evict (view counts, Drive sync paths missed) | Evict on every listed mutation. 30 s staleness is accepted for counts |
| Permission flag flip takes up to 15 s to apply | Accepted. The flag is an operational switch, not per request |
| `RETURNING (xmax = 0)` is PostgreSQL-specific | The project is PostgreSQL-only (tech strategy). A query-text test pins it |
| Incognito cache serves a stale value if a settings write path is missed | Evict on all writes listed (grep verified). TTL 60 s bounds the window |
| Index creation locks `video_sources` writes briefly on deploy | The table is small (about 60 rows). `CREATE INDEX` without `CONCURRENTLY` is fine at this size |

## Checklist

### Before Starting

- [x] Facts read and cited (Key facts)
- [x] TASKS.md section added

### Before PR

- [x] `mvn -f api/pom.xml verify` green, coverage ≥ threshold
- [x] `BeanConstructorTest` green

### Before Merge

- [x] Code review (quality + security + perf)

## Progress Log

| Date | Task | Commit | Note |
|------|------|--------|------|
| 2026-10-24 | — | — | Plan written for autopilot run `2026-10-24-be-perf-medium` |
| 2026-10-24 | — | — | Plan review round 1 revise: complete feed eviction rule, PM-5 test rewrite; 5 advisories folded |
| 2026-10-24 | — | — | Plan review round 2 approve; 2 advisories folded (files column, after-commit invalidate) |
| 2026-10-24 | PM-1..PM-7 | 9a02f760..441daa10 | Built by autopilot; code review approve (4 advisories open); QA ×2 329/329; mvn verify 1854 tests, line coverage 96.38%; 268 non-test LOC (estimate was ~140: three cache classes + wiring) |
