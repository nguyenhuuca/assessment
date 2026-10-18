# Plan: Time Filter on the Admin Drive Sync Tab

<!--
Implementation Plan
Filename: docs/plans/plan-drive-sync-time-filter.md
Owner: Builder (/swarm-execute)
Handoff to: Builder, QA Engineer
-->

## Overview

**Status:** In Review (awaiting owner approval)
**Author:** nguyenhuuca
**Date:** 2026-10-18
**Related plan:** [plan-admin-drive-force-sync](plan-admin-drive-force-sync.md) (the `DRIVE_SYNC` tab this extends)
**ADR:** none. Client-side filtering of a list the tab already loads is a Two-Way Door that follows the existing `onlyUnsynced` pattern (`swarm-plan` Artifact Requirements).
**Size:** Small feature (< 1 day), so this plan is the only artifact (no PRD).

## Objective

On the admin **DRIVE_SYNC** tab, an admin can narrow the file table by the Drive **created time** in two ways:

- **Relative time**: a preset such as "last 24 hours" or "last 7 days", counted back from now
- **Specific time**: a from/to range picked with date-time inputs (Vietnam time, like the rest of the admin)

The time filter combines with the existing "only unsynced" checkbox, so the typical flow is: *last 7 days + only unsynced → select all unsynced → FORCE_SYNC*.

## Verified Facts (exploration 2026-10-18)

| Area | Fact | Impact |
|------|------|--------|
| Data | `GET /admin/drive-sync/files` (`AdminDriveSyncController.java:37`) takes no params and returns **every** file in the folder: `DriveSyncServiceImpl.listFolder()` reads all Drive pages (pageSize 1000) and joins `video_sources` in memory | The whole list is already on the client, so filtering there needs no API change |
| Time field | `DriveSyncFileDto.createdAt` (`Instant`, Drive `createdTime`) is the only time on a file row, shown in the "Created" column (`DriveSyncPanel.jsx:175`) | The filter applies to `createdAt`. Orphans (`DriveSyncOrphanDto`) and the run have no per-row time, so they are not filtered |
| Existing filter | `onlyUnsynced` is client-side (`DriveSyncPanel.jsx:93,101`). `selectAllUnsynced` picks from `allFiles`, not from the visible rows (`:117`) | The time filter goes in the same place. "Select all unsynced" must pick from the visible rows, otherwise it selects files outside the chosen time range |
| Time zone | Admin UI uses Asia/Ho_Chi_Minh: `localToIso` turns a `datetime-local` value into ISO UTC (`utils/importUtils.js:51`) and `formatTime` displays in VN time (`:81`). `ImportForm.jsx:101` already uses `<input type="datetime-local">` | Reuse `localToIso` and the same input. There is no date library in `package.json`, and none is needed |
| No prior art | No admin endpoint or FE component takes a time range or relative preset | New small util + component; nothing to reuse beyond `localToIso` |
| i18n | Drive strings are in `webapp/src/i18n/locales/{vi,en}/admin.json`, namespace `admin` (e.g. `onlyUnsynced`) | New keys go next to them, in both locales |
| Tests | `DriveSyncPanel.test.jsx` mocks `api/admin.js` and asserts Vietnamese labels, with `createdAt: '2026-10-01T10:00:00Z'` fixtures | Add fixtures with different `createdAt` values; inject `now` into the util so tests are deterministic |

## Decisions

| # | Decision | Why |
|---|----------|-----|
| D1 | Filter **on the client**, over the list already loaded | The backend already returns all files; a server filter would mean a new API param, Drive `q` changes and a test-stub rewrite for no gain. If the folder ever needs paging, move the filter to the server then (the util's range shape maps directly to `createdTime >= … and createdTime < …`) |
| D2 | One control with modes: **All** (default), presets **1 h / 24 h / 7 d / 30 d**, and **Custom** (from/to) | Covers both requests with one select. "All" keeps today's behaviour. The presets match the cron window (60 min) and typical catch-up ranges |
| D3 | Custom range: from and to are each **optional**; from is inclusive, to is inclusive to the minute (`createdAt < to + 1 min`); from > to shows an inline error and **does not filter** | An open range ("since 10/10") is common. Showing an error instead of an empty table avoids a silent "no files" |
| D4 | Relative presets count back from `Date.now()` at render time | The files list is not polled; it is refetched on refresh or when a run finishes, which re-renders. No timer needed |
| D5 | "Select all unsynced" selects unsynced rows **among the visible rows** (time filter applied), still capped at `maxBatch` | Matches what the admin sees. With no time filter, the result is the same as today |
| D6 | Already-selected files stay selected when the filter changes; the FORCE_SYNC count and the confirm modal still list them | Changing a filter should not silently drop a selection. The modal already lists files from `allFiles` |
| D7 | Filter state is local to the panel (not in the URL), like `onlyUnsynced` | Admin tabs keep no URL state today |

## Scope

### In Scope

- `webapp/src/utils/timeRange.js`: `resolveTimeRange(filter, now)` and `inTimeRange(iso, range)` + unit tests
- `DriveTimeFilter` component (select + two `datetime-local` inputs in Custom mode + inline error), placed in `.drive-sync-toolbar`
- `DriveSyncPanel`: apply the time filter before `onlyUnsynced`; `selectAllUnsynced` uses the visible rows; `showingFiles` reflects both filters
- vi/en strings

### Out of Scope

- Server-side filtering or paging of `GET /files`
- Filtering orphans or the run progress
- Filtering by modified time or sync time (the API exposes neither)
- Remembering the filter across reloads

## Contract of the util (FE-1)

```js
// filter: { mode: 'all' }
//       | { mode: 'relative', preset: '1h' | '24h' | '7d' | '30d' }
//       | { mode: 'custom', from: 'YYYY-MM-DDTHH:mm' | '', to: 'YYYY-MM-DDTHH:mm' | '' }   // datetime-local, VN time
resolveTimeRange(filter, now = Date.now())
// → { from: number | null, to: number | null, error: '' }   // epoch ms; from inclusive, to exclusive
// → { from: null, to: null, error: 'admin:timeRangeInvalid' } when custom from > to
// mode 'all', or custom with both empty → { from: null, to: null, error: '' } (no filtering)

inTimeRange(createdAtIso, range)  // true when no bound excludes it; a missing/invalid createdAt is kept only when range has no bounds
```

`custom.to` becomes `localToIso(to) + 60 000 ms` so the picked minute is included.

## Tasks

| ID | Task | Est. LOC | Depends on |
|----|------|---------:|------------|
| FE-1 | `utils/timeRange.js` + unit tests | ~120 | — |
| FE-2 | `DriveTimeFilter` component, panel wiring, vi/en strings, panel tests | ~250 | FE-1 |
| QA-1 | Local check against a real folder + prod smoke, tracking/docs | — | FE-2 |

Merge order to `main`: FE-1 → FE-2 → QA-1. FE-1 is dead code until FE-2 lands but is safe to merge on its own. No backend change, so only the frontend deploys.

### FE-1: Time-range util

- `webapp/src/utils/timeRange.js` with `TIME_PRESETS` (`1h`, `24h`, `7d`, `30d` → ms), `resolveTimeRange`, `inTimeRange`, per the contract above; reuse `localToIso` from `importUtils.js`
- `webapp/src/utils/__tests__/timeRange.test.js`

**Acceptance criteria**
- [ ] Each preset gives `from = now − preset`, `to = null`, with `now` injected
- [ ] Custom from only, to only, both, and both empty behave per D3; `to` includes its whole minute
- [ ] Custom from > to returns the `timeRangeInvalid` error and no bounds
- [ ] A VN-time input `2026-10-18T07:00` maps to `2026-10-18T00:00:00Z`
- [ ] `inTimeRange` handles boundaries (equal to `from` is in, equal to `to` is out) and a null `createdAt`
- [ ] `npm run test` and `npm run lint` pass

### FE-2: Filter control + panel wiring

- `webapp/src/components/admin/DriveTimeFilter.jsx`: a labelled `<select>` (All, 1 h, 24 h, 7 d, 30 d, Custom); in Custom mode two `<input type="datetime-local">` (from, to) with labels, plus `role="alert"` text for the error. Controlled through `value`/`onChange`
- `DriveSyncPanel.jsx`:
  - state `timeFilter` (default `{ mode: 'all' }`); `range = resolveTimeRange(timeFilter)`
  - `files = allFiles.filter(f => inTimeRange(f.createdAt, range)).filter(onlyUnsynced ? notSynced : all)`
  - `selectAllUnsynced` picks from the time-filtered rows (D5)
  - `showingFiles` uses the filtered count over `allFiles.length`
- `admin.json` (vi + en): `timeFilter`, `timeAll`, `timeLast1h`, `timeLast24h`, `timeLast7d`, `timeLast30d`, `timeCustom`, `timeFrom`, `timeTo`, `timeRangeInvalid`
- Toolbar CSS only if the inputs wrap badly on mobile (reuse existing admin classes first)

**Acceptance criteria**
- [ ] Default "All" shows every file (existing panel tests pass unchanged)
- [ ] A preset hides files older than the window; the "Showing x / y" line updates
- [ ] Custom from/to filters to the range; from > to shows the error and the full list
- [ ] Time filter + "only unsynced" combine (AND)
- [ ] "Select all unsynced" selects only visible unsynced rows, capped at `maxBatch`
- [ ] A file selected before narrowing the filter is still counted in FORCE_SYNC and listed in the confirm modal
- [ ] Every new string exists in vi and en (i18n parity test passes); controls are reachable by keyboard and labelled
- [ ] `npm run test`, `npm run lint`, `npm run build` pass

### QA-1: Verify and close

- Local: run against the real Drive folder, check presets against the "Created" column, check the custom range around a file's exact minute, check mobile width
- After push + deploy: prod smoke on the DRIVE_SYNC tab as ADMIN and SUPER_ADMIN
- Mark the plan Done, update `docs/tracking.md` and `TASKS.md`

**Acceptance criteria**
- [ ] Prod tab filters as above for both roles; FORCE_SYNC still works on a filtered selection
- [ ] Plan, `docs/tracking.md` and `TASKS.md` updated with commit hashes

## Risks

| Risk | Mitigation |
|------|------------|
| Admin expects the filter to also cut orphans | The filter sits above the file table only; orphans have no time field. Mentioned in Out of Scope |
| Browser `datetime-local` UI differs (Safari older versions) | Same input as `ImportForm`, already used in prod |
| Folder grows large enough that loading everything is slow | Not a new problem (the tab already loads everything). D1 records the move to a server filter if that happens |
