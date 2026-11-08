# Autopilot run: Split AdminController by admin area

- **Request:** Split `AdminController` following `docs/plans/plan-admin-controller-split.md` (AC-1 to AC-6). Plan mode: the plan already existed.
- **Mode:** local
- **Plan:** [plan-admin-controller-split](../plans/plan-admin-controller-split.md)
- **Started:** 2026-11-08 11:51 · **Landed:** 2026-11-08 12:12, local commits; push pending Gate 2
- **Result:** committed locally (`7c30894e`, `41906488`, `0fbd3d0e`, `0db76628`, plus the closing docs commit). Push waits for Gate 2.

## Pipeline

| Stage | Result | Rounds |
|-------|--------|--------|
| Start | Owner request in chat (`/autopilot`) | — |
| Plan | Reused (`9b0d586f`). The orchestrator revised it after review (`e9e0e533`) | — |
| Plan review | Round 1 `revise`: 2 blocking (route count 19, not 20; renaming the permission test would break 2 `@Import`s), 4 advisory. Round 2 `approve`: 0 blocking, 1 advisory (fixed). `swarm-review` ran as one `worker-reviewer` | 2 of 3 |
| Gate 1 (owner) | approved | — |
| Implement | `worker-builder` in a worktree. It wrote one incremental patch per task, and the orchestrator committed each as its own commit. 9 files; main code +219/−154, tests +357/−150 | 1 of 3 |
| Code review | `approve`, 0 blocking, 1 advisory. The reviewer compared line multisets: all 19 handlers were moved byte for byte | 1 of 3 |
| Local QA ×2 | `qa-twice.sh` on 10 classes: 123 tests, pass 123 · flaky 0 · fail 0. Mutation check: loosening `PUT /videos/{id}/hls` to `hasRole('ADMIN')` and dropping `@HasPermission` on `/stats` makes `AdminRouteContractTest` fail. The file was restored and the test passes again | 1 of 3 |
| Fix | not needed | — |
| Land: full gates | `mvn verify`: green, line coverage 96.52% (threshold 90%). Frontend not run: side untouched | — |
| Gate 2 (owner) | pending | — |
| CI + smoke | pending: runs after the push | — |

## What changed

- `api/src/main/java/com/canhlabs/funnyapp/web/AdminController.java` is split by area, with no URL or security change. The original class is renamed to `AdminVideoController` and keeps 10 endpoints, including `/stats`. The 5 account endpoints move to `AdminAccountController` and the 4 comment endpoints to `AdminCommentController`.
- Direct fan-out, measured with jdeps as in the 2026-11-07 report: 31 becomes 16, 14 and 13.
- New guard `AdminRouteContractTest`: 19 rows of (HTTP method, path, effective `@PreAuthorize`, `@HasPermission`, `@AuditLog`, `@RateLimited`). It also fails on duplicate routes.
- The tests are split by area: 26 = 17 video + 7 account + 2 comment. `AdminControllerPermissionTest` keeps its name and now loads the 3 controllers. `HasPermissionAnnotationTest` points at `AdminVideoController.getStats`.
- The 2026-11-07 report records finding 3 as done.

## Runs

| # | cycle | stage | round | path | result | blocking | advisory | turns | min | tokens | model |
|---|-------|-------|-------|------|--------|----------|----------|-------|-----|--------|-------|
| 1 | 1 | plan | — | — | reused | — | — | — | — | — | — |
| 2 | 1 | plan review | 1 | skill → worker-reviewer | revise | 2 | 4 | 9 | 0.5 | 45k | sonnet |
| 3 | 1 | plan fix | 2 | orchestrator | 19 routes; no rename; AC-1 algorithm | — | — | — | — | — | opus |
| 4 | 1 | plan review | 2 | skill → worker-reviewer | approve | 0 | 1 | 3 | 0.1 | 50k | sonnet |
| 5 | 1 | gate1 | — | — | approved | — | — | — | — | — | — |
| 6 | 1 | implement | 1 | fallback worker-builder (worktree) | AC-1..AC-4, verify green | — | — | 46 | 9.7 | 114k | sonnet |
| 7 | 1 | code review | 1 | skill → worker-reviewer | approve | 0 | 1 | 6 | 0.6 | 35k | sonnet |
| 8 | 1 | QA ×2 | 1 | qa-twice.sh | pass 123/123 ×2 | — | — | — | — | — | — |
| 9 | 1 | QA mutation | 1 | orchestrator | contract test catches loosened security | — | — | — | — | — | opus |
| 10 | 1 | land | — | orchestrator | verify green, 96.52% | — | — | — | — | — | opus |

## Open advisories

- `webapp/src/components/admin/AdminAccountTable.jsx:39`: a comment still names `AdminController`. Left as is, outside `api/`.
- Historical docs (`docs/sequence/*`, `docs/specs/spec-collection-only-videos.md:328`, `docs/adr/0036-admin-bulk-actions.md:14`) keep the old name on purpose.

## Manual checks for the owner

- The local boot check (`./startLocal.sh`) was not run, because it needs the database from `api/.env` and this run did not check which database that is. Instead, the permission test's `@WebMvcTest` loads all 3 controllers together, so an ambiguous mapping would fail there. After the deploy, the prod health check and `mock-test.sh` cover boot.
- After the deploy, open the admin Vault, Accounts and Comments tabs. Expect 200s and no 404 or 405.

## Ignored instructions

- none

## Cost

API list-price equivalent, not a bill. Window 2026-11-08T04:51:01Z → 05:12:25Z.

| who | model | requests | cache read | output | cost |
|---|---|---|---|---|---|
| main session (orchestrator) | claude-opus-5-5 | 36 | 4,551,089 | 20,928 | $1.77 |
| worker-reviewer: plan review | claude-sonnet-5-5 | 6 | 186,014 | 4,706 | $0.19 |
| worker-reviewer: code review | claude-sonnet-5-5 | 5 | 107,055 | 3,659 | $0.13 |
| worker-builder: implement | claude-sonnet-5-5 | 28 | 2,032,414 | 27,691 | $0.76 |
| **TOTAL** |  | **75** | **6,876,572** | **56,984** | **$2.85** |
