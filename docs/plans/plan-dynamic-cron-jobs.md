# Plan: Dynamic Cron Job Management

<!--
Implementation Plan
Filename: docs/plans/plan-dynamic-cron-jobs.md
Owner: Builder (/swarm-execute) — run by /autopilot
Handoff to: Builder
-->

## Overview

**Status:** Planned 2026-10-19 (autopilot)
**Author:** nguyenhuuca
**Date:** 2026-10-19
**ADR:** [ADR-0023](../adr/0023-dynamic-cron-jobs.md). The ADR is needed because this adds a new API pattern and a new table (D1–D6 live there; this plan does not repeat them).
**Size:** Small (1–2 days). There is no PRD, because there is a single admin-only capability with no user-facing product decision.

## Objective

Move the six job schedules in `AppScheduler` from hardcoded `@Scheduled` annotations (`AppScheduler.java:69, 79, 91, 107, 117, 127`) to the `scheduled_job` table. An admin can then list jobs, and a SUPER_ADMIN can change cron, zone and on/off, or run a job now, from a new Admin tab, all without a deploy.

## Facts the tasks rely on

- **Jobs and current schedules** (`AppScheduler.java`):

  | Job | Line | Schedule | Zone |
  |-----|------|----------|------|
  | `scheduleMakePoem` | `:69` | `0 45 10 * * *` | VN |
  | `logStats` | `:79` | `fixedRate` 5 min | — |
  | `syncDriveVideos` | `:91` | `0 */15 * * * *` | — |
  | `notificationDigest` | `:107` | `0 */15 * * * *` | — |
  | `notificationRetention` | `:117` | `0 30 3 * * *` | VN |
  | `cleanUpOldVideos` | `:127` | `0 0 3 30 2 *` | — |

  `cleanUpOldVideos` never fires, and its body is a no-op (`:130-132`). The wrong log is at `:71` and the wrong comment at `:91`.
- **Existing test:** `AppSchedulerTest.java` asserts the `@Scheduled` cron of the notification jobs (`:326-334`). These assertions move to the migration/registry tests, because the annotations are removed.
- **Scheduling setup:** `@EnableScheduling` is at `FunnyApp.java:17`. There is no custom `TaskScheduler` bean, and virtual threads are on (`application.yaml:46`).
- **Liquibase:**
  - `db.changelog-master.yaml:1-4` uses `includeAll sql/`, ordered by filename, so no master edit is needed.
  - Migrations are plain SQL.
  - Table + seed example: `sql/202605010001-init_permissions.sql:4-24`.
  - Latest file: `sql/202610190001-suggestion-rereview-count.sql`.
- **Entity style:**
  - `entity/VideoSuggestion.java:23-29` uses Lombok `@Getter @Setter @NoArgsConstructor`.
  - `@UpdateTimestamp Instant` is used in `entity/BaseDomain.java:20-26`.
- **Admin controller pattern:**
  - `web/AdminDriveSyncController.java:30-56`: class-level `hasRole('ADMIN')`; reads use `@HasPermission(perm = Permission.ADMIN)`.
  - Mutations use `@PreAuthorize("hasRole('SUPER_ADMIN') and @permissionServiceImpl.hasPermission(authentication.details.permissions, ADMIN)")` + `@AuditLog` + `@RateLimited` (`:53-57`).
  - Errors use `CustomException.of(HttpStatus, subCode, key)` (`:47`), with keys in `i18n/messages.properties` and `messages_vi.properties`.
- **Security config:** the `/v1/funny-app/admin/**` role guard is at `WebSecurityConfig.java:54`. PATCH is already in CORS (`:139`), so no change is needed.
- **Error mapping:** in `RestExceptionHandler.java`, `CustomException` maps to its own status (`:225-235`).
- **Test style:**
  - Plain Mockito: `web/AdminControllerTest.java:42-46`.
  - Security slice: `@WebMvcTest` in `web/AdminControllerPermissionTest.java:81`.
- **Webapp:**
  - `components/admin/AdminView.jsx`: `TABS` at `:13`, `TAB_QUERY` at `:16-24`, content switch at `:113-121`.
  - `api/admin.js` wraps `api/client.js` (`api.get/post/patch`, `:42-48`).
  - Hooks pattern: `hooks/useAdmin.js:28-34` (mutation + `invalidateQueries`).
  - `utils/roles.js:5` provides `isSuperAdmin`.
  - i18n: `i18n/locales/{en,vi}/admin.json` (`tabs` at `en/admin.json:14-22`).
  - Tests: `components/admin/__tests__/AdminView.test.jsx:8-25` and `api/__tests__/admin.test.js:5`.

## Tasks

| ID | Task | Acceptance criteria | Depends on |
|----|------|---------------------|------------|
| **DJ-1** | Migration `sql/202610190002-create-scheduled-job.sql` (table + 6 seed rows per ADR D1), entity `ScheduledJob`, `ScheduledJobRepository` | Columns and types match ADR D1. The seed holds 6 rows whose cron/zone equal today's annotations; `log-stats` is `0 */5 * * * *` and `cleanup-old-videos` has `enabled=false`. Every seeded cron passes `CronExpression.isValidExpression` (a unit test reads the SQL file from the classpath and checks each seeded cron), and every seeded name exists in `JobRegistry`. No `ddl-auto` change | — |
| **DJ-2** | `jobs/JobRegistry` (name constants → `AppScheduler` method refs) + `jobs/DynamicJobScheduler` (ADR D3); remove `@Scheduled` from the 6 methods; drop the wrong log text at `:71` and the comment at `:91` | Tests (Mockito, mocked `TaskScheduler`): (a) startup schedules only enabled rows that have code, and WARNs for unknown rows and for code without a row; (b) `reschedule` cancels the old future before scheduling a new one, and a disabled job ends with no future; (c) a run while one is in progress is skipped (`AtomicBoolean` guard), and the guard is released after an exception; (d) an exception in a job is caught and recorded as `lastResult=FAILED`; (e) if loading rows at startup throws, or returns no rows, an ERROR is logged and the app keeps starting. No `synchronized` (grep). `AppSchedulerTest` keeps all body tests; only the annotation asserts `:326-334` are removed | DJ-1 |
| **DJ-3** | `service/ScheduledJobService` + impl, DTOs `ScheduledJobDto` / `UpdateScheduledJobRequest`, `web/AdminJobController` (GET list, PATCH `{name}`, POST `{name}/runs`, ADR D4); i18n error keys en + vi | Service tests: an invalid cron gives 400 `error.job.invalidCron`; a seconds field other than one number (e.g. `*/5 * * * * *`) gives 400 `error.job.tooFrequent`; a bad zone gives 400 `error.job.invalidZone`; an unknown name gives 404 `error.job.notFound`; a valid PATCH saves first, then calls `reschedule` (if `reschedule` throws, the transaction rolls back, so the DB and the live schedule stay the same); PATCH semantics are as follows:
  - only fields that are sent change;
  - `zone: ""` clears it to NULL;
  - an empty body gives 400 `error.job.emptyUpdate`;
  - `cron` longer than 64 characters gives 400 `error.job.invalidCron`.

  Run-now goes through `TaskScheduler.schedule(task, Instant.now())` and returns 202 when started. It works on a disabled job (a manual run), and returns 409 `error.job.running` when the guard is held; `nextRunAt` is null when disabled. `@WebMvcTest` permission test: an ADMIN gets 200 on GET and 403 on PATCH/POST, and a SUPER_ADMIN gets 200/202. `mvn verify` is green with coverage ≥ `api/.coverage-threshold` | DJ-2 |
| **DJ-4** | FE `api/admin.js` (`getJobs`, `updateJob`, `runJob`) + hooks `useAdminJobs`, `useUpdateJob`, `useRunJob` (key `['admin','jobs']`, mutations invalidate it) | `admin.test.js`: correct URL/method/body for each call. The hook test shows a mutation success invalidates `['admin','jobs']` | DJ-3 (API contract) |
| **DJ-5** | FE `components/admin/AdminJobsTab.jsx` + `SCHEDULED_JOBS` in `TABS`/`TAB_QUERY`/switch of `AdminView.jsx` + `tabs.SCHEDULED_JOBS` and `jobs.*` keys in en/vi `admin.json` | Tests (vi + en): the table shows name, description, cron, zone, enabled, next run, last run/result. ADMIN sees read-only (no input or buttons). SUPER_ADMIN can toggle enabled, edit cron (save → `updateJob` with the new value) and click "Run now" (button disabled while running/pending). A server error key is shown translated. `npm run test` and `npm run lint` are green | DJ-4 |

**Landing:** add the feature to `docs/tracking.md` (it has an ADR and a plan).

**Commits:** BE (DJ-1..3) and FE (DJ-4..5) are 2 commits, each independently mergeable. The FE tab only calls endpoints that exist after the BE commit. Estimate: about 350 LOC BE prod + 300 test, and about 200 LOC FE prod + 150 test.

## Risks

| Risk | Mitigation |
|------|------------|
| Rolling deploy: a new pod reads the table before Liquibase has run | Liquibase runs at startup, before `ApplicationReadyEvent`, in the same pod. An old pod keeps its `@Scheduled` until it stops (the same short double-run exposure as today, ADR D5) |
| A `CronTrigger` with an impossible date (`30 Feb`) returns no next run | The job is seeded disabled. `nextRunAt` = null is shown as "—". This is tested in DJ-3 |
| `logStats` timing shifts from `fixedRate` to a clock-aligned cron | It is a log-only job, so there is no business effect (ADR D1) |
| A minimum of once per minute still allows `0 * * * * *` on `sync-drive-videos` | Accepted: SUPER_ADMIN only, and the overlap guard skips a run while the previous one is still going. A per-job floor can come later if needed |
| An admin sets a valid but wrong time | SUPER_ADMIN only, `@AuditLog`, and the UI shows the new `nextRunAt` after save |

## Manual checks for the owner (after deploy)

- In Admin → Scheduled jobs on prod, all 6 jobs are listed with the expected next run, and `cleanup-old-videos` is disabled.
- Disable `log-stats` and confirm the 5-minute log line stops. Then enable it again.
