# ADR-0023: Dynamic Cron Jobs (DB-backed schedule, admin API)

## Metadata

**Status:** Proposed (2026-10-19) · **Date:** 2026-10-19 · **Deciders:** nguyenhuuca · **Tags:** scheduling, admin, api-contract, data-model
**Related:** [plan-dynamic-cron-jobs](../plans/plan-dynamic-cron-jobs.md)

## Context

- Every recurring job lives in `jobs/AppScheduler.java` with a hardcoded `@Scheduled` (`AppScheduler.java:69, 79, 91, 107, 117, 127`). Changing a schedule, or turning a job off, needs a code change and a deploy.
- Two annotations are already wrong against their own comments: the log at `:71` says 10:10 but the cron is 10:45, and the comment at `:91` says 20 minutes but the cron is every 15. `cleanUpOldVideos` (`:127`) uses `0 0 3 30 2 *` (30 February), so it never fires, and its body is a no-op (`:130-132`).
- `@EnableScheduling` is at `FunnyApp.java:17`. No custom `TaskScheduler` or `SchedulingConfigurer` exists, and virtual threads are on (`application.yaml:46`), so Spring Boot's auto-configured `TaskScheduler` bean is the one to use.
- Prod runs **one replica** (HPA 1-1, `tech-strategy.md` Infrastructure) on a 1 vCPU VM.
- `tech-strategy.md` forbids `synchronized` and does not list Quartz or ShedLock.
- Admin pattern to follow: `AdminDriveSyncController.java:30-56`. ADMIN reads, and only SUPER_ADMIN plus the ADMIN permission may mutate (`:55`), with `@AuditLog` and `@RateLimited`. `/v1/funny-app/admin/**` is role-guarded at `WebSecurityConfig.java:54`, and PATCH is already allowed in CORS (`WebSecurityConfig.java:139`).

## Decision Drivers

- Change a job's cron, zone or on/off state **without a deploy**, and see it take effect immediately.
- No new library and no new infrastructure.
- Safe on 1 vCPU:
  - a job can never run twice at the same time;
  - an admin cannot schedule a job every second.
- Destructive or costly controls are restricted to SUPER_ADMIN.

## Decisions

### D1. Table `scheduled_job` is the source of truth for schedules

```sql
scheduled_job(
  name        VARCHAR(64) PRIMARY KEY,   -- stable key, e.g. 'notification-digest'
  cron        VARCHAR(64) NOT NULL,      -- Spring 6-field cron
  zone        VARCHAR(64),               -- NULL = JVM default zone (today's behavior for jobs without zone)
  enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
  description VARCHAR(255),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
)
```

- The Liquibase SQL migration seeds one row per job with today's cron and zone. `logStats` moves from `fixedRate` 5 min to `0 */5 * * * *`, so its runs now line up with the clock. `cleanup-old-videos` is seeded with `enabled = false`, which matches today's "never fires".
- The job **code** stays in `AppScheduler`. Only the schedule moves. A row without code is ignored with a WARN at startup, and code without a row is not scheduled, also with a WARN.
- Last run time and result are kept **in memory** only. That means no DB write per run, and they reset on restart.

### D2. `JobRegistry` maps a name to code

`JobRegistry` is a `@Component` holding a `Map<String, Runnable>`, built from `AppScheduler` method references. Names are constants in one place. `@Scheduled` is removed from the six methods.

### D3. `DynamicJobScheduler` uses Spring's `TaskScheduler` + `CronTrigger`

- On `ApplicationReadyEvent`, it loads all rows and schedules the enabled ones.
- It keeps a `ConcurrentHashMap<String, ScheduledFuture<?>>`.
  - `reschedule(job)` runs inside `map.compute(name, …)`, which is atomic per key: cancel the old future (`cancel(false)`), then schedule a new one if enabled.
  - Two concurrent PATCHes therefore cannot leave two schedules for one job. No `synchronized` is used.
- **Overlap guard:** a `ConcurrentHashMap<String, AtomicBoolean>` per job.
  - A run starts only if `compareAndSet(false, true)` succeeds; otherwise it is skipped and logged.
  - This covers a scheduled fire, a "run now", and a slow previous run alike.
- Each run is wrapped in `try/catch`, so one failing job does not affect the scheduler. The result goes to the in-memory status.

### D4. Admin API under `/v1/funny-app/admin/jobs`

| Method | Path | Who | Notes |
|--------|------|-----|-------|
| GET | `/admin/jobs` | ADMIN (`@HasPermission ADMIN`) | List of name, description, cron, zone, enabled, `nextRunAt` (from `CronExpression.next`), `lastRunAt`, `lastResult`, `running` |
| PATCH | `/admin/jobs/{name}` | SUPER_ADMIN + ADMIN perm (same expression as `AdminDriveSyncController.java:55`) | Body `{cron?, zone?, enabled?}`, `@AuditLog`, `@RateLimited(permit = 10)` |
| POST | `/admin/jobs/{name}/runs` | SUPER_ADMIN + ADMIN perm | 202 when started, 409 when already running, `@AuditLog`, `@RateLimited(permit = 5)` |

Validation, with errors as `CustomException` + i18n keys:
- **cron:** must pass `CronExpression.isValidExpression`. Its **seconds field must also be a single number (0–59)**, which means at most once per minute and prevents `* * * * * *`.
- **zone:** must pass `ZoneId.of`.
- **name:** an unknown name gets 404.

### D5. One replica → no distributed lock

- A schedule change applies to the pod that served the PATCH, and it persists to the DB for the next start. With one replica, that is all pods.
- **When HPA max > 1**, a new ADR must add one of two options:
  - **ShedLock** (JDBC provider, one lock row per job), which is enough for "run once across pods";
  - **Quartz** with a JDBC job store, if clustered misfire handling or run history is needed.
- At the same time, schedule changes must reach the other pods, e.g. through a DB poll of `updated_at`.
- Until then, a rolling deploy can briefly run two pods. That is the same exposure as today's `@Scheduled`, so it is unchanged.

### D6. Out of scope

These are infrastructure timers, not business jobs:
- the SSE heartbeat (`InMemorySsePublisher.java:89`);
- the import poll (`VideoImportWorker.java:110`);
- the one-off thumbnail runner (`AppScheduler.java:63-67`).

## Options Considered

| Option | Runtime change | New dependency | Effort | Verdict |
|--------|----------------|----------------|--------|---------|
| Cron in `application.yaml` + env (`${…}` in `@Scheduled`) | No (restart) | No | XS | Rejected: does not meet "without a deploy" |
| **DB table + `TaskScheduler` (this ADR)** | Yes | No | S | **Chosen** |
| Quartz JDBC store | Yes | Yes (Quartz, ~11 tables) | M | Rejected for now: off tech strategy, oversized for 1 replica (see D5) |

## Consequences

- **Positive:**
  - Schedules can be edited, paused or run on demand from the Admin UI, and every change is audit-logged.
  - The wrong comments disappear.
- **Negative:**
  - A bad but valid cron (e.g. 03:00 vs 15:00) now takes effect without code review. Mitigations: the change is audit-logged and restricted to SUPER_ADMIN, and the UI shows `nextRunAt` before and after the change.
  - Last-run state is lost on restart.
- **Reversibility:** Two-way door. Putting `@Scheduled` back and dropping the table restores today's behavior.
