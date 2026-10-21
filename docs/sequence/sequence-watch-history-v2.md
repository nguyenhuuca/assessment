# Sequence Diagrams: Watch History v2

- **PRD:** [PRD-watch-history-v2](../prd/PRD-watch-history-v2.md)
- **ADR:** [0021-watch-history](../adr/0021-watch-history.md)
- **Spec:** — (plan: [plan-watch-history-v2](../plans/plan-watch-history-v2.md))
- **Status:** As-built (2026-10-21)

All endpoints are under `/v1/funny-app`. The user id always comes from the JWT (`AppUtils.getCurrentUser()`), never from the request. Writes are native SQL in `WatchHistoryRepository` (ADR-0021). In the diagrams `:videoId` stands for the path variable `{videoId}`.

## 1. Record a watch (view reported) and save resume position

A logged-in user plays a video. After 3 s of real, visible playback `VideoPlayer.jsx` calls the public view endpoint, which also upserts the history row. Later the player sends the resume point (update-only) on a 30 s tick (at most every 10 s), on pause, on end, on tab hidden, and on slide switch or unmount. The history write is best effort: a failure is logged and the view still returns 204.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (VideoPlayer.jsx)
    participant VC as VideoViewController
    participant VV as VideoViewService
    participant WC as WatchHistoryController
    participant WS as WatchHistoryServiceImpl
    participant US as UserSettingsRepository
    participant WR as WatchHistoryRepository
    participant DB as PostgreSQL

    U->>FE: plays video (3 s of real playback)
    FE->>VC: POST /v1/funny-app/videos/:videoId/view (viewsApi.record)
    VC->>VV: recordView(videoId, viewerKey)
    VV-->>VC: counted once per viewer per video per 30 min
    VC->>VC: recordHistory(videoId) - skipped for guests (no user id)
    VC->>WS: recordWatch(userId, videoId)
    WS->>US: findById(userId) - isIncognito
    US-->>WS: UserSettings or empty
    alt incognitoEnabled is true
        WS-->>VC: return, nothing written
    else not incognito
        WS->>WR: upsertWatched(userId, videoId)
        WR->>DB: INSERT SELECT FROM video_sources (PUBLISHED, not hidden) ON CONFLICT DO UPDATE watched_at
        DB-->>WR: rows affected (0 if video unknown or not public)
        opt rows affected greater than 0
            WS->>WR: trimToCap(userId, app.watch-history.max-entries = 500)
            WR->>DB: DELETE rows beyond the newest 500
        end
    end
    VC-->>FE: 204 No Content
    FE->>FE: invalidateQueries watchHistory (logged in only)

    loop while playing (30 s tick, at most every 10 s) and on pause, end, tab hidden, slide change, unmount
        FE->>FE: reportProgress(videoId) - only if view already sent and logged in
        FE->>WC: PUT /v1/funny-app/watch-history/:videoId/progress with positionSec and durationSec (keepalive, rate limit 120)
        WC->>WS: saveProgress(userId, videoId, positionSec, durationSec)
        alt value null, negative or not finite, or incognito
            WS-->>WC: return silently
        else valid
            WS->>WR: updateProgress(userId, videoId, pos, dur)
            WR->>DB: UPDATE watch_history SET position_sec, duration_sec (update-only, never inserts)
        end
        WC-->>FE: 204 No Content
    end
```

## 2. Browse, search, resume and manage history (History tab)

The user opens History, then the "Lich su xem" tab (`WatchHistorySection.jsx`). The list is newest first, grouped by day on the client, with a debounced title search (300 ms) and "load more" paging (page size 20). Clicking a card plays the video at the saved position unless it is completed.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (WatchHistorySection.jsx)
    participant HK as useWatchHistory.js (React Query)
    participant API as WatchHistoryController
    participant S as WatchHistoryServiceImpl
    participant R as WatchHistoryRepository
    participant DB as PostgreSQL

    U->>FE: opens History tab, types a search term
    FE->>HK: useWatchHistoryInfinite(q) after 300 ms debounce
    HK->>API: GET /v1/funny-app/watch-history?q=...&page=0&size=20 (rate limit 60)
    API->>API: currentUserId() from JWT (401 error.auth.required if missing)
    API->>S: list(userId, q, page, size)
    S->>S: clamp size to 1..50, trim q to 100 chars, escape LIKE wildcards
    S->>R: findPage(userId, pattern, PageRequest)
    R->>DB: SELECT join watch_history and video_sources (PUBLISHED, not hidden) ORDER BY watched_at DESC
    DB-->>R: page of HistoryRow
    R-->>S: Page of HistoryRow
    S-->>API: Page of WatchHistoryItemDto (completed = 90 percent or within 10 s of end)
    API-->>HK: 200 ResultObjectInfo with Page
    HK-->>FE: groups Today, Yesterday, Earlier
    U->>FE: clicks Load more
    FE->>HK: fetchNextPage
    HK->>API: GET /v1/funny-app/watch-history?page=1&size=20
    API-->>HK: 200 next page
    U->>FE: clicks a card
    FE->>FE: onPlayVideo(videoId, startAt = positionSec, or 0 when completed)
    Note over FE: VideoPlayer seeks only if startAt is over 5 s and not near the end

    alt remove one entry
        U->>FE: clicks remove on a card
        FE->>API: DELETE /v1/funny-app/watch-history/:videoId (useRemoveWatchEntry, rate limit 20)
        API->>S: remove(userId, videoId)
        S->>R: deleteOne(userId, videoId)
        R->>DB: DELETE one row (idempotent)
        API-->>FE: 204 No Content
        FE->>HK: invalidateQueries watchHistory
    else clear all
        U->>FE: clicks Clear all and confirms in dialog
        FE->>API: DELETE /v1/funny-app/watch-history (useClearWatchHistory, rate limit 20)
        API->>S: clear(userId)
        S->>R: deleteAll(userId)
        R->>DB: DELETE all rows of the user
        API-->>FE: 204 No Content
        FE->>HK: invalidateQueries watchHistory
    end
```

## 3. Pause history (incognito switch) and its effect

The "Tam dung lich su" switch on the History tab is the same flag as Settings, Incognito: `user_settings.incognito_enabled` (PRD OQ-4, one flag). While it is on, `recordWatch` and `saveProgress` return without writing. Existing entries stay visible and can still be removed.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (WatchHistorySection.jsx)
    participant HK as useSettings.js
    participant SC as UserSettingsController
    participant SS as UserSettingsServiceImpl
    participant SR as UserSettingsRepository
    participant DB as PostgreSQL
    participant WS as WatchHistoryServiceImpl

    U->>FE: toggles Pause history
    FE->>HK: useUpdateSettings.mutate incognitoEnabled = true
    HK->>SC: PATCH /v1/funny-app/user/settings (JWT)
    SC->>SS: updateSettings(request)
    SS->>SR: findById(userId) or create defaults
    SS->>SR: save(settings with incognitoEnabled)
    SR->>DB: UPDATE user_settings
    SC-->>HK: 200 ResultObjectInfo with UserSettingsDto
    HK->>FE: setQueryData settings, then invalidateQueries settings
    FE-->>U: paused banner shown

    Note over U,WS: later, while paused
    U->>FE: watches a video
    FE->>WS: POST /view then PUT /watch-history/:videoId/progress
    WS->>SR: findById(userId) - isIncognito
    SR-->>WS: incognitoEnabled true
    WS-->>FE: 204, no insert and no update
```

## 4. Watched badges (summary) in feed and Explore

`WatchedBadge.jsx` uses `useWatchHistorySummary()`: one shared request (query key `watchHistory`, `summary`, stale time 60 s), only for logged-in users. Guests get an empty map and no request.

```mermaid
sequenceDiagram
    autonumber
    participant FE as Browser (WatchedBadge.jsx)
    participant HK as useWatchHistorySummary
    participant API as WatchHistoryController
    participant S as WatchHistoryServiceImpl
    participant R as WatchHistoryRepository
    participant DB as PostgreSQL

    FE->>HK: render badge for videoId
    alt guest (not logged in)
        HK-->>FE: empty Map, no request
    else logged in
        HK->>API: GET /v1/funny-app/watch-history/summary (rate limit 30)
        API->>S: summary(userId)
        S->>R: findSummary(userId, 500)
        R->>DB: SELECT video_id, position_sec, duration_sec ORDER BY watched_at DESC LIMIT 500
        DB-->>R: SummaryRow list
        S-->>API: list of WatchSummaryDto with completed flag
        API-->>HK: 200 ResultListInfo
        HK-->>FE: Map videoId to progress
        FE-->>FE: shows Watched badge or progress line
    end
```

## 5. Account deletion clears history

Soft-deleting the account (see sequence-user-settings) also removes all watch history rows.

```mermaid
sequenceDiagram
    autonumber
    participant FE as Browser (DeleteAccountModal.jsx)
    participant SC as UserSettingsController
    participant SS as UserSettingsServiceImpl
    participant WR as WatchHistoryRepository
    participant DB as PostgreSQL

    FE->>SC: DELETE /v1/funny-app/user/account (after step-up check)
    SC->>SS: deleteAccount(request)
    SS->>DB: save user as DEACTIVATED and anonymized
    SS->>WR: deleteAll(userId)
    WR->>DB: DELETE FROM watch_history WHERE user_id
    SC-->>FE: 204 No Content
```

## Notes: PRD vs code

- `PUT /watch-history/:videoId/progress` is update-only and is only sent after the view has counted (`viewSentRef`), so a row always exists first.
- `POST /videos/:videoId/view` is public. The history write is skipped for guests inside `VideoViewController.recordHistory` and never fails the view.
- The history cap is configurable (`app.watch-history.max-entries`, env `WATCH_HISTORY_MAX_ENTRIES`, default 500), while the summary limit is a fixed 500 (`WatchHistoryServiceImpl.SUMMARY_LIMIT`).
- List and search hide videos that are hidden or not PUBLISHED, but the summary query does not join `video_sources`, so those rows can still appear in the badge map.
