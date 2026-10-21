# Sequence Diagrams: In-App Report

- **PRD:** [PRD-in-app-report](../prd/PRD-in-app-report.md)
- **ADR:** [0026-in-app-report](../adr/0026-in-app-report.md)
- **Spec:** [spec-in-app-report](../specs/spec-in-app-report.md) (plans: [plan-in-app-report](../plans/plan-in-app-report.md), [plan-report-webhook-state](../plans/plan-report-webhook-state.md), [plan-report-closed-notify](../plans/plan-report-closed-notify.md))
- **Status:** As-built (2026-10-21)

Notes where the code differs from the PRD:

- PRD "Out of scope" says there is no in-app notification when an issue is closed. That is superseded: `ReportNotificationListener` now creates a `REPORT_CLOSED` notification (ADR-0026 D7).
- The 429 for rate limits comes from `@RateLimited` (5 per 3600 s on create and retry, 60 per minute on list). The AOP layer is not drawn in detail.
- The "global cap of 10 autopilot reports per 24 h" is not a rejection. Over the cap the issue is still created, without the `autopilot` label, and the DTO reports `capReached = true`.
- Retry is allowed for the reporter or a SUPER_ADMIN only (403 otherwise), although the controller is open to ADMIN.

## 1. Submit a report and create the GitHub issue

An ADMIN or SUPER_ADMIN fills the form on the Report page. The service stores the row first as FAILED with error `pending`, then calls GitHub outside any transaction, then saves the outcome. A GitHub failure does not fail the HTTP call.

```mermaid
sequenceDiagram
    autonumber
    actor U as Admin
    participant FE as Browser (ReportPage.jsx, useCreateReport)
    participant API as AdminReportController
    participant S as ReportServiceImpl
    participant R as AppReportRepository
    participant GH as JdkGitHubIssueClient (api.github.com)
    participant DB as PostgreSQL

    U->>FE: submit form (type, title, description, optional autoLabel)
    FE->>API: POST /v1/funny-app/admin/reports (JWT, context page path appVersion userAgent screenWidth)
    API->>API: PreAuthorize hasRole ADMIN, RateLimited 5 per hour, AuditLog createReport
    API->>S: create(request)
    S->>S: currentUser, parseType, trimmed title max 120 and description max 5000,<br/>validateContext, parseAutoLabel
    alt validation fails
        S-->>API: CustomException 400 (error.report.invalidType, invalidTitle, invalidDescription, invalidContext, invalidAutoLabel)
        API-->>FE: 400 ResultErrorInfo
    else autoLabel sent by a non SUPER_ADMIN
        S-->>API: CustomException 403 error.report.autoLabelForbidden
        API-->>FE: 403
    else valid
        S->>R: save(AppReport status FAILED, error pending)
        R->>DB: INSERT app_report
        S->>S: callGitHub(row, role): isCapped counts ADMIN autopilot reports in 24 h
        S->>R: countByStatusAndAutopilotTrueAndReporterRoleAndCreatedAtAfter
        S->>GH: create(title, body, labels)
        GH->>GH: POST /repos/{owner}/{repo}/issues, 10 s timeout, no redirects
        GH-->>S: CreatedIssue(number, htmlUrl) on HTTP 201
        S->>S: status CREATED, issueNumber, issueUrl, labels, autopilot, error null
        S->>R: save(row)
        R->>DB: UPDATE app_report
        S-->>API: ReportDto (githubState OPEN, capReached when not autopilot)
        API-->>FE: 200 ResultObjectInfo<ReportDto>
        FE->>FE: invalidate ['admin','reports']
        FE-->>U: success alert with link to the issue
    end
```

## 2. GitHub failure and retry

When GitHub is down, slow or not configured, the row stays FAILED with a short error code and the response is still 200. The user can retry the same stored row.

```mermaid
sequenceDiagram
    autonumber
    actor U as Reporter or SUPER_ADMIN
    participant FE as Browser (ReportPage.jsx, useRetryReport)
    participant API as AdminReportController
    participant S as ReportServiceImpl
    participant R as AppReportRepository
    participant GH as JdkGitHubIssueClient

    Note over S,GH: create() reached callGitHub
    S->>GH: create(title, body, labels)
    alt not configured
        GH-->>S: GitHubException github.notConfigured
    else non 201 response
        GH-->>S: GitHubException github.http.STATUS
    else timeout
        GH-->>S: GitHubException github.timeout
    else IO error or bad JSON
        GH-->>S: GitHubException github.io
    end
    S->>S: markFailed(row, code): status FAILED, issueNumber null, issueUrl null, error code
    S->>R: save(row)
    S-->>API: ReportDto status FAILED, error code
    API-->>FE: 200 ResultObjectInfo<ReportDto>
    FE-->>U: error text from errorText(code) and a Retry button

    U->>FE: click Retry
    FE->>API: POST /v1/funny-app/admin/reports/{id}/retry (JWT)
    API->>API: RateLimited 5 per hour, AuditLog retryReport, parseId
    alt id is not a UUID or unknown
        API-->>FE: 404 error.report.notFound
    else caller is neither reporter nor SUPER_ADMIN
        API-->>FE: 403 error.report.retryForbidden
    else row already CREATED
        API-->>FE: 409 error.report.alreadyCreated
    else retryable
        API->>S: retry(id)
        S->>R: findWithReporterById(id)
        S->>GH: create(...) via callGitHub (label and cap follow the caller's current role)
        S-->>API: ReportDto CREATED or FAILED again
        API-->>FE: 200 ResultObjectInfo<ReportDto>
        FE->>FE: invalidate ['admin','reports']
    end
```

## 3. Recent list with GitHub state resolution

The list is fetched only while the Recent tab is open. For each CREATED row the state comes from the webhook-stored column first, then a 5 minute cache, then a live GitHub call, run in parallel for the missing rows. FAILED rows get no state.

```mermaid
sequenceDiagram
    autonumber
    actor U as Admin
    participant FE as Browser (ReportPage.jsx, useReports)
    participant API as AdminReportController
    participant S as ReportServiceImpl
    participant R as AppReportRepository
    participant C as GitHubIssueStateCache (Guava, 5 min TTL)
    participant GH as JdkGitHubIssueClient

    U->>FE: open the Recent tab
    FE->>API: GET /v1/funny-app/admin/reports?page=0&size=20 (JWT)
    API->>API: PreAuthorize hasRole ADMIN, RateLimited 60 per minute
    API->>S: list(page, size)
    alt page below 0 or size outside 1..50
        S-->>API: CustomException 400 error.report.invalidPage
        API-->>FE: 400
    else valid
        S->>R: findAllByOrderByCreatedAtDesc(PageRequest) with reporter entity graph
        S->>S: resolveStates(rows)
        loop each row
            alt status is not CREATED
                S->>S: state null
            else githubState stored by webhook
                S->>S: use stored OPEN or CLOSED
            else issueNumber is null
                S->>S: UNKNOWN
            else
                S->>C: get(issueNumber)
                alt cache hit
                    C-->>S: state
                else miss
                    S->>S: queue state lookup
                end
            end
        end
        par missing rows in parallel (TaskUtils.runAllIgnoreError)
            S->>GH: state(number)
            GH->>GH: GET /repos/{owner}/{repo}/issues/{n}
            GH-->>S: OPEN, CLOSED or UNKNOWN on any failure
        end
        S->>C: put(number, state) (UNKNOWN is never cached)
        S-->>API: Page<ReportDto> with githubState and githubStateReason
        API-->>FE: 200 ResultObjectInfo<Page<ReportDto>>
        FE-->>U: table with StateBadge, failed rows show a failed badge and Retry
    end
```

## 4. GitHub webhook receives an issue state change

GitHub posts repo webhooks to a public path with no JWT. The HMAC signature is the authentication. `/webhooks/github` POST is whitelisted in `AppConstant`. Check order in `GitHubWebhookController.receive`: 503, 413, 401, 400, 204.

```mermaid
sequenceDiagram
    autonumber
    participant GH as GitHub (repo webhook)
    participant API as GitHubWebhookController
    participant WS as GitHubWebhookServiceImpl
    participant R as AppReportRepository
    participant EP as ApplicationEventPublisher
    participant L as ReportNotificationListener

    GH->>API: POST /v1/funny-app/webhooks/github<br/>X-GitHub-Event, X-GitHub-Delivery, X-Hub-Signature-256
    API->>WS: isConfigured()
    alt webhook secret not configured
        API-->>GH: 503 (body not read)
    else configured
        API->>API: read at most 1 MiB plus 1 byte
        alt body larger than 1 MiB
            API-->>GH: 413
        else within limit
            API->>WS: verify(signature, body)
            WS->>WS: HMAC SHA-256 of body, MessageDigest.isEqual (constant time)
            alt signature missing, wrong prefix or mismatch
                API-->>GH: 401 (fixed log text only)
            else signature ok
                API->>WS: handle(event, deliveryId, body)
                WS->>WS: parse JSON object
                alt body not JSON or not an object
                    WS-->>API: IllegalArgumentException
                    API-->>GH: 400
                else event is not issues
                    WS-->>API: IGNORED
                    API-->>GH: 204
                else issues event
                    WS->>WS: require issue.number >= 1 and valid updated_at (else IllegalArgumentException, 400)
                    alt action not closed or reopened, state not open or closed, or repo is not ours
                        WS-->>API: IGNORED
                        API-->>GH: 204
                    else relevant
                        WS->>R: applyGithubState(number, state, reason, updated_at)
                        Note over R: UPDATE app_report SET github_state, github_state_reason, github_state_at<br/>WHERE status CREATED AND issue_number = n<br/>AND (github_state_at IS NULL OR github_state_at < updated_at)
                        R-->>WS: rows changed
                        alt changed more than 0
                            Note over WS: outcome APPLIED
                            opt new state is CLOSED
                                WS->>EP: publishEvent(ReportClosedEvent(number, reason))
                                EP-)L: onClosed (see flow 5)
                            end
                        else no row changed
                            WS->>R: existsByStatusAndIssueNumber(CREATED, number)
                            Note over WS: true means STALE (older event), false means UNKNOWN_ISSUE
                        end
                        WS-->>API: Outcome (logged with sanitized delivery id)
                        API-->>GH: 204
                    end
                end
            end
        end
    end
```

## 5. ReportClosedEvent to REPORT_CLOSED notification

The webhook stores state without an outer transaction, so the listener uses `fallbackExecution = true` and runs on the async executor. It never lets an exception escape. The admin reporter then sees the item under the ADMIN tab of the bell dropdown.

```mermaid
sequenceDiagram
    autonumber
    participant L as ReportNotificationListener (async)
    participant R as AppReportRepository
    participant NR as NotificationRepository
    participant P as InMemorySsePublisher
    participant FE as Reporter browser (useNotificationStream)
    actor U as Reporter (admin)

    Note over L: @Async @TransactionalEventListener AFTER_COMMIT, fallbackExecution true
    L->>R: findFirstWithReporterByStatusAndIssueNumber(CREATED, issueNumber)
    alt no report for that issue
        L->>L: nothing to do
    else report found
        L->>L: notifyReporter(report, reason)
        alt reporter null or DEACTIVATED
            L->>L: skip
        else active reporter
            L->>NR: upsertGrouped(userId, REPORT_CLOSED, groupKey report:reportId:REPORT_CLOSED,<br/>null, null, null, payload snippet=title and optional reason)
            alt insert fails
                L->>L: log error, return without push
            else stored
                L->>P: unreadChanged(userId)
                P-)FE: SSE event unread data {"unread":N}
            end
        end
    end
    FE->>FE: setQueryData(UNREAD_KEY), invalidate LIST_KEY
    U->>FE: open bell, ADMIN tab
    FE->>FE: GET /v1/funny-app/notifications?category=ADMIN (ADMIN_TYPES contains REPORT_CLOSED)
    U->>FE: click the item
    FE->>FE: PATCH /notifications/{id}/read, then open the Report page (nav key report, admin only)
    Note over L: Failures are only logged. The same notification also appears in the<br/>email digest via NotificationDigestService.describeReportClosed (reason label from mail.digest.reportReason.*).
```
