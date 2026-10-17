# Plan: Watch History (v2)

<!--
Implementation Plan
Filename: docs/plans/plan-watch-history-v2.md
Owner: Builder (/swarm-execute)
Handoff to: Builder, QA Engineer
-->

## Overview

**Status:** Done (2026-10-18, QA-1 verified on prod). Approved 2026-10-15. Defaults are accepted: 30 s progress heartbeat, accent-sensitive search, and the feed only shows a badge.
**Author:** nguyenhuuca
**Date:** 2026-10-15
**Related PRD:** [PRD-watch-history-v2](../prd/PRD-watch-history-v2.md)
**ADR:** [ADR-0021](../adr/0021-watch-history.md). It supersedes ADR-0013 and ADR-0014.
**Supersedes:** `plan-watch-history.md` and `plan-watch-history-opus.md` (never implemented).
**Builds on:**
- the view counter (`POST /videos/{id}/view`, `VideoViewServiceImpl`)
- `user_settings.incognito_enabled`
- the History page (`HistoryView`, `hist-*` CSS)
- ADR-0020 (i18n)

Estimate: about 6.5 dev-days. BE about 2.5d, FE about 3.5d, QA 0.5d.

## Technical Approach

### Data: one migration, `202610160001-create-watch-history.sql`

```sql
CREATE TABLE watch_history (
  user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  video_id     BIGINT      NOT NULL REFERENCES video_sources(id) ON DELETE CASCADE,
  watched_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  position_sec INT         NOT NULL DEFAULT 0 CHECK (position_sec >= 0),
  duration_sec INT                     CHECK (duration_sec > 0),
  PRIMARY KEY (user_id, video_id)
);
CREATE INDEX idx_watch_history_user_recent ON watch_history (user_id, watched_at DESC);
```

Other migrations already reference `users(id)`, so the foreign key works as written.

### Backend

**`WatchHistoryRepository`** uses native SQL, like `VideoReactionRepository`:
- `upsertWatched(userId, videoId)`:
  ```sql
  INSERT INTO watch_history (user_id, video_id, watched_at)
  SELECT :u, v.id, now() FROM video_sources v
  WHERE v.id = :vid AND v.is_hide = false AND v.status = 'PUBLISHED'
  ON CONFLICT (user_id, video_id) DO UPDATE SET watched_at = now()
  ```
  It returns the number of rows. 0 means the video is unknown or hidden.
- `trimToCap(userId, cap)`:
  ```sql
  DELETE … WHERE user_id = :u AND video_id IN
    (SELECT video_id … ORDER BY watched_at DESC, video_id DESC OFFSET :cap)
  ```
- `updateProgress(userId, videoId, pos, dur)` runs `UPDATE … WHERE user_id = :u AND video_id = :vid`. It never inserts.
- Page query: a constructor-expression or projection that joins `video_sources`. It takes an optional `lower(v.title) LIKE lower(:q)` filter, with `%` and `_` escaped. It returns videoId, title, fileId, viewCount, watchedAt, positionSec, durationSec. One query plus one count.
- `findSummary(userId)` returns `(video_id, position_sec, duration_sec)`, limit 500.
- `deleteOne(userId, videoId)`, `deleteAll(userId)`.

**`WatchHistoryService` / `WatchHistoryServiceImpl`**:
- `recordWatch(userId, videoId)`:
  - If `settingsRepo` says incognito, it returns without writing.
  - Otherwise `@Transactional` upsert, then trim to `app.watch-history.max-entries` (default 500).
- `saveProgress(userId, videoId, pos, dur)`:
  - It is a no-op in incognito.
  - It clamps `dur` to 1..21600 and `pos` to 0..dur, and ignores non-finite or negative values.
- `list(userId, q, page, size)`:
  - `size` is clamped to 1..50.
  - `q` is trimmed, at most 100 characters, and a blank `q` means no filter.
  - The DTO includes `completed = pos >= 0.9·dur || dur − pos <= 10`.
- `summary(userId)`, `remove(userId, videoId)`, `clear(userId)`.

**Hook into the view path.**
- In `VideoViewController.record`, when `AppUtils.getCurrentUser()` is non-null, call `watchHistoryService.recordWatch(user.getId(), videoId)` **in addition to** `recordView`. It is not gated by the 30-minute dedupe, so a re-watch still bumps `watched_at`.
- Wrap it in try/catch and log at warn level. A history failure must never fail the view call (still 204).

**`WatchHistoryController`**, base `/watch-history`, authenticated. These paths are not in the public `AppConstant` list.

| Method | Path | Body / params | Response | Rate |
|---|---|---|---|---|
| GET | `/watch-history` | `q?, page=0, size=20` | `{data: Page<WatchHistoryItemDto>}` | 60 |
| GET | `/watch-history/summary` | — | `{data: [{videoId, positionSec, durationSec, completed}]}` | 30 |
| PUT | `/watch-history/{videoId}/progress` | `{positionSec, durationSec}` | 204 | 120 |
| DELETE | `/watch-history/{videoId}` | — | 204 | 20 |
| DELETE | `/watch-history` | — | 204 | 20 |

- CORS needs no change: `filter/WebSecurityConfig` already allows `PUT` and `DELETE`.
- **Account deletion:** `UserSettingsServiceImpl.deleteAccount` calls `watchHistoryRepository.deleteAll(userId)`.
- **Config:** add `app.watch-history.max-entries: ${WATCH_HISTORY_MAX_ENTRIES:500}`.

### Frontend

- **`api/watchHistory.js`:** `list(q, page, size)`, `summary()`, `progress(videoId, pos, dur)`, `remove(id)`, `clear()`.
  - `progress` uses `fetch` with `keepalive: true`. Add a `keepalive` option to `client.js` `request()`.
  - It is a no-op for guests.
- **`hooks/useWatchHistory.js`:**
  - `useWatchHistoryInfinite(q)` under query key `['watchHistory', 'list', q]`.
  - `useWatchHistorySummary()`, enabled only when logged in, with `staleTime` of 60 s. It returns a `Map<videoId, {positionSec, durationSec, completed}>`.
  - Mutations `useRemoveWatchEntry` and `useClearWatchHistory`, which invalidate `['watchHistory']`.
  - The view call invalidates the summary and list after it succeeds.
- **`VideoPlayer.jsx`:**
  - After the view is sent, if the user is logged in, report progress: on `pause`, on `active` → false, on `visibilitychange` hidden, every 30 s while playing, and on unmount. Throttle to at most one call per 10 s, except on pause or unmount.
  - New prop `startAt` (seconds). On `loadedmetadata`, seek to it once when it is > 5 and < duration − 10.
- **Resume entry:**
  - `AppShell.playVideo(id, { startAt })` stores `deepLinkStartAt` next to `deepLinkId`.
  - `PublicFeed` passes it to the `VideoPlayer` of the deep-linked video only, once.
  - History cards call `onPlayVideo(id, { startAt: completed ? 0 : positionSec })`.
- **Badges:**
  - `WatchedBadge` shows "Đã xem" when completed, otherwise a 3 px progress line.
  - It appears in the `VideoPlayer` meta overlay (feed and Library player) and on `ExploreView` cards. It reads from the summary map, with no request per card.
- **History page** (`HistoryView`), per the Stitch design:
  - Top tab pills: "Lịch sử xem (N)" | "Đề xuất của tôi (N)". "Lịch sử xem" is the default tab. The tab is remembered in `localStorage`, wrapped in try/catch.
  - `WatchHistorySection` contains:
    - a search input (debounced 300 ms);
    - a controls row with the "Tạm dừng lịch sử" switch and a "Xoá toàn bộ" button. The switch PATCHes `incognitoEnabled` through the existing settings hook and invalidates settings. The clear button asks for confirmation through an `app-modal`.
    - When paused, a banner reads "Lịch sử đang tạm dừng".
    - Day groups Today / Yesterday / Earlier, computed in local time.
    - `WatchHistoryCard`:
      - thumbnail via `thumbnailUrl(fileId)`, with a duration or "Đã xem hết" chip
      - title, view count (`compactNumber`)
      - "Đã xem {relativeTime}"
      - a progress bar
      - a "⋮" menu with "Xoá khỏi lịch sử"
    - "Xem thêm" paging.
  - Reuse the `hist-*` styles and add `hist-watch-*` classes, with light theme and ≤ 768 px rules.
- **i18n:** add keys under the `history` namespace (vi and en) for the tab, groups, controls, confirm dialog, empty, paused and badge. The Settings incognito description is unchanged.

## Tasks

Beads is not installed (no `.beads/`), so this table is the task list.

| ID | Task | Est. | Depends on | Acceptance criteria |
|---|---|---|---|---|
| BE-1 | Migration, `WatchHistoryRepository` (native upsert, trim, progress, page, summary, deletes), `WatchHistoryService` and config | 1d | — | Unit tests cover: first watch inserts; re-watch updates `watched_at` and keeps one row; hidden or unknown video writes 0 rows; incognito writes nothing; the 501st entry evicts the oldest; progress does not insert; progress clamping; `completed` flag; `q` escaping of `%` and `_`. |
| BE-2 | `WatchHistoryController` (5 endpoints, rate limits, auth required) and the hook in `VideoViewController.record` | 0.75d | BE-1 | Controller tests: 401 for guests; owner isolation (user A cannot delete user B's entry, and B's entries never appear for A); size clamp. A view call by a logged-in user records history even when the view is deduped. A history exception still returns 204 for the view. Guests get no history. |
| BE-3 | Account deletion clears history. Update ADR-0021 if anything changes, and the API contract note. | 0.25d | BE-1 | `deleteAccount` test asserts `deleteAll(userId)`. `mvn verify` is green and coverage stays ≥ 90 %. |
| FE-1 | `api/watchHistory.js`, `keepalive` in `client.js`, `hooks/useWatchHistory.js` | 0.5d | BE-2 contract | Hook tests: list paging, `q` in the key, summary map, remove and clear invalidate. Guests make no calls. |
| FE-2 | `VideoPlayer` progress reporting and `startAt` seek; `AppShell.playVideo(id, {startAt})` → `PublicFeed` deep link | 1d | FE-1 | Tests with fake timers: no progress before the view is sent; pause sends progress; switching or unmounting sends it; at most one call per 10 s while playing; guests and incognito send none; `startAt` seeks once only when > 5 s and not near the end. |
| FE-3 | History page tabs and `WatchHistorySection` (search, pause switch, clear-all confirm, day groups, cards, remove, paging, empty and paused states), CSS for dark, light and mobile | 1.5d | FE-1, FE-2 | Tests (vi, plus one en render): groups by local day; card shows progress or "Đã xem hết"; clicking a card calls `onPlayVideo(id, {startAt})`; remove and clear call the API; the pause switch PATCHes `incognitoEnabled`; search is debounced. Preview checked at 1440 px and 390 px in both themes. |
| FE-4 | `WatchedBadge` on the feed player overlay and Explore cards | 0.5d | FE-1 | Badge renders from the summary map and makes no request per card. Hidden for guests. |
| QA-1 | Prod smoke after deploy, logged in | 0.5d | all | Watch a video for ≥ 3 s and it appears under Today. Pause mid-video, reopen from History and it resumes. Incognito on records nothing. Remove and clear work. Badge shows in the feed. Endpoints return 401 for guests. |

## Dependency Graph

```
BE-1 ──► BE-2 ──► FE-1 ──► FE-2 ──► FE-3 ──► QA-1
  │        │        └────► FE-4 ─────────────┘
  └──► BE-3 ────────────────────────────────┘
```

BE-3 and FE-4 can run in parallel with the main chain. FE-1 can start from the contract above before BE-2 is merged.

## Risks

| Risk | Mitigation |
|---|---|
| Extra write on every logged-in view on a 1 vCPU VM | One upsert plus one bounded delete on an indexed key, in the same transaction. Measure the p95 of the view call in QA-1 (PRD target ≤ +10 ms). |
| Progress heartbeats while many users watch | 30 s interval, a 10 s client throttle, a 120/min server limit, and an `UPDATE` by primary key only. |
| `keepalive` fetch is dropped on tab close | Best effort by design. Pause and switch events already save most positions. |
| Resume deep link races with feed loading | `startAt` is applied on `loadedmetadata` of the deep-linked player only, once. Covered by FE-2 tests. |
| Incognito toggled while a video plays | The server checks the flag on every write, so the client state does not matter. |
| Search on Vietnamese titles without accents | Documented as OQ-2. Follow-up: `unaccent`. |

## Rollback

- Remove the `recordWatch` call from `VideoViewController`. The view counting is unchanged.
- Hide the History tab.
- Drop the `watch_history` table in a follow-up migration. Nothing else depends on it.

## Progress Log

| Date | Update |
|------|--------|
| 2026-10-15 | Plan created (`/swarm-plan history`). Scope chosen by the owner: list with day groups, progress and resume, clear-all and pause, search and badges. Recording piggybacks on the view call. New docs supersede the June drafts. |
| 2026-10-15 | `/swarm-execute`: BE-1..3 and FE-1..4 implemented. `mvn verify`: 1305 tests, LINE coverage 96.63 %. Vitest: 401 tests; eslint 0 errors; build OK. Previewed at desktop and 390 px in dark and light themes (fixed mobile top padding under the floating header). Native SQL not yet run against a real Postgres (no local DB); covered by QA-1. |
| 2026-10-16 | Pushed and deployed (`0a2a365` BE, `4c46791` FE). Liquibase changeset `202610160001-create-watch-history` ran on prod; endpoints return 401 for guests. |
| 2026-10-18 | QA-1 passed on prod, logged in (owner): recording, resume, pause history, remove/clear, badges. |
