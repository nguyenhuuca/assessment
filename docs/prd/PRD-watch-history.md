# PRD: Watch History

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-05-31
**Version:** 1.0
**Beads Issue:** N/A
**PR-FAQ:** N/A
**Stakeholders:** Product, Engineering, Design

---

## Problem Statement

Logged-in users who watch a video and later want to find it again have no way to do so. After leaving the app or ending a session, the video is gone from their view — they must manually re-search, often unsuccessfully, and give up. There is no persistent record of what they have watched.

### Evidence

**Quantitative Evidence:**
- 100% of watch intent is lost at session end — no history mechanism exists
- The `/history` nav item in the frontend already exists and routes to a "Coming Soon" placeholder, confirming the feature is anticipated by users

**Qualitative Evidence:**
- Common behaviour: play a video → navigate away → return later → cannot find it again
- Universal expectation: YouTube, Netflix, and every major video platform provide watch history as a baseline feature

---

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Help users rediscover watched content | Re-watch rate from /history page | ≥ 20% of history entries are replayed |
| Feature adoption | % of active users with ≥ 1 history entry | ≥ 30% within 30 days of launch |
| Reduce re-search friction | Session bounce rate for users with history | Measurable decrease vs. users without history |

---

## User Stories

### Authenticated User

- As a logged-in user, I want my watched videos to be automatically recorded so that I can find them again without searching.
  - Acceptance: Every video play event creates or updates a history entry for the current user. No manual action required.

- As a logged-in user, I want a `/history` page listing all videos I have watched, sorted newest first, so that I can quickly find something I watched recently.
  - Acceptance: The history page shows all entries with thumbnail, title, and watch timestamp. Clicking a video plays it.

- As a logged-in user, I want a "watched" indicator on video cards so that I know at a glance which videos I have already seen.
  - Acceptance: Video cards display a visual badge/icon when the video is in my history. Cards without history show no indicator.

- As a logged-in user, I want to delete individual history entries or clear my entire history so that I can manage my privacy.
  - Acceptance: Each entry has a delete button. A "Clear all" action removes all entries. Both actions are confirmed before executing.

---

## Requirements

### Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | Record a history entry automatically when a logged-in user plays a video | Must Have | Triggered on play event |
| FR-2 | If the video is already in history, update the `watched_at` timestamp (do not duplicate) | Must Have | Upsert by (user_id, video_id) |
| FR-3 | `/history` page lists all entries for the current user, sorted by `watched_at` DESC | Must Have | Uses `ResultListInfo<T>` |
| FR-4 | Video cards display a watched indicator for videos in the user's history | Must Have | Client-side lookup via history IDs |
| FR-5 | User can delete a single history entry | Must Have | |
| FR-6 | User can clear entire history (delete all entries) | Must Have | Requires confirmation |
| FR-7 | History is private — only the owner can read or delete their entries | Must Have | JWT-enforced |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | History recording must not slow down video playback | Fire-and-forget async call from frontend |
| NFR-2 | History page load (list query) | < 500 ms p99 for up to 500 entries |
| NFR-3 | Watched indicator per card | 0 extra API calls after page load (client-side Set lookup) |
| NFR-4 | Security | All endpoints require valid JWT |

---

## Scope

### In Scope

- Auto-record history entry on video play (upsert — no duplicates)
- `/history` page listing all watched videos, newest first
- Watched indicator (badge/icon) on video cards
- Delete single entry
- Clear all history (with confirmation)
- New `watch_history` DB table via Liquibase migration
- Backend REST API: `WatchHistoryController` under `BASE_URL/watch-history`
- Frontend: replace `ComingSoon` placeholder with real `HistoryPage` component

### Out of Scope

- Resume playback (remember timestamp where user stopped)
- Watch time / duration analytics
- Public history or sharing with other users
- Video recommendations based on history
- History for guest (unauthenticated) users

---

## Dependencies

| Dependency | Owner | Status | Risk |
|------------|-------|--------|------|
| `VideoSource` entity (`video_sources` table) | Existing | Stable | Low |
| `User` entity (`users` table) | Existing | Stable | Low |
| Frontend `history` nav item | Existing (ComingSoon) | Ready to replace | Low |
| Liquibase migration | Engineering | To Do | Low |
| JWT authentication | Existing | Stable | Low |

---

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| History recording slows playback | L | H | Fire-and-forget POST from frontend; no await |
| VideoSource deleted while in history | M | M | ON DELETE SET NULL on video_id; UI shows "Video unavailable" |
| Unbounded history growth | L | M | Cap at 500 entries/user; auto-evict oldest on insert |
| Watched IDs stale across tabs | L | L | React Query invalidation on any play event |

---

## Open Questions

- [ ] Should history auto-evict the oldest entry when cap is reached, or reject new entries?
- [ ] Should re-watching a video move it to the top of the list (update timestamp) or keep original position?
- [ ] Minimum watch duration before recording? (e.g., must watch ≥ 5 seconds)

---

## Appendix

### Data Model (Sketch)

```
watch_history
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid()
  user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE
  video_id    BIGINT               REFERENCES video_sources(id) ON DELETE SET NULL
  source_video_id BIGINT  NOT NULL  -- immutable, used for uniqueness
  watched_at  TIMESTAMPTZ NOT NULL DEFAULT now()
  UNIQUE (user_id, source_video_id)
```

### API Endpoints (Sketch)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/watch-history/ids` | All watched video IDs (for card indicators) |
| `GET` | `/api/v1/watch-history` | Full history list, newest first |
| `POST` | `/api/v1/watch-history` | Record/update a watch event `{ videoId }` |
| `DELETE` | `/api/v1/watch-history?videoId=X` | Remove single entry |
| `DELETE` | `/api/v1/watch-history/all` | Clear entire history |

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | 2026-05-31 | Pending |
| Engineering | | | Pending |
| Design | | | Pending |

---

## Next Steps & Handoffs

1. [ ] **Architect Review** → `docs/adr/0013-watch-history-design.md`
2. [ ] **Spec** → `docs/specs/spec-watch-history.md`
3. [ ] **Implementation Plan** → `docs/plans/plan-watch-history.md`

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-31 | nguyenhuuca | Initial draft |
