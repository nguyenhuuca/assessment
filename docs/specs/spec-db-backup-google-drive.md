# Feature Specification: Scheduled Database Backups to Google Drive

<!--
Feature Specification
Filename: docs/specs/spec-db-backup-google-drive.md
Owner: nguyenhuuca
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
-->

## Metadata

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-11-17
**Related PRD:** [PRD-db-backup-google-drive](../prd/PRD-db-backup-google-drive.md)
**Related ADR:** [ADR-0039](../adr/0039-db-backup-google-drive.md)
**Sequence diagrams:** [sequence-db-backup-google-drive](../sequence/sequence-db-backup-google-drive.md)

---

## Overview

Three scheduled jobs (`db-backup-daily`, `db-backup-weekly`, `db-backup-monthly`) each run the host `pg_dump` against the app database. Each run uploads the custom-format dump to the private Google Drive folder `BACKUP_FOLDER_ID` with the owner's OAuth credential, then moves that tier's older backups to the Drive trash so that 7 / 4 / 12 remain. Any failure ends the run as `FAILED` in the admin jobs view and sends one alert email to the owner. There is no new HTTP endpoint and no UI change: the jobs appear in the existing Admin → Scheduled Jobs tab, where the existing "Run now", cron edit and enable/disable controls apply.

---

## Business Rules

### Rule 1: Tiers and retention

| Tier | Job name | Default cron (zone `Asia/Ho_Chi_Minh`) | Keep newest |
|---|---|---|---|
| `daily` | `db-backup-daily` | `0 0 3 * * *` (03:00 every day) | 7 |
| `weekly` | `db-backup-weekly` | `0 30 3 * * SUN` (03:30 Sunday) | 4 |
| `monthly` | `db-backup-monthly` | `0 0 4 1 * *` (04:00 on the 1st) | 12 |

Each tier takes its own full dump (ADR-0039, linked choice 1). Retention values come from `app.backup.retention.*` (defaults above).

### Rule 2: Clean up only after a good upload

The cleanup (prune) of a tier runs only after that run's upload has succeeded. A run that fails before or during the upload must not trash any file.

### Rule 3: One subfolder per tier; only job-made files are ever trashed

Backups are stored in one subfolder per tier under `BACKUP_FOLDER_ID`:

```
BACKUP_FOLDER_ID/
├── daily/     funnyapp-daily-<UTC>Z.dump    (newest 7)
├── weekly/    funnyapp-weekly-<UTC>Z.dump   (newest 4)
└── monthly/   funnyapp-monthly-<UTC>Z.dump  (newest 12)
```

The job creates a missing subfolder itself (FR-6). The file name keeps the tier, so a downloaded file still says what it is.

Cleanup considers only files in that tier's subfolder that carry the Drive `appProperties` `backupApp=funnyapp-db` **and** `backupTier=<tier>`. Files without these tags (for example one the owner put there by hand) are never touched, whatever their name.

### Rule 4: Cleanup moves files to the Drive trash; it does not delete them for good

Older files are moved to the trash (`trashed=true`). Google empties the trash after 30 days, so a wrong cleanup can be undone for 30 days.

### Rule 5: One backup at a time across all tiers

At most one backup run (any tier, scheduled or "Run now") runs at a time. A run that starts while another backup run is in progress does nothing and ends as `FAILED` with code `ALREADY_RUNNING` (Rule 7 applies).

### Rule 6: Master switch

When `app.backup.enabled` is `false` (default; env `BACKUP_ENABLED`), a backup run logs `DB backup disabled` at INFO and returns normally (job result `SUCCESS`), with no dump, no Drive call and no email. Prod sets `BACKUP_ENABLED=true`. This keeps local and dev machines, which share the seed migration, from running backups or sending alerts.

### Rule 7: Every failed run sends exactly one alert email

A run that ends `FAILED` sends exactly one email to `app.backup.alert-email` (env `BACKUP_ALERT_EMAIL`), or to `spring.mail.username` (`EMAIL_SENDER`) when the former is blank. A run that succeeded but whose cleanup failed sends one **warning** email (EC-4). A run that succeeds cleanly sends nothing. If sending the email itself fails, the error is logged at ERROR and the run result does not change.

### Rule 8: "Run now" behaves exactly like a scheduled run

`POST /v1/funny-app/admin/jobs/{name}/runs` on a backup job produces a file of that job's tier and applies that tier's cleanup. Repeated manual runs can push older files of the tier out, which is safe because each new file is a newer full dump.

---

## Functional Requirements

### FR-1: Configuration

New `AppProperties.Backup` (prefix `app.backup`), bound in `application.yaml`:

| Property | Env | Default | Meaning |
|---|---|---|---|
| `enabled` | `BACKUP_ENABLED` | `false` | Master switch (Rule 6) |
| `folder-id` | `BACKUP_FOLDER_ID` | `""` | Drive folder for backups; never committed (public repo) |
| `pg-dump-path` | `BACKUP_PG_DUMP_PATH` | `pg_dump` | Binary name on `PATH` or absolute path |
| `work-dir` | `BACKUP_WORK_DIR` | `/opt/data/backups` | Local temp directory |
| `timeout` | — | `10m` | Hard limit for one `pg_dump` process |
| `alert-email` | `BACKUP_ALERT_EMAIL` | `""` | Alert recipient; blank → `EMAIL_SENDER` |
| `retention.daily` / `.weekly` / `.monthly` | — | `7` / `4` / `12` | Files kept per tier (each must be ≥ 1) |

`api/.env.example` gains `BACKUP_ENABLED`, `BACKUP_FOLDER_ID`, `BACKUP_ALERT_EMAIL` with empty values and a comment. Connection details are **not** new config: host, port and database are parsed from `spring.datasource.url` (`jdbc:postgresql://<host>:<port>/<db>`), and the user and password come from `spring.datasource.username` / `password`.

### FR-2: Job registration

- `JobRegistry` gains `DB_BACKUP_DAILY = "db-backup-daily"`, `DB_BACKUP_WEEKLY`, `DB_BACKUP_MONTHLY`, mapped to `AppScheduler.dbBackupDaily()` / `dbBackupWeekly()` / `dbBackupMonthly()`, each a one-line call to `DbBackupService.run(BackupTier.X)`.
- Liquibase `api/src/main/resources/db/changelog/sql/202611170001-db-backup-jobs.sql` seeds three `scheduled_job` rows (see Database Changes).
- `DbBackupService.run` must **throw** on failure (after sending the alert), so that `DynamicJobScheduler.execute` records `FAILED`; it returns normally on success, on a success with a cleanup warning, and when disabled.

### FR-3: Backup run steps

`DbBackupServiceImpl.run(tier)` must perform, in order:

1. **Lock**: `ReentrantLock.tryLock()` shared by all tiers (no `synchronized`, per tech strategy). Not acquired → `ALREADY_RUNNING` (Rule 5).
2. **Disabled check**: `enabled=false` → log and return (Rule 6).
3. **Config check**: blank `folder-id` → `NOT_CONFIGURED`. The Drive OAuth credential not configured (`GoogleOauth.isConfigured()` false) → `NOT_CONFIGURED`.
4. **Prepare the work dir**: create `work-dir` if missing; delete any leftover `funnyapp-*.dump*` files in it (left by an earlier crash).
5. **Dump** (FR-4) into `<work-dir>/<fileName>.part`, then check it (FR-5), then rename it to `<fileName>`.
6. **Upload** (FR-6).
7. **Cleanup** (FR-7). A failure here only produces a warning (EC-4).
8. **Finish**: always, in `finally`, delete the local file(s) and release the lock. Log at INFO one line: `DB backup tier={} file={} sizeBytes={} driveFileId={} trashed={} durationMs={}`.

File name: `funnyapp-<tier>-<yyyyMMdd-HHmmss>Z.dump`, with the time in UTC taken at the start of the run, for example `funnyapp-daily-20261117-200000Z.dump`.

### FR-4: Running `pg_dump`

- Command, as a list (no shell, no string concatenation): `<pg-dump-path> --host <host> --port <port> --username <user> --dbname <db> --format=custom --no-password --file <path>.part`.
- Started through the existing `ProcessFactory` seam (`client/`), which gains an overload `start(List<String> command, Map<String, String> extraEnv)`. `DefaultProcessFactory` still strips the env down to `ALLOWED_ENV` (CWE-526), then adds only `extraEnv`.
- Child environment: the allowlist plus `PGPASSWORD=<datasource password>`; no other app secret. The password must never appear in the argument list, in logs or in emails.
- stdout is discarded; stderr is captured, keeping at most the last 2 KB.
- The process is waited for at most `timeout`. On timeout: `destroyForcibly()`, wait up to 10 s, then `PG_DUMP_TIMEOUT`.
- Start failure (`IOException` from `ProcessBuilder.start()`, for example the binary is missing) → `PG_DUMP_NOT_FOUND`.
- Non-zero exit → `PG_DUMP_FAILED`; the reason is the captured stderr after redaction (FR-9).
- The child is started from the job thread; no request thread waits on it.

### FR-5: Checking the dump

The `.part` file must exist, be larger than 5 bytes and start with the custom-format magic bytes `PGDMP`. Otherwise → `DUMP_INVALID`. On POSIX file systems the file is created with permissions `rw-------` (600).

### FR-6: Upload to Drive

- Uses the owner OAuth Drive client (`UserCredentials` with the `GOOGLE_OAUTH_*` refresh token, ADR-0019), **not** the service-account `Drive` bean in `ConfigBean`, which cannot write to the owner's folder.
- The client construction in `OAuthDriveUploader.buildDrive` moves into a shared `OAuthDriveClientFactory` (lazy, built once). `OAuthDriveUploader` uses the factory; its behaviour, `DriveUploader` contract, `video/mp4` MIME and video folder stay unchanged.
- A new `BackupStore` interface (`service/`) with impl `DriveBackupStore` (`service/impl/`) offers: `String upload(Path file, String name, BackupTier tier)`, `List<BackupFile> list(BackupTier tier)`, `void trash(String fileId)`.
- **Tier subfolder resolution** (`resolveTierFolder(tier)`): query `'<folder-id>' in parents and name = '<tier>' and mimeType = 'application/vnd.google-apps.folder' and trashed = false`, `orderBy=createdTime`. If none is found, create it (`files.create` with `mimeType=application/vnd.google-apps.folder`, `parents=[folder-id]`, `appProperties={backupApp: "funnyapp-db"}`). If several exist, use the oldest. The id is cached per tier in memory (`ConcurrentHashMap`). When an upload or list returns 404 for a cached subfolder, the cache entry is dropped, the folder is resolved again and the call is retried **once** (EC-13). A 404 on `folder-id` itself → `DRIVE_FOLDER_NOT_FOUND`.
- Upload metadata: `name`, `parents=[<tier subfolder id>]`, `mimeType=application/octet-stream`, `appProperties={backupApp: "funnyapp-db", backupTier: "<tier>"}`. Resumable upload, 8 MB chunks (as in video import). Returns the Drive file id; null id → `DRIVE_UPLOAD_FAILED`.
- Error mapping: HTTP 401, or an `IOException` message that contains `invalid_grant` / `invalid_client` / `unauthorized_client` → `DRIVE_AUTH_FAILED`; HTTP 404 on the folder → `DRIVE_FOLDER_NOT_FOUND`; any other error → `DRIVE_UPLOAD_FAILED`.

### FR-7: Cleanup (retention)

- `list(tier)` queries `'<tier subfolder id>' in parents and trashed = false and appProperties has { key='backupApp' and value='funnyapp-db' } and appProperties has { key='backupTier' and value='<tier>' }`, `orderBy=createdTime desc`, `fields=nextPageToken, files(id, name, createdTime)`, following pages to the end.
- Files at index ≥ `retention.<tier>` are trashed one by one with `files().update(id, new File().setTrashed(true))`.
- The file uploaded in this run is never trashed, even when retention is changed to a smaller number (it is always the newest, at index 0).
- If listing or any trash call fails, stop the cleanup, keep the run `SUCCESS` and send one warning email with code `PRUNE_FAILED` (EC-4). Files already trashed in this run stay trashed.

### FR-8: Alert and warning email

Sent through `MailService.sendSimpleMail(to, subject, body)`, in English (ops-only, no locale):

- Subject, failure: `[Funny Movies] DB backup FAILED (<tier>): <CODE>`
- Subject, warning: `[Funny Movies] DB backup WARNING (<tier>): PRUNE_FAILED`
- Body lines: `Tier`, `Step` (`CONFIG` | `DUMP` | `UPLOAD` | `PRUNE`), `Code`, `Time (UTC)`, `Reason` (≤ 500 characters, redacted), `Hint` (fixed text per code, see below), `Last good backup` (`createdTime` of the newest file of that tier if a listing succeeded, otherwise `unknown`).
- Hints: `DRIVE_AUTH_FAILED` → "The Google OAuth refresh token has expired or was revoked. Issue a new one and update GOOGLE_OAUTH_REFRESH_TOKEN, then use Run now."; `PG_DUMP_NOT_FOUND` → "Install postgresql-client-17 on the host or set BACKUP_PG_DUMP_PATH."; `PG_DUMP_FAILED` → "Check that the pg_dump major version is ≥ the server's, and the DB credentials."; `NOT_CONFIGURED` → "Set BACKUP_FOLDER_ID and the GOOGLE_OAUTH_* variables."; `DRIVE_FOLDER_NOT_FOUND` → "The folder does not exist or the OAuth account cannot access it."; other codes → "See the server log around this time."
- No stack trace, connection string, host, password or token in the email.

### FR-9: Redaction

A small `BackupRedactor` (the existing `MaskingUtils` masks object fields, not free text). Before stderr or an exception message goes into a log line or an email, the text is truncated (log 2 KB, email 500 characters), and every occurrence of the datasource password and of the OAuth client secret and refresh token is replaced by `***`.

### FR-10: Error codes

New enum `BackupErrorCode`: `NOT_CONFIGURED`, `ALREADY_RUNNING`, `PG_DUMP_NOT_FOUND`, `PG_DUMP_TIMEOUT`, `PG_DUMP_FAILED`, `DUMP_INVALID`, `DRIVE_AUTH_FAILED`, `DRIVE_FOLDER_NOT_FOUND`, `DRIVE_UPLOAD_FAILED`, `PRUNE_FAILED` (warning only), `INTERNAL_ERROR`. Carried by `BackupException extends RuntimeException` (same pattern as `ImportException`), which records the step as well.

### FR-11: Metrics

Micrometer (exposed on `/actuator/prometheus`):

- `db_backup_last_success_timestamp_seconds{tier}`: gauge, set after a successful upload
- `db_backup_size_bytes{tier}`: gauge, size of the last uploaded dump
- `db_backup_runs_total{tier, result}`: counter with `result` = `success` | `failed` | `disabled` | `prune_warning`

---

## API Changes

### New Endpoints

None.

### Modified Endpoints

None. The existing Admin Jobs endpoints (`GET /v1/funny-app/admin/jobs`, `PATCH /v1/funny-app/admin/jobs/{name}`, `POST /v1/funny-app/admin/jobs/{name}/runs`) now also list and act on the three backup jobs. Their contract, auth (`SUPER_ADMIN` role + `ADMIN` permission bit, `@RateLimited(permit = 5)` on run) and error codes do not change. `POST …/runs` returns `202` and the job DTO; it returns the existing `409 error.job.running` when the *same* job is already running. The cross-tier lock (Rule 5) shows up only as the run's `FAILED` result.

---

## Database Changes

### New Tables

None.

### Modified Tables

| Table | Change | Column | Type | Notes |
|-------|--------|--------|------|-------|
| `scheduled_job` | Seed 3 rows | — | — | Data only, no schema change |

Migration `api/src/main/resources/db/changelog/sql/202611170001-db-backup-jobs.sql`:

```sql
-- Scheduled DB backups to Google Drive (ADR-0039). Rows are enabled; the env switch
-- BACKUP_ENABLED (app.backup.enabled, default false) decides at run time whether a run does anything.
INSERT INTO scheduled_job (name, cron, zone, enabled, description) VALUES
  ('db-backup-daily',   '0 0 3 * * *',    'Asia/Ho_Chi_Minh', TRUE, 'DB backup to Google Drive (keep 7)'),
  ('db-backup-weekly',  '0 30 3 * * SUN', 'Asia/Ho_Chi_Minh', TRUE, 'DB backup to Google Drive (keep 4)'),
  ('db-backup-monthly', '0 0 4 1 * *',    'Asia/Ho_Chi_Minh', TRUE, 'DB backup to Google Drive (keep 12)');
```

### No Changes

No new table and no column change. Backup history lives on Drive and in the logs/metrics, not in the database. This is on purpose: the database is what is being backed up.

---

## Security Requirements

### Authentication

No new endpoint. The admin jobs endpoints keep their existing JWT checks.

### Authorization

Only a `SUPER_ADMIN` with the `ADMIN` permission bit can trigger "Run now", change the cron or disable a backup job (existing `AdminJobController` `@PreAuthorize`, `@AuditLog`, `@RateLimited` rules).

### Data Validation

| Field | Rule | Error |
|-------|------|-------|
| `spring.datasource.url` | Must match `jdbc:postgresql://<host>(:<port>)?/<db>`; port defaults to 5432 | `NOT_CONFIGURED` |
| `app.backup.folder-id` | Not blank; matches `[A-Za-z0-9_-]+` (it is placed inside a Drive query string) | `NOT_CONFIGURED` |
| `app.backup.retention.*` | Integer ≥ 1 | Startup fails (`Contract.require` in the `DbBackupServiceImpl` constructor, since `AppProperties` has no bean validation), so a misconfiguration can never trash everything |
| `app.backup.pg-dump-path` | Not blank; passed as one argv element, never through a shell | `PG_DUMP_NOT_FOUND` at run time |

### Sensitive Data

- DB password only in the child's `PGPASSWORD` (not argv: `ps` shows argv) (CWE-214).
- Redaction of password, client secret and refresh token in logs and emails (FR-9, CWE-532).
- Local dump file mode 600, deleted in `finally` and at the start of every run.
- Dumps on Drive are **not encrypted** (accepted risk, ADR-0039 linked choice 3). The folder must stay "Restricted".
- `BACKUP_FOLDER_ID` is never committed (public repo); docs refer to it by env name only.

---

## Caching Impact

No cache impact. The job reads no Guava cache and writes none. `pg_dump` reads the database directly in its own snapshot.

---

## Frontend Changes

### New Routes

None.

### Modified Components

None. `AdminJobsTab` already renders every row from `GET /admin/jobs`, with its `description`, cron text, last result and the Run now / toggle / edit controls.

### State Management

No new query keys. The existing admin jobs query shows the new rows.

---

## Event / Job Changes

### Domain Events

| Event | Change | Schema |
|-------|--------|--------|
| — | No change | — |

### Scheduled Jobs

Three new jobs (`db-backup-daily`, `db-backup-weekly`, `db-backup-monthly`) in `JobRegistry` / `AppScheduler`, scheduled by `DynamicJobScheduler` from the seed rows above. `ScheduledJobMigrationTest` keeps reading only the original seed file; a new `DbBackupJobMigrationTest` parses `202611170001-db-backup-jobs.sql` and asserts the three names exist in `JobRegistry`, the crons are valid, and the zone is `Asia/Ho_Chi_Minh`.

---

## Non-Functional Requirements

### Performance

| Operation | Target | Notes |
|-----------|--------|-------|
| `pg_dump` of prod (≈ 20 MB DB, 134 KB dump) | < 10 s; hard timeout 10 min | Measured ≈ 1 s on 2026-11-17 |
| Upload to Drive | < 30 s | Single 8 MB chunk at today's size |
| Whole run (lock → cleanup) | < 60 s p99 | |
| Impact on API latency during a run | No measurable change; no 5xx | Off-peak schedule; one child process; no request thread involved |

### Availability

The job runs on the scheduler thread pool, isolated from request handling. Rollback: set `BACKUP_ENABLED=false` (no redeploy), or disable the rows in Admin → Scheduled Jobs. Removing the feature is reverting the code; the seed rows without code are ignored by `DynamicJobScheduler` (it logs a warning).

### Scalability

Fine up to about 1 GB of dump (temp disk, upload time, Drive quota with 23 files). Past that, revisit retention and ADR-0039. Single replica only, as for every `scheduled_job` job (ADR-0023); with more replicas each would run its own backup.

---

## Edge Cases

### EC-1: Files the owner put in the folder by hand

**Condition:** `BACKUP_FOLDER_ID` holds files without `backupApp=funnyapp-db` / `backupTier` app properties (any name, including `funnyapp-daily-….dump` copied by hand).
**Expected:** They are never listed for cleanup and never trashed (Rule 3); they do not count towards retention.

### EC-1b: Files in the wrong subfolder or at the top level

**Condition:** A tagged file sits directly in `BACKUP_FOLDER_ID` or in another tier's subfolder (for example the owner moved it).
**Expected:** It is not in the listed subfolder, so it does not count towards retention and is never trashed. Moving files around by hand can never cause deletions.

### EC-2: Backup folder not configured

**Condition:** `BACKUP_ENABLED=true` and `BACKUP_FOLDER_ID` blank (or `GOOGLE_OAUTH_*` not set).
**Expected:** No dump is taken; run ends `FAILED`, code `NOT_CONFIGURED`, step `CONFIG`; one alert email. With `BACKUP_ENABLED=false`, nothing happens and no email is sent (Rule 6).

### EC-3: `pg_dump` hangs

**Condition:** The process has not exited after `timeout` (10 min).
**Expected:** Process killed (`destroyForcibly`), `.part` file deleted, no Drive call, run `FAILED`, code `PG_DUMP_TIMEOUT`, one alert email. The lock is released, so the next scheduled run can proceed.

### EC-4: Upload succeeded, cleanup failed

**Condition:** Upload returns a file id, then listing or trashing throws (network error, 5xx, 401).
**Expected:** Run result `SUCCESS` (a new good backup exists); one **warning** email, code `PRUNE_FAILED`, step `PRUNE`; metric `db_backup_runs_total{result="prune_warning"}`. The folder may hold more than the retention count until a later run cleans it up; that run trashes all surplus files, not just one.

### EC-5: OAuth refresh token expired

**Condition:** The OAuth app is in "Testing" and the token is older than 7 days; upload fails with `invalid_grant` / 401.
**Expected:** `.part`/dump deleted, run `FAILED`, code `DRIVE_AUTH_FAILED`, step `UPLOAD`, alert with the token-renewal hint. No cleanup. After the owner updates `GOOGLE_OAUTH_REFRESH_TOKEN` and restarts, "Run now" on `db-backup-daily` restores RPO.

### EC-6: `pg_dump` missing or too old

**Condition:** The binary is not on `PATH` / at `BACKUP_PG_DUMP_PATH`, or its major version is lower than the server's (it exits non-zero with `server version mismatch`).
**Expected:** `PG_DUMP_NOT_FOUND` or `PG_DUMP_FAILED` respectively, with the redacted stderr as the reason; one alert; no Drive call.

### EC-7: Two backup runs at once (concurrency)

**Condition:** "Run now" on `db-backup-weekly` while `db-backup-daily` is running (or two tiers' crons overlap after an admin edit).
**Expected:** The second run does nothing, ends `FAILED` with code `ALREADY_RUNNING` and sends one alert; the first run is not affected. "Run now" on the *same* job while it runs is refused earlier with `409 error.job.running` by `ScheduledJobServiceImpl.runNow` (existing behaviour, no alert).

### EC-8: Leftover temp files after a crash

**Condition:** The JVM died mid-run, leaving `funnyapp-*.dump.part` or `.dump` in `work-dir`.
**Expected:** The next run deletes them in step 4 before dumping.

### EC-9: Folder ID wrong or not shared with the OAuth account

**Condition:** Drive returns 404 for the parent folder.
**Expected:** `DRIVE_FOLDER_NOT_FOUND`, step `UPLOAD`, one alert, no cleanup.

### EC-10: Retention lowered in config

**Condition:** `retention.daily` changed from 7 to 3.
**Expected:** On the next daily run, after the upload, the 4 oldest daily files (by `createdTime`) are trashed in one cleanup; the new file is kept. Other tiers are not touched.

### EC-11: Alert email cannot be sent

**Condition:** SMTP fails while sending the alert.
**Expected:** ERROR log `Backup alert email failed` (no secrets); the run result does not change (still `FAILED`, or `SUCCESS` for EC-4).

### EC-12: App stopped while a run is in progress

**Condition:** Shutdown during `pg_dump` or upload.
**Expected:** The `pg_dump` child is destroyed on shutdown (`Process.destroy` in a shutdown path / `finally`); a half-finished Drive upload leaves no file (resumable session never completes); temp files are removed by the next run (EC-8).

### EC-13: Tier subfolder deleted or renamed by hand

**Condition:** The owner trashes or renames `daily/` after its id was cached.
**Expected:** The next upload gets 404 → the cache entry is dropped → `daily/` is resolved again (a new one is created if needed) → the upload is retried once and succeeds. Files in the old (trashed or renamed) folder are no longer managed by the job. A second 404 → `DRIVE_UPLOAD_FAILED`.

---

## Acceptance Criteria

- [ ] With `BACKUP_ENABLED=true` and valid config, "Run now" on `db-backup-daily` creates exactly one file `funnyapp-daily-<UTC>Z.dump` in the `daily/` subfolder (created on the first run) of the Drive folder with app properties `backupApp=funnyapp-db`, `backupTier=daily`, and the job's last result is `SUCCESS` (FR-3, FR-6)
- [ ] The uploaded file restores with `pg_restore --list` and into an empty PostgreSQL 17 DB; row counts of `users`, `video_source`, `payment_transactions` match the source at dump time (PRD Goal 3)
- [ ] After 8 daily runs, exactly 7 tagged daily files remain outside the trash, the oldest is in the trash, and weekly/monthly and untagged files are untouched (Rules 1–4, EC-1)
- [ ] With `BACKUP_ENABLED=false`, a run does nothing, sends no email and records `SUCCESS` (Rule 6)
- [ ] Each failure in EC-2, EC-3, EC-5, EC-6, EC-7, EC-9 records `FAILED` and sends exactly one alert email with the code and hint of FR-8 (Rule 7)
- [ ] A cleanup failure after a good upload records `SUCCESS` and sends one warning email (EC-4)
- [ ] The DB password never appears in the `pg_dump` argv, in any log line or in any email; a unit test feeds stderr that contains the password and asserts `***` (FR-9)
- [ ] No local dump file remains in `work-dir` after a run, success or failure (FR-3 step 8)
- [ ] The three seed rows exist after migration and appear in Admin → Scheduled Jobs with Run now / toggle / cron edit working (FR-2)
- [ ] Video import upload behaviour is unchanged after the `OAuthDriveClientFactory` extraction (existing `OAuthDriveUploaderTest` passes unchanged)
- [ ] Metrics `db_backup_last_success_timestamp_seconds`, `db_backup_size_bytes`, `db_backup_runs_total` appear on `/actuator/prometheus` after a run (FR-11)
- [ ] `BeanConstructorTest` passes (new beans wire) and the app boots locally with `BACKUP_ENABLED` unset
- [ ] Existing flows are unaffected (regression)
- [ ] `mvn verify` passes with the coverage gate met
- [ ] `npm run test` passes (no FE change expected)

---

## Open Questions

None blocking. Decided in the `/spec` interview of 2026-11-17: the alert goes to `BACKUP_ALERT_EMAIL` or else `EMAIL_SENDER`; "Run now" behaves like a scheduled run; edge cases EC-1..EC-4 as listed.

> **Assumption:** Cleanup moves files to the Drive trash instead of deleting them for good (Rule 4), as a safety net. Quota impact is negligible (≈ 134 KB per file).

> **Assumption:** No `nice`/process priority change for `pg_dump`. At about 1 s per run, off-peak, the ADR's "low priority" mitigation is met by the schedule alone. Revisit if dump time passes 1 min.

> **Assumption:** Alert emails are in English: the only reader is the owner, and they are not tied to a user locale.

---

## Version History

| Version | Date | Author | Change |
|---------|------|--------|--------|
| 1.0 | 2026-11-17 | nguyenhuuca | Initial draft from `/spec` |
