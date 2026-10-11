# Plan: Admin Drive Force Sync (Google Drive ↔ local ↔ DB)

<!--
Implementation Plan
Filename: docs/plans/plan-admin-drive-force-sync.md
Owner: Builder (/builder)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer), Security Auditor (/security-auditor)
-->

## Overview

**Status:** In Review (FS-1..FS-4 implemented 2026-10-11; FS-5 prod E2E pending)
**Author:** nguyenhuuca
**Date:** 2026-10-11
**Size:** Small (~2.5 d) → plan only, no PRD/ADR (decision below is a Two-Way Door except the unique index, gated by FS-0)

Admin tab **DRIVE_SYNC** shows every video in the Drive folder next to its local file and DB row.
**ADMIN** can view the tab (states + run progress). Only **SUPER_ADMIN** can select files and click **Force Sync**: the server downloads them like the cron does,
but **always** re-downloads and **upserts** the `video_sources` row instead of skipping existing ones.

## Verified Facts (exploration 2026-10-11)

| Area | Fact | Impact |
|------|------|--------|
| Cron | `AppScheduler.syncDriveVideos` every 15 min, window `createdTime >= now-60m` | Files older than the window are never retried → the reason force sync is needed |
| Download | `VideoStorageServiceImpl.downloadFileFromFolder` writes **directly** to `video-cache/{id}.full`, skips if the file exists | A crash mid-download leaves a partial file the cron skips forever → move to temp + atomic rename |
| DB save | `saveInfo` skips when `existsBySourceId` | Force needs an upsert path |
| Uniqueness | `video_sources.source_id` has **no unique constraint** (`202506220101-create-source-stream.sql`) | `ON CONFLICT` needs `UNIQUE (source_type, source_id)` → prod duplicate check first (FS-0) |
| Source type | Only `"google_drive"` is written | Index on `(source_type, source_id)` |
| Drive scope | Service Account, `DriveScopes.DRIVE` (full) | Request ids **must** be checked against the folder listing (else any Drive file the SA can read could be pulled) |
| Caches | `VideoCache` / `ChunkIndexCache` hold chunks by `fileId` | Re-downloaded file → evict that file's chunks + chunk index |
| Roles | `UserRole` + `RoleHierarchy` (SA ⊃ ADMIN); controllers use class-level `hasRole('ADMIN')` | Class-level `hasRole('ADMIN')` (SA inherits) for reads; method-level `@PreAuthorize("hasRole('SUPER_ADMIN')")` on the POST |
| Admin FE | `AdminView` TABS, polling via `refetchInterval` in `useAdmin.js`, `isAdminRole` in `utils/roles.js` | Same pattern; add `isSuperAdmin` |
| Lock | No per-file lock between jobs; `synchronized` prohibited (tech-strategy) | `ConcurrentHashMap.newKeySet()` claim, shared by cron + force sync |

## Design

### Sync state per Drive file (GET listing)

| State | Local `.full` | DB row | Meaning |
|-------|:--:|:--:|---------|
| `SYNCED` | ✅ size == Drive size | ✅ | Nothing to do |
| `MISSING_LOCAL` | ❌ | ✅ | App shows it but cannot stream |
| `MISSING_DB` | ✅ | ❌ | Downloaded, not on app |
| `MISSING_BOTH` | ❌ | ❌ | Cron window missed it |
| `SIZE_MISMATCH` | ✅ size ≠ Drive size | any | Partial/old download |
| `SYNCING` | — | — | Currently held by cron or a force run |

DB rows whose file is no longer on Drive are listed as `ORPHAN` (read-only, no action in this scope).

### Force sync run
- `POST /admin/drive-sync/runs {fileIds: [...]}` → 202 + run snapshot. Max 50 ids, each must be in the current folder listing (else 400 `NOT_IN_FOLDER`).
- **One run at a time** (`AtomicReference<RunState>.compareAndSet`), 409 `SYNC_RUNNING` otherwise.
- Executes on a virtual thread, files **sequentially** (same disk/bandwidth profile as the importer).
- Per file: claim lock (`DriveFileLocks`, shared with the cron; held → status `SKIPPED_BUSY`) → disk guard (Drive size + `minFreeDisk`) → download to `{id}.full.part` with a counting stream (progress bytes) → atomic `Files.move(REPLACE_EXISTING, ATOMIC_MOVE)` → evict `VideoCache`/`ChunkIndexCache` for that id → thumbnail (ffmpeg) → **upsert** → release lock, delete `.part` in `finally`.
- Progress is **in-memory** (run id, per-file `QUEUED/DOWNLOADING/SAVING/DONE/FAILED/SKIPPED_BUSY`, bytes/total, error code). Polling `GET /admin/drive-sync/runs/current` every 2 s while active. Lost on restart by design; startup deletes stale `*.part`.

### Upsert (native, `VideoSourceRepository.upsertDriveSource`)
```sql
INSERT INTO video_sources (video_id, source_type, source_id, title, description, credentials_ref,
                           thumbnail_path, is_hide, status, priority, created_at, updated_at)
VALUES (:videoId, 'google_drive', :sourceId, :title, :desc, '', :thumb, false, 'PUBLISHED', 0, now(), now())
ON CONFLICT (source_type, source_id) DO UPDATE
   SET title          = EXCLUDED.title,
       thumbnail_path = EXCLUDED.thumbnail_path,
       description    = COALESCE(video_sources.description, EXCLUDED.description),
       updated_at     = now();
```
Kept on update (owner decision 2026-10-11): `video_id`, `status`, `priority`, `is_hide`, `view_count`, an existing `description`.
The ChatGPT poem is only requested when the row has no description (`makeDesc` fallback stays).

### Security
- Reads (`GET /files`, `GET /runs/current`): class-level `hasRole('ADMIN')` → ADMIN and SA (role hierarchy).
- Write (`POST /runs`): method-level `@PreAuthorize("hasRole('SUPER_ADMIN')")` + `@AuditLog` + `@RateLimited(permit = 5)`; ADMIN → 403.
- Folder-membership check (above) prevents pulling arbitrary Drive files (CWE-639).
- FE shows the tab to every admin role; checkboxes and **FORCE_SYNC** are disabled for ADMIN with a tooltip ("Chỉ SUPER_ADMIN được force sync"), same pattern as `AdminAccountTable` `lockReason`. BE is the enforcement.

## Implementation Steps

### FS-0 — Prod pre-check (0.1 d) ⚠️ gate, needs owner OK
- [x] Read-only query on prod (owner, 2026-10-11 → 0 rows): `SELECT source_type, source_id, count(*) FROM video_sources GROUP BY 1,2 HAVING count(*) > 1;`
- [x] ~~Duplicates found → owner decides which row to keep (views/bookmarks/comments reference `video_sources.id`) before FS-1 ships~~ (none)

### FS-1 — Data + shared downloader (0.75 d) — after FS-0
- [x] Migration `202610110003-video-sources-unique-source.sql`: `CREATE UNIQUE INDEX ux_video_sources_source ON video_sources (source_type, source_id);`
- [x] `VideoSourceRepository.upsertDriveSource(...)` (`@Modifying @Transactional`, native as above)
- [x] `DriveFileLocks` (`tryClaim(id)` / `release(id)` / `isHeld(id)`, `ConcurrentHashMap.newKeySet()`)
- [x] Extract `DriveVideoDownloader.download(file, ProgressListener)`: temp `.part` → atomic move → evict caches → thumbnail. Cron path uses it too (keeps skip-if-exists + `saveInfo`); startup cleanup of `*.part`
- [x] Tests: lock claim/release, temp file removed on failure, existing file replaced only on success, cache eviction called, cron still skips existing files

### FS-2 — DriveSyncService (0.75 d) — after FS-1
- [x] `listFolder()`: all Drive pages (`nextPageToken`, fields `id,name,size,createdTime`), batched DB lookup (`findAllBySourceTypeAndSourceIdIn`), local file sizes → states table above + orphans
- [x] `startRun(fileIds, actorId)`: validation (1..50, distinct, in folder), single-run guard, virtual-thread executor, per-file flow, error codes (`DRIVE_ERROR`, `DISK_FULL`, `FFMPEG_ERROR`, `DB_ERROR`), one file failing does not stop the run
- [x] `currentRun()` snapshot (immutable DTO)
- [x] Tests: each state, not-in-folder → 400, second run → 409, busy lock → `SKIPPED_BUSY`, upsert called with Drive name as title, failure isolation

### FS-3 — API (0.25 d) — after FS-2
- [x] `AdminDriveSyncController` `/admin/drive-sync`: `GET /files`, `POST /runs`, `GET /runs/current` — class-level `hasRole('ADMIN')`, `POST /runs` adds `hasRole('SUPER_ADMIN')`
  - `@HasPermission` is a meta `@PreAuthorize`; two on one method fail at runtime, so `POST /runs` uses one combined `@PreAuthorize` (SA role + ADMIN bit)
- [x] Permission tests: GETs — SA 200, ADMIN 200, USER 403; `POST /runs` — SA 202, ADMIN 403, USER 403

### FS-4 — FE tab DRIVE_SYNC (0.5 d) — contract-first, parallel with FS-2/3
- [x] `utils/roles.js` `isSuperAdmin`; `AdminView` adds `DRIVE_SYNC` tab for all admin roles; select + FORCE_SYNC enabled only when `isSuperAdmin`
- [x] `api/admin.js` + hook: files query, run mutation, current-run query with `refetchInterval` 2 s while active (refetch files when the run ends)
- [x] `DriveSyncPanel`: table (name, size, created, state badge, checkbox), filter "chỉ chưa sync", select-all-unsynced, **FORCE_SYNC (n)** button with confirm, per-file progress bar + error
- [x] Tests: ADMIN sees table/progress but checkboxes + button disabled with reason, SA can select, selection → POST body, progress rendering, button disabled while a run is active

### FS-5 — Verify & docs (0.25 d)
- [ ] Prod E2E by owner: one `MISSING_BOTH` + one `SYNCED` (forced re-download) file
- [ ] `/security-auditor` on the diff
- [ ] `docs/tracking.md`, mkdocs nav, TASKS.md

## Dependency Graph

```
FS-0 ──► FS-1 ──► FS-2 ──► FS-3 ──► FS-5
                     FS-4 (contract-first) ──┘
```

## Owner Decisions (2026-10-11)

1. **Force on `SYNCED` files**: always re-download from Drive (replace local file, evict chunk caches, upsert).
2. **Moderation fields**: upsert keeps `is_hide` / `status` / `priority` (a hidden video stays hidden).
3. **Title**: always overwritten with the Drive file name (Drive name is the title source of truth).
4. **Access**: ADMIN can view (file states, run progress); only SUPER_ADMIN can start a force sync.
