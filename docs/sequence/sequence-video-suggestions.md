# Sequence Diagrams: Video Suggestions

<!--
One file per feature that has a PRD: docs/sequence/sequence-<feature>.md (slug = PRD slug).
- Mermaid `sequenceDiagram` only (rendered by mkdocs via pymdownx.superfences); no PNG/PlantUML.
- Draw what the code does (or will do, per ADR/spec). Cite the class/method in each participant or note.
- One diagram per main flow: the happy path first, then the important alternative or failure paths
  (auth failure, validation error, async/event, scheduled job). 2-6 diagrams is typical.
- Participants: Browser (React page/hook) → Controller → Service → Repository/DB, plus external
  systems (YouTube, GitHub, SMTP, SSE) and async listeners/jobs. Use `autonumber`.
- Keep in sync: when the as-built flow changes, update the diagram in the same commit.
-->

- **PRD:** [PRD-video-suggestions](../prd/PRD-video-suggestions.md)
- **ADR:** — (no ADR of its own. The PRD builds on [0019-admin-video-import](../adr/0019-admin-video-import.md) and [0017-user-notifications](../adr/0017-user-notifications.md))
- **Spec:** — (plan: [plan-video-suggestions](../plans/plan-video-suggestions.md). Later changes: [plan-suggestion-transitions](../plans/plan-suggestion-transitions.md), [plan-suggestion-rereview](../plans/plan-suggestion-rereview.md))
- **Status:** As-built (2026-10-21)

Notes (code vs PRD):

- Base path for every endpoint is `/v1/funny-app`. In the diagrams `:id` stands for the path variable `{id}`. Suggestion endpoints are not in any public path list, so the JWT filter answers 401 for guests.
- The PRD describes submit, duplicate (409) and daily limit (429, default 5 per 24 hours, `app.video-suggestion.daily-limit`). The code matches. The code also has features the PRD does not draw: `POST /suggestions/{id}/rereview` (max 2 per suggestion), `POST /admin/video-suggestions/{id}/reopen`, and a `LIVE` filter.
- The quota is checked before the URL is resolved, so an over-quota user never triggers a network call to resolve a share link.
- Approve reuses the import pipeline: it creates one `video_import_jobs` row through `VideoImportService.createResolved`. A video becomes live only after the Drive sync ingests the uploaded file (see the admin video import diagrams).

## 1. User submits a suggestion

A logged-in user taps "+" in `AppShell.jsx`, which opens `SuggestVideoModal.jsx` (admins go straight to the Import tab instead). The modal validates the URL locally, then posts to `VideoSuggestionController.submit`. The new row starts as `PENDING`.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant FE as Browser (SuggestVideoModal.jsx, useSubmitSuggestion)
    participant API as VideoSuggestionController
    participant S as VideoSuggestionServiceImpl
    participant R as ImportUrlResolver
    participant REPO as VideoSuggestionRepository
    participant JR as VideoImportJobRepository
    participant DB as PostgreSQL

    U->>FE: paste URL, optional title and note, submit
    FE->>FE: validateSuggestionUrl(url) (shows a local error and stops when invalid)
    FE->>API: POST /v1/funny-app/suggestions (JWT, RateLimited 10)
    alt no JWT (guest)
        API-->>FE: 401 ResultErrorInfo
    else authenticated
        API->>API: currentUserId() (401 error.auth.required if none)
        API->>S: submit(userId, url, title, note)
        S->>REPO: countByUserIdAndCreatedAtAfter(userId, now minus 24h)
        alt count is at or above dailyLimit
            S-->>API: CustomException 429 error.suggestion.dailyLimit
        else under the limit
            S->>R: resolve(url)
            alt not a supported YouTube or Facebook URL
                S-->>API: CustomException 400 error.suggestion.invalidUrl
            else resolved
                S->>REPO: existsByNormalizedUrlAndStatus(url, PENDING)
                S->>JR: existsByNormalizedUrlAndStatusIn(url, ACTIVE plus DONE)
                alt pending suggestion or active or done import covers the URL
                    S-->>API: CustomException 409 error.suggestion.duplicate
                else free
                    S->>REPO: saveAndFlush(VideoSuggestion status PENDING)
                    REPO->>DB: INSERT video_suggestions
                    Note over S,DB: a lost race on the partial unique index on pending normalized_url is also mapped to 409 duplicate
                    S-->>API: SuggestionDto
                    API-->>FE: 201 ResultObjectInfo of SuggestionDto
                    FE->>FE: invalidate the suggestions mine and mine-infinite queries
                    FE-->>U: "submitted" message, link to the History page
                end
            end
        end
    end
    API-->>FE: 4xx ResultErrorInfo with code
    FE-->>U: errorKeyFor(err.code) text, else err.message
```

## 2. Admin reviews: approve creates an import job

The Admin tab (`AdminSuggestionTable.jsx`, `useAdminSuggestions.js`) lists suggestions with pending first and polls `pending-count` every 60 s for the badge. Approve runs in one transaction under a row lock on the suggestion, creates exactly one import job scheduled now, and publishes `SuggestionReviewedEvent`. After commit the worker is woken again and the suggester is notified.

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    participant FE as Browser (AdminSuggestionTable.jsx, useAdminSuggestions.js)
    participant API as AdminVideoSuggestionController
    participant S as VideoSuggestionServiceImpl
    participant REPO as VideoSuggestionRepository
    participant IMP as VideoImportServiceImpl
    participant W as VideoImportWorker
    participant EV as SuggestionNotificationListener
    participant DB as PostgreSQL

    loop every 60 s
        FE->>API: GET /v1/funny-app/admin/video-suggestions/pending-count
        API-->>FE: 200 ResultObjectInfo of Long (badge)
    end
    FE->>API: GET /v1/funny-app/admin/video-suggestions?status&page&size
    API->>S: list(filter, page, size)
    S->>REPO: findAllPendingFirst or findAllLiveNewest or findAllByStatusNewest
    S-->>FE: 200 Page of SuggestionDto (suggesterName, importJobId, canReopen, live)
    A->>FE: click Approve (optional title override)
    FE->>API: POST /v1/funny-app/admin/video-suggestions/:id/approve (ADMIN, AuditLog)
    API->>S: approve(id, title, adminId)
    alt app.video-import.enabled is false
        S-->>API: CustomException 503 error.import.disabled
    else enabled
        S->>REPO: findByIdForUpdate(id) inside TransactionTemplate
        alt id unknown
            S-->>API: CustomException 404 error.suggestion.notFound
        else status is not PENDING
            S-->>API: CustomException 409 error.suggestion.notPending
        else pending
            S->>IMP: createResolved(ImportUrl from stored sourceUrl, title, adminId)
            IMP->>DB: INSERT video_import_jobs (PENDING, scheduled now)
            alt import line rejected (DUPLICATE_ACTIVE, INVALID_URL)
                S-->>API: CustomException 409 error.import.duplicateActive or error.import.invalidUrl
            else job created
                S->>REPO: save(status APPROVED, importJobId, reviewedBy, reviewedAt)
                S--)EV: publish SuggestionReviewedEvent(approved true)
                S-->>API: SuggestionDto (after commit)
                S->>W: wakeUp() (job is visible now)
                API-->>FE: 200 ResultObjectInfo of SuggestionDto
                FE->>FE: invalidate the admin suggestions and admin imports queries
            end
        end
    end
    EV->>EV: onReviewed() AFTER_COMMIT on async executor
    EV->>DB: notificationRepository.upsertGrouped(SUGGESTION_APPROVED, group suggestion:id:type)
    EV->>EV: publisher.unreadChanged(userId) (SSE hint)
```

## 3. Import finishes and the suggester is told the video is live

The import job downloads the video and uploads it to the Drive folder. The Drive sync (cron job `sync-drive-videos` or the admin force sync) later saves the `video_sources` row and publishes `VideoIngestedEvent`. The listener links the Drive file id back to the job, then to the approved suggestion, and notifies the suggester.

```mermaid
sequenceDiagram
    autonumber
    participant W as VideoImportWorker
    participant GD as Google Drive folder
    participant J as Drive sync (AppScheduler.syncDriveVideos)
    participant VS as VideoStorageServiceImpl
    participant DB as PostgreSQL
    participant EV as SuggestionNotificationListener
    participant SSE as InMemorySsePublisher
    participant FE as Browser (useNotifications.js, NotificationBell)
    actor U as Suggester

    W->>GD: upload title.mp4 (job DONE, driveFileId stored)
    J->>GD: list new files and download the new one
    J->>VS: saveInfo(fileId, title, thumbnailUrl)
    VS->>DB: INSERT video_sources (source_type google_drive, source_id = fileId)
    VS--)EV: publish VideoIngestedEvent(driveFileId, videoSourceId)
    Note over VS,EV: publish failures are only logged. fallbackExecution is on because the cron runs without a transaction
    EV->>DB: jobRepository.findFirstByDriveFileId(driveFileId)
    EV->>DB: suggestionRepository.findFirstByImportJobId(job.id)
    alt no linked suggestion or status is not APPROVED
        EV->>EV: nothing to do
    else approved suggestion found
        EV->>DB: userRepo.findById(userId)
        alt user missing or DEACTIVATED
            EV->>EV: skip
        else active user
            EV->>DB: notificationRepository.upsertGrouped(SUGGESTION_LIVE, videoId = videoSourceId)
            EV->>SSE: unreadChanged(userId)
            SSE--)FE: SSE event unread (GET /v1/funny-app/notifications/stream)
            FE->>FE: update unread badge
            FE-->>U: notification "your suggestion is live"
        end
    end
    Note over FE: the History tab shows the item as LIVE once findLiveRefs finds the video_sources row
```

## 4. Reject, re-review request and reopen

An admin rejects a pending suggestion with a reason (note is required for `OTHER`). The suggester can ask for a re-review at most `MAX_REREVIEWS` (2) times, which moves the suggestion back to pending. An admin can also reopen a rejected suggestion, or an approved one whose import ended `FAILED` or `CANCELLED`. All transitions are decided by `SuggestionTransition` and run under a row lock.

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    actor U as Suggester
    participant FE as Browser (AdminSuggestionTable, SuggestionCard.jsx)
    participant AAPI as AdminVideoSuggestionController
    participant UAPI as VideoSuggestionController
    participant S as VideoSuggestionServiceImpl
    participant T as SuggestionTransition
    participant REPO as VideoSuggestionRepository
    participant EV as SuggestionNotificationListener

    A->>FE: choose reject reason and optional note
    FE->>AAPI: POST /v1/funny-app/admin/video-suggestions/:id/reject (reason, note)
    AAPI->>S: reject(id, reason, note, adminId)
    alt reason OTHER without a note
        S-->>AAPI: CustomException 400 error.suggestion.noteRequired
    else valid
        S->>REPO: findByIdForUpdate(id)
        S->>T: REJECT.check(status, null, rereviewCount)
        alt not PENDING
            S-->>AAPI: CustomException 409 error.suggestion.notPending
        else pending
            S->>REPO: save(status REJECTED, rejectReason, rejectNote, reviewedBy)
            S--)EV: publish SuggestionReviewedEvent(approved false, reason)
            EV->>EV: onReviewed() creates SUGGESTION_REJECTED notification
            S-->>AAPI: SuggestionDto
            AAPI-->>FE: 200 ResultObjectInfo of SuggestionDto
        end
    end

    U->>FE: click "Ask for re-review" (optional message up to 300 chars)
    FE->>UAPI: POST /v1/funny-app/suggestions/:id/rereview (RateLimited 10)
    UAPI->>S: requestRereview(id, userId, message)
    S->>REPO: findByIdForUpdate(id) and owner check
    alt not found or not the owner
        S-->>UAPI: CustomException 404 error.suggestion.notFound
    else owner
        S->>T: REREVIEW.check(status, null, rereviewCount)
        alt status is not REJECTED
            S-->>UAPI: CustomException 409 error.suggestion.notRejected
        else rereviewCount is 2 or more
            S-->>UAPI: CustomException 409 error.suggestion.rereviewLimit
        else allowed
            S->>REPO: existsByNormalizedUrlAndStatus(url, PENDING) and import job check
            alt URL is covered again
                S-->>UAPI: CustomException 409 error.suggestion.duplicate
            else free
                S->>REPO: requestRereview(id, userId, message, MAX_REREVIEWS, now) (guarded UPDATE back to PENDING)
                S-->>UAPI: SuggestionDto
                UAPI-->>FE: 200 ResultObjectInfo of SuggestionDto
                FE->>FE: invalidate the suggestions queries
            end
        end
    end

    A->>FE: click "Reopen"
    FE->>AAPI: POST /v1/funny-app/admin/video-suggestions/:id/reopen
    AAPI->>S: reopen(id, adminId)
    S->>REPO: findByIdForUpdate(id) and lock the linked import job row when APPROVED
    S->>T: REOPEN.check(status, jobStatus, rereviewCount)
    alt status is PENDING
        S-->>AAPI: CustomException 409 error.suggestion.alreadyPending
    else APPROVED and job is not FAILED or CANCELLED
        S-->>AAPI: CustomException 409 error.suggestion.cannotReopen
    else allowed
        S->>S: requireNoDuplicate(url)
        S->>REPO: save(status PENDING, review fields and importJobId cleared)
        S-->>AAPI: SuggestionDto
        AAPI-->>FE: 200 ResultObjectInfo of SuggestionDto
    end
```
