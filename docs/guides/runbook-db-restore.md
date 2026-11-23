# Runbook: Restore the Database from a Google Drive Backup

Disaster-recovery path for the scheduled backups of [ADR-0039](../adr/0039-db-backup-google-drive.md) ([spec](../specs/spec-db-backup-google-drive.md)). v1 has no restore code: these steps are done by hand.

**Backups:** private Drive folder `BACKUP_FOLDER_ID`, subfolders `daily/` (newest 7), `weekly/` (newest 4), `monthly/` (newest 12). File name `funnyapp-<tier>-<yyyyMMdd-HHmmss>Z.dump` (UTC), format `pg_dump -Fc`. Files the job cleaned up stay in the Drive trash for 30 days.

---

## 1. Pick the backup

1. Open the backup folder on Google Drive.
2. Choose the newest file from before the incident:
   - VM or disk lost: the newest file in `daily/`.
   - Bad migration or a wrong delete found late: the newest `daily/`, `weekly/` or `monthly/` file from **before** the bad change.
3. Download it. Do not share it or leave it in a shared place: the file contains PII and payment records unencrypted.

## 2. Check the file

On a host with PostgreSQL 17 client tools:

```bash
pg_restore --list funnyapp-daily-20261117-200000Z.dump | head -20   # must list TABLE DATA rows
```

An error here means the file is damaged. Pick an older one.

## 3. Prepare an empty PostgreSQL 17

The server major version must be **≥ 17**, the version the dump came from.

New VM, same setup as today (Docker). Pin the image tag and bind only to localhost:

```bash
docker run -d --name my-postgres --restart unless-stopped \
  -e POSTGRES_PASSWORD='<DB_PASS>' \
  -v pgdata:/var/lib/postgresql/data \
  -p 127.0.0.1:5432:5432 postgres:17
```

Then create the empty database:

```bash
export PGPASSWORD='<DB_PASS>'          # or: read -s PGPASSWORD && export PGPASSWORD
createdb -h localhost -U postgres funnyapp
```

## 4. Restore

```bash
pg_restore -h localhost -U postgres --dbname funnyapp --no-owner --exit-on-error \
  funnyapp-daily-20261117-200000Z.dump
unset PGPASSWORD
```

- Restoring over an existing database: restore into a **new** database first (`createdb funnyapp_restore`), check it, then switch the app to it. Never drop the live database before the restore has been checked.
- Restoring a single table after an operator mistake:
  ```bash
  pg_restore --dbname funnyapp_restore --no-owner -t <table> file.dump
  ```
  Then copy the rows you need into the live database.

## 5. Check the restore

```bash
psql -h localhost -U postgres -d funnyapp -c "
  SELECT 'users' t, count(*) FROM users
  UNION ALL SELECT 'video_source', count(*) FROM video_source
  UNION ALL SELECT 'payment_transactions', count(*) FROM payment_transactions
  UNION ALL SELECT 'databasechangelog', count(*) FROM databasechangelog;"
```

The counts must be plausible for the backup time. `databasechangelog` must be non-empty, so that Liquibase sees the changesets as already applied.

## 6. Start the app

1. Point `DB_HOST` and `DB_PASS` at the restored database, then start the API (`startServer.sh` on the VM, or a Helm upgrade).
2. Check that `/actuator/health` is `UP`. At startup, Liquibase applies only changesets newer than the backup.
3. Smoke test: log in, play a video, open Admin → Payments.
4. Admin → Scheduled Jobs → **Run now** on `db-backup-daily`. This gives the new server a first backup.

---

## Troubleshooting the backup job

The alert email names the code and gives a hint. The common cases:

| Code | Cause | Fix |
|---|---|---|
| `DRIVE_AUTH_FAILED` | The OAuth app is in "Testing", so the refresh token expires every 7 days | Issue a new token, update `GOOGLE_OAUTH_REFRESH_TOKEN`, restart, then **Run now** |
| `PG_DUMP_NOT_FOUND` | `pg_dump` is not on `PATH` | `apt-get install postgresql-client-17` (PGDG repo), or set `BACKUP_PG_DUMP_PATH` |
| `PG_DUMP_FAILED` with `server version mismatch` | The server was upgraded past the client (untagged `postgres` image) | Install `postgresql-client-<server major>`; pin the server image tag |
| `NOT_CONFIGURED` | `BACKUP_FOLDER_ID` or `GOOGLE_OAUTH_*` is missing | Set the env vars and restart |
| `DRIVE_FOLDER_NOT_FOUND` | Wrong folder id, or the folder is not owned by or shared with the OAuth account | Fix `BACKUP_FOLDER_ID` |
| `ALREADY_RUNNING` | Two tiers or Run now calls overlapped | Usually harmless; run the skipped tier again |
| `PRUNE_FAILED` (warning) | Drive listing or trash failed after a good upload | Nothing urgent; the next run cleans up |

Check that the client is new enough: `pg_dump --version` must be ≥ `docker exec my-postgres postgres -V`.

**Restore drill:** once per quarter (PRD Goal 3), run steps 1–5 into a scratch container (`docker run --rm -p 127.0.0.1:5433:5432 -e POSTGRES_PASSWORD=x postgres:17`) and note the date and the counts in `docs/tracking.md`.
