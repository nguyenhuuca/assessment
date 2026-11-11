# Plan: Show Video File Size in Admin Video Table

<!--
Implementation Plan
Filename: docs/plans/plan-admin-video-size.md
Owner: Builder (/builder)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
Related Skills: swarm-plan, testing
-->

## Overview

**Status:** Planned
**Author:** nguyenhuuca
**Date:** 2026-11-11
**Tracking:** `docs/tracking.md` row "Admin Video Size"; `TASKS.md` section "Admin video size"
**Related PRD / ADR / Sequence:** none (Small Feature; new column follows the existing `video_sources` column pattern, so no ADR)

## Objective

Admins see each video's file size (e.g. `412.3 MB`) in the Video Vault table, so they can see what takes disk on the nearly full prod VM (ADR-0031 context).

## Scope

### In Scope

- New nullable `file_size BIGINT` column on `video_sources`, written at ingest and backfilled once from the local `.full` files.
- `AdminVideoDto.fileSize` returned by `GET /admin/videos`.
- New **Size** column in `AdminVideoTable` (`—` when unknown, e.g. YouTube videos).
- One shared `formatBytes` helper in `webapp/src/utils/` (replaces the local `formatSize` in `DriveSyncPanel`).

### Out of Scope

- User-facing pages (player, Explore, feed): `VideoDto` and `FeedCache` stay unchanged.
- HLS copy size (`video-cache/hls/{fileId}/`), library total, sort by size.
- YouTube videos (embedded, no local file).

## Technical Approach

### Size sources (from exploration)

| Source | Where | Persisted today |
|--------|-------|-----------------|
| Local `.full` file | `VideoStorageServiceImpl.getFileSizeFromDisk` (`file.length()`) | No |
| Drive API `size` | `VideoStorageServiceImpl.listFilesInFolder` (fields include `size`) | No |
| Import job | `VideoImportJob.totalBytes` | Job only |

### Key Decisions

- **Stored column, not disk stat on request:** listing does no per-row file I/O, and the value survives removal of the `.full` file (user's choice, 2026-11-11).
- **Write point:** `saveInfo` and `upsertInfo` read the size of `CACHE_DIR/<fileId>.full` when the file exists. Both run after the download, so the file is there. If it is missing, they leave the column null and do not overwrite an existing value (`COALESCE` in `upsertDriveSource`).
- **Backfill:** a one-shot `@EventListener(ApplicationReadyEvent)` on a virtual thread fills rows where `file_size IS NULL AND source_type = 'google_drive'` from disk. It is idempotent, so a restart only touches rows still null. Failures per row are logged at WARN and skipped.
- No change to `VideoDto`, so there is no feed cache impact.

## Implementation Steps

| ID | Task | Acceptance criteria | Blocked by |
|----|------|---------------------|-----------|
| **VS-1** | Liquibase `sql/202611110001-video-source-file-size.sql` (`ALTER TABLE video_sources ADD COLUMN file_size BIGINT NULL`) + `VideoSource.fileSize` | Migration runs on a clean DB and on prod data; entity maps the column; existing tests green | — |
| **VS-2** | Write size at ingest: `saveInfo` sets `fileSize`; `upsertDriveSource` takes a `fileSize` param with `COALESCE(:fileSize, file_size)` | Unit tests: new row gets the size; upsert with a missing file keeps the old value; missing file → null, no exception | VS-1 |
| **VS-3** | `VideoFileSizeBackfill` startup listener (null rows only, Drive only, skip missing files) | Tests: fills null rows, skips non-null, missing file, and YouTube rows; one failing row does not stop the rest; bean boots (`BeanConstructorTest`) | VS-1 |
| **VS-4** | `AdminVideoDto.fileSize` + mapping in `AdminVideoServiceImpl` (~L247) | Controller/service test: `GET /admin/videos` JSON has `fileSize` (number or null) | VS-1 |
| **VS-5** | FE: `utils/formatBytes.js` (1024-based, B/KB/MB/GB/TB, `—` for null/undefined), `DriveSyncPanel` uses it | Unit tests for 0, 1023, 1 MiB, 1.5 GiB, null; `DriveSyncPanel.test.jsx` unchanged and green | — |
| **VS-6** | FE: **Size** column in `AdminVideoTable` (`colSize`), keys in `locales/{en,vi}/admin.json` | Test: row with `fileSize: 1048576` shows `1.0 MB`, null shows `—`; i18n parity test green | VS-4, VS-5 |

Dependency graph: `VS-1 → {VS-2, VS-3, VS-4}`, `{VS-4, VS-5} → VS-6`. VS-5 can start at once. Each task merges to `main` on its own. Estimate: about 1 day, roughly 200 LOC plus tests.

## Files to Modify

- `api/src/main/resources/db/changelog/sql/202611110001-video-source-file-size.sql` (new)
- `api/.../entity/VideoSource.java`, `repository/VideoSourceRepository.java` (`upsertDriveSource`)
- `api/.../service/impl/VideoStorageServiceImpl.java` (`saveInfo`, `upsertInfo`)
- `api/.../service/impl/VideoFileSizeBackfill.java` (new)
- `api/.../dto/admin/AdminVideoDto.java`, `service/impl/AdminVideoServiceImpl.java`
- `webapp/src/utils/formatBytes.js` (new), `components/admin/DriveSyncPanel.jsx`, `components/admin/AdminVideoTable.jsx`
- `webapp/src/i18n/locales/{en,vi}/admin.json`

## Testing Strategy

- Backend unit tests per task; coverage stays at or above 90% (`api/.coverage-threshold`).
- Frontend: Vitest for `formatBytes`, `AdminVideoTable`, and i18n parity.
- Prod smoke: after deploy, the Video Vault shows sizes for Drive videos (backfill done) and `—` for YouTube videos.

## Rollback Plan

The column is nullable and additive. Revert the code commits; the column can stay (unused) or be dropped by a follow-up changelog.

## Risks

- **Rolling deploy plus `ALTER TABLE`:** cached-plan errors were seen before; `prepareThreshold=0` is already the mitigation (memory: CORS/Postgres gotcha).
- **Backfill on a 1 vCPU VM:** it does only `stat` calls with no reads; batch update per row, run once.
- **`.full` evicted before the backfill:** the size stays null (`—`). This is acceptable, and Drive `size` could fill it later if needed.
