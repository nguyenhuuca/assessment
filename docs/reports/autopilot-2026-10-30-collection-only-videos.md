# Autopilot run: Collection-only videos (CO-1..CO-6)

- **Request:** plan mode, `docs/plans/plan-collection-only-videos.md`, build CO-1..CO-6; push the backend first, then the frontend
- **Mode:** local
- **Plan:** [plan-collection-only-videos](../plans/plan-collection-only-videos.md)
- **Started:** 2026-10-30 21:50 · **Landed:** 2026-10-30 (push 1 backend `02376098`, push 2 frontend `488e285f`)
- **Result:** ✅ pushed `02376098` (backend), then `488e285f` (frontend)

## Pipeline

| Stage | Result | Rounds |
|-------|--------|--------|
| Start | Owner request in chat (`/autopilot docs/plans/plan-collection-only-videos.md …`) | — |
| Plan | Reused (`plan | reused`); plan + spec edited by review rounds | — |
| Plan review | Cycle 1: revise ×3 (1 blocking each: feed-surfaces test, deep-link video in search, search index mapping) → round cap, owner chose one more review; cycle 2: approve, 0 blocking, 4 advisory (3 folded into CO-6) | 3 of 3 + 1 of 3 |
| Gate 1 (owner) | Cycle 1: revise ("review once more"); cycle 2: approved | — |
| Implement | 4 `worker-builder` runs in worktrees: CO-1; CO-4+CO-5; CO-6 (parallel), then CO-2+CO-3; 2 commits, 49 files, +785/−179 (non-test +315/−97) | 1 of 3 |
| Code review | Frontend: approve, 0 blocking, 3 advisory; backend: approve, 0 blocking, 3 advisory | 1 of 3 |
| Local QA ×2 | `qa-twice.sh`: 380 tests, pass 380 · flaky 0 · fail 0 | 1 of 3 |
| Fix | Frontend advisory A2 fixed (inserted deep-link slide deduplicated when the feed refetches), regression test fails without it; A1 not reproducible on React Query 5, kept as a guard test | — |
| Land: full gates | `mvn verify` exit 0 (coverage gate ≥ 0.90 passed); `npm run test` 78 files / 680 tests; `npm run lint` clean | — |
| Gate 2 (owner) | Push 1 approved after the owner's prod `pg_dump` backup; push 2 approved after push 1 was verified live | — |
| CI + smoke | Push 1: Funny App Build and Deploy 4/4 jobs success (Trivy skipped as usual); `mock-test.sh` 22/22; prod feed 59 videos, `GET /video-stream/{id}` 200, health 200, so the migration applied and the app booted. Push 2: Deploy Frontend (see tracking) | — |

## What changed

- **Schema** (`api/src/main/resources/db/changelog/sql/202610300001-video-visibility.sql`): the first Liquibase formatted-SQL file in the repo. It adds `video_sources.visibility` (backfilled from `is_hide`), recreates `idx_video_sources_feed` on `visibility = 'FEED'`, drops `is_hide`, adds `video_import_jobs.visibility` and a `drive_file_id` index, and carries `--rollback` lines.
- **Feed** lists `FEED` only (`StreamVideoServiceImpl` `findAllByVisibilityAndStatus…`). Collections, watch history, `GET /video-stream/{id}`, views, reactions and HLS accept anything but `HIDDEN`. `VisibilityQueryPredicatesTest` guards the native SQL predicates, because the repo has no DB test harness.
- **Admin** `PATCH /admin/videos/{id}/visibility` takes `{visibility}` and still accepts the legacy `{hidden}`. Bad bodies get 400. `AdminVideoDto` has `visibility` plus a derived `hidden`.
- **Import:** `visibility` is `FEED` (default) or `COLLECTION_ONLY`; `HIDDEN` or an unknown value gets 400/4104 `INVALID_VISIBILITY`. `saveInfo` and `upsertInfo` copy the latest job's value for the Drive file; an existing row keeps its own.
- **Admin UI:** a Feed / Collection only / Hidden select (the tooltip says collection-only is not private), a badge, an Add to collection row action, an Import "Collection only" checkbox and a badge in the import table.
- **Deep link** (`webapp/src/components/video/VideoFeed.jsx`): a video that is not in the feed is fetched by id and inserted as slide 0. Search keeps the feed list and maps by id (`VideoSwiper.jsx`). The link now resolves once in every outcome; before, a missing id was never resolved.

## Runs

| # | cycle | stage | round | path | result | blocking | advisory | turns | min | tokens | model |
|---|-------|-------|-------|------|--------|----------|----------|-------|-----|--------|-------|
| 1 | 1 | plan | — | — | reused | — | — | — | — | — | — |
| 2 | 1 | plan review | 1 | fallback | revise | 1 | 5 | 12 | 1.6 | 74,749 | sonnet |
| 3 | 1 | plan review | 2 | fallback | revise | 1 | 1 | 8 | 0.7 | 43,285 | sonnet |
| 4 | 1 | plan review | 3 | fallback | revise (cap) | 1 | 0 | 10 | 0.8 | 38,888 | sonnet |
| 5 | 1 | gate1 | — | — | revise (owner: review once more) | — | — | — | — | — | — |
| 6 | 2 | plan review | 1 | fallback | approve | 0 | 4 | 18 | 1.5 | 55,914 | sonnet |
| 7 | 2 | gate1 | — | — | approved | — | — | — | — | — | — |
| 8 | 2 | implement CO-1 | 1 | fallback | done | — | — | 42 | 8.3 | 73,773 | sonnet |
| 9 | 2 | implement CO-4+CO-5 | 1 | fallback | done | — | — | 23 | 6.0 | 76,846 | sonnet |
| 10 | 2 | implement CO-6 | 1 | fallback | done | — | — | 26 | 6.6 | 64,331 | sonnet |
| 11 | 2 | implement CO-2+CO-3 | 1 | fallback | done | — | — | 40 | 11.7 | 123,735 | sonnet |
| 12 | 2 | code review FE | 1 | fallback | approve | 0 | 3 | 7 | 0.9 | 49,341 | sonnet |
| 13 | 2 | code review BE | 1 | fallback | approve | 0 | 3 | 16 | 1.9 | 68,605 | sonnet |
| 14 | 2 | QA ×2 | 1 | — | pass 380 | — | — | — | — | — | — |

Stage path: every review and build ran as a `worker-*` fallback (the agent read the skill's SKILL.md), so that worker transcripts stay out of the orchestrator context.

## Open advisories

- [A] `webapp/src/components/video/VideoSwiper.jsx:216`: the slide counter shows N+1 while an inserted deep-link slide exists. This is a known limitation in the plan.
- [A] `api/src/main/java/com/canhlabs/funnyapp/service/impl/VideoStorageServiceImpl.java:6`: two new imports are out of alphabetical order.
- [A] The migration and rollback are checked only as file text. The owner skipped CO-1 AC 9, the local Postgres run (2026-10-30).

## Manual checks for the owner

- **Before push 1:** take a `pg_dump` of prod. It is the only way back if the `--rollback` statements fail.
- **Between push 1 and push 2:** make no visibility edits on the Admin page. The old page sends `{hidden:false}`, which resets a `COLLECTION_ONLY` row to `FEED` (EC-1).
- **After push 2, prod smoke (CO-7 AC 4):**
  - A video set to Collection only leaves the feed within 30 s, and still plays in a collection and through `?v=`.
  - An import with Collection only never shows in the feed.
  - Hidden still hides the video everywhere.

## Ignored instructions

- none

## Cost

API list-price equivalent, not a bill (`node scripts/claude-cost.mjs --from 2026-10-30T14:50:40Z --md`, session window up to the land stage).

| job | turns | cost | models | run |
|-----|-------|------|--------|-----|
| main session (orchestrator) | 55 | $5.13 | claude-opus-5-5 | local |
| 6 plan/code reviewers | 47 | $1.33 | claude-sonnet-5-5 | local |
| 4 builders | 94 | $2.21 | claude-sonnet-5-5 | local |
| **TOTAL** | **196** | **$8.67** | | |
