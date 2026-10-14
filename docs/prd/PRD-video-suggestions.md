# PRD: Video Suggestions (the "+" button)

<!--
Product Requirements Document
Filename: docs/prd/PRD-video-suggestions.md
Owner: nguyenhuuca
Handoff to: Builder (/swarm-execute)
-->

## Overview

**Status:** Approved (2026-10-15)
**Author:** nguyenhuuca
**Date:** 2026-10-15
**Related Plan:** [plan-video-suggestions](../plans/plan-video-suggestions.md)
**Builds on:** [ADR-0019 Admin Video Import](../adr/0019-admin-video-import.md), [ADR-0017 User Notifications](../adr/0017-user-notifications.md)

The "+" button no longer shares a YouTube link (the legacy `share_link` feature is being retired). It becomes **"Đề xuất video" / "Suggest a video"**:
1. A logged-in user submits a YouTube or Facebook link.
2. An admin approves or rejects it.
3. Approved links go through the existing import pipeline (yt-dlp → Drive → cron).
4. The user is notified at each step.

---

## Problem Statement

Logged-in users see three "+"/upload buttons (the desktop FAB, the mobile nav FAB and a header icon) that open `ShareModal`. The share feature is dead, so the buttons do nothing useful. The only way to add videos today is the admin-only import, so users cannot contribute content at all.

---

## Goals & Success Metrics

| Goal | Metric | Target (30 days) |
|------|--------|------------------|
| Users contribute content | Suggestions submitted per week | ≥ 10 |
| Quality is moderated | Suggestions that reach the app without admin approval | 0 |
| Fast feedback | Median time from suggestion to admin decision | ≤ 24 h |
| No abuse | Suggestions per user per 24 h | ≤ 5, enforced |
| Closed loop | Approved suggestions whose author gets a "video is live" notification | 100% |

---

## Users & Stories

- **User:** I paste a YouTube or Facebook link, with an optional title and a note on why it is fun, and see the status of my recent suggestions in the same dialog.
- **User:** in History → My suggestions I can browse all my past suggestions, filter them by status, and play the ones that went live.
- **User:** I get a notification when my suggestion is approved, when it is rejected (with the reason), and when the video is live. Tapping the live notification plays the video.
- **Admin:** I see a queue of pending suggestions with the link, a preview title, who suggested it, and when.
  - I approve with one click (and can edit the title). The link then enters the normal import queue.
  - I reject with a reason.
- **Admin:** pressing "+" opens the import form directly.

---

## Requirements

### Functional
1. **The "+" buttons:**
   - Logged-in non-admin users: the desktop FAB and the mobile nav FAB open `SuggestVideoModal`.
   - Admins: they open the Admin → Import tab.
   - The header "upload" icon is removed, so two entry points are enough.
2. **Submit:**
   - The URL is required. Only HTTPS YouTube or Facebook links are accepted, using the same allowlist and normalization as import.
   - The title is optional, at most 200 characters. The note is optional, at most 300 characters.
3. **Reject on submit when:**
   - **Duplicate:** a suggestion for the same normalized URL is already pending, or an import of that URL is active or done. The response is 409 with a clear message.
   - **Daily limit:** the user has already made 5 suggestions in the last 24 hours (configurable). The response is 429 with a clear message.
4. **The user's own suggestions:**
   - The modal shows the latest 5 with their status (Chờ duyệt / Đã duyệt / Bị từ chối with the reason / Đã lên app with a link) and a "Xem tất cả" link.
   - The **History → "Đề xuất của tôi"** page shows the full list, paged, with a status filter. It replaces the ComingSoon placeholder of the History nav item.
   - Users only ever see their own suggestions.
5. **The Admin "Đề xuất" tab:**
   - It lists suggestions, pending first, with a status filter and pagination, and shows the pending count on the tab.
   - **Approve** has an optional title override. It creates an import job, either now or using the import defaults. The suggestion moves to APPROVED and is linked to the job.
   - **Reject** uses a reason from a fixed list (duplicate, inappropriate, low quality, copyright, other) plus a note, which is required for "other". The suggestion moves to REJECTED.
6. **Notifications** to the suggester, in-app and in the email digest, in their language:
   - `SUGGESTION_APPROVED`
   - `SUGGESTION_REJECTED` (with the reason)
   - `SUGGESTION_LIVE`, when the cron ingests the Drive file. It deep-links `?v=<videoId>`.
7. **Retire share:** remove `ShareModal`, `videosApi.share`/`delete` and their translations. The backend `share_link` tables and endpoints are left for the owner to delete separately.

### Non-functional
- No new infrastructure. It reuses import, notifications, i18n and rate limiting.
- Approving needs `VIDEO_IMPORT_ENABLED=true`. When import is disabled, approve returns 503, while submissions still queue.
- All text is in vi and en (ADR-0020), and all API errors carry a `code`.

---

## Out of Scope
- Uploading a video file directly from the user's device.
- Auto-approval or trust levels.
- Platforms other than YouTube and Facebook.
- Users editing or withdrawing a suggestion (a possible follow-up).

## Open Questions
- **OQ-1:** Daily limit. **Default:** 5 per user per 24 hours.
- **OQ-2:** Should approving start the download immediately, or follow the import schedule? **Default:** immediately (`scheduledAt = now`). The import semaphore already serializes downloads on 1 vCPU.
- **OQ-3:** Should the suggester's name show in the Admin tab? **Default:** yes, as a masked email (`NotificationText.actorDisplay`).
