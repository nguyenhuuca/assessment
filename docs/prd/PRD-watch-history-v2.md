# PRD: Watch History (v2)

<!--
Product Requirements Document
Filename: docs/prd/PRD-watch-history-v2.md
Owner: nguyenhuuca
Handoff to: Builder (/swarm-execute)
-->

## Overview

**Status:** Approved (2026-10-15)
**Author:** nguyenhuuca
**Date:** 2026-10-15
**ADR:** [ADR-0021 Watch History](../adr/0021-watch-history.md)
**Plan:** [plan-watch-history-v2](../plans/plan-watch-history-v2.md)
**Design:** Stitch "History & My Suggestions" (`artifacts/stitch_history/`)
**Supersedes:** [PRD-watch-history](PRD-watch-history.md) and [PRD-watch-history-opus](PRD-watch-history-opus.md) (June 2026 drafts, never implemented).

The History page gets a **"Lịch sử xem" / "Watch history"** tab next to "Đề xuất của tôi". It is a private, per-user list of the videos the user watched, with these features:
- grouping by day
- progress and resume
- search
- removing one entry or clearing all
- a "pause history" switch

The feed marks the videos already watched.

---

## Problem Statement

- A logged-in user who scrolls past a video cannot find it again. The app has no record of what they watched.
- The History nav item only shows suggestions.
- Settings already offers "Incognito Mode — watch history won't be recorded", but there is no history, so the switch does nothing.

---

## Goals & Success Metrics

| Goal | Metric | Target (30 days) |
|------|--------|------------------|
| Users come back to videos | Plays started from the History tab / weekly active users | ≥ 10 % |
| Resume works | Resumed plays that start within 2 s of the saved position | ≥ 95 % |
| Privacy promise is kept | Entries or progress written while incognito is on | 0 |
| Cheap | p95 latency of `POST /videos/{id}/view` with history vs. without | ≤ +10 ms |

---

## Users & Stories

**Logged-in user:**
- I open History → **Lịch sử xem** and see my videos, newest first, grouped **Hôm nay / Hôm qua / Trước đó**. Each card shows the thumbnail, title, views, "Đã xem 2 giờ trước" and a progress bar, or "Đã xem hết".
- I tap a card and the video plays from where I stopped. A finished video starts from the beginning.
- I search my history by title.
- I remove one entry from its "⋮" menu. I clear all entries after a confirmation.
- I switch **"Tạm dừng lịch sử"** on, and nothing I watch is recorded until I switch it off. It is the same switch as Settings → Incognito.
- In the feed and in Explore, videos I already watched show a small **"Đã xem"** badge, or a progress line if I watched only part.

**Guest:** has no watch history. The History tab shows a sign-in hint, as it does today.

---

## Requirements

### Functional

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-1 | A video is recorded for a logged-in user when its view is reported (≥ 3 s of real, visible playback, the existing rule). A re-watch moves it to the top. | P0 |
| FR-2 | Nothing is recorded, inserted or updated, while `incognito_enabled` is on. | P0 |
| FR-3 | List newest first, paged (20 per page, "Xem thêm"), grouped client-side by local day: Today / Yesterday / Earlier. | P0 |
| FR-4 | Card: thumbnail, title, view count, relative watched time, progress bar, or "Đã xem hết" when completed (≥ 90 % or within 10 s of the end). | P0 |
| FR-5 | Progress saved on pause, on switching video, on tab hide and every 30 s while playing. | P0 |
| FR-6 | Resume: opening a card plays the video and seeks to the saved position when it is > 5 s and not completed. | P0 |
| FR-7 | Remove one entry. Clear all, after a confirmation dialog. | P0 |
| FR-8 | A "Tạm dừng lịch sử" toggle on the History tab, bound to `user_settings.incognito_enabled` (the same flag as Settings). | P0 |
| FR-9 | Search by title, case-insensitive, debounced 300 ms. | P1 |
| FR-10 | A "Đã xem" badge or progress line on feed and Explore videos, from one summary request per session (no request per card). | P1 |
| FR-11 | History tab header shows the total count. The page has top tabs "Lịch sử xem (N)" and "Đề xuất của tôi (N)". | P1 |
| FR-12 | Deleting the account deletes the user's history. | P0 |
| FR-13 | All text in vi and en (ADR-0020). | P0 |

### Non-functional

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Owner-only access: the user id comes from the JWT; other users' entries are never readable or deletable. | 100 % |
| NFR-2 | At most 500 entries per user, enforced on write. | Hard cap |
| NFR-3 | History list is one query per page (no N+1). | 1 query |
| NFR-4 | Progress calls never disturb playback (fire-and-forget, `keepalive`). | — |
| NFR-5 | Rate limits on every endpoint (ADR-0021 Compliance). | — |

---

## Out of Scope

- History for guests, and syncing a guest's history at login.
- Watch-time analytics and recommendations based on history.
- Time-based retention. The 500-entry cap is the only bound.
- Accent-insensitive search (see OQ-2).
- History for videos that are not on the app (external links in suggestions).

## Open Questions

- **OQ-1:** Progress heartbeat interval. **Default:** 30 s, plus pause, switch and hide.
- **OQ-2:** Accent-insensitive search ("meo" finds "mèo") needs the Postgres `unaccent` extension on prod. **Default:** accent-sensitive `ILIKE` in v1, revisited if users ask.
- **OQ-3:** Should the feed skip or demote already-watched videos? **Default:** no, only a badge.
- **OQ-4:** The Stitch design shows "Pause history" and "Incognito" as two switches. **Default:** one flag, shown as "Tạm dừng lịch sử" on History and "Chế độ ẩn danh" in Settings.
