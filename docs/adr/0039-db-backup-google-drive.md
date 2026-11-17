# ADR-0039: Scheduled Database Backups to Google Drive (in-process `pg_dump` job)

## Metadata

**Status:** Proposed · **Date:** 2026-11-17 · **Deciders:** nguyenhuuca · **Tags:** ops, backup, disaster-recovery, jobs, google-drive
**Related PRD:** [PRD-db-backup-google-drive](../prd/PRD-db-backup-google-drive.md) · **Supersedes:** N/A · **Superseded By:** N/A

**Tech Strategy:** ✅ Follows Golden Path. Background work runs as a Spring job in `AppScheduler`, scheduled from `scheduled_job` (ADR-0023); Google Drive is reached through the existing OAuth client (ADR-0019); no new library. The only new runtime tool is the PostgreSQL client (`pg_dump` 17), installed on the host by the owner, outside the app build.

---

## Context

All production data sits in one PostgreSQL 17.5 database, in the Docker container `my-postgres` on the single 1 vCPU VM that also runs the API JAR (`/opt/funnyapp`, reached at `localhost:5432`). There is no copy outside that VM. Losing the VM or its disk loses users, collections, watch history, `scheduled_job` and `payment_transactions` (ADR-0038) for good.

The PRD asks for daily, weekly and monthly backups in a Google Drive folder the owner controls, with RPO ≤ 24 h, retention 7 / 4 / 12, an alert email on failure and a manual restore runbook.

Facts checked on prod on 2026-11-17:

| Fact | Value |
|---|---|
| Server | PostgreSQL 17.5 (image `postgres`, untagged = `latest`), port published on `0.0.0.0:5432` |
| Client on host | `postgresql-client-17` from PGDG, installed by hand by the owner |
| Dump size | `pg_dump -Fc` = **134 KB**, about 1 s (DB about 20 MB on disk) |
| Drive auth | OAuth refresh token of the folder owner (`OAuthDriveUploader`, ADR-0019; Service Accounts have no Drive quota). The OAuth app stays in "Testing", so the token expires every 7 days and the owner renews it by hand |
| Jobs | `DynamicJobScheduler` + `JobRegistry` + `scheduled_job` rows, editable in the admin UI (ADR-0023/0024) |
| Alerts | `MailService` (Gmail SMTP) |

This ADR decides **where the backup runs and how it is produced**. It also records two linked choices: one dump per tier, and storing dumps unencrypted in v1.

---

## Decision Drivers

- **Off-site, automatic, RPO ≤ 24 h**: the owner does nothing day to day
- **Visible failures**: a failed run must reach the owner, through the same channel as the other job failures
- **Golden path, no new infrastructure**: reuse the job scheduler, the Drive OAuth credential and `MailService`; no new service, no new secret store
- **Lowest privilege on the VM**: the app must not gain root-equivalent access (for example the Docker socket) just to take a backup
- **Restorable with standard tools**: `pg_restore` from a stock PostgreSQL install, no custom format
- **Light on a 1 vCPU VM**: avoid a repeat of the October virtual-thread starvation incidents
- **Configurable without redeploy**: schedule and on/off per tier, like every other job

---

## Considered Options

### Option 1: In-process Spring job that runs the host `pg_dump` (chosen)

Three `JobRegistry` entries (`db-backup-daily`, `db-backup-weekly`, `db-backup-monthly`) with `scheduled_job` rows. Each run starts `pg_dump -Fc` through `ProcessBuilder` against the app's own datasource (host, port, database, user from the Spring config; password passed as `PGPASSWORD` in the child environment). It writes to a private temp file, uploads it with the existing OAuth Drive client into `BACKUP_FOLDER_ID`, prunes that tier's old files, and emails the owner on any failure.

| Pros | Cons |
|------|------|
| Golden path: same scheduler, admin controls, last-run status and alerting as every other job | Depends on an external binary that the app build does not control (`pg_dump` installed by hand; version can drift) |
| Reuses the Drive OAuth credential and `MailService`; no new secret | The backup runs inside the app process; if the API is down, no backup is taken |
| `-Fc` is the standard, compressed, selectively restorable format | A child process needs careful handling (timeout, exit code, stderr capture without secrets) |
| No Docker socket access; the app only needs the DB credentials it already has | |
| Schedule changes and on/off per tier without a redeploy | |

### Option 2: `docker exec my-postgres pg_dump` from the app (or a host script)

Use the `pg_dump` bundled in the server container, so the client version always matches.

| Pros | Cons |
|------|------|
| Client and server versions always match | The app user needs access to the Docker socket, which is root-equivalent on the VM: violates least privilege (CWE-250) |
| Nothing to install on the host | Ties the backup to the container name and to Docker; breaks if the DB moves to a managed service |
| | Same in-process scheduling questions as Option 1, with more privilege |

### Option 3: OS cron + shell script (`pg_dump` + `rclone`) on the VM

| Pros | Cons |
|------|------|
| Independent of the API: runs even if the app is down | Outside the golden path ("Background Jobs = Spring `@Scheduled`"); invisible in the admin jobs view |
| Very little code | New tool (`rclone`) and a second copy of the Drive OAuth secret on disk, outside the app's env handling |
| | Alerting, retention and logging rebuilt in shell; no tests, no coverage gate |
| | Schedule changes need SSH |

### Option 4: GitHub Actions scheduled workflow that SSHes in, dumps and uploads

| Pros | Cons |
|------|------|
| Runs off the VM; GitHub keeps logs and failure notifications | Puts the prod SSH key and the DB password in GitHub secrets for a public repo's workflow: wider attack surface |
| | The dump crosses an extra hop (VM → runner → Drive) |
| | Scheduled workflows are best-effort (delays, auto-disabled after 60 days without repo activity) |

### Option 5: Pure-Java export through JDBC (no `pg_dump`)

| Pros | Cons |
|------|------|
| No external binary | A home-made format that cannot be restored with `pg_restore`; misses sequences, constraints, functions; a serious correctness risk for DR |
| | Large effort to build and to keep in step with Liquibase changes |

---

## Decision Outcome

**Chosen Option:** Option 1, an in-process Spring job that runs the host `pg_dump`.

**Rationale:** It is the only option that meets *visible failures*, *golden path* and *configurable without redeploy* together, because it rides on the scheduler, admin jobs view and alerting that already exist. Option 2 gets automatic version matching at the price of root-equivalent Docker access. That breaks *lowest privilege* and is not worth it while a pinned host client plus a version check covers the same risk. Option 3 is the strongest fallback, since it survives the API being down, but it duplicates the Drive secret, leaves the golden path and has no tests. Option 4 widens secret exposure for a public repo. Option 5 fails *restorable with standard tools*.

The accepted trade-off is "no backup while the API is down". The API is the product, so an outage is noticed and fixed long before the 24 h RPO matters, and the next scheduled run catches up because every run is a full dump.

### Linked choices

1. **One dump per tier, not promotion of the daily file.** At 134 KB and about 1 s per dump, a separate `pg_dump` for the weekly and monthly runs costs nothing measurable. Each tier stays independent: a failed daily run does not leave the week without a weekly file, and there is no Drive copy or re-tag step to get wrong. Tiers are scheduled at different times (03:00 / 03:30 / 04:00, `Asia/Ho_Chi_Minh`) and share one guard so they never overlap.
2. **Retention by tier through Drive `appProperties`.** Each file is tagged `backupTier=<tier>`. Prune runs only after a successful upload, and only within that tier, keeping the newest 7 / 4 / 12.
3. **No encryption in v1 (accepted risk).** Dumps contain PII (emails) and payment records. They sit in a private, unshared folder of the owner's Google account, which has 2FA. Client-side encryption is deferred to v2 by the owner's decision (PRD Out of Scope). This must be revisited before the folder is ever shared or the data grows in sensitivity.
4. **Expiring OAuth token accepted.** The OAuth app stays in "Testing", so the refresh token expires every 7 days. A run that fails with HTTP 401 / `invalid_grant` sends an alert that names the cause, and the next run after renewal restores RPO.

### Quantified Impact

| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| Off-site copies | 0 | up to 23 (7 + 4 + 12) | |
| RPO after VM loss | total loss | ≤ 24 h | ≤ 7 days if the OAuth token lapses unnoticed (alerted) |
| Drive storage | 0 | ≈ 3 MB | 23 × 134 KB; revisit retention if the dump passes about 100 MB |
| VM load per run | — | about 1 s of `pg_dump` CPU, ~134 KB upload | at 03:00–04:00 local |
| New runtime dependencies | — | `pg_dump` 17 on host | no new Maven dependency |

---

## Consequences

**Positive:**
- First off-site copy of production data; the VM is no longer a single point of total data loss.
- Backups show up in the admin jobs view next to other jobs, with the same last-run status and the same on/off and cron controls.
- Standard `-Fc` dumps restore with stock `pg_restore` and allow single-table restores after operator mistakes.
- The owner OAuth Drive client becomes a shared factory, which later Drive features can reuse.

**Negative:**
- The app now depends on an external binary that it does not ship. A missing or too old `pg_dump` fails the job (alerted), but nothing in CI catches it.
- `OAuthDriveUploader` is hard-wired to the video folder (`AppConstant.FOLDER_ID`) and `video/mp4`. Instead of generalising it, the OAuth client construction moves into a shared `OAuthDriveClientFactory`, and a separate `BackupStore` handles upload, list and trash for backups. The video import contract stays unchanged (spec FR-6).
- Dumps are unencrypted on Drive (accepted risk, see linked choice 3).
- No backup is taken while the API is down.
- The OAuth token expires every 7 days; RPO depends on the owner renewing it on time.

**Risks:**
- *`pg_dump` / server version drift*: the server image is untagged (`latest`), so recreating the container could jump to PostgreSQL 18 and leave the host client too old. Mitigation: pin `postgres:17` on the next recreate; the job fails loudly on a non-zero `pg_dump` exit; upgrade the client together with any server major upgrade.
- *Secrets leaking through the child process*: the DB password could leak via the command line (`ps`), `pg_dump` stderr or the alert email. Mitigation: password only in the child's environment; stderr truncated and checked for secrets before logging or emailing (CWE-532).
- *Load on the 1 vCPU VM*: Mitigation: off-peak schedule, tiers never overlap, the child runs at low priority, a hard timeout kills a hung `pg_dump`.
- *Backups that do not restore*: Mitigation: a restore drill into a scratch database is part of the Definition of Done and is repeated quarterly (PRD Goal 3).
- *Postgres port published on `0.0.0.0:5432`* (found while checking the facts above): outside this ADR's scope but higher priority than it. Block port 5432 at the cloud firewall, or rebind to `127.0.0.1:5432` when the container is recreated (after a fresh backup).

---

## Validation

- [ ] A manual dump from prod with the host client succeeds (done 2026-11-17: 134 KB `-Fc`)
- [ ] Restore drill: a Drive file restored with `pg_restore` into an empty PostgreSQL 17 database, key table row counts match, the app starts against it
- [ ] Failure paths alert once: `pg_dump` missing, wrong password, wrong `BACKUP_FOLDER_ID`, expired OAuth token
- [ ] Retention verified: after more than 7 daily runs (test with a short cron), only the newest 7 daily files remain and no other tier was touched
- [ ] Security review: no secret in the command line, logs or email; temp file mode `600` and deleted after each run
- [ ] Tech Strategy alignment confirmed (no new library; job through `JobRegistry` / `scheduled_job`)
- [ ] Spec + sequence diagrams: `docs/specs/spec-db-backup-google-drive.md`, `docs/sequence/sequence-db-backup-google-drive.md`
- [ ] Plan: `docs/plans/plan-db-backup-google-drive.md`

---

## Links

- [PRD: Scheduled Database Backups to Google Drive](../prd/PRD-db-backup-google-drive.md)
- [Implementation Plan](../plans/plan-db-backup-google-drive.md) (to be written)
- [ADR-0019: Admin Video Import](./0019-admin-video-import.md) (OAuth Drive uploader, why not a Service Account)
- [ADR-0023: Dynamic Cron Jobs](./0023-dynamic-cron-jobs.md) · [ADR-0024: Cron Description Library](./0024-cron-description-library.md)
- [ADR-0038: Payment Provider Abstraction](./0038-payment-provider-abstraction.md) (`payment_transactions`, data that must survive a disaster)

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-11-17 | nguyenhuuca | Initial draft (Proposed) |
