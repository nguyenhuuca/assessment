# ADR-0021: Watch History

## Metadata

**Status:** Accepted (2026-10-15) · **Date:** 2026-10-15 · **Deciders:** nguyenhuuca · **Tags:** history, privacy, data-model, api-contract
**Supersedes:** [ADR-0013](0013-watch-history-design.md), [ADR-0014](0014-watch-history-design-opus.md). Both were written on 2026-06-01, before collections, i18n, the view counter and the History page existed, and neither was implemented.
**Related:** [PRD-watch-history-v2](../prd/PRD-watch-history-v2.md) · [plan-watch-history-v2](../plans/plan-watch-history-v2.md) · ADR-0020 (i18n)

## Context

- The History page (nav key `history`) exists, but it only holds "My suggestions". The Stitch design ("History & My Suggestions") adds a watch-history tab: entries grouped by day, a watched-progress bar, "pause history", "clear all" and search.
- The player already reports real playback. `VideoPlayer` calls `POST /videos/{id}/view` once **3 s of real, visible playback** have been watched; seeks are ignored. `VideoViewServiceImpl` dedupes per viewer for 30 min and increments `video_sources.view_count`. The request carries the JWT, so the server knows the user.
- `user_settings.incognito_enabled` exists, and Settings shows it as "Incognito Mode — watch history won't be recorded while active". Nothing reads it yet.
- Per-user tables in this schema (`video_reactions`, `video_collection_items`) use a composite primary key and native `INSERT … ON CONFLICT`.
- Prod runs on a 1 vCPU VM with Postgres on the same host. Writes must stay cheap, with no fan-out.

## Decision Drivers

- **One trigger, one threshold:** a "watch" means the same thing as a counted view (≥ 3 s of real playback), with no second client timer.
- **No extra requests on play start:** zero new requests for the common case.
- **Privacy:**
  - Owner-only access, derived from the JWT and never from a client `userId`.
  - Incognito really records nothing, including progress.
  - The user can delete one entry or everything.
- **Bounded storage:** at most 500 entries per user, enforced on write.
- **Convention fit:** composite primary key and native upsert, like `video_reactions`. Liquibase SQL. No new infrastructure.

## Decisions

### D1. Record history inside the existing view endpoint

- In `POST /videos/{id}/view`, an authenticated caller who is not in incognito also gets a history upsert.
- This is independent of the 30-minute view dedupe: a re-watch inside the window still moves the entry to the top. It does not count a second view.
- Guests (IP viewer key) get no history.
- *Rejected:* a separate `POST /watch-history` on play start (ADR-0013/0014). It costs an extra request per play and duplicates the threshold logic. It would also record 1-second skips.

### D2. Table `watch_history`, composite primary key `(user_id, video_id)`

```sql
watch_history(
  user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  video_id     BIGINT      NOT NULL REFERENCES video_sources(id) ON DELETE CASCADE,
  watched_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  position_sec INT         NOT NULL DEFAULT 0 CHECK (position_sec >= 0),
  duration_sec INT                      CHECK (duration_sec > 0),
  PRIMARY KEY (user_id, video_id))
INDEX (user_id, watched_at DESC)
```

- One row per user per video. A re-watch updates `watched_at`.
- The upsert inserts through `INSERT … SELECT … FROM video_sources WHERE id = ? AND is_hide = false AND status = 'PUBLISHED'`, the same public-visibility rule as the feed. An unknown or hidden id therefore writes nothing, and no foreign-key error escapes. This replaces ADR-0014's "store null video_id" approach.
- *Rejected:* a `BIGINT` surrogate key or a UUID. The pair is the identity, and no entry id is ever exposed.

### D3. 500-entry cap, enforced in the upsert transaction

- After the upsert, run `DELETE FROM watch_history WHERE user_id = ? AND video_id IN (SELECT video_id … ORDER BY watched_at DESC OFFSET 500)`.
- This is self-healing under concurrency: a concurrent writer can only leave the table transiently over the cap.
- There is no time-based retention job. The cap bounds storage at 500 × users.

### D4. Progress is a separate, update-only call

- `PUT /watch-history/{videoId}/progress {positionSec, durationSec}` updates the existing row only. It never inserts, so it cannot bypass the 3 s threshold.
- It is a no-op in incognito.
- The client sends it on pause, on switching video, on tab hide and every 30 s while playing. It uses `fetch` with `keepalive`. `sendBeacon` is not usable because it cannot send the `Authorization` header.
- The server clamps values: position ≤ duration, duration ≤ 6 h.
- A video counts as **completed** at ≥ 90 % or within 10 s of the end. Completed videos show "Watched" and resume from 0.

### D5. Read API, owner-scoped and paged

- `GET /watch-history?q=&page=&size=` returns entries newest first, joined to `video_sources` in one query (no N+1). `q` is a case-insensitive title match.
- `GET /watch-history/summary` returns at most 500 entries as `{videoId, positionSec, durationSec}`. It drives the "Watched" badges and resume without a request per card.
- `DELETE /watch-history/{videoId}` and `DELETE /watch-history` return 204.
- The client groups entries into Today / Yesterday / Earlier in the user's local time zone.

### D6. "Pause history" is `user_settings.incognito_enabled`

- The History page toggle and the Settings "Incognito Mode" toggle edit the same flag through the existing `PATCH /user/settings`. They are two entry points for one setting, with no new column.
- While it is on, D1 and D4 write nothing. Existing entries remain readable and deletable.

### D7. Account deletion clears history

`UserSettingsServiceImpl.deleteAccount` soft-deletes the user (ADR-0016). It now also deletes that user's `watch_history`, because viewing history is personal data with no audit value. The cascade covers hard deletes.

## Consequences

**Positive:**
- No new request on play start.
- History and view counts share one definition of a watch.
- Incognito finally does what Settings says.
- Storage is bounded.

**Negative:**
- History depends on the view call. A blocked or dropped view request means no history entry. This is the same best-effort contract as view counts, and it is acceptable.
- Progress heartbeats add about 1 small `PUT` per 30 s per active logged-in viewer. On 1 vCPU this is fine at current traffic; revisit if traffic grows ten times.
- The title search is accent-sensitive (`ILIKE`). The `unaccent` extension is a later option (see open questions in the PRD).

**Reversibility:** a two-way door. The table and endpoints are additive. Rollback drops the table and removes the upsert call from the view path.

## Compliance

- `@RateLimited`:
  - `GET` list: 60/min
  - summary: 30/min
  - progress: 120/min
  - deletes: 20/min
- All endpoints are authenticated and use the JWT user id only.
- Tests cover:
  - owner isolation
  - incognito
  - the cap
  - a hidden or unknown video
  - re-watch inside the dedupe window
  - clamping
