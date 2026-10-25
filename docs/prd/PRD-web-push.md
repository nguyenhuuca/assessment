# PRD: Web Push Notifications

<!--
Product Requirements Document
Filename: docs/prd/PRD-web-push.md
Owner: nguyenhuuca
Handoff to: Architect (/architect), UI/UX Designer (/ui-ux-designer)
Related Skills: scope, planning-artifacts
-->

## Overview

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-10-25
**Version:** 1.0
**Tracking:** N/A (added to `docs/tracking.md` after approval)
**PR-FAQ:** N/A. A retention feature on top of the existing PWA and notification system, not a new initiative.
**Stakeholders:** nguyenhuuca (owner, SUPER_ADMIN), admins, signed-in viewers
**Builds on:** [plan-pwa-basic](../plans/plan-pwa-basic.md) (service worker `webapp/public/sw.js`), [ADR-0017 User Notifications](../adr/0017-user-notifications.md) (in-app notifications + SSE), [ADR-0019 Admin Video Import](../adr/0019-admin-video-import.md)

## Problem Statement

Today a viewer only hears from Funny Movies while the site is open:
- In-app notifications reach the browser over SSE (`service/notification/InMemorySsePublisher.java`).
- The email digest (`service/notification/NotificationDigestService.java`) only covers personal events such as comment replies and suggestions.

Once the tab is closed, nothing tells a viewer that new videos arrived, so there is no pull back to the site.

Admins have the same gap for operational events. An import that finishes or fails, or an HLS transcode that fails, is only visible when they open the admin pages.

The PWA work (2026-10-24) added a service worker and made the site installable, which is the base Web Push needs.

### Evidence

**Quantitative Evidence:**
- None measured yet. There is no retention or return-visit metric today; this feature adds the first one (push click-through).
- 60 Drive videos and a steady import flow (Facebook reels, YouTube) mean new content arrives most days. Today that content is invisible to anyone not on the site.

**Qualitative Evidence:**
- Owner, 2026-10-24: wants features that bring viewers back. Push was ranked right after share previews and analytics.
- The `user_settings.notify_new_content` toggle already exists (`entity/UserSettings.java:30`, Settings page `webapp/src/components/settings/SettingsPage.jsx:257`), but nothing sends new-content notifications today. Users can switch it on and nothing happens.

## Goals & Success Metrics

| Goal | Metric | Target |
|------|--------|--------|
| Reach users when the site is closed | A subscribed device receives the push with the tab or app closed | Works on Chrome desktop, Chrome Android, and iOS 16.4+ with the PWA installed; tapping opens the right screen |
| Bring viewers back | Push click-through rate = opened / delivered, per push type, shown in admin stats | Measured from day 1. Target set after 2 weeks of data (assumption: ≥ 5% for the daily digest) |
| Admins learn about failures fast | Time from import DONE/FAILED or HLS ERROR to the admin push | < 1 min |

> **Assumption:** the click-through target is a starting guess. The first two weeks set the real baseline.

## User Stories

### Signed-in viewer

- As a viewer, I want one notification a day when new videos are out, so that I come back without being spammed.
  - Acceptance: at most 1 digest push per user per day, sent at 19:00 Asia/Ho_Chi_Minh, only when at least one video was published since the previous digest. It shows "N video mới hôm nay" (localized) with the app icon, no image, and opens the feed when tapped.
- As a viewer, I want to turn push on for this device from Settings, so that the browser only asks for permission when I choose to.
  - Acceptance: a "Bật thông báo trên thiết bị này" control in Settings. The browser permission prompt appears only after that click, never on page load. A denied permission shows a short hint on how to re-enable it.
- As a viewer, I want to stop new-video pushes without uninstalling anything.
  - Acceptance: the existing "new content" toggle (`notify_new_content`) controls the digest. "Turn off on this device" removes that device's subscription.

### Admin / SUPER_ADMIN

- As an admin, I want a push when a video import finishes or fails, so that I can check or retry it.
  - Acceptance: the push is sent when an import job reaches `DONE` or `FAILED` (`enums/ImportStatus.java`, set in `service/impl/VideoImportWorker.java:210,303`). The text names the video title or URL and the error code. Tapping opens Admin → Import.
- As an admin, I want a push when an HLS transcode fails, so that I know a selected video stays on Range.
  - Acceptance: the push is sent when a job ends in `ERROR` with a code (HLS worker `fail()`). It is not sent for a status the owner changed. Tapping opens Admin → Videos.
- As an admin, I want to turn admin pushes off without losing the daily digest.
  - Acceptance: a separate "Admin alerts" toggle, shown only to ADMIN and SUPER_ADMIN.

## Requirements

### Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | Store push subscriptions per signed-in user and device (endpoint, keys, user agent, created/last-success time). The endpoint is unique. | Must Have | New table, Liquibase changeset in `api/src/main/resources/db/changelog/sql/` |
| FR-2 | Endpoints to subscribe and unsubscribe the current device (authenticated), and to fetch the public VAPID key | Must Have | Responses wrapped in `ResultObjectInfo<T>` |
| FR-3 | Service worker handles `push` (show the notification with title, body, app icon and target URL) and `notificationclick` (focus or open the target URL and report the click) | Must Have | Extends `webapp/public/sw.js` without changing its caching rules |
| FR-4 | Daily digest job at 19:00 Asia/Ho_Chi_Minh, registered as a dynamic scheduled job, so the owner can change the cron in Admin → Jobs | Must Have | Uses the existing `DynamicJobScheduler` and `scheduled_job` table |
| FR-5 | The digest is sent only to users with `notify_new_content = true`, a subscription, and new published videos since their last digest | Must Have | At most one digest per user per day |
| FR-6 | Admin pushes on import `DONE` or `FAILED` and HLS `ERROR`, sent to ADMIN and SUPER_ADMIN with admin alerts on | Must Have | Asynchronous: never block or fail the import or the HLS job |
| FR-7 | Delivery bookkeeping: count sent, failed and clicked per push type. Show click-through in admin stats | Must Have | Success metric |
| FR-8 | Expired subscriptions (push service answers 404 or 410) are deleted automatically. Subscriptions are deleted with the account | Must Have | |
| FR-9 | Settings UI: "enable on this device" and "turn off on this device" controls with the permission flow and states (unsupported, denied, enabled). An "Admin alerts" toggle for admins. Strings in en/vi | Must Have | iOS: hint to install the PWA first, because push needs it |
| FR-10 | Payload in the user's locale (`user_settings.locale`, default vi) | Should Have | |
| FR-11 | A test-push button for the SUPER_ADMIN (to the caller's own devices) | Nice to Have | Helps manual checks |

### Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Performance | The digest for all subscribers completes in < 1 min at today's scale, sent from a bounded virtual-thread pool, never on a request thread. Admin push dispatch takes < 5 s |
| NFR-2 | Availability | A push failure (provider down, bad key) never affects imports, HLS, the feed or the in-app notifications |
| NFR-3 | Security / privacy | VAPID private key only in env or secret config, never in the repo. The payload holds no secrets or tokens and is encrypted per RFC 8291. Subscriptions are only for authenticated users, and a user can only remove their own. External data routing (Google FCM, Apple, Mozilla push endpoints) is documented in the ADR (`security.md` "No Silent External Data Routing") |
| NFR-4 | Abuse | Subscribe endpoint rate-limited. At most 10 subscriptions per user (oldest dropped) |

## Scope

### In Scope

- Web Push (W3C Push API + VAPID) sent directly from the backend
- Signed-in users and admins, one subscription per device
- Daily new-video digest (19:00 VN, text + app icon, opens the feed), controlled by `notify_new_content`
- Admin alerts: import DONE or FAILED, HLS ERROR
- Settings controls, service worker push and click handling, click-through tracking in admin stats
- Cleanup of expired subscriptions and on account deletion

### Out of Scope

- Push for guests (not signed in)
- Push for personal interactions (comment reply, suggestion decided, report closed). These keep using in-app notifications, SSE and email
- A per-user digest time (fixed 19:00 in v1)
- Third-party push services (Firebase FCM SDK, OneSignal)
- Report or suggestion admin alerts
- Images or thumbnails in the notification (owner, 2026-10-25: not needed)
- Rich actions in the notification (action buttons), badges, silent pushes

## Dependencies

| Dependency | Owner | Status | Risk |
|------------|-------|--------|------|
| PWA service worker `webapp/public/sw.js` | — | Shipped 2026-10-24 | Low |
| A Java Web Push library (VAPID signing + RFC 8291 encryption), e.g. `nl.martijndwars:web-push` with BouncyCastle | nguyenhuuca | To choose in the ADR | Med: new dependency, tech-strategy update |
| VAPID key pair in prod env (`VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT`) | nguyenhuuca | Not created | Low |
| Browser push services (FCM for Chrome, Apple Push for Safari/iOS, Mozilla autopush) | external | Available | Med: external data routing, must be documented |
| `DynamicJobScheduler` / `scheduled_job` (ADR-0023) | — | In place | Low |
| iOS 16.4+ and the PWA installed on the home screen | users | — | Med: iOS users must install first |

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Users find pushes annoying and block them | M | H | One digest per day max, only when there is new content, opt-in from Settings (no prompt on page load) |
| iOS users never get pushes because the PWA is not installed | H | M | Settings explains "install to home screen first" on iOS. Measure per platform |
| Bad or rotated VAPID keys break all pushes | L | H | Keys in env. Startup check logs a clear error. Pushes fail closed without affecting the app |
| Push provider slow or down makes the digest job hang | L | M | Per-request timeouts, bounded concurrency, the job ends within its window. Failures are counted, not retried forever |
| Dead subscriptions pile up | M | L | Delete on 404/410. Cap per user |
| A new dependency pulls a large crypto library onto a small VM | M | L | Pick a maintained, minimal library in the ADR. Measure the startup and heap impact |

## Open Questions

- [ ] Which Java Web Push library (maintenance status, BouncyCastle version, Java 24 compatibility)? Decide in the ADR.
- [ ] Does the digest count only videos published since the user's last digest, or the last 24 h for everyone? Proposed: since the user's last digest, else 24 h.
- [ ] Should a click also mark related in-app notifications as read? Proposed: no (separate systems in v1).

## Appendix

### Mockups/Wireframes

N/A. One new block in Settings (device push on/off with state text, admin alerts toggle for admins). It reuses the existing settings toggle rows.

### Research

- [W3C Push API](https://www.w3.org/TR/push-api/), [RFC 8030 Web Push protocol](https://datatracker.ietf.org/doc/html/rfc8030), [RFC 8291 message encryption](https://datatracker.ietf.org/doc/html/rfc8291), [RFC 8292 VAPID](https://datatracker.ietf.org/doc/html/rfc8292)
- WebKit: Web Push for web apps on iOS and iPadOS 16.4 (home-screen web apps only)

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product | nguyenhuuca | | Pending |
| Engineering | nguyenhuuca | | Pending |
| Design | N/A | | N/A |

---

## Next Steps & Handoffs

After PRD approval:

1. [ ] **Architect Review**: ADR for the push provider and library, VAPID key handling, subscription storage and the external data routing
   - Trigger: `/architect`
   - Output: ADR (`docs/adr/0032-web-push.md`)

2. [ ] **Spec + sequence diagrams**: `/spec docs/prd/PRD-web-push.md docs/adr/0032-web-push.md`
   - Output: `docs/specs/spec-web-push.md`, `docs/sequence/sequence-web-push.md`

3. [ ] **Plan**: `/swarm-plan` → `docs/plans/plan-web-push.md`
