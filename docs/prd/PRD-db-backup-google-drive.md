# PRD: Scheduled Database Backups to Google Drive

<!--
Product Requirements Document
Filename: docs/prd/PRD-db-backup-google-drive.md
Owner: nguyenhuuca
Handoff to: Architect (/architect)
Related Skills: scope, planning-artifacts
-->

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-11-17
**Version:** 1.0
**Tracking:** N/A (add a `docs/tracking.md` row once approved)
**PR-FAQ:** N/A (internal ops feature, single owner, no end-user surface)
**Stakeholders:** nguyenhuuca (owner, ops)

## Problem Statement

The whole production dataset of Funny Movies (users, `video_source`, collections, watch history, `scheduled_job`, `payment_transactions`, ...) lives in one PostgreSQL database next to a single 1 vCPU VM. There is no off-site copy. If the VM disk, the database host, or the database itself is lost or corrupted, all data is gone and cannot be rebuilt: Drive video files would survive, but every row that ties them to users, collections and payments would not.

The owner needs automatic, off-site backups at daily, weekly and monthly granularity, stored in a Google Drive folder the owner already controls, so the database can be restored after a disaster with at most one day of data loss.

### Evidence

**Quantitative Evidence:**
- Backup copies outside the VM today: **0**. Nothing in `api/`, the deploy scripts or CI runs `pg_dump` or any snapshot (searched 2026-11-17).
- Production runs on one VM with 1 vCPU (see the prod virtual-thread deadlock incidents of 2026-10-15 and 2026-10-24). One host means one failure domain for app and data.
- Since ADR-0037/0038 the database holds payment records (`payment_transactions`). Losing them breaks reconciliation with the bank and SePay.

**Qualitative Evidence:**
- Owner request (2026-11-17): "I need SQL backups daily, weekly and monthly, stored in a Google Drive folder."
- The owner chose disaster recovery as the main driver. Restoring from operator mistakes and long-term archiving are secondary benefits.

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Bound data loss after a disaster | RPO (age of newest good backup on Drive) | ≤ 24 h |
| Keep a useful history without filling Drive | Backups kept per tier | 7 daily, 4 weekly, 12 monthly (older files pruned automatically) |
| Backups are actually restorable | Restore drill from a Drive file into a scratch database | Succeeds; row counts of key tables match the source at dump time; repeated at least once per quarter |
| Failures do not go unnoticed | Time from a failed run to the owner knowing | Alert email sent in the same run; zero silent failures |
| No impact on users | Request latency / errors during the backup window | No 5xx and no visible slowdown while the job runs |

## User Stories

### Owner / Ops (only persona)

- As the owner, I want a database backup to be created and uploaded to my Google Drive backup folder every day without my doing anything, so that I lose at most one day of data if the server dies.
  - Acceptance: after the daily run, a new compressed dump file named with the tier and a UTC timestamp exists in the Drive backup folder, and the job's last run shows success.
- As the owner, I want separate weekly and monthly backups kept longer than the daily ones, so that I can go back further when a problem is found late (for example a bad migration noticed after a week).
  - Acceptance: weekly and monthly files are tagged by tier; after N runs, Drive holds at most 7 daily, 4 weekly and 12 monthly files, oldest removed first.
- As the owner, I want an email when a backup run fails (dump, upload or prune), so that I can fix it before the next run.
  - Acceptance: forcing a failure (for example a wrong folder ID) sends one alert email with the tier, the failed step and a short reason; it contains no secrets or connection strings.
- As the owner, I want a written restore runbook, so that I can restore under pressure without working out the steps from scratch.
  - Acceptance: following the runbook, a dump downloaded from Drive restores into an empty database with `pg_restore`, and the app starts against it.
- As the owner, I want to change the backup schedule or turn a tier off without a redeploy, the same way I manage other jobs.
  - Acceptance: the three tiers appear as `scheduled_job` rows and follow the existing admin job controls (ADR-0023/0024).

## Requirements

### Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | Create a full logical dump of the production database with `pg_dump` (custom format `-Fc`, which is compressed) | Must Have | Called from the API with `ProcessBuilder`; the password goes to the process through `PGPASSWORD` in its environment, never on the command line or in logs |
| FR-2 | Upload the dump to a dedicated Google Drive folder set by a new env var `BACKUP_FOLDER_ID` | Must Have | Reuse the OAuth refresh-token Drive client (`OAuthDriveUploader`, ADR-0019), because Service Accounts have no Drive quota. Separate from the video `FOLDER_ID` |
| FR-3 | Three tiers, daily, weekly and monthly, each scheduled as its own `scheduled_job` row registered in `JobRegistry` | Must Have | Default cron in `Asia/Ho_Chi_Minh`: daily 03:00; weekly Sunday 03:30; monthly on the 1st at 04:00 (low traffic) |
| FR-4 | File name encodes tier and time: `funnyapp-<tier>-<yyyyMMdd-HHmmss>Z.dump`; the tier is also stored in Drive `appProperties` (`backupTier`) | Must Have | Pruning selects by `appProperties`, not by parsing names |
| FR-5 | Retention pruning after each successful upload: keep the newest 7 daily, 4 weekly, 12 monthly; delete older files of that tier | Must Have | Prune only after the new upload succeeds, so a failing run never shrinks the history |
| FR-6 | Send an alert email to the owner through the existing `MailService` when any step fails | Must Have | One email per failed run; no secrets, hosts or stack traces in the body |
| FR-7 | Delete the local temp dump file after every run, success or failure | Must Have | The 1 vCPU VM has limited disk |
| FR-8 | Only one backup run at a time; a run that starts while another is still running is skipped and logged | Must Have | `DynamicJobScheduler` already guards each job from overlapping with itself; tiers also need a guard against each other |
| FR-9 | Restore runbook in `docs/guides/` (download from Drive, `pg_restore` into an empty DB, verify, point the app at it) | Must Have | Manual restore is the v1 recovery path |
| FR-10 | Log and expose the outcome of each run (tier, size, duration, Drive file ID) in the existing job last-run status | Should Have | Visible through the existing admin scheduled-jobs view; no new UI |
| FR-11 | Micrometer metrics: `db_backup_last_success_timestamp{tier}`, `db_backup_size_bytes{tier}`, `db_backup_failures_total{tier}` | Nice to Have | Enables a later Grafana alert on staleness (RPO breach) |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Performance | The run must not block request threads: it runs on the scheduler, streams to a temp file, and uploads in resumable chunks (8 MB, like video upload) |
| NFR-2 | Availability / RPO | Newest daily backup on Drive is ≤ 24 h old at any time |
| NFR-3 | Security | No secrets in code, logs or emails (CWE-532); credentials only from env (`DB_PASS`, `GOOGLE_OAUTH_*`); the Drive backup folder is private to the owner; dumps are not encrypted in v1 (accepted risk, see Risks) |
| NFR-4 | Reliability | A failure in one step leaves no partial file on Drive and does not run the prune step |
| NFR-5 | Compatibility | `pg_dump` major version ≥ the PostgreSQL server major version |
| NFR-6 | Testability | Dump, upload and prune sit behind small interfaces so unit tests run without Postgres or Drive; coverage stays ≥ the gate in `api/.coverage-threshold` |

## Scope

### In Scope

- Scheduled daily, weekly and monthly full logical dumps (`pg_dump -Fc`) of the production database.
- Upload to a dedicated Google Drive folder through the existing OAuth Drive credential.
- Retention pruning at 7, 4 and 12 files per tier.
- Failure alert email to the owner.
- Liquibase changeset that seeds the three `scheduled_job` rows (`api/src/main/resources/db/changelog/sql/`).
- New config: `BACKUP_FOLDER_ID` and an on/off flag (disabled by default locally); `.env.example` updated.
- A startup/run check that `pg_dump` is on the `PATH` (or at `BACKUP_PG_DUMP_PATH`), with a clear alert when it is missing. Installing it is not part of this feature (see Dependencies).
- Restore runbook, plus one restore drill done before the feature is marked done.

### Out of Scope

- Automatic restore or a "Restore" button (v2).
- An admin UI for listing or downloading backups; the owner uses Google Drive directly (v2).
- Encrypting the dump files (v2; v1 relies on the Drive folder being private).
- Backing up video files, HLS segments, thumbnails or the local video cache; they already live on Drive or can be rebuilt.
- Point-in-time recovery (WAL archiving) and RPO under 24 h.
- Backing up databases other than the app database (`funnyapp`).

## Dependencies

| Dependency | Owner | Status | Risk |
|------------|-------|--------|------|
| Google OAuth refresh token with the `drive` scope (`GOOGLE_OAUTH_CLIENT_ID/SECRET/REFRESH_TOKEN`) | nguyenhuuca | Exists (ADR-0019) | Med: if the token is revoked or expires, backups fail; FR-6 alert covers it |
| Dedicated private Drive backup folder + `BACKUP_FOLDER_ID` env on prod | nguyenhuuca | Folder created 2026-11-17; the ID lives only in the prod env, not in the repo (public) | Low |
| `postgresql-client-17` (`pg_dump` 17.x) on the prod host, installed from the PGDG apt repo; the server is PostgreSQL **17.5** in Docker container `my-postgres`, reached at `localhost:5432` (checked 2026-11-17) | nguyenhuuca (installs it manually, outside this feature) | Installed; manual test dump OK (134 KB) | Med: a version mismatch makes `pg_dump` refuse to run; the job alerts if `pg_dump` is missing or fails |
| Dynamic job scheduler + `scheduled_job` table + `JobRegistry` | Codebase | Exists (ADR-0023/0024) | Low |
| `MailService` (Gmail SMTP) for alerts | Codebase | Exists | Low |
| Google Drive storage quota on the owner account | nguyenhuuca | Unknown free space | Med: see Open Questions |

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Dumps stored unencrypted on Drive contain PII (emails) and payment records; a leaked Drive account or share link exposes them | L | H | Private folder with no sharing; owner account has 2FA; encryption planned for v2; record this as an accepted risk in the ADR |
| `pg_dump` on the 1 vCPU VM slows the app or triggers the virtual-thread starvation seen in the October incidents | M | M | Run at 03:00–04:00 local; external process at low priority (`nice`); stream to disk, not to memory; tiers do not overlap (FR-8) |
| `pg_dump` missing or version mismatch with the server (installed by hand, so it can drift) | M | M | The owner installs a client whose major version is ≥ the server's; the job checks `pg_dump --version` and the dump exit code, and alerts on failure |
| OAuth refresh token expires every 7 days (app stays in "Testing"; owner renews it by hand); a missed renewal breaks RPO | H | H | Accepted by the owner. Alert email on 401 / `invalid_grant` names the cause ("Drive token expired"); runbook step for re-issuing the token; the next run after renewal catches up, since each tier is a full dump |
| Drive quota runs out, so uploads fail (unlikely: ~20 MB DB, at most 23 files) | L | M | Retention caps the file count; the alert includes the Drive error; metric FR-11 tracks dump size |
| The prod DB container uses the untagged image `postgres` (= `latest`); recreating it after a `docker pull` can jump to PostgreSQL 18, which cannot open the 17 data directory and makes the host `pg_dump` 17 too old | M | H | Pin the image to `postgres:17` the next time the container is recreated; take a fresh backup before any major upgrade, then upgrade `postgresql-client` at the same time |
| Backups exist but cannot be restored | M | H | Mandatory restore drill before Done, repeated quarterly (Goal 3) |
| Local temp files fill the VM disk after crashes | L | M | Fixed temp directory, cleaned at the start of each run as well as at the end (FR-7) |

## Open Questions

- [x] Do weekly and monthly runs take their own `pg_dump`, or promote that day's daily dump? **Each tier takes its own dump** (ADR-0039, linked choice 1): about 1 s and 134 KB per dump, and the tiers stay independent.
- [x] Current database size? About **20 MB** on disk (owner estimate). A manual test on prod on 2026-11-17 (`pg_dump` 17 → PostgreSQL 17.5, `-h localhost`, `-Fc`) produced a **134 KB** dump in about a second. Retention keeps at most 23 files, so Drive usage stays well under 1 GB and temp disk and quota are not a concern. Confirm later with `SELECT pg_size_pretty(pg_database_size('funnyapp'))`.
- [x] Is the Google OAuth app in "Production" publishing status? No, it stays in **"Testing"** (2026-11-17). The refresh token expires after 7 days and the owner renews it by hand. Accepted: backups can fail until the token is renewed, and the `DRIVE_AUTH_FAILED` alert email tells the owner when.
- [x] Who installs `pg_dump`? The owner installs it manually on the prod host (answered 2026-11-17). The code only looks it up (`PATH` or `BACKUP_PG_DUMP_PATH`) and does not change `api/Dockerfile`.
- [ ] Should the alert email go only to the owner address, or to a configurable list (`BACKUP_ALERT_EMAIL`)?

> **Assumption:** Backups run inside the API process (Spring job via `JobRegistry`/`scheduled_job`), following the tech-strategy "Background Jobs = Spring `@Scheduled` in `AppScheduler`" golden path, not as an OS cron or a GitHub Actions job.

> **Assumption:** The production database is about 20 MB (owner estimate), so a full logical dump finishes in seconds and fits in memory, temp disk and Drive quota with a wide margin. If it grows past roughly 1 GB, revisit the retention and the in-process approach.

> **Assumption:** Times are scheduled in `Asia/Ho_Chi_Minh` (the owner's timezone); file names use UTC.

## Appendix

### Mockups/Wireframes

N/A, no UI. The tiers appear as rows in the existing admin scheduled-jobs view.

### Research

- ADR-0019 `docs/adr/0019-admin-video-import.md`: why Drive uploads use an OAuth refresh token (Service Accounts have no Drive quota).
- ADR-0023 / ADR-0024: dynamic job scheduling from the `scheduled_job` table and cron text in the admin UI.
- Existing code: `OAuthDriveUploader`, `DriveUploader`, `DynamicJobScheduler`, `JobRegistry`, `AppScheduler`, `MailService`.
- PostgreSQL docs: `pg_dump -Fc` (custom format, compressed, restorable selectively with `pg_restore`).

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | | Pending |
| Engineering | nguyenhuuca | | Pending |
| Design | N/A (no UI) | | N/A |

---

## Next Steps & Handoffs

After PRD approval:

1. [ ] **Architect Review**: dump-per-tier vs promotion, in-process vs external runner, the accepted risk of unencrypted dumps
   - Trigger: `/architect`
   - Output: ADR (`docs/adr/0039-db-backup-google-drive.md`)

2. [ ] **UI/UX Designer**: N/A (no UI in v1)

3. [ ] **Spec + sequence diagrams**: `/spec`
   - Output: `docs/specs/spec-db-backup-google-drive.md`, `docs/sequence/sequence-db-backup-google-drive.md`

4. [ ] **Engineering Estimate**: Implementation Plan (`docs/plans/plan-db-backup-google-drive.md`)

5. [ ] **Track it**: add the feature row to `docs/tracking.md`
   - Then decompose: `/swarm-plan docs/prd/PRD-db-backup-google-drive.md`

**Related Artifacts**:
- ADR: [ADR-0039](../adr/0039-db-backup-google-drive.md) (Proposed)
- Design Spec: N/A
- Implementation Plan: TBD

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-17 | nguyenhuuca | Initial draft from `/scope` |
