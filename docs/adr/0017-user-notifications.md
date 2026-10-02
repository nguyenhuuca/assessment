# ADR-0017: User Notifications

**Status:** Accepted
**Date:** 2026-10-02
**Deciders:** nguyenhuuca
**Related:** [ADR-0003 Use Cache](0003-use-cache.md) (no Redis until horizontal scaling), [ADR-0016 User Settings](0016-user-settings-design.md) (`notify_email`, `notify_new_content`), [plan-comment-replies](../plans/plan-comment-replies.md), [plan-admin-comment-moderation](../plans/plan-admin-comment-moderation.md)

---

## Context

Users now interact with each other (threaded replies) and with moderators (comment removal), but nothing tells them it happened. They must reopen the comment panel and look.

Current state (verified in code):

| Fact | Where | Consequence |
|------|-------|-------------|
| `user_settings.notify_email` and `notify_new_content` exist, are editable in Settings, but **nothing reads them** | `UserSettings`, `UserSettingsServiceImpl` | Preferences already promised to users; notifications are the missing consumer |
| Comment author is stored as **email** (`video_comments.user_id VARCHAR`), users are keyed by `users.id BIGINT` | `VideoComment`, `User` | Recipient must be resolved email → user id |
| Guests comment with a token, no account | `VideoCommentServiceImpl` | Guests cannot receive notifications |
| `video_sources` has **no owner column** | `VideoSource` | "Your video was liked/commented" has no recipient today |
| Mail via `JavaMailSender` (Gmail SMTP), some methods `@Async`, `EmailCacheLimiter` exists | `MailServiceImpl` | Email channel exists but is slow and quota-limited |
| Production is **one VM, one JVM instance** behind nginx (CI: SCP jar → `deploy-funny-app.sh`, `systemctl reload nginx`); Helm chart also pins 1 replica; virtual threads on | `.github/workflows/funnyapp-ci.yml`, `helm/funny-app/values.yaml` | Every SSE connection and every event live in the same JVM → an in-memory registry is sufficient today |
| No event bus, no WebSocket/SSE anywhere | grep | Greenfield for real-time |

## Decision Drivers

1. Ship value fast; fit Golden Paths (Spring Boot, JPA, Liquibase, React Query) — **no Redis/Kafka** (ADR-0003).
2. Never block or fail the user action (posting a reply) because of notification work.
3. Respect existing preferences (`notify_email`).
4. Survive the future move to >1 replica without a rewrite.
5. No email spam; stay within Gmail SMTP limits.

## Scope (v1)

| Type | Trigger | Recipient | Channels |
|------|---------|-----------|----------|
| `COMMENT_REPLY` | Someone replies in a thread | Root comment author **and** the author of the comment being replied to (if different); never the actor | In-app; email if `notify_email` (digest) |
| `COMMENT_REMOVED` | Admin removes a comment (single or bulk) | Comment author | In-app (+ reason); email if `notify_email` |

Out of scope v1: video likes/comments on "your video" (no owner column), `@mention` parsing, new-content broadcast (`notify_new_content`), browser/mobile push, guest notifications.

---

## Options

### A. Delivery to the browser

| Option | How | Pros | Cons |
|--------|-----|------|------|
| **A1. Polling** | `GET /notifications/unread-count` via React Query `refetchInterval` 60 s + refetch on window focus | Trivial, stateless, works with any replica count and proxy, cacheable | Up to 60 s delay; ~1 req/min per open tab |
| **A2. SSE** | `GET /notifications/stream` (`SseEmitter`, one virtual thread per connection), in-memory `userId → emitters` | Near real-time; cheap with virtual threads; one-way fits | In-memory registry breaks with >1 replica (needs Redis pub/sub, prohibited now); proxy buffering/timeouts; reconnect logic; auth needs care (see A′ below) |
| **A3. WebSocket/STOMP** | Spring WebSocket + broker | Bi-directional | Overkill (one-way data), new dependency, same scaling issue |

**Why "multi-instance" matters for push:** an SSE connection lives in the JVM the browser connected to. If the event (Bob's reply) is handled by a different instance than the one holding the recipient's (Alice's) connection, that instance has no emitter to push to. Sticky sessions do not help (actor ≠ recipient). Fix when >1 instance: cross-instance pub/sub — **PostgreSQL `LISTEN/NOTIFY`** (no new infra) first, Redis later.

**Scoring for the actual deployment (single VM, single JVM), 1–5, higher better:**

| Criterion (weight) | A1 Polling | A2 SSE (in-memory) | A3 WS |
|--------------------|-----------:|-------------------:|------:|
| Simplicity (3) | 5 | 4 | 1 |
| Latency (2) | 2 | 5 | 5 |
| Load at scale (2) — idle users cost nothing | 2 | 5 | 5 |
| Fits current deployment (3) | 5 | 5 | 3 |
| Golden-path fit (2) | 5 | 4 | 2 |
| **Weighted total** | **48** | **54** | **34** |

Load comparison (60 s polling vs SSE), estimates to be validated by load test:

| Online users | Polling `unread-count` | SSE |
|-------------:|-----------------------:|----:|
| 1,000 | ~17 req/s | 1,000 idle connections |
| 10,000 | ~170 req/s | 10,000 connections → above Tomcat default `max-connections` (8192); needs tuning/more RAM |
| 100,000 | ~1,700 req/s | needs multiple instances + pub/sub |

### A′. Client transport & auth for the SSE stream

`EventSource` (the browser's built-in SSE API) **cannot set request headers**, so it cannot send `Authorization`.

| Option | How | Pros | Cons |
|--------|-----|------|------|
| **A′1. `EventSource` + `?token=<jwt>`** | JWT in the query string | Built-in auto-reconnect; least FE code | JWT written to nginx/Cloudflare logs (CWE-598/532) — exactly the leak just fixed for video URLs (commit `9e1fb36`); needs custom query-token auth on the server |
| **A′2. `fetch` + `Authorization` header, read `response.body` as a stream** | Parse `event:`/`data:` blocks from a `ReadableStream` | URL carries nothing sensitive; **reuses the existing `JWTAuthenticationFilter`** (endpoint is a normal authenticated route); no new dependency | ~40 lines of FE code: SSE line parsing + reconnect/backoff written by hand |
| **A′3. One-time ticket** | `POST /notifications/stream-ticket` (JWT header) → 30 s single-use ticket → `EventSource(?ticket=)` | Built-in reconnect; logs only hold expired tickets | Extra endpoint + ticket store; every reconnect needs a new ticket |

**Chosen: A′2.** Security first and zero new server-side auth code.

### B. Producing notifications

| Option | How | Pros | Cons |
|--------|-----|------|------|
| **B1. Inline call** | `CommentService` calls `NotificationService.create(...)` in the same transaction | Simple | Couples domains; a failure in notification code fails the reply |
| **B2. Spring application events** | Publish `CommentRepliedEvent` / `CommentRemovedEvent`; `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` (virtual-thread executor) writes notifications | Decoupled; only fires after the reply is committed; failure isolated; no new infra | Lost if the JVM dies between commit and handler (rare; acceptable for v1) |
| **B3. Transactional outbox** | Write `outbox` row in same tx; scheduler dispatches | Guaranteed delivery | More tables + job; premature at this scale |

### C. Email

| Option | Pros | Cons |
|--------|------|------|
| **C1. Immediate email per event** | Simple | Spam on busy threads; Gmail quota |
| **C2. Digest job** (`AppScheduler`, every 15 min): unread & not-yet-emailed notifications per user → one email | Bounded volume; respects `notify_email` | Up to 15 min delay |

---

## Decision

1. **Storage:** new table `notifications` (in-app inbox is the source of truth).
2. **Producing:** **B2** — Spring events, `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` on the virtual-thread executor. Upgrade path to B3 (outbox) if delivery guarantees become required.
3. **Delivery:** **A2 SSE with an in-memory emitter registry** (`userId → emitters`, one user may have several tabs), emitting only `{"unread": n}` hints — the browser then fetches via REST. **Polling stays as a fallback** (React Query `refetchInterval` 120 s while SSE is connected, 30 s while it is down, plus on window focus), so a dropped stream or a restart never loses data, only latency.
   - Push goes through an interface `NotificationPublisher { void unreadChanged(long userId); }`:
     `InMemorySsePublisher` now; `PgNotifyPublisher` (LISTEN/NOTIFY) when a second instance appears; `RedisPublisher` only beyond that (ADR-0003). Swapping the implementation does not touch the API, table or FE.
   - Auth (A′2): the browser opens the stream with **`fetch` + `Authorization` header** and reads `response.body` as a stream; **no token in the URL**. The endpoint is a normal authenticated route (not in `ALLOW_ALL_METHOD` / `OPTIONAL_AUTH_PATH`), so the existing `JWTAuthenticationFilter` returns 401 without a valid JWT. The server closes the stream when the JWT expires; the client reconnects with the current token (or stops after logout).
   - Heartbeat comment every 25 s; emitter timeout 30 min (client auto-reconnects); per-user cap 5 connections (oldest closed).
   - Deploy restarts the JVM → all streams drop; the client reconnects with exponential backoff (1 s → 30 s cap) plus 1–5 s jitter; on 401 it stops and waits for login. Hidden tabs keep the stream (cheap); `AbortController` closes it on logout/unmount.
4. **Email:** **C2 digest** every 15 minutes, only for users with `notify_email = true`, only for unread notifications not yet emailed; at most one email per user per run.
5. **Recipient resolution:** comment `user_id` (email) → `users.id` via `UserRepo` at event-handling time; guests and deactivated users are skipped.
6. **Grouping:** multiple replies in the same thread for the same recipient while unread are **collapsed** into one row (`actor_count`, latest actor, `updated_at` bumped) — avoids a flood from busy threads.

## Data Model

```sql
CREATE TABLE notifications (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type          VARCHAR(30)  NOT NULL,              -- COMMENT_REPLY | COMMENT_REMOVED
    group_key     VARCHAR(120) NOT NULL,              -- e.g. 'REPLY:<rootCommentId>', 'REMOVED:<commentId>'
    video_id      VARCHAR(100),
    comment_id    UUID,                               -- comment to deep-link to
    actor_display VARCHAR(100),                       -- e.g. 'bob' (never full email)
    actor_count   INT          NOT NULL DEFAULT 1,
    payload       JSONB        NOT NULL DEFAULT '{}', -- snippet (≤140 chars), reason code
    read_at       TIMESTAMPTZ,
    emailed_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id, read_at, updated_at DESC);
CREATE UNIQUE INDEX uq_notifications_unread_group
    ON notifications (user_id, group_key) WHERE read_at IS NULL;   -- enables collapsing via upsert
CREATE INDEX idx_notifications_digest ON notifications (emailed_at, read_at) WHERE emailed_at IS NULL;
```

Retention: a scheduled job deletes read notifications older than 90 days.

## API Contract

All endpoints require JWT; a user only ever sees their own rows.

```
GET   /v1/funny-app/notifications?page=&size=20      → ResultObjectInfo<Page<NotificationDto>>  (updated_at DESC)
GET   /v1/funny-app/notifications/unread-count       → ResultObjectInfo<{ count: number }>
GET   /v1/funny-app/notifications/stream              → text/event-stream   (Authorization header required; 401 otherwise)
        event: unread   data: {"unread": 3}        (on connect and on every change)
        : ping                                     (every 25 s)
PATCH /v1/funny-app/notifications/{id}/read          → 204   (404 if not owner — no existence leak)
PATCH /v1/funny-app/notifications/read-all           → 204
```

```json
// NotificationDto
{
  "id": "uuid",
  "type": "COMMENT_REPLY",
  "videoId": "22",
  "commentId": "uuid",
  "actorDisplay": "bob",
  "actorCount": 3,
  "snippet": "haha same here…",
  "reason": null,
  "read": false,
  "updatedAt": "2026-10-02T10:00:00Z"
}
```

FE renders text from `type` + fields (i18n stays in FE), e.g. *"bob và 2 người khác đã trả lời bình luận của bạn"*, *"Bình luận của bạn đã bị gỡ: SPAM"*.

## Flow

```
POST /videos/{id}/comments (reply)
  └─ VideoCommentServiceImpl.createComment  ──tx commit──►
       publish CommentRepliedEvent(videoId, rootId, parentId, newCommentId, actorEmail, snippet)
            └─ NotificationEventListener (@TransactionalEventListener AFTER_COMMIT, @Async)
                 recipients = {root.author, parent.author} − {actor} − guests
                 for r: upsert notifications ON CONFLICT (user_id, group_key) WHERE read_at IS NULL
                        DO UPDATE SET actor_count = actor_count + 1, actor_display = EXCLUDED.actor_display,
                                      payload = EXCLUDED.payload, updated_at = now(), emailed_at = NULL
                        notificationPublisher.unreadChanged(r)   → SSE "unread" to r's open tabs (if any)
mark read / read-all  → notificationPublisher.unreadChanged(currentUser)   (sync other tabs)

AdminCommentServiceImpl.moderate / bulkModerate (REMOVE)  ──► CommentRemovedEvent(commentIds, reason) ──► same listener

AppScheduler.notificationDigest (every 15 min, ShedLock-free: single replica; guard with DB row lock later)
  users with notify_email && unread && emailed_at IS NULL → 1 email each → set emailed_at
```

FE:
```
useNotificationStream(): fetch('/notifications/stream', { headers: { Authorization: jwt }, signal })
                         → read res.body (TextDecoderStream), split on blank line, parse `event:` / `data:`
                         → setQueryData(['notifications','unread']) + invalidate ['notifications','list']
                         → on end/error: backoff + jitter reconnect; on 401: stop until login
AppShell header: bell icon + badge  ← useUnreadCount() (fallback refetch 120 s / 30 s when stream down, on focus)
Bell click → dropdown (latest 10) ← useNotifications(page)
Item click → mark read → open video + CommentPanel scrolled to commentId
"Đánh dấu đã đọc tất cả"
```

## Security & Privacy

- Owner-only access enforced in the repository query (`WHERE user_id = :currentUserId`); `PATCH` on someone else's id → 404 (CWE-639).
- SSE stream: authenticated with the `Authorization` header (A′2) — **no credential ever appears in a URL**, so nothing sensitive reaches nginx/Cloudflare logs. The stream only carries the caller's own unread count.
- Rule (project-wide): never put JWTs or guest tokens in URLs (fixed for video streaming in `9e1fb36`; documented in `webapp/CLAUDE.md`).
- `actor_display` is the email local part / display name, **never the full email** (consistent with the pending public-email fix).
- Snippet truncated to 140 chars and rendered as text (no HTML) — XSS-safe in FE and email (plain-text email).
- Removed/deleted comments: notification shows reason only, never the removed content.
- Email digest uses existing `EmailCacheLimiter`; unsubscribe = Settings toggle (link in email footer).

## Operations (single VM)

Verified on the server (`nginx -T`, nginx 1.24, 2026-10-02). Chain: **Browser → Cloudflare (proxied) → nginx `*.canh-labs.com` → `localhost:8081`**.

Existing `location /api/` (proxies to `http://localhost:8081/`, strips `/api`) already has:

| Setting | Value | Effect on SSE |
|---------|-------|---------------|
| `proxy_buffering` | `off` | ✅ events flushed immediately — no extra location needed for buffering |
| `proxy_http_version` | `1.1` | ✅ keep-alive to upstream |
| `proxy_read_timeout` | default `60s` | ✅ OK **only because** the 25 s heartbeat resets it; never raise heartbeat above ~50 s |
| `gzip` | `on`, default `gzip_types` (text/html only) | ✅ `text/event-stream` not compressed (compression would buffer) |
| `access_log` | default format, full `$request` incl. query string | ✅ irrelevant for the stream: auth is in a header (A′2). Note: video stream URLs carried `?token=<jwt>` until `9e1fb36` — older log files still contain tokens (cleanup tracked separately) |
| Cloudflare | proxied; idle timeout ~100 s | ✅ heartbeat 25 s keeps the connection open; Cloudflare streams `text/event-stream` without buffering |

So SSE works through the current config **without changes**. Optional hardening — a dedicated location with a longer read timeout and a log format that drops query strings (defence in depth):

```nginx
# http {} : log format without query string
log_format no_query '$remote_addr - $remote_user [$time_local] "$request_method $uri $server_protocol" '
                    '$status $body_bytes_sent "$http_referer" "$http_user_agent"';

# server *.canh-labs.com {} : before "location /api/"
location /api/v1/funny-app/notifications/stream {
    proxy_pass            http://localhost:8081/v1/funny-app/notifications/stream;
    proxy_http_version    1.1;
    proxy_set_header      Connection "";
    proxy_set_header      Host $host;
    proxy_set_header      X-Real-IP $remote_addr;
    proxy_set_header      X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_buffering       off;
    proxy_read_timeout    1h;          # heartbeat still sent; this just avoids 60 s cut if a ping is late
    access_log            /var/log/nginx/access.log no_query;
}
```

The backend also sets `X-Accel-Buffering: no` and `Cache-Control: no-cache`. Spring: `server.tomcat.max-connections` default 8192 is enough for one VM; expose `notifications_sse_active_connections` on `/actuator/prometheus`.

## Scaling Path

| Concurrent online users | Setup |
|-------------------------|-------|
| < 5k (today) | One JVM, `InMemorySsePublisher`, polling fallback |
| 5k – 8k | Same, raise JVM heap / VM size; watch the SSE gauge |
| > 1 instance needed | `PgNotifyPublisher` (PostgreSQL `LISTEN/NOTIFY`, one dedicated non-pooled connection per instance, payload = userId) — no new infra |
| 50k+ or high event rate | `RedisPublisher` (revisit ADR-0003) or a dedicated push gateway |

**Email will hit limits first:** Gmail SMTP allows roughly 500–2,000 messages/day. Once the digest exceeds that, move to a transactional provider (SES / SendGrid / Mailgun) behind the existing `MailService` interface.

## Consequences

**Positive**
- Activates the existing `notify_email` setting; users learn about replies and moderation.
- Near-instant badge updates; idle users cost one idle connection instead of a request per minute.
- Zero new infrastructure; transport is behind `NotificationPublisher`, so moving to more instances is a new implementation, not a redesign.
- Event-driven producer keeps comment/admin code unaware of notifications.

**Negative / trade-offs**
- SSE adds moving parts: hand-written stream parsing and reconnect/backoff in the FE (A′2), heartbeat, reconnect after each deploy.
- In-memory registry is correct only while there is exactly one JVM; adding a second instance **requires** switching to `PgNotifyPublisher` (polling fallback keeps it correct-but-slow if forgotten).
- Up to 15 min email latency (digest).
- AFTER_COMMIT + `@Async` can drop a notification if the process crashes in between — accepted for v1.

**Follow-ups**
- `PgNotifyPublisher` when a second instance is introduced.
- v1.2: `video_sources.owner_user_id` to enable "your video got a comment/like".
- `@mention` notifications once display names exist.
- Outbox (B3) if guaranteed delivery becomes a requirement.
- Transactional email provider when the digest volume approaches Gmail limits.

## Implementation Outline (for /swarm-plan)

| ID | Task | Est. |
|----|------|------|
| NT-1 | Migration `notifications`, entity, repo (owner-scoped queries, native upsert) | 0.5 d |
| NT-2 | Events + `NotificationEventListener` (reply, removed incl. bulk), recipient resolution, grouping | 1 d |
| NT-3 | `NotificationController` (list, unread-count, read, read-all) + tests (owner isolation) | 0.5 d |
| NT-4 | SSE: `NotificationPublisher` + `InMemorySsePublisher`, `/notifications/stream` (header JWT via existing filter, heartbeat, per-user cap, close on JWT expiry), SSE gauge, tests (401 without header) | 1 d |
| NT-5 | Digest job + plain-text email template + retention job | 0.5 d |
| NT-6 | FE: `useNotificationStream` (`fetch` + `Authorization` header, stream parser, backoff + jitter reconnect, stop on 401, `AbortController`) + polling fallback, bell + badge + dropdown + deep link + tests (parser split across chunks, reconnect, 401) | 1.5 d |
| NT-7 | (optional) nginx stream location: `proxy_read_timeout 1h` + `no_query` log format (manual on VM, `nginx -t` then reload) | 0.25 d |
| NT-8 | E2E on prod (two browsers: reply → badge updates instantly; restart → reconnect); update `docs/tracking.md` | 0.25 d |

**Total ≈ 5.5 days.**
