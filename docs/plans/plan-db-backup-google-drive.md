# Plan: Scheduled Database Backups to Google Drive

**Status:** In Progress · **Date:** 2026-11-17 · **Author:** nguyenhuuca
**Tracking:** `docs/tracking.md` row "DB Backup to Google Drive"
**PRD:** [PRD-db-backup-google-drive](../prd/PRD-db-backup-google-drive.md) · **ADR:** [ADR-0039](../adr/0039-db-backup-google-drive.md) · **Spec:** [spec-db-backup-google-drive](../specs/spec-db-backup-google-drive.md) · **Sequence:** [sequence-db-backup-google-drive](../sequence/sequence-db-backup-google-drive.md)

## Objective

Ship the three backup jobs of the spec (`db-backup-daily|weekly|monthly`): `pg_dump -Fc` through `ProcessBuilder`, upload to the private Drive folder with the owner OAuth client, retention 7 / 4 / 12 by moving older files to the Drive trash, an alert email on failure, metrics, and a restore runbook. The plan ends with a restore drill on prod.

## Scope

### In Scope
- Backend only (`api/`): config, process seam overload, OAuth Drive client factory, dump runner, Drive backup store, orchestration service, alert email, metrics, job wiring, Liquibase seed
- Restore runbook in `docs/guides/`
- Prod rollout by the owner: env vars, Run now, failure drill, restore drill

### Out of Scope (spec / PRD)
- Restore code, admin UI for backups, encryption, video file backup, PITR
- Installing `pg_dump` (done by the owner on 2026-11-17: `postgresql-client-17`)
- Fixing the Postgres port published on `0.0.0.0:5432` (tracked separately, higher priority, ADR-0039 Risks)

## Technical Approach

Design: ADR-0039 (in-process job running the host `pg_dump`) + spec FR-1..FR-11. Flows: sequence diagrams 1–5.

### Architecture Changes

```
DynamicJobScheduler ─▶ AppScheduler.dbBackup{Daily,Weekly,Monthly}()
                              │
                              ▼
                     DbBackupService (service/) ── DbBackupServiceImpl (service/impl/)
                       │ lock, enabled, config, steps, metrics, alert
          ┌────────────┼──────────────────────┬────────────────────┐
          ▼            ▼                      ▼                    ▼
   PgDumpRunner   BackupStore (port)    BackupAlertNotifier   MeterRegistry
   (ProcessFactory   └ DriveBackupStore     └ MailService
    + extraEnv)        └ OAuthDriveClientFactory ◀── also used by OAuthDriveUploader (unchanged behaviour)
```

### Key Decisions

| Decision | Rationale |
|----------|-----------|
| Reuse `ProcessFactory` + `FakeProcess`; add `start(command, extraEnv)` overload | Keeps the CWE-526 env allowlist; same test seam as yt-dlp / HLS. A default method on the interface keeps test lambdas compiling |
| Extract `OAuthDriveClientFactory` from `OAuthDriveUploader.buildDrive` instead of generalising `DriveUploader` | Video import contract untouched; backup gets list/trash, which `DriveUploader` does not have (ADR-0039 Consequences) |
| Datasource coordinates via `@Value("${spring.datasource.url|username|password}")` parsed by a small `JdbcUrl` record | Nothing reads them today; no new env vars (spec FR-1) |
| Retention ≥ 1 checked with `Contract.require` in the service constructor | `AppProperties` has no bean validation; fail-fast at startup |
| Free-text `BackupRedactor` (not `MaskingUtils`) | `MaskingUtils` masks object fields, stderr is free text |
| Two hats: DB-1 is a pure refactor (own commit), the rest is new behaviour | `code-quality.md` Refactoring Discipline |
| Every task merges to `main` alone; jobs are wired last (DB-6) | Classes from DB-2..DB-5 are unused until DB-6, so each merge is inert |

Decision type: Two-Way Door (switch `BACKUP_ENABLED=false`, disable rows, or revert). The only one-way part is the seed migration, which is data-only and harmless without code.

## Implementation Steps

| ID | Task | Done when | Depends | Size |
|---|---|---|---|---|
| **DB-1** | **Refactor (hat 1).** (a) `client/ProcessFactory`: add `default Process start(List<String> cmd, Map<String,String> extraEnv)`, which delegates to `start(cmd)` when `extraEnv` is empty and otherwise throws `UnsupportedOperationException`; `DefaultProcessFactory` overrides it: `restrictEnvironment` then `putAll(extraEnv)`. (b) Extract `service/impl/OAuthDriveClientFactory` (lazy, built once, `Supplier<Drive>`, maps build failure to an exception), with `buildDrive` moved there; `OAuthDriveUploader` public ctor uses it; `VideoImportConfig` creates one factory bean and passes it | `OAuthDriveUploaderTest`, `YtDlpClientImplTest`, HLS tests unchanged and green; new `DefaultProcessFactoryTest` case: extra var present, `DB_PASS`/`JWT_SECRET` still stripped; `BeanConstructorTest` green | — | S (~120 LOC) |
| **DB-2** | **Config + model.** `AppProperties.Backup` (enabled, folderId, pgDumpPath, workDir, timeout `Duration`, alertEmail, `Retention{daily=7,weekly=4,monthly=12}`); `application.yaml` `app.backup.*` with `${BACKUP_ENABLED:false}`, `${BACKUP_FOLDER_ID:}`, `${BACKUP_PG_DUMP_PATH:pg_dump}`, `${BACKUP_WORK_DIR:/opt/data/backups}`, `${BACKUP_ALERT_EMAIL:}`; `api/.env.example` (empty values + comment). `BackupTier` enum (`DAILY("daily")`…, `retention(Backup)`), `BackupErrorCode` (spec FR-10), `BackupStep`, `BackupException extends RuntimeException` (code, step, safe reason). `BackupRedactor` (truncate + replace secrets with `***`). `JdbcUrl.parse("jdbc:postgresql://h:p/db")` (port default 5432) | Binding test for defaults and env overrides; redactor tests (password in the middle, repeated, null/blank secret ignored, truncation); `JdbcUrl` tests (with/without port, query params dropped, non-postgres → `NOT_CONFIGURED`) | — | S (~200 LOC) |
| **DB-3** | **`PgDumpRunner`** (`service/impl/`, `@Component`): builds argv (spec FR-4), `extraEnv = {PGPASSWORD}`, writes `<name>.part` with POSIX 600 (when supported), drains stdout, keeps the stderr tail ≤ 2 KB on a virtual thread, waits `timeout`, `destroyForcibly` descendants + process on timeout, maps `IOException` on start → `PG_DUMP_NOT_FOUND`, exit ≠ 0 → `PG_DUMP_FAILED` (redacted stderr), timeout → `PG_DUMP_TIMEOUT`; checks the dump (> 5 B, magic `PGDMP`) → `DUMP_INVALID`; renames `.part` to the final name | Tests with `FakeProcess` + `@TempDir`: argv exact and password absent from argv; `PGPASSWORD` in `extraEnv`; exit 0 + valid file → path; exit 1 → `PG_DUMP_FAILED` with `***`; never-ending process → `PG_DUMP_TIMEOUT` and process killed; start IOException → `PG_DUMP_NOT_FOUND`; empty / non-PGDMP file → `DUMP_INVALID` | DB-1, DB-2 | M (~250 LOC) |
| **DB-4** | **`BackupStore` port + `DriveBackupStore`** (`service/` + `service/impl/`): Tier subfolders `daily/`, `weekly/`, `monthly/` under the backup folder: `resolveTierFolder(tier)` finds the folder (oldest if several) or creates it, caches the id in a `ConcurrentHashMap`, and on 404 drops the cache entry and retries once (spec FR-6, EC-13). `upload(Path, name, tier)` (resumable, 8 MB chunk, `application/octet-stream`, parent = tier subfolder, `appProperties{backupApp, backupTier}`), `list(tier)` (query of spec FR-7 inside the tier subfolder, `createdTime desc`, all pages), `trash(id)` (`files.update trashed=true`). Error mapping: 401 / `invalid_grant|invalid_client|unauthorized_client` → `DRIVE_AUTH_FAILED`; 404 → `DRIVE_FOLDER_NOT_FOUND`; else `DRIVE_UPLOAD_FAILED`; list/trash errors → `PRUNE_FAILED`. `folderId` checked against `[A-Za-z0-9_-]+` before building the query | Mocked `Drive` (same style as `OAuthDriveUploaderTest`): subfolder found / created / several → oldest / cached (one lookup for two uploads) / 404 → re-resolve + one retry; metadata + appProperties asserted; query string exact; paging followed; trash sets `trashed=true`; each error mapping; invalid folder id rejected | DB-1, DB-2 | M (~300 LOC) |
| **DB-5** | **`DbBackupService` + `DbBackupServiceImpl` + `BackupAlertNotifier`.** One `ReentrantLock` for all tiers; steps 1–8 of spec FR-3 (disabled → return; config check; work-dir prep and leftover cleanup; dump; upload; cleanup keeps newest N and never the new file; `finally` delete + unlock; INFO summary line). Throws `BackupException` after alerting on failure; on cleanup failure returns normally + warning email (EC-4). Notifier: subject/body/hints of spec FR-8, recipient `alertEmail` or `spring.mail.username`, catches mail errors (EC-11). Metrics (FR-11): gauges `db_backup_last_success_timestamp_seconds{tier}`, `db_backup_size_bytes{tier}` (backed by `AtomicLong`s), counter `db_backup_runs_total{tier,result}`. `Contract.require(retention ≥ 1)` in the ctor | Unit tests with mocked runner/store/mail + `SimpleMeterRegistry`: happy path (upload then trash index ≥ N, new file kept, local file deleted); disabled → no calls, no mail; `NOT_CONFIGURED` (blank folder, OAuth missing); `ALREADY_RUNNING` while lock held; each failure → exactly one mail + rethrow + no trash; cleanup failure → warning mail, no throw; mail failure swallowed; leftover files deleted; retention 0 → ctor fails; metrics values | DB-3, DB-4 | M (~350 LOC) |
| **DB-6** | **Job wiring + migration.** `JobRegistry` constants + map entries; `AppScheduler` gets `DbBackupService` and `dbBackupDaily/Weekly/Monthly()`; `DynamicJobSchedulerTest:235` ctor call gets one more `null`; Liquibase `202611170001-db-backup-jobs.sql` (spec Database Changes); `DbBackupJobMigrationTest` (names in registry, crons valid, zone) | `mvn verify` green with the coverage gate; `BeanConstructorTest` green; `./startLocal.sh` boots with `BACKUP_ENABLED` unset, the 3 rows show in Admin → Scheduled Jobs, Run now → `SUCCESS` with log `DB backup disabled` | DB-5 | S (~120 LOC) |
| **DB-7** | **Docs.** `docs/guides/runbook-db-restore.md` (download from Drive, `createdb`, `pg_restore --no-owner`, row-count check, point `DB_HOST`, renew OAuth token, check `pg_dump --version`); `mkdocs.yml` nav (Spec, Sequence, Plan, Runbook); `docs/tracking.md` row; PRD Status Approved, spec Approved | Docs build links resolve (mkdocs nav paths exist) | DB-6 | S |
| **DB-8** | **Prod rollout (owner + agent).** Prod env: `BACKUP_ENABLED=true`, `BACKUP_FOLDER_ID`, optional `BACKUP_ALERT_EMAIL`; push (owner approval) → CI deploy; Run now daily, weekly, monthly; failure drill (temporarily wrong `BACKUP_FOLDER_ID` → one alert `DRIVE_FOLDER_NOT_FOUND`); restore drill into a scratch DB (`docker run postgres:17`) with row counts; ADR-0039 → Accepted; tracking ✅ | 3 tagged files on Drive; alert email received; restore drill counts match; next 03:00 run `SUCCESS` | DB-7 | — |

```
DB-1 ─┬─ DB-3 ─┐
DB-2 ─┴─ DB-4 ─┴─ DB-5 ─ DB-6 ─ DB-7 ─ DB-8
```

DB-1 and DB-2 can run in parallel (wave 1); DB-3 and DB-4 in parallel (wave 2); then DB-5 → DB-8 in sequence. Each task is one commit on `main`, independently green.

## Files to Modify

| File | Action | Task |
|------|--------|------|
| `api/src/main/java/com/canhlabs/funnyapp/client/ProcessFactory.java` | Modify (default overload) | DB-1 |
| `api/src/main/java/com/canhlabs/funnyapp/client/DefaultProcessFactory.java` | Modify (extraEnv) | DB-1 |
| `api/src/main/java/com/canhlabs/funnyapp/service/impl/OAuthDriveClientFactory.java` | Create | DB-1 |
| `api/src/main/java/com/canhlabs/funnyapp/service/impl/OAuthDriveUploader.java` | Modify (use factory) | DB-1 |
| `api/src/main/java/com/canhlabs/funnyapp/service/impl/VideoImportConfig.java` | Modify (factory bean) | DB-1 |
| `api/src/main/java/com/canhlabs/funnyapp/config/AppProperties.java` | Modify (`Backup`) | DB-2 |
| `api/src/main/resources/application.yaml` | Modify (`app.backup`) | DB-2 |
| `api/.env.example` | Modify (3 vars) | DB-2 |
| `api/src/main/java/com/canhlabs/funnyapp/service/backup/` → `BackupTier`, `BackupStep`, `BackupErrorCode`, `BackupException`, `BackupRedactor`, `JdbcUrl` | Create | DB-2 |
| `api/src/main/java/com/canhlabs/funnyapp/service/impl/PgDumpRunner.java` | Create | DB-3 |
| `api/src/main/java/com/canhlabs/funnyapp/service/BackupStore.java`, `service/impl/DriveBackupStore.java` | Create | DB-4 |
| `api/src/main/java/com/canhlabs/funnyapp/service/DbBackupService.java`, `service/impl/DbBackupServiceImpl.java`, `service/impl/BackupAlertNotifier.java` | Create | DB-5 |
| `api/src/main/java/com/canhlabs/funnyapp/jobs/JobRegistry.java`, `jobs/AppScheduler.java` | Modify | DB-6 |
| `api/src/main/resources/db/changelog/sql/202611170001-db-backup-jobs.sql` | Create | DB-6 |
| `api/src/test/java/com/canhlabs/funnyapp/jobs/DynamicJobSchedulerTest.java` | Modify (ctor arg) | DB-6 |
| Tests: `DefaultProcessFactoryTest`, `BackupRedactorTest`, `JdbcUrlTest`, `AppPropertiesBackupTest`, `PgDumpRunnerTest`, `DriveBackupStoreTest`, `DbBackupServiceImplTest`, `BackupAlertNotifierTest`, `DbBackupJobMigrationTest` | Create | DB-1..DB-6 |
| `docs/guides/runbook-db-restore.md`, `mkdocs.yml`, `docs/tracking.md` | Create / Modify | DB-7 |

## Dependencies

### Code Dependencies

None new. Already in `api/pom.xml`: `google-api-services-drive`, `google-auth-library` (`UserCredentials`), Micrometer, Spring Mail.

### Service Dependencies

| Service | Status | Notes |
|---------|--------|-------|
| `pg_dump` 17 on the prod host | Available (installed 2026-11-17) | Manual dump 134 KB OK |
| Drive backup folder | Available (created 2026-11-17) | ID only in prod env |
| OAuth refresh token (`GOOGLE_OAUTH_*`) | Available | App in "Testing": 7-day expiry, owner renews |
| Gmail SMTP (`EMAIL_SENDER`) | Available | |

## Testing Strategy

### Unit Tests

| Component | Test Cases | Status |
|-----------|------------|--------|
| `DefaultProcessFactory` | extraEnv added; secrets still stripped | Pending |
| `BackupRedactor`, `JdbcUrl`, `AppProperties.Backup` | see DB-2 | Pending |
| `PgDumpRunner` | argv, env, exit codes, timeout kill, missing binary, invalid dump | Pending |
| `DriveBackupStore` | metadata, query, paging, trash, error mapping, folder-id check | Pending |
| `DbBackupServiceImpl` | happy, disabled, not configured, already running, each failure, prune warning, mail failure, leftovers, retention check, metrics | Pending |
| `BackupAlertNotifier` | subject/body per code, hints, recipient fallback, no secret in body | Pending |
| `DbBackupJobMigrationTest` | names in registry, crons valid, zone | Pending |

### Integration Tests

| Scenario | Expected Outcome | Status |
|----------|------------------|--------|
| Local: `pg_dump` against the dev DB via `PgDumpRunner` (manual, `startLocal.sh` + Run now with `BACKUP_ENABLED=true` and a test folder) | File on the test Drive folder, `pg_restore --list` works | Pending |
| Prod Run now ×3 tiers | 3 tagged files, `SUCCESS` | Pending |
| Prod restore drill | Row counts match | Pending |

No `@SpringBootTest` in CI (repo convention); boot is verified manually in DB-6.

### Manual Testing
- [ ] App boots locally with `BACKUP_ENABLED` unset; Admin → Scheduled Jobs shows 3 rows
- [ ] Failure drill sends exactly one email with the right hint
- [ ] After 8 daily Run now calls (test folder), exactly 7 daily files are outside the trash

## Rollback Plan

1. Set `BACKUP_ENABLED=false` on prod and restart: runs become no-ops (no redeploy).
2. Or disable the three rows in Admin → Scheduled Jobs (instant, persists).
3. Full revert: revert the commits; the seed rows remain and `DynamicJobScheduler` ignores rows without code (WARN). Drive files stay and are harmless.

## Risks

| Risk | Mitigation |
|------|------------|
| DB-1 refactor changes video import upload | Tests unchanged in meaning (two-hats rule); own commit |
| `DynamicJobSchedulerTest` constructor call breaks | Planned in DB-6 |
| OAuth token expiry silences backups | Alert hint names the fix; owner renews weekly |
| Postgres port exposed on `0.0.0.0` | Out of scope; handle before or right after DB-8 |
| Coverage gate (90 %) dips with process/Drive glue | Seams (`ProcessFactory`, mocked `Drive`) make every branch unit-testable |

## Checklist

### Before Starting
- [x] PRD / ADR / spec / sequence written
- [ ] PRD, ADR-0039 and spec approved by the owner
- [x] `pg_dump` 17 installed on prod; Drive folder created

### Before Each Commit
- [ ] `mvn verify` green, coverage gate met
- [ ] No secret in code, tests, logs (`.env.example` empty values)
- [ ] `TASKS.md` line ticked with the commit hash

### Before Push
- [ ] Owner approves the push (every push)

## Notes

- `TaskCreate` is not available in this session; `TASKS.md` (section "Feature: DB backup to Google Drive") and the table above are the task record.
- Execution: `/swarm-execute docs/plans/plan-db-backup-google-drive.md` (waves: {DB-1, DB-2} → {DB-3, DB-4} → DB-5 → DB-6 → DB-7), DB-8 with the owner.

---

## Progress Log

| Date | Update |
|------|--------|
| 2026-11-17 | Plan drafted from spec; exploration of process seam, Drive test seam, metrics, config, test patterns |
