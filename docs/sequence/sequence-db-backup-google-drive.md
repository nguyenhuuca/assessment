# Sequence Diagrams: Scheduled Database Backups to Google Drive

- **PRD:** [PRD-db-backup-google-drive](../prd/PRD-db-backup-google-drive.md)
- **ADR:** [0039-db-backup-google-drive](../adr/0039-db-backup-google-drive.md)
- **Spec:** [spec-db-backup-google-drive](../specs/spec-db-backup-google-drive.md)
- **Status:** Draft

## 1. Scheduled backup, happy path

The cron of `db-backup-daily` fires (03:00 `Asia/Ho_Chi_Minh`). The run dumps the database with the host `pg_dump`, uploads it to the Drive backup folder with the owner OAuth client, and trashes daily files beyond the newest 7 (spec FR-3..FR-7).

```mermaid
sequenceDiagram
    autonumber
    participant SCH as DynamicJobScheduler
    participant APP as AppScheduler.dbBackupDaily()
    participant S as DbBackupServiceImpl
    participant FS as Local work-dir
    participant PG as pg_dump (host process)
    participant DB as PostgreSQL 17 (my-postgres)
    participant ST as DriveBackupStore
    participant GD as Google Drive API (OAuth)

    SCH->>APP: execute("db-backup-daily") [per-job guard]
    APP->>S: run(DAILY)
    S->>S: lock.tryLock() ✔ · enabled ✔ · folder-id + OAuth configured ✔
    S->>FS: mkdir work-dir · delete leftover funnyapp-*.dump*
    S->>PG: ProcessBuilder [--host --port --username --dbname --format=custom --no-password --file x.part]<br/>env PGPASSWORD
    PG->>DB: COPY tables (consistent snapshot)
    DB-->>PG: data
    PG-->>FS: x.part (mode 600)
    PG-->>S: exit 0 (within 10 min)
    S->>FS: check size > 5 B and magic "PGDMP" · rename to funnyapp-daily-YYYYMMDD-HHMMSSZ.dump
    S->>ST: upload(file, name, DAILY)
    alt "daily" subfolder id not cached
        ST->>GD: files.list q="BACKUP_FOLDER_ID in parents, name='daily', folder, not trashed"
        GD-->>ST: [] or [folder]
        opt not found
            ST->>GD: files.create folder "daily" parents=[BACKUP_FOLDER_ID]
            GD-->>ST: folderId
        end
        ST->>ST: cache daily → folderId
    end
    ST->>GD: files.create (resumable) parents=[daily folderId]<br/>appProperties{backupApp=funnyapp-db, backupTier=daily}
    GD-->>ST: fileId
    ST-->>S: fileId
    S->>ST: list(DAILY)
    ST->>GD: files.list q="daily folderId in parents, not trashed, backupApp, backupTier=daily" orderBy createdTime desc
    GD-->>ST: [f0 (new), f1 … f7]
    ST-->>S: files
    loop each file at index ≥ 7
        S->>ST: trash(fileId)
        ST->>GD: files.update trashed=true
    end
    S->>FS: delete local dump (finally)
    S->>S: metrics last_success, size_bytes, runs_total{success} · INFO summary · unlock
    S-->>APP: return
    APP-->>SCH: return → lastRun = SUCCESS
```

## 2. Dump or upload fails → FAILED + alert email

Any failure before the cleanup (config, `pg_dump`, dump check, Drive upload) stops the run: nothing is trashed, one alert email goes out, and the exception reaches `DynamicJobScheduler`, which records `FAILED` (spec Rule 2, Rule 7, EC-2, EC-3, EC-5, EC-6, EC-9).

```mermaid
sequenceDiagram
    autonumber
    participant SCH as DynamicJobScheduler
    participant S as DbBackupServiceImpl
    participant PG as pg_dump
    participant ST as DriveBackupStore
    participant GD as Google Drive API
    participant M as MailService (Gmail SMTP)
    actor O as Owner

    SCH->>S: run(DAILY)
    alt pg_dump missing / exit ≠ 0 / timeout 10 min
        S->>PG: start
        PG-->>S: IOException · exit 1 + stderr · no exit → destroyForcibly()
        S->>S: BackupException(PG_DUMP_NOT_FOUND | PG_DUMP_FAILED | PG_DUMP_TIMEOUT, step DUMP)<br/>stderr redacted (password → ***)
    else OAuth refresh token expired (OAuth app in Testing, 7-day expiry)
        S->>ST: upload(...)
        ST->>GD: files.create
        GD-->>ST: 401 / invalid_grant
        ST-->>S: BackupException(DRIVE_AUTH_FAILED, step UPLOAD)
    end
    S->>S: delete local files · runs_total{failed} · unlock (finally)
    S->>M: sendSimpleMail(BACKUP_ALERT_EMAIL or EMAIL_SENDER,<br/>"[Funny Movies] DB backup FAILED (daily): CODE", tier/step/code/time/reason/hint)
    M-->>O: alert email
    S-->>SCH: throw BackupException
    SCH->>SCH: log error · lastRun = FAILED
```

## 3. Upload OK, cleanup fails → SUCCESS + warning

The new backup is safe on Drive, so the run counts as a success. The surplus files stay until a later run cleans them up (spec EC-4).

```mermaid
sequenceDiagram
    autonumber
    participant SCH as DynamicJobScheduler
    participant S as DbBackupServiceImpl
    participant ST as DriveBackupStore
    participant GD as Google Drive API
    participant M as MailService

    SCH->>S: run(WEEKLY)
    S->>ST: upload(...)
    ST->>GD: files.create
    GD-->>ST: fileId
    S->>ST: list(WEEKLY)
    ST->>GD: files.list
    GD-->>ST: 5xx / network error
    ST-->>S: BackupException(PRUNE_FAILED, step PRUNE)
    S->>S: last_success + size metrics · runs_total{prune_warning}
    S->>M: "[Funny Movies] DB backup WARNING (weekly): PRUNE_FAILED"
    S-->>SCH: return normally
    SCH->>SCH: lastRun = SUCCESS
```

## 4. Admin "Run now", and the cross-tier lock

An admin triggers a run from Admin → Scheduled Jobs. It behaves exactly like a scheduled run (spec Rule 8). A second backup tier started while one is running is refused by the shared lock (Rule 5, EC-7).

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin (SUPER_ADMIN + ADMIN bit)
    participant FE as Browser (AdminJobsTab.jsx)
    participant API as AdminJobController
    participant SJ as ScheduledJobServiceImpl
    participant SCH as DynamicJobScheduler
    participant S as DbBackupServiceImpl
    participant M as MailService

    A->>FE: Run now · db-backup-daily
    FE->>API: POST /v1/funny-app/admin/jobs/db-backup-daily/runs (JWT)
    API->>SJ: runNow("db-backup-daily")
    SJ->>SCH: runNow(name)
    alt same job already running
        SCH-->>SJ: false
        SJ-->>API: CustomException 409 error.job.running
        API-->>FE: 409 ResultErrorInfo
    else accepted
        SCH-->>SJ: true (scheduled now)
        SJ-->>API: ScheduledJobDto
        API-->>FE: 202 ResultObjectInfo<ScheduledJobDto>
        SCH->>S: run(DAILY) (async, as in diagram 1)
    end

    Note over SCH,S: Meanwhile the weekly cron (or another Run now) fires
    SCH->>S: run(WEEKLY)
    S->>S: lock.tryLock() ✘ → BackupException(ALREADY_RUNNING)
    S->>M: "[Funny Movies] DB backup FAILED (weekly): ALREADY_RUNNING"
    S-->>SCH: throw → lastRun(db-backup-weekly) = FAILED
```

## 5. Backups switched off (local / dev)

The seed rows are enabled in every environment; the env switch `BACKUP_ENABLED` (default `false`) keeps local machines quiet (spec Rule 6).

```mermaid
sequenceDiagram
    autonumber
    participant SCH as DynamicJobScheduler
    participant S as DbBackupServiceImpl
    SCH->>S: run(DAILY)
    S->>S: lock ✔ · app.backup.enabled = false
    S->>S: INFO "DB backup disabled" · runs_total{disabled} · unlock
    S-->>SCH: return → lastRun = SUCCESS (no dump, no Drive call, no email)
```

## 6. Manual restore (runbook, outside the app)

Disaster recovery path for v1 (PRD FR-9). There is no restore code in the app.

```mermaid
sequenceDiagram
    autonumber
    actor O as Owner
    participant GD as Google Drive (backup folder)
    participant H as New / repaired host
    participant PG as PostgreSQL 17
    participant API as Funny Movies API

    O->>GD: pick the newest good funnyapp-<tier>-*.dump
    GD-->>O: download
    O->>H: copy the file (scp)
    O->>PG: createdb funnyapp (empty)
    O->>PG: pg_restore --no-owner --dbname funnyapp file.dump
    PG-->>O: restored
    O->>PG: check row counts (users, video_source, payment_transactions)
    O->>API: start with DB_HOST pointing to the restored DB
    API->>PG: Liquibase: changelog already applied → no-op
    API-->>O: /actuator/health UP
```
