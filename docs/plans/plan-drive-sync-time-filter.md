# Plan: Time Filter on the Admin Drive Sync Tab

<!--
Implementation Plan
Filename: docs/plans/plan-drive-sync-time-filter.md
Owner: Builder (/swarm-execute)
Handoff to: Builder, QA Engineer
-->

## Overview

**Status:** Approved 2026-10-18 · BE-1 `cc7f8f2b`, FE-1 `ad0adc82`, FE-2 `31f01e86` committed; QA-1 (deploy + prod smoke) pending
**Author:** nguyenhuuca
**Date:** 2026-10-18
**Related plan:** [plan-admin-drive-force-sync](plan-admin-drive-force-sync.md) (the `DRIVE_SYNC` tab this extends)
**ADR:** none. Two optional query params on an existing admin endpoint follow the existing pattern; nothing is stored and the change is easy to undo (`swarm-plan` Artifact Requirements).
**Size:** Small feature (~1 day), so this plan is the only artifact (no PRD).

## Objective

On the admin **DRIVE_SYNC** tab, the file table shows only files whose Drive **created time** falls in a chosen range, and the **server** does the filtering:

- **Default: the last 12 hours.** The tab no longer loads the whole folder when it opens
- **Relative time**: presets 1 h / 12 h / 24 h / 7 d / 30 d, counted back from the moment of the request
- **Specific time**: a from/to range picked with date-time inputs (Vietnam time, like the rest of the admin)
- **All**: the whole folder, as today. Orphans are listed **only** in this mode
- Every filter change calls `GET /admin/drive-sync/files` again with the new range

## Verified Facts (exploration 2026-10-18)

| Area | Fact | Impact |
|------|------|--------|
| Listing | `GET /admin/drive-sync/files` (`AdminDriveSyncController.java:37`) takes no params. `DriveSyncServiceImpl.listFolder()` (`:87`) lists **every** Drive page (`'<folder>' in parents and trashed = false`, pageSize 1000), then loads **every** `google_drive` row (`findAllBySourceType`) | Both the Drive call and the DB load grow with the folder. A range cuts both |
| States | `stateOf()` (`:201`) works per Drive file: lock → `SYNCING`; no local file → `MISSING_LOCAL` / `MISSING_BOTH`; size differs → `SIZE_MISMATCH`; else `SYNCED` / `MISSING_DB` | States stay correct on a filtered list: each file only needs its own DB row and local file |
| Orphans | Orphans = DB rows left over after matching **all** folder files (`:107`) | With a range, every older row would look like an orphan → compute orphans only when no range is given (D3) |
| Run check | `startRun` checks requested ids against a **full** listing (`:130`) | Must keep calling the unfiltered listing |
| Time field | `DriveSyncFileDto.createdAt` = Drive `createdTime`, the "Created" column (`DriveSyncPanel.jsx:175`) | The range applies to `createdTime`. Drive's `q` supports `createdTime >= '<RFC 3339>'` |
| FE data flow | `getDriveSyncFiles()` (`api/admin.js:66`) sends no params; `useDriveSyncFiles()` (`useAdmin.js:137`) key `['admin','drive-sync','files']`; refresh and run completion invalidate `['admin','drive-sync']` | Add the filter to the key; prefix invalidation keeps working. Reuse `buildUrl`/`cleanParams` (`admin.js:28,47`) like `getComments` |
| Time zone | `localToIso` (`utils/importUtils.js:51`) turns a `datetime-local` value (Asia/Ho_Chi_Minh) into ISO UTC; `ImportForm.jsx:101` already uses that input | Reuse both; no date library needed |
| Prior art | No admin endpoint takes a time range yet; admin lists take enum/text filters + paging | First `Instant` request params: use `@DateTimeFormat(iso = DATE_TIME)` |
| Tests | `DriveSyncServiceImplTest` stubs the exact `setQ` string (`:79`); `AdminDriveSyncControllerPermissionTest` is `@WebMvcTest`; `DriveSyncPanel.test.jsx` mocks `api/admin.js` | Update the stub; add range cases in all three |

## Decisions

| # | Decision | Why |
|---|----------|-----|
| D1 | Filter **on the server**: `GET /files?from=<ISO>&to=<ISO>`, both optional. The range goes into the Drive `q` | Owner's choice (2026-10-18): the tab should not load the whole folder by default |
| D2 | Dropdown: **1 h / 12 h (default) / 24 h / 7 d / 30 d**, **All**, **Custom** (from/to) | Owner's choice (2026-10-18) |
| D3 | **Orphans are computed only when no range is sent (All).** With a range the response has `orphans: []` and `orphansChecked: false`; the panel shows "Chọn Tất cả để xem orphan" | Owner's choice (2026-10-18). Orphans need the full folder; a range would report every older row as an orphan |
| D4 | With a range, the DB lookup loads only rows whose `source_id` is in the listed file ids (`findAllBySourceTypeAndSourceIdIn`, in chunks of 1 000) | Matches the smaller Drive listing; no full-table load for a 12 h view |
| D5 | Relative presets are resolved to `from = now − preset` **inside the query function**, at fetch time. The query key holds the preset, not the timestamp | The key stays stable, and refresh or run completion refetches a fresh "last 12 h" window |
| D6 | Custom range: from and to each optional; `to` includes its whole minute (FE sends `to + 1 min`, server treats `to` as exclusive). From > to shows an inline error and **does not call the server**. The server also rejects `from > to` with 400 | No wasted request; the API is safe for other callers too |
| D7 | **Changing the filter clears the selection** | The new list may not contain the selected files; the confirm modal lists files from the current list, so keeping hidden ids would make the count and the modal disagree |
| D8 | Keep the previous rows on screen while the new range loads (`placeholderData: keepPreviousData`) with a small loading state on the table | Avoids the table flashing empty on every change |
| D9 | `startRun` keeps validating ids against the **unfiltered** listing | The folder-membership check is a security control (plan-admin-drive-force-sync) |

## Scope

### In Scope

- BE: `from`/`to` params, Drive `q` with `createdTime`, filtered DB lookup, `orphansChecked` flag, 400 on `from > to`, tests
- FE: `getDriveSyncFiles(params)`, `useDriveSyncFiles(filter)`, `utils/timeRange.js`, `DriveTimeFilter` control, panel wiring, orphan note, vi/en strings, tests

### Out of Scope

- Paging of the file table
- Filtering by modified time or sync time
- Remembering the filter across reloads (it resets to 12 h)
- Changing the cron (`AppScheduler.syncDriveVideos`) or the force-sync run

## API Contract (FE-1 can start from this before BE-1 lands)

```
GET /v1/funny-app/admin/drive-sync/files?from=2026-10-18T00:00:00Z&to=2026-10-18T12:01:00Z
  from, to: optional ISO-8601 instants (UTC). from inclusive, to exclusive.
  No params → whole folder + orphans (today's behaviour).
200 ResultObjectInfo<DriveSyncListingDto>
    { files: [...], orphans: [...], orphansChecked: true|false, maxBatch: 50 }
    orphansChecked = false and orphans = [] whenever from or to is given
400 when from > to, or a param is not a valid ISO instant
502 DRIVE_UNAVAILABLE (unchanged)
hasRole('ADMIN') (unchanged)
```

Drive query built by the server (values come from typed `Instant`s, formatted as RFC 3339 in UTC, so no user text reaches the `q` string):

```
'<folder>' in parents and trashed = false and createdTime >= '2026-10-18T00:00:00Z' and createdTime < '2026-10-18T12:01:00Z'
```

## FE util contract

```js
// filter: { mode: 'all' }
//       | { mode: 'relative', preset: '1h' | '12h' | '24h' | '7d' | '30d' }    // default { mode: 'relative', preset: '12h' }
//       | { mode: 'custom', from: 'YYYY-MM-DDTHH:mm' | '', to: 'YYYY-MM-DDTHH:mm' | '' }   // datetime-local, VN time
toRangeParams(filter, now = Date.now())
// → { params: { from?: ISO, to?: ISO }, error: '' }
// → { params: null, error: 'admin:timeRangeInvalid' } when custom from > to
// mode 'all', or custom with both empty → { params: {}, error: '' }
```

## Tasks

| ID | Task | Est. LOC | Depends on |
|----|------|---------:|------------|
| BE-1 | `from`/`to` on `GET /files`, Drive `q`, filtered DB lookup, `orphansChecked`, tests | ~220 | — |
| FE-1 | `utils/timeRange.js`, `getDriveSyncFiles(params)`, `useDriveSyncFiles(filter)` + tests | ~150 | — (uses the contract above) |
| FE-2 | `DriveTimeFilter` control, panel wiring, orphan note, vi/en strings, tests | ~250 | FE-1 |
| QA-1 | Local check against the real folder + prod smoke, tracking/docs | — | BE-1, FE-2 |

Merge order to `main`: BE-1 and FE-1 → FE-2 in parallel. **Push FE-2 only after BE-1 is deployed**: an old backend ignores unknown params and would return the whole folder while the panel says "12 h". BE-1 alone is backward compatible (no params = today's response plus `orphansChecked: true`).

### BE-1: Server-side range

- `AdminDriveSyncController.files(@RequestParam(required = false) @DateTimeFormat(iso = DATE_TIME) Instant from, … Instant to)`; `Contract.require(from == null || to == null || !from.isAfter(to), …)` → 400 with an i18n message key (vi + en)
- `DriveSyncService.listFolder(Instant from, Instant to)` (replace the no-arg method; update the interface and its one caller)
- `DriveSyncServiceImpl`:
  - `listDriveFolder(Instant from, Instant to)` appends `and createdTime >= '…'` / `and createdTime < '…'` when set (`DateTimeFormatter.ISO_INSTANT` on `Instant.truncatedTo(SECONDS)`); `startRun` calls it with `(null, null)` (D9)
  - with a range: DB rows from `videoSourceRepository.findAllBySourceTypeAndSourceIdIn(SOURCE_TYPE, ids)` in chunks of 1 000, empty list → no DB call; orphans = `[]`, `orphansChecked = false`
  - without a range: today's code path, `orphansChecked = true`
- `DriveSyncListingDto`: add `boolean orphansChecked`
- `VideoSourceRepository`: derived query `findAllBySourceTypeAndSourceIdIn(String, Collection<String>)`

**Acceptance criteria**
- [ ] `DriveSyncServiceImplTest`: no range → same `q` as today and orphans computed; `from` only, `to` only, both → exact `q` strings; range → `findAllBySourceTypeAndSourceIdIn` used, `findAllBySourceType` not called, `orphansChecked = false`; empty listing → no DB call; states unchanged for a filtered file; `startRun` still lists without a range
- [ ] `AdminDriveSyncControllerPermissionTest` (or a new WebMvc test): params bind as ISO instants; `from > to` → 400; malformed instant → 400; ADMIN allowed, anonymous rejected (unchanged)
- [ ] New message key in `messages.properties` and `messages_vi.properties` (parity test passes)
- [ ] `mvn verify` passes with LINE coverage ≥ `api/.coverage-threshold`

### FE-1: Util + API + hook

- `webapp/src/utils/timeRange.js`: `TIME_PRESETS` (`1h`, `12h`, `24h`, `7d`, `30d` → ms), `DEFAULT_TIME_FILTER = { mode: 'relative', preset: '12h' }`, `toRangeParams` per the contract; reuse `localToIso`
- `api/admin.js`: `getDriveSyncFiles(params = {})` via `buildUrl` + `cleanParams`
- `hooks/useAdmin.js`: `useDriveSyncFiles(filter)` → key `['admin','drive-sync','files', filter]`, `queryFn` calls `toRangeParams(filter)` at fetch time (D5), `enabled` only when there is no error, `placeholderData: keepPreviousData` (D8)
- Tests: `utils/__tests__/timeRange.test.js`; hook/API call params checked through the panel test or a hook test

**Acceptance criteria**
- [ ] Each preset gives `from = now − preset` and no `to`, with `now` injected; `all` gives `{}`
- [ ] Custom: from only, to only, both, both empty; `to` includes its minute; `2026-10-18T07:00` (VN) → `2026-10-18T00:00:00.000Z`; from > to → error and `params: null`
- [ ] `getDriveSyncFiles({ from })` calls `/admin/drive-sync/files?from=…`; empty params call the bare path
- [ ] `npm run test` and `npm run lint` pass

### FE-2: Filter control + panel wiring

- `webapp/src/components/admin/DriveTimeFilter.jsx`: labelled `<select>` (1 h, 12 h, 24 h, 7 d, 30 d, All, Custom); in Custom mode two labelled `<input type="datetime-local">` (from, to) and a `role="alert"` error. Controlled via `value` / `onChange`
- `DriveSyncPanel.jsx`:
  - state `timeFilter = DEFAULT_TIME_FILTER`; `useDriveSyncFiles(timeFilter)`
  - on filter change: `setSelected(new Set())` (D7)
  - `onlyUnsynced` stays client-side on the returned rows; `selectAllUnsynced` and `showingFiles` work on the returned rows
  - loading state on the table while `isFetching` with previous data (D8)
  - orphan section: when `orphansChecked === false`, show the note "Chọn Tất cả để xem orphan" instead of the list
- `admin.json` (vi + en): `timeFilter`, `timeLast1h`, `timeLast12h`, `timeLast24h`, `timeLast7d`, `timeLast30d`, `timeAll`, `timeCustom`, `timeFrom`, `timeTo`, `timeRangeInvalid`, `orphansOnlyInAll`
- Toolbar CSS only if the controls wrap badly on mobile (reuse admin classes first)

**Acceptance criteria**
- [ ] First render calls `getDriveSyncFiles` with `from ≈ now − 12 h` and no `to`
- [ ] Each preset change calls the API again with the new `from`; "All" calls it with no params and shows orphans
- [ ] Custom from/to calls the API with both bounds; from > to shows the error and makes no call
- [ ] Changing the filter clears the selection and the FORCE_SYNC count
- [ ] With a range, the orphan note shows instead of the orphan table
- [ ] Existing panel tests pass (updated for the new default params); every new string exists in vi and en; controls are labelled and keyboard-reachable
- [ ] `npm run test`, `npm run lint`, `npm run build` pass

### QA-1: Verify and close

- Local (`startLocal.sh` + `npm run dev`) against the real Drive folder: default 12 h matches the "Created" column; presets, All (with orphans) and a custom range around a file's exact minute; mobile width
- Push BE-1 → wait for deploy → push FE-2; prod smoke as ADMIN and SUPER_ADMIN, including FORCE_SYNC on a filtered list
- Mark the plan Done, update `docs/tracking.md` and `TASKS.md`

**Acceptance criteria**
- [ ] Prod tab filters per range for both roles; orphans only under All; FORCE_SYNC still works
- [ ] Plan, `docs/tracking.md` and `TASKS.md` updated with commit hashes

## Risks

| Risk | Mitigation |
|------|------------|
| Admin misses orphans because the default is 12 h | Orphan note points to "All" (D3) |
| Many filter changes → many Drive list calls | Each call is now smaller than today's full listing; the select changes one value at a time and custom inputs only fetch on a valid range. Add debounce in FE-2 only if QA shows typing in the inputs fires too many calls |
| Drive `createdTime` precision / format mismatch | Server sends seconds-truncated RFC 3339 UTC; BE-1 tests pin the exact `q` string; QA-1 checks a boundary minute against real data |
| FE deployed before BE | Push order in the Tasks section |
