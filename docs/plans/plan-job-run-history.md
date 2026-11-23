# Plan: Job run history (`job_run`)

| Field | Value |
|-------|-------|
| Status | ✅ Done (deployed 325e865c, history modal d81ff63b; prod records runs 2026-11-18) |
| Date | 2026-11-18 |
| Related | [ADR-0023](../adr/0023-dynamic-cron-jobs.md) (dynamic jobs), [ADR-0039](../adr/0039-db-backup-google-drive.md) (DB backup) |

## Problem

`DynamicJobScheduler` keeps only the latest run of each job in memory (`lastRuns` map). A restart or redeploy wipes it, there is no history, and a run that is skipped because the previous one is still running leaves only a WARN log line. The owner cannot tell from Admin → Jobs whether last night's backup ran.

## Decisions (owner, 2026-11-18)

- Keep the **latest 100 runs per job**; older rows are deleted after each insert (count-based, not time-based).
- Each row: job name, trigger (`SCHEDULE` / `MANUAL`), result (`SUCCESS` / `FAILED` / `SKIPPED`), `started_at`, `finished_at`, `duration_ms`, error text (exception class + message, secrets masked, max 500 chars, no stack trace).
- UI: Admin → Jobs, each row expands to show its 20 latest runs. "Last run" in the list comes from the DB, so it survives restarts.
- No ADR: a new table following the existing Liquibase/JPA pattern, no new technology.

## Design

- `job_run` table (`BIGINT IDENTITY` id, FK `job_name → scheduled_job(name) ON DELETE CASCADE`, index `(job_name, started_at DESC)`).
- `DynamicJobScheduler.execute(name, task, trigger)` times the run and calls `JobRunService.record(...)` once at the end. A failed write is logged at WARN and never fails the job or the scheduler thread. A skipped run is recorded with `duration_ms = 0`.
- `JobRunService` (interface in `service/`, impl in `service/impl/`): `record`, `latestByJob()` (one `DISTINCT ON` query for the list, no N+1), `recent(name, limit)`.
- `JobScheduler.lastRun` and the in-memory `JobRun` record are removed.
- API: `GET /v1/funny-app/admin/jobs/{name}/runs?limit=20` (ADMIN, same as the list; `limit` clamped 1..100; 404 for an unknown job).
- A crash mid-run leaves no row (only one write at the end); the gap in the history shows it.

## Tasks

| ID | Task | Blocked by |
|----|------|-----------|
| JR-1 | Migration `202611180001-job-run.sql`, `JobRun` entity, `JobRunResult`/`JobTrigger` enums, `JobRunRepository` (+ migration test) | — |
| JR-2 | `JobRunService` + impl (record, prune to 100, error formatting/masking, latest, recent) + tests | JR-1 |
| JR-3 | `DynamicJobScheduler` records every run (SCHEDULE/MANUAL, SKIPPED); remove in-memory `lastRuns`; `ScheduledJobServiceImpl` reads last run from DB; `GET /{name}/runs` + DTO + contract test | JR-2 |
| JR-4 | Webapp: `getJobRuns` API, `useJobRuns` hook, expandable run history in `AdminJobsTab`, i18n en/vi, badge styles, tests | JR-3 |
| JR-5 | Verify (mvn verify, vitest, build), docs/tracking, push after owner approval, check on prod | JR-4 |
