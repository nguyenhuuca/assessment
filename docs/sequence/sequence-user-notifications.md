# Sequence Diagrams: User Notifications

- **PRD:** [PRD-user-notifications](../prd/PRD-user-notifications.md)
- **ADR:** [0017-user-notifications](../adr/0017-user-notifications.md)
- **Spec:** — (plan: [plan-user-notifications](../plans/plan-user-notifications.md))
- **Status:** As-built (2026-10-21)

Notes where the code differs from the PRD:

- The SSE event is named `unread` with data `{"unread":N}` (the server-side publisher method is `NotificationPublisher.unreadChanged(userId)`).
- The PRD predates `REPORT_CLOSED` and the `category` filter. `NotificationType.ADMIN_TYPES` is `{REPORT_CLOSED}`. `GET /notifications?category=GENERAL|ADMIN` is accepted from any logged-in user, because the controller and service do no admin check. Only the React dropdown limits the two tabs to admins (`user.isAdmin`).
- `NotificationType` has six values, not only comment replies: `COMMENT_REPLY` and `COMMENT_REMOVED` (`NotificationEventListener`), `SUGGESTION_APPROVED`, `SUGGESTION_REJECTED` and `SUGGESTION_LIVE` (`SuggestionNotificationListener`), and `REPORT_CLOSED` (`ReportNotificationListener`, driven by `ReportClosedEvent`). They all use the same upsert, SSE push, list and digest path shown below. Diagram 1 draws only the comment-reply producer.
- The email digest formats each type per item: `describeSuggestion` for the three `SUGGESTION_*` types, `describeReportClosed` for `REPORT_CLOSED` (reason label from `mail.digest.reportReason.*`), a "removed" line (with optional reason) for `COMMENT_REMOVED`, and the reply line (actor, "and N others", snippet) for `COMMENT_REPLY`.
- Emitters are held in memory per JVM (`InMemorySsePublisher`), at most 5 per user, 30 minute timeout.

## 1. Event to notification row to SSE unread push

A comment reply is committed, an async listener upserts one grouped notification row per recipient, then pushes the new unread count to the recipient's open tabs. `CommentRemovedEvent` follows the same path through `onCommentRemoved`. `ReportClosedEvent` uses `ReportNotificationListener` (see the in-app-report diagrams).

```mermaid
sequenceDiagram
    autonumber
    actor A as Replying user
    participant API as CommentController
    participant CS as VideoCommentServiceImpl
    participant DB as PostgreSQL
    participant L as NotificationEventListener (async)
    participant NR as NotificationRepository
    participant P as InMemorySsePublisher
    participant FE as Recipient browser (useNotificationStream)

    A->>API: POST /v1/funny-app/videos/{videoId}/comments
    API->>CS: createComment(videoId, req) @Transactional
    CS->>DB: INSERT video_comment
    CS->>CS: publishEvent(CommentRepliedEvent)
    CS-->>API: CreateCommentResponse
    API-->>A: 200 ResultObjectInfo
    Note over CS,L: AFTER_COMMIT, @Async virtual thread
    CS-)L: onCommentReplied(event)
    L->>L: replyRecipients: root author + parent author<br/>minus actor, guests, duplicates
    loop each recipient email
        L->>L: deliver(email, COMMENT_REPLY, groupKey REPLY:rootId, ...)
        L->>DB: userRepo.findAllByUserName(email)
        alt user missing or DEACTIVATED
            L->>L: skip
        else active user
            L->>NR: upsertGrouped(userId, type, groupKey, videoId, commentId, actorDisplay, payload)
            NR->>DB: INSERT ... ON CONFLICT (user_id, group_key) WHERE read_at IS NULL<br/>DO UPDATE actor_count + 1, updated_at now, emailed_at NULL
            L->>P: unreadChanged(userId)
            P->>NR: countByUserIdAndReadAtIsNull(userId)
            loop each open emitter of the user
                P-)FE: SSE event unread data {"unread":N}
            end
            FE->>FE: setQueryData(UNREAD_KEY, count N)<br/>invalidateQueries(LIST_KEY)
        end
    end
    Note over L: any exception is logged, never rethrown
```

## 2. SSE stream connect, heartbeat and reconnect

The logged-in browser opens one `fetch` based stream (JWT in the `Authorization` header, never in the URL). The hook is mounted once in `AppShell`.

```mermaid
sequenceDiagram
    autonumber
    participant FE as Browser (useNotificationStream in AppShell)
    participant F as JWT filter
    participant API as NotificationController
    participant P as InMemorySsePublisher
    participant NR as NotificationRepository

    FE->>API: GET /v1/funny-app/notifications/stream (Authorization header)
    API->>F: authenticate
    alt no or invalid JWT
        F-->>FE: 401
        FE->>FE: setUp(false), stop reconnecting for good
    else valid JWT
        API->>API: AppUtils.getCurrentUser()
        API->>P: register(userId)
        P->>P: add emitter, evict oldest when more than 5 per user
        P->>NR: countByUserIdAndReadAtIsNull(userId)
        P-)FE: SSE event unread data {"unread":N}
        API-->>FE: 200 text/event-stream, Cache-Control no-cache, X-Accel-Buffering no
        FE->>FE: setUp(true), polling slows to 120 s
    end
    loop every 25 s (HEARTBEAT_MS)
        P-)FE: SSE comment ping
        Note over P: a failed send removes and completes the emitter
    end
    Note over FE: stream ends or errors
    FE->>FE: setUp(false), polling 30 s,<br/>wait backoffDelay(attempt) 1 s to 30 s plus 1 to 5 s jitter
    FE->>API: GET /notifications/stream (retry)
    Note over FE,P: Fallback while the stream is down: GET /notifications/unread-count<br/>every 30 s and on window focus
```

## 3. Bell dropdown list with optional category filter

The bell shows the badge from `GET /notifications/unread-count`. Opening the dropdown loads page 0 (size 20, shows the first 10). Admins get two tabs, GENERAL and ADMIN, each its own query.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (NotificationBell, NotificationDropdown)
    participant API as NotificationController
    participant S as NotificationServiceImpl
    participant R as NotificationRepository
    participant DB as PostgreSQL

    FE->>API: GET /v1/funny-app/notifications/unread-count (JWT)
    API->>S: unreadCount()
    S->>R: countByUserIdAndReadAtIsNull(userId)
    R->>DB: SELECT count
    API-->>FE: 200 ResultObjectInfo<UnreadCountDto>
    FE-->>U: badge number

    U->>FE: open the bell dropdown
    alt non admin user
        FE->>API: GET /notifications?page=0&size=20 (no category)
    else admin user
        FE->>API: GET /notifications?page=0&size=20&category=GENERAL
        FE->>API: GET /notifications?page=0&size=20&category=ADMIN
    end
    API->>API: page max 0, size clamped to 1..50
    API->>S: list(category, pageable)
    S->>S: currentUserId() (401 4011 if no user)
    alt category null
        S->>R: findByUserIdOrderByUpdatedAtDesc
    else category ADMIN
        S->>R: findByUserIdAndTypeInOrderByUpdatedAtDesc(ADMIN_TYPES)
    else category GENERAL
        S->>R: findByUserIdAndTypeNotInOrderByUpdatedAtDesc(ADMIN_TYPES)
    end
    R->>DB: SELECT ... ORDER BY updated_at DESC
    S-->>API: Page<NotificationDto>
    API-->>FE: 200 ResultObjectInfo<Page<NotificationDto>>
    FE-->>U: list, GENERAL tab by default, red dot on a tab with unread rows

    opt invalid category value
        API-->>FE: 400 (enum conversion error)
    end
```

## 4. Mark one read and mark all read

Both calls are optimistic in the browser. The service commits the update first (the service methods are deliberately not transactional), then asks the publisher to push the fresh count to every open tab.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (useMarkRead, useMarkAllRead)
    participant API as NotificationController
    participant S as NotificationServiceImpl
    participant R as NotificationRepository
    participant P as InMemorySsePublisher
    participant T as Other tabs of the user

    U->>FE: click an unread item
    FE->>FE: onMutate: UNREAD_KEY count minus 1
    FE->>API: PATCH /v1/funny-app/notifications/{id}/read (JWT)
    API->>S: markRead(id)
    S->>R: findByIdAndUserId(id, userId)
    alt not found or owned by someone else
        S-->>API: CustomException 404 subcode 4041
        API-->>FE: 404
        FE->>FE: onError: restore previous count
    else own row
        S->>R: markRead(id, userId, now) (UPDATE where read_at IS NULL)
        S->>P: unreadChanged(userId)
        P-)T: SSE event unread data {"unread":N}
        API-->>FE: 204
    end
    FE->>FE: onSettled: invalidate UNREAD_KEY and LIST_KEY

    U->>FE: click Mark all read
    FE->>FE: onMutate: UNREAD_KEY count 0
    FE->>API: PATCH /v1/funny-app/notifications/read-all (JWT)
    API->>S: markAllRead()
    S->>R: markAllRead(userId, now)
    S->>P: unreadChanged(userId)
    P-)T: SSE event unread data {"unread":0}
    API-->>FE: 204
    FE->>FE: onSettled: invalidate UNREAD_KEY and LIST_KEY
```

## 5. Email digest and retention scheduled jobs

`DynamicJobScheduler` reads the `scheduled_job` table at startup (`ApplicationReadyEvent`) and runs `JobRegistry` tasks by cron. Seeded crons from the Liquibase changelog `202610190002-create-scheduled-job.sql`: `notification-digest` is `0 */15 * * * *`, `notification-retention` is `0 30 3 * * *` in zone Asia/Ho_Chi_Minh. The PRD says "every 15 min", which matches.

```mermaid
sequenceDiagram
    autonumber
    participant D as DynamicJobScheduler
    participant J as AppScheduler.notificationDigest
    participant DS as NotificationDigestService
    participant NR as NotificationRepository
    participant UR as UserRepo
    participant EL as EmailCacheLimiter
    participant M as MailService (Gmail SMTP)

    D->>D: cron fires, execute(name, task) with a per-job running guard
    D->>J: run notificationDigest()
    J->>DS: sendDigests()
    DS->>NR: findUserIdsForDigest()
    Note over NR: users with notify_email = true<br/>and unread, not yet emailed rows
    loop each userId
        DS->>EL: getDailyCount(today)
        alt count x 100 >= maxDailyEmails x 50
            DS->>DS: log warn, stop the run (budget protects magic links)
        else budget left
            DS->>UR: findAllById(userId)
            alt missing, DEACTIVATED or blank email
                DS->>DS: skip user
            else active
                DS->>NR: findByUserIdAndReadAtIsNullAndEmailedAtIsNull...OrderByUpdatedAtDesc
                DS->>DS: build subject and body (max 5 items, then "and N more"), locale via EmailLocaleResolver,<br/>each item via describe(): reply, removed, describeSuggestion or describeReportClosed
                DS->>M: sendSimpleMail(userName, subject, body)
                DS->>EL: incrementDailyCount(today)
                DS->>NR: markEmailed(ids, now)
            end
        end
    end
    DS-->>J: number of emails sent
    J->>J: log "Notification digest sent N email(s)"
    Note over DS: A failure for one user is logged, emailed_at stays NULL,<br/>so the next run retries. A later upsertGrouped on the same group<br/>resets emailed_at to NULL.

    D->>D: nightly cron for notification-retention
    D->>DS: AppScheduler.notificationRetention() then purgeOldRead(now)
    DS->>NR: deleteReadOlderThan(now minus 90 days)
    NR-->>DS: deleted rows
```
