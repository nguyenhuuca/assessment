# Plan: User Notifications

<!--
Implementation Plan
Filename: docs/plans/plan-user-notifications.md
Owner: Builder (/builder)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
-->

## Overview

**Status:** Approved
**Author:** nguyenhuuca
**Date:** 2026-10-02
**Related PRD:** [PRD-user-notifications](../prd/PRD-user-notifications.md)
**Related ADR:** [ADR-0017 User Notifications](../adr/0017-user-notifications.md) (accepted)

## Objective

Implement ADR-0017: `notifications` table, Spring events after commit, owner-only REST API, SSE stream (header auth, in-memory publisher), FE bell with real-time badge + polling fallback, deep link to comment, email digest within the email budget, retention job.

## Verified Facts (exploration 2026-10-02)

| Area | Fact | Impact on plan |
|------|------|----------------|
| Async | `@EnableAsync` + `@EnableScheduling` on `FunnyApp`; no custom executor; `spring.threads.virtual.enabled=true` | Verify `applicationTaskExecutor` is virtual-thread based (Boot ≥ 3.2 does this); otherwise add one `@Bean` |
| Events | No `ApplicationEventPublisher` / `@EventListener` anywhere | First event-driven code — keep it small and documented |
| Scheduler | `jobs/AppScheduler` uses `@Scheduled` cron/fixedRate, try/catch per job, no ShedLock (single instance) | Add digest + retention jobs in the same style |
| Mail | `MailService` (`sendSimpleMail`, `@Async sendInvitation`), `EmailCacheLimiterImpl` daily counter, `app.email-setting.max-daily-emails: 200` | Digest must check/increment the same limiter and use ≤ 50% of the budget |
| Users | `UserRepo.findAllByUserName(email)` returns `User` (status ACTIVE/DEACTIVATED); `UserSettingsRepository` has no finder by user | Add `findByUserId` |
| JWT | `JwtProvider.verifyToken` checks `exp` but does not expose it; TTL 30 days; filter writes 401 JSON | Stream does not close on expiry; **emitter timeout 30 min** forces re-auth on reconnect |
| CORS | origins `*.canh-labs.com`, `localhost:*`; all headers allowed | `fetch` with `Authorization` to the stream works cross-origin |
| Metrics | Micrometer + `/actuator/prometheus`; no custom meters yet | First custom gauge |
| FE header | `AppShell.jsx` right section: search, login/profile/logout, theme toggle | Bell goes before the theme toggle (logged-in only), also in mobile header |
| FE auth | `AuthContext` exposes `jwt`, `isLoggedIn`, `logout`; `client.js` exports `getBaseUrl`-style base + `createHeaders()` | Reuse for the stream `fetch` |
| FE queries | `retry: 1`, `refetchOnWindowFocus: false` globally | Unread-count query overrides `refetchOnWindowFocus: true` |
| Deep link | `AppShell` reads `?v={id}` on mount → `jumpIndex`; comment panel opened via `onShowComments(video)` | Extend to `?v={id}&c={commentId}` → open panel + highlight |
| FE tests | Only `window.matchMedia` mocked; `client.test.js` mocks `fetch` | Stream tests need a `ReadableStream` helper |

---

## Implementation Steps

### Phase 1 — Data layer (0.5 d) — `NT-1`
- [ ] Migration `api/src/main/resources/db/changelog/sql/202610030001-create-notifications.sql` (schema from ADR-0017: table, `idx_notifications_user_unread`, partial unique `uq_notifications_unread_group`, `idx_notifications_digest`)
- [ ] `enums/NotificationType` (`COMMENT_REPLY`, `COMMENT_REMOVED`)
- [ ] `entity/Notification` (`payload` as `@JdbcTypeCode(SqlTypes.JSON) Map<String,Object>`)
- [ ] `repo/NotificationRepository`
  - `Page<Notification> findByUserIdOrderByUpdatedAtDesc(Long, Pageable)`
  - `long countByUserIdAndReadAtIsNull(Long)`
  - `Optional<Notification> findByIdAndUserId(UUID, Long)` (owner-scoped)
  - `@Modifying int markAllRead(Long userId, Instant now)`
  - native `upsertGrouped(...)` — `INSERT … ON CONFLICT (user_id, group_key) WHERE read_at IS NULL DO UPDATE SET actor_count = notifications.actor_count + 1, actor_display = EXCLUDED.actor_display, payload = EXCLUDED.payload, comment_id = EXCLUDED.comment_id, updated_at = now(), emailed_at = NULL`
  - `@Modifying int deleteReadOlderThan(Instant)`
- [ ] `UserSettingsRepository.findByUserId(Long)`

**AC:** migration applies; repo methods covered by unit tests (Mockito) — native SQL reviewed manually.

### Phase 2 — Producers (1 d) — `NT-2` (after NT-1)
- [ ] Events (records): `CommentRepliedEvent(videoId, rootId, parentId, commentId, actorEmail, snippet)`, `CommentRemovedEvent(List<RemovedComment(commentId, videoId, authorEmail)>, reason)`
- [ ] Publish from `VideoCommentServiceImpl.createComment` (only when `parentId` present; capture *original* parent before root normalization) and `AdminCommentServiceImpl.moderate/bulkModerate` (only comments that actually changed to REMOVED)
- [ ] `NotificationEventListener` — `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`
  - Reply recipients = {root author, original-parent author} − actor − guests (`userId` blank) − unknown/deactivated users
  - `group_key = "REPLY:" + rootId`; `REMOVED:` + commentId
  - `actor_display` = email local part; snippet ≤ 140 chars, whitespace-collapsed
  - After write → `NotificationPublisher.unreadChanged(userId)`
  - All exceptions caught and logged (never propagate)
- [ ] Verify async executor uses virtual threads (log `Thread.currentThread().isVirtual()` in a test or add `@Bean(name = "applicationTaskExecutor")` with `Executors.newVirtualThreadPerTaskExecutor()`)
- [ ] Tests: recipients (actor excluded, guest excluded, same person root+parent → one row), grouping calls upsert with same key, removed event per changed comment only, listener swallows errors, nothing published when reply validation fails

**AC:** posting a reply/moderating never fails because of notifications; correct recipients.

### Phase 3 — REST API (0.5 d) — `NT-3` (after NT-1)
- [ ] `dto/notification/NotificationDto` (fields per ADR), `UnreadCountDto`
- [ ] `service/NotificationService` + impl (`list`, `unreadCount`, `markRead`, `markAllRead`) — current user from `AppUtils.getCurrentUser()`; `markRead/markAllRead` → `publisher.unreadChanged`
- [ ] `web/NotificationController` at `BASE_URL + "/notifications"` — **authenticated route** (not in `ALLOW_ALL_METHOD` / `OPTIONAL_AUTH_PATH`)
- [ ] Tests: `@WebMvcTest` 401 without JWT; another user's id → 404; paging; read-all

### Phase 4 — SSE (1 d) — `NT-4` (after NT-3)
- [ ] `service/notification/NotificationPublisher` interface; `InMemorySsePublisher` (`ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>`, no `synchronized`)
  - `register(userId)`: new `SseEmitter(30 min)`; cap 5 per user (complete oldest); remove on completion/timeout/error
  - `unreadChanged(userId)`: compute count once, send `event: unread` `data: {"unread": n}` to each emitter; drop failed emitters
  - Heartbeat `@Scheduled(fixedRate = 25 s)`: send comment `ping` to all emitters
  - Gauge `notifications_sse_active_connections`
- [ ] `GET /notifications/stream` (`produces = text/event-stream`), headers `Cache-Control: no-cache`, `X-Accel-Buffering: no`; sends current unread on connect
- [ ] Tests: register/send/remove-on-error, cap 5, heartbeat, gauge value, controller returns 401 without header (via real `JWTAuthenticationFilter`, like `VideoReactionControllerTest`)

**AC:** `curl -N -H "Authorization: <jwt>" …/notifications/stream` prints `event: unread` then pings; reply from another account pushes a new count within 3 s.

### Phase 5 — Email digest & retention (0.5 d) — `NT-5` (after NT-1)
- [ ] `AppScheduler.notificationDigest` `@Scheduled(cron = "0 */15 * * * *")`: users with `notify_email` and unread `emailed_at IS NULL` → one plain-text email each (list ≤ 5 items + "và N thông báo khác", link to app, footer "Tắt email trong Cài đặt") → set `emailed_at`
- [ ] Budget: before each email check `EmailCacheLimiter` daily count `< max-daily-emails * 0.5`; stop the run otherwise; increment after send
- [ ] `AppScheduler.notificationRetention` daily 03:30 Asia/Ho_Chi_Minh → `deleteReadOlderThan(now − 90 d)`
- [ ] Tests: opted-out user skipped, budget stop, `emailed_at` set, retention cutoff

### Phase 6 — Frontend (1.5 d) — `NT-6` (contract-first; integrate after NT-3/NT-4)
- [ ] `src/api/notifications.js`: `list(page)`, `unreadCount()`, `markRead(id)`, `markAllRead()`
- [ ] `src/api/sseStream.js`: `openStream({ path, headers, onEvent, signal })` — `fetch` + `TextDecoderStream`, buffer split on blank line, parse `event:`/`data:`, ignore `:` comments; returns on end; throws `{status}` on non-2xx
- [ ] `src/hooks/useNotifications.js`
  - `useUnreadCount()` — key `['notifications','unread']`, `refetchOnWindowFocus: true`, `refetchInterval` 30 s when stream down / 120 s when up
  - `useNotificationList(page)`, `useMarkRead()`, `useMarkAllRead()` (optimistic badge decrement)
  - `useNotificationStream()` — mounted once in `AppShell` when logged in; `AbortController` on logout/unmount; backoff 1 s → 30 s + 1–5 s jitter; stop on 401; on `unread` → `setQueryData` + invalidate list
- [ ] `components/notifications/NotificationBell.jsx` (badge, 9+ cap, `aria-label`), `NotificationDropdown.jsx` (latest 10, empty state, mark all, Esc/outside click closes), item text per type:
  - `COMMENT_REPLY`: "**bob** đã trả lời bình luận của bạn" / "**bob** và N người khác đã trả lời…" + snippet
  - `COMMENT_REMOVED`: "Bình luận của bạn đã bị gỡ: {reason label}"
- [ ] Deep link: item click → `markRead` → navigate `?v={videoId}&c={commentId}`; `AppShell` reads `c` → opens `CommentPanel` → scroll to + highlight comment (`data-comment-id`), expand its thread
- [ ] Styles in `styles/index.css` (`.notif-bell`, `.notif-badge`, `.notif-dropdown`, `.notif-item.unread`, `.comment-highlight`)
- [ ] Tests: SSE parser (event split across chunks, multiple events in one chunk, comments ignored), stream hook reconnect + stop on 401 (fake timers), bell badge from stream event, dropdown render + mark all, deep link opens panel and highlights
- [ ] `npm run lint && npx vitest run && npm run build`

### Phase 7 — Ops & verification (0.5 d) — `NT-7`
- [ ] (Optional) nginx stream location with `proxy_read_timeout 1h` (ADR-0017 Operations) — manual on VM, only with owner approval
- [ ] Prod E2E with two accounts: B replies to A → A's badge updates < 3 s without reload; click → opens comment; mark all; admin removes A's comment → A notified with reason; restart backend (deploy) → stream reconnects; digest email received only when `notify_email` on
- [ ] Check `/actuator/prometheus` gauge; update `docs/tracking.md`

---

## Dependency Graph

```
NT-1 ──┬──► NT-2 ──┐
       ├──► NT-3 ──► NT-4 ──┐
       └──► NT-5            ├──► NT-7
NT-6 (contract-first) ──────┘
```

Suggested swarm: **Builder A** NT-1 → NT-2 → NT-5; **Builder B** NT-3 → NT-4 (after NT-1 lands); **Builder C** NT-6 in parallel. In practice NT-1..NT-5 share packages → one BE builder sequentially + one FE builder is safer.

**Estimate:** ≈ 5.5 days (≈ 3 days on the critical path with BE/FE in parallel).

## Risks

| Risk | Mitigation |
|------|-----------|
| `@Async` not on virtual threads → platform pool saturation | NT-2 verification step / explicit executor bean |
| Event published inside a rolled-back tx | `AFTER_COMMIT` phase only |
| SSE parser bugs (chunk boundaries) | Dedicated unit tests with split chunks |
| Email budget shared with magic links | 50% cap + stop run |
| Native upsert with partial unique index syntax | Manual review + prod E2E; `ON CONFLICT (user_id, group_key) WHERE read_at IS NULL` must match the index predicate exactly |
