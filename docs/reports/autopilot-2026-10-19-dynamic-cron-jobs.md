# Autopilot run: Dynamic cron job management

<!--
Every autopilot run report uses this template, local and headless alike.
File: docs/reports/autopilot-<YYYY-MM-DD>-<slug>.md (same <slug> as the plan).
Keep every section. When a value cannot be known yet, write why, e.g. "n/a in CI: runs after merge";
never delete a row or a section.
Headless: the plan job creates this file on the PR branch and fills the plan rows; the build job reads it
back and fills the rest. The workflow appends the rows under "## Cost" itself; do not edit them.
-->

- **Request:** feature "dynamic cron job management (Mức 2)": DB-backed schedules + admin API + Admin UI tab
- **Mode:** local
- **Plan:** [plan-dynamic-cron-jobs](../plans/plan-dynamic-cron-jobs.md) · [ADR-0023](../adr/0023-dynamic-cron-jobs.md)
- **Started:** 2026-10-19 21:03 · **Landed:** pending (Gate 2)
- **Result:** pending Gate 2: committed `985ba5b5` BE + `e7b53b4f` FE, not pushed

## Pipeline

| Stage | Result | Rounds |
|-------|--------|--------|
| Start | owner request in chat (`/autopilot`), scope guard: Small feature + ADR | — |
| Plan | plan + ADR-0023 + TASKS section (DJ-1..5); 3 worker-explorers; path skill | 1 of 3 |
| Plan review | approve, 0 blocking, 7 advisory (6 folded into the plan before Gate 1, 1 accepted as a risk) | 1 of 3 |
| Gate 1 (owner) | approved | — |
| Implement | 2 worker-builders in parallel worktrees (BE `590bfaa1`, FE `6c5c937c`), cherry-picked uncommitted; 26 files | 1 of 3 |
| Code review | quality: approve, 0 blocking, 3 advisory · security: approve, 0 blocking, 3 advisory | 1 of 3 |
| Local QA ×2 | `qa-twice.sh`: 84 tests, pass 84 · flaky 0 · fail 0 | 1 of 3 |
| Fix | advisories folded in during QA: hook test for `['admin','jobs']` invalidation (DJ-4 AC), zone length check, `nextRunAt` = null for a corrupt row instead of a 500 | — |
| Land: full gates | `mvn verify`: 1425 tests, 0 fail, LINE 96.82% (≥ 90%) · `npm run test` 481/481 · `npm run lint` 0 errors (3 old warnings in untouched files) | — |
| Gate 2 (owner) | pending | — |
| CI + smoke | pending: runs after push | — |

## What changed

- New table `scheduled_job` with 6 seed rows (`api/src/main/resources/db/changelog/sql/202610190002-create-scheduled-job.sql`). `log-stats` moves to `0 */5 * * * *`. `cleanup-old-videos` is seeded disabled, as before (30 Feb never fired).
- `AppScheduler` no longer has `@Scheduled`. `JobRegistry` maps names to its methods, and `DynamicJobScheduler` schedules them with `TaskScheduler` + `CronTrigger`, a per-job `AtomicBoolean` overlap guard, and `try/catch` per run. The wrong "10:10" log and "20 minutes" comment are gone.
- Admin API `/v1/funny-app/admin/jobs` (`web/AdminJobController.java`):
  - GET is for ADMIN.
  - PATCH `{name}` and POST `{name}/runs` need SUPER_ADMIN + the ADMIN permission, with `@AuditLog` and `@RateLimited`.
  - Validation: cron must have 6 fields, the seconds field must be a single number (at most once per minute), max 64 characters; zone must be valid and at most 64 characters; an unknown name returns 404 and a running job returns 409.
- Webapp: a new Admin tab "Scheduled jobs" (`components/admin/AdminJobsTab.jsx`), in en and vi. It is read-only for ADMIN. SUPER_ADMIN can toggle a job, edit its cron and use "Run now". It polls every 5 s while a job is running.

## Runs

Same columns and rules as `.claude/skills/autopilot/resources/ledger.template.md`, without `≈$` (cost is below).

| # | cycle | stage | round | path | result | blocking | advisory | turns | min | tokens | model |
|---|-------|-------|-------|------|--------|----------|----------|-------|-----|--------|-------|
| 1 | 1 | plan | 1 | skill | plan + ADR written | — | — | 59 | 1.6 | 156545 | haiku ×3 + opus |
| 2 | 1 | plan review | 1 | skill | approve | 0 | 7 | 9 | 1.0 | 44192 | sonnet |
| 3 | 1 | gate 1 | — | — | approved | — | — | — | — | — | — |
| 4 | 1 | implement | 1 | skill | BE + FE committed in worktrees, cherry-picked | — | — | 64 | 12.5 | 166135 | sonnet ×2 |
| 5 | 1 | code review | 1 | skill | approve (quality + security) | 0 | 6 | 23 | 2.8 | 98388 | sonnet ×2 |
| 6 | 1 | QA ×2 | 1 | orchestrator | pass 84 | — | — | — | 2 | — | opus |
| 7 | 1 | land: gates + commit | — | orchestrator | green; `985ba5b5`, `e7b53b4f` | — | — | — | 5 | — | opus |

## Open advisories

- [A] `DynamicJobScheduler.java:91`: `runNow` returns 202 before the guard flag is set. The refetch right after can still show `running=false`, so polling may not start and "last run" stays stale until the next refresh. This is cosmetic.
- [A] A "run now" on a disabled job is allowed by design (manual run), and a run that loses the race is still answered 202 and then skipped. Both are documented in the plan (DJ-3).
- [A] The once-per-minute minimum still allows `0 * * * * *` on `sync-drive-videos`. This is accepted: SUPER_ADMIN only, plus the overlap guard (plan Risks).

## Manual checks for the owner

- In Admin → Scheduled jobs on prod, all 6 jobs are listed with the expected next run, and `cleanup-old-videos` is disabled.
- Disable `log-stats` and check that the 5-minute log line stops. Then enable it again.

## Ignored instructions

- none

## Cost

API list-price equivalent, not a bill. Window 2026-10-19T14:03:30Z → 14:29:30Z, before the docs commit.

| who | model | requests | input | cache write | cache read | output | cost |
|---|---|---|---|---|---|---|---|
| main session (orchestrator) | claude-opus-5-5 | 47 | 114 | 85,772 | 5,935,710 | 32,848 | $2.53 |
| worker-builder: Implement BE DJ-1..DJ-3 | claude-sonnet-5-5 | 27 | 54 | 98,843 | 1,677,094 | 788 | $0.42 |
| worker-explorer: Explore Liquibase and entity patterns | claude-haiku-5-5 | 5 | 10 | 41,303 | 126,614 | 2,568 | $0.01 |
| worker-reviewer: Code review quality round 1 | claude-sonnet-5-5 | 5 | 12 | 54,761 | 165,953 | 1,725 | $0.17 |
| worker-reviewer: Code review security round 1 | claude-sonnet-5-5 | 6 | 14 | 41,157 | 174,090 | 3,457 | $0.15 |
| worker-explorer: Explore webapp admin UI patterns | claude-haiku-5-5 | 7 | 14 | 49,037 | 220,442 | 2,518 | $0.01 |
| worker-explorer: Explore backend admin patterns | claude-haiku-5-5 | 8 | 16 | 58,622 | 312,076 | 3,848 | $0.01 |
| worker-builder: Implement FE DJ-4..DJ-5 | claude-sonnet-5-5 | 16 | 32 | 64,842 | 789,300 | 702 | $0.25 |
| worker-reviewer: Plan review dynamic cron jobs | claude-sonnet-5-5 | 6 | 14 | 42,889 | 174,061 | 3,062 | $0.16 |
| **TOTAL** |  | **127** | **280** | **537,226** | **9,575,340** | **51,516** | **$3.71** |
