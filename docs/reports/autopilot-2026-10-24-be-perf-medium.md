# Autopilot run: Backend performance, Medium items

- **Request:** `/autopilot fix BE perf Medium items from the 2026-10-24 review: (1) JWT key/parser once … (7) hot-path logs to DEBUG`
- **Mode:** local (request mode, full: estimated above the 100-LOC lite limit)
- **Plan:** [plan-be-perf-medium](../plans/plan-be-perf-medium.md)
- **Started:** 2026-10-24 16:44 · **Landed:** 2026-10-24 17:10
- **Result:** ✅ committed `9a02f760`..`441daa10` (Gate 2 pending at report time)

## Pipeline

| Stage | Result | Rounds |
|-------|--------|--------|
| Start | owner request in chat (`/autopilot <request>`), from the `/swarm-review` performance pass of 2026-10-24 | — |
| Plan | `docs/plans/plan-be-perf-medium.md`, 7 tasks PM-1..PM-7, `TASKS.md` section | — |
| Plan review | Round 1 revise: 2 blocking. The feed-cache eviction list missed writers (`updateDesc`, `requeueHls`, HLS READY transitions), and PM-5 contradicted existing tests. 5 advisory. Round 2 approve: 0 blocking, 2 advisory, folded in | 2 of 3 |
| Gate 1 (owner) | approved | — |
| Implement | 1 worker-builder in a worktree, 8 commits cherry-picked onto `main`. 38 files +750/−64 (non-test 20 files +268/−44) | 1 of 3 |
| Code review | Correctness, security and performance: approve, 0 blocking, 4 advisory | 1 of 3 |
| Local QA ×2 | `qa-twice.sh`, 18 changed test classes + `BeanConstructorTest`: 329 tests, pass 329 · flaky 0 · fail 0 | 1 of 3 |
| Fix | none needed | — |
| Land: full gates | `mvn verify`: 1854 tests, 0 failures, 2 skipped, line coverage 96.38% (threshold 90%). Frontend unchanged | — |
| Gate 2 (owner) | pending | — |
| CI + smoke | pending (after push) | — |

## What changed

- **PM-1 JWT** (`filter/JwtProvider.java`): the `SecretKey` and `JwtParser` are built once in `volatile` fields by an idempotent `ensureInit()`, and reused for warm-up, signing and verification. The 601/602 error mapping is unchanged.
- **PM-2 Permissions** (`service/impl/PermissionServiceImpl.java`): the `permission_enforcement_enabled` flag is cached for 15 s in a 1-entry Guava cache. If loading fails, it falls back to a direct lookup. Per-check logs are now DEBUG; DENY logs stay at INFO. `invalidateEnforcementCache()` is public for two controller permission tests in another package.
- **PM-3 Feed** (`cache/FeedCache.java`, `cache/AfterCommit.java`, `service/impl/StreamVideoServiceImpl.java`):
  - `GET /video-stream/list` is served from a 30 s cache keyed by the HLS flag value.
  - The cache is invalidated after commit wherever the public feed can change: the admin status, priority, hidden, delete and HLS toggles; a new row from Drive download or force sync; `updateDesc`; HLS READY in and out (`markHlsReady`, `markHlsOutputMissing`, `requeueHls`).
  - New partial index `idx_video_sources_feed` (`202610240002-video-sources-feed-index.sql`).
- **PM-4 Hikari** (`application.yaml`): `maximum-pool-size ${DB_POOL_SIZE:10}`, `connection-timeout ${DB_CONNECTION_TIMEOUT_MS:5000}`, checked for the default, dev and prod profiles.
- **PM-5 Comments**: deleting a comment uses `existsByParentId` instead of loading every reply. The old `findByParentId` is removed.
- **PM-6 Watch history**:
  - `IncognitoCache` holds each user's incognito flag for 60 s and is evicted after commit on every `user_settings` write and account delete.
  - `upsertWatched` returns `RETURNING (xmax = 0)`, so `trimToCap` runs only when a new history row is inserted.
- **PM-7 Logs**: the per-request and per-chunk Range-path logs are now DEBUG.

## Runs

| # | cycle | stage | round | path | result | blocking | advisory | turns | min | tokens | model |
|---|-------|-------|-------|------|--------|----------|----------|-------|-----|--------|-------|
| 1 | 1 | plan | 1 | orchestrator | plan written, 7 tasks | — | — | — | 8 | — | opus |
| 2 | 1 | plan review | 1 | fallback (worker-reviewer) | revise | 2 | 5 | — | 2 | — | sonnet |
| 3 | 1 | plan (revise) | 2 | orchestrator | eviction rule + PM-5 tests; advisories folded | — | — | — | 2 | — | opus |
| 4 | 1 | plan review | 2 | fallback (worker-reviewer) | approve | 0 | 2 | — | 1 | — | sonnet |
| 5 | 1 | gate 1 | — | owner | approved | — | — | — | — | — | — |
| 6 | 1 | implement PM-1..PM-7 | 1 | fallback (worker-builder, worktree) | mvn test green 1854 | — | — | 72 | 14 | 145,764 | sonnet |
| 7 | 1 | code review | 1 | fallback (worker-reviewer, correctness+security+perf) | approve | 0 | 4 | 9 | 2 | 68,162 | sonnet |
| 8 | 1 | local QA ×2 | 1 | `qa-twice.sh` | pass 329 | — | — | — | 5 | — | — |
| 9 | 1 | land: full gates | — | orchestrator | green | — | — | — | 5 | — | opus |

## Open advisories

- **PM-6 query not run against PostgreSQL in tests.** `RETURNING (xmax = 0)` is checked by annotation reflection only. A smoke test after deploy is needed (see Manual checks).
- **Incognito lag.** After-commit eviction can be undone by a reader that loaded the old value just before the commit, so an incognito change can take up to 60 s to apply (privacy). Accepted: a shorter TTL would defeat the cache, because progress pings come every 30 s.
- **`PermissionServiceImpl.invalidateEnforcementCache()`** is public only for tests. Keep it, and document it as test-only.
- **`FeedCache` shares `VideoDto` instances across requests.** Callers must treat them as read-only.
- **Size.** The non-test diff is 268 LOC against an estimate of about 140: three cache classes plus their wiring, and the `ExecutionException` unwrapping is duplicated in two caches. There is no dead code.

## Manual checks for the owner

1. **Watch history (PM-6, required):**
   - Watch a video for more than 3 s while logged in. It appears at the top of History, and resume works.
   - Watch it again: it stays one entry and moves to the top.
   - Turn incognito on in Settings, watch another video, and within about a minute check that it is not recorded.
2. **Feed (PM-3):**
   - Hide a video in Admin and reload the feed: it is gone.
   - Unhide it: it is back.
   - Change a priority: the order changes.
3. **Permissions (PM-2):** admin pages still load; a USER still gets 403 on admin APIs.
4. **Logs (PM-7):** prod log has no per-chunk "Streaming bytes" or "CACHE HIT/MISS" lines.
5. Run `./api/mock-test.sh` after the deploy.
- **Rollback note:** `git revert` the commits. The index changeset can stay; drop it with a new changeset if needed.

## Ignored instructions

- None (local run).

## Cost

API list-price equivalent, not a bill. Window 2026-10-24T09:44:01Z → 10:08:42Z.

| who | model | requests | input | cache write | cache read | output | cost |
|---|---|---|---|---|---|---|---|
| main session (orchestrator) | claude-opus-5-5 | 17 | 42 | 38,561 | 10,741,643 | 20,470 | $2.87 |
| worker-builder: Implement PM-1..PM-7 | claude-sonnet-5-5 | 63 | 126 | 143,257 | 5,686,215 | 45,779 | $1.38 |
| worker-reviewer: Code review perf Medium diff | claude-sonnet-5-5 | 6 | 12 | 66,171 | 220,430 | 5,669 | $0.24 |
| worker-reviewer: Plan review perf Medium | claude-sonnet-5-5 | 11 | 26 | 62,024 | 463,990 | 10,458 | $0.31 |
| **TOTAL** |  | **97** | **206** | **310,013** | **17,112,278** | **82,376** | **$4.80** |

| job | turns | cost | models | run |
|-----|-------|------|--------|-----|
| local | 97 requests | $4.80 | opus 5.5 + sonnet 5.5 | 2026-10-24-be-perf-medium |
