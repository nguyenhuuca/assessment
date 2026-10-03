# PRD: User Notifications

<!--
Product Requirements Document
Filename: docs/prd/PRD-user-notifications.md
Owner: nguyenhuuca
Handoff to: Architect (ADR-0017, accepted), Builder (/builder)
-->

## Overview

**Status:** Approved
**Author:** nguyenhuuca
**Date:** 2026-10-02
**Related ADR:** [ADR-0017 User Notifications](../adr/0017-user-notifications.md)
**Related Plan:** [plan-user-notifications](../plans/plan-user-notifications.md)

Logged-in users get an in-app notification inbox (bell icon with unread badge, updated in real time) and an optional email digest when someone replies to them or when a moderator removes their comment.

---

## Problem Statement

Threaded replies and admin moderation shipped on 2026-10-02, but users are never told when:
- someone replies to their comment — they must reopen the comment panel of every video they commented on;
- their comment was removed for a policy violation — it silently disappears, so they don't learn the rule.

The Settings page already shows **"Email notifications"** and **"New content"** toggles (`user_settings.notify_email`, `notify_new_content`), but no code reads them — a promise not kept.

### Evidence
- **Quantitative:** comments exist on multiple videos (e.g. #6, #22, #40, #41) with no reply activity before replies shipped; no metric yet on return-to-thread rate.
- **Qualitative:** reply and moderation features have no feedback loop to the affected user.

---

## Goals & Success Metrics

| Goal | Metric | Target (30 days after launch) |
|------|--------|-------------------------------|
| Users learn about replies | % of reply notifications opened (read) | ≥ 40% |
| Conversations continue | % of reply notifications followed by a reply from the recipient within 24 h | ≥ 10% |
| Real-time feel | p95 time from reply commit to badge update (SSE connected) | ≤ 3 s |
| No spam | Digest emails per user per day | ≤ 4 (max 1 per 15 min, only when unread) |
| Reliability | 5xx on `/notifications/**` | < 0.1% |

---

## User Stories

### Persona: Logged-in viewer
- **US-1** As a viewer, when someone replies to my comment (or replies after me in a thread I started), I see a red badge on the bell within seconds, without reloading.
- **US-2** As a viewer, I open the bell and see "bob và 2 người khác đã trả lời bình luận của bạn", with a short snippet and time.
- **US-3** As a viewer, clicking a notification opens that video with the comment panel scrolled to the comment, and marks it read.
- **US-4** As a viewer, I can mark all notifications as read.
- **US-5** As a viewer whose comment was removed, I get a notification with the policy reason (not the removed text).
- **US-6** As a viewer with "Email notifications" on, I receive at most one email per 15 minutes summarizing unread notifications; with it off, I get none.
- **US-7** As a viewer with two tabs open, reading a notification in one tab updates the badge in the other.

### Persona: Admin
- **US-8** As an admin, removing comments (single or bulk) notifies each affected author once per comment, without slowing down the moderation action.

---

## Requirements

### Functional

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-1 | Create `COMMENT_REPLY` notification for the thread root author and the replied-to author; never for the actor; never for guests or deactivated users | Must |
| FR-2 | Collapse unread reply notifications per (recipient, thread) into one row with `actorCount` and latest actor | Must |
| FR-3 | Create `COMMENT_REMOVED` notification for the author on admin REMOVE (single and bulk); include reason code; never include removed content | Must |
| FR-4 | `GET /notifications` (paged, newest first), `GET /notifications/unread-count`, `PATCH /notifications/{id}/read`, `PATCH /notifications/read-all` — owner-only | Must |
| FR-5 | `GET /notifications/stream` (SSE) authenticated with the `Authorization` header; emits `unread` count on connect and on change; heartbeat every 25 s | Must |
| FR-6 | FE bell with unread badge (9+ cap), dropdown with latest 10, empty state, "Đánh dấu đã đọc tất cả" | Must |
| FR-7 | Click → mark read → open video via `?v={videoId}&c={commentId}` with comment panel open and the comment highlighted | Must |
| FR-8 | FE opens the stream with `fetch` + header (no token in URL), reconnects with backoff + jitter, stops on 401, falls back to polling (30 s when disconnected, 120 s when connected) | Must |
| FR-9 | Email digest every 15 min for users with `notify_email = true`: unread & not-yet-emailed notifications → one plain-text email; respects daily email budget | Should |
| FR-10 | Retention: delete read notifications older than 90 days (daily job) | Should |
| FR-11 | Notification creation never blocks or fails the reply/moderation request | Must |

### Non-Functional

| ID | Requirement |
|----|-------------|
| NFR-1 | Security: owner isolation (other user's id → 404, CWE-639); no credentials in URLs (CWE-598); snippet ≤ 140 chars, rendered as text (no HTML) |
| NFR-2 | Privacy: actor shown as display name / email local part, never full email |
| NFR-3 | Performance: `unread-count` < 20 ms p95 (indexed); SSE idle cost ≈ one socket per tab |
| NFR-4 | Capacity: ≥ 3,000 concurrent SSE connections on the current single VM (nginx `worker_connections` 8192 since 2026-10-03; was ≈ 380); `notifications_sse_active_connections` gauge exposed |
| NFR-5 | Email budget: digest must leave ≥ 50% of `app.email-setting.max-daily-emails` (currently 200/day) for login magic links |
| NFR-6 | Accessibility: bell button has `aria-label` with unread count; dropdown keyboard-navigable; Esc closes |

---

## Scope

### In Scope
Reply and moderation notifications, in-app inbox + SSE + polling fallback, email digest, retention, deep link to comment.

### Out of Scope
- "Your video got a like/comment" (no video owner column yet)
- `@mention` notifications
- New-content broadcast (`notify_new_content`) — later
- Browser/OS push notifications, mobile push
- Guest notifications
- Per-type notification preferences (only the global email toggle)

---

## Dependencies

| Dependency | Status |
|------------|--------|
| ADR-0017 | Accepted |
| Threaded replies (`parentId` normalized to root) | Shipped (`a23950f`) |
| Comment moderation + bulk | Shipped (`acfc953`, `6f66b15`) |
| `user_settings.notify_email` | Exists |
| nginx `/api/` with `proxy_buffering off` | Verified on server |
| Gmail SMTP, 200 emails/day budget | Exists — constraint |

---

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| Email budget exhausted by digests, blocking magic-link login | Medium | High | Digest capped at 50% of daily budget; magic links take priority; move to SES/SendGrid when needed |
| Notification lost if JVM crashes between commit and async handler | Low | Low | Accepted v1; outbox later |
| SSE dropped by Cloudflare/nginx idle timeouts | Medium | Low | 25 s heartbeat; polling fallback |
| Busy threads flood a user | Medium | Medium | Grouping per thread while unread |
| Second instance added later breaks in-memory push | Low | Medium | `NotificationPublisher` interface; polling keeps correctness; switch to `PgNotifyPublisher` |

---

## Open Questions

| # | Question | Decision |
|---|----------|----------|
| OQ-1 | Notify the root author when someone replies to *another* reply in their thread? | **Yes** (they started the conversation) — grouped per thread |
| OQ-2 | Notify on admin RESTORE? | **No** (v1) |
| OQ-3 | Show notifications for comments on hidden videos? | Create, but deep link shows "Video không còn khả dụng" |

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | 2026-10-02 | Approved |
| Engineering | nguyenhuuca | 2026-10-02 | Approved (ADR-0017) |

## Next Steps & Handoffs
1. Plan: [plan-user-notifications](../plans/plan-user-notifications.md)
2. Execute: `/swarm-execute docs/plans/plan-user-notifications.md`
3. QA: E2E with two accounts on prod
4. Update `docs/tracking.md`

## Version History

| Version | Date | Change |
|---------|------|--------|
| 1.0 | 2026-10-02 | Initial |
