# Plan: Notify the Reporter When the Report's Issue Is Closed

**Status:** Done, deployed 2026-10-21 (RN-1..RN-3, `801dcb9f`, `b3030e52`); owner check of the bell pending · **ADR:** none (follows ADR-0017 notifications and ADR-0026 D7 webhook) · **Parent:** [plan-report-webhook-state](plan-report-webhook-state.md)

> Started as an autopilot lite run; promoted to full mode when the diff passed ~100 LOC (tests excluded).
> Implementation note: `ReportClosedEvent` carries `(issueNumber, reason)` instead of the report id; the listener looks the report up by issue number, so the webhook needs no extra query.

Request: "notify user tao report khi issue duoc close". Lite: ~90 LOC non-test, follows the existing
notification pattern (ADR-0017 user notifications; SuggestionNotificationListener), no ADR.

## Behaviour
- When the GitHub webhook APPLIES a `closed` state to a CREATED report (ADR-0026 D7), the report's reporter gets
  an in-app notification `REPORT_CLOSED` (bell + SSE unread count), and it is included in the email digest like
  other notifications.
- Payload: `snippet` = report title, `reason` = stored state_reason (completed / not_planned / duplicate, or absent).
- Not sent for: `reopened`; STALE or UNKNOWN_ISSUE outcomes (so a redelivery never notifies twice); reporter deleted
  (reporter_id null) or DEACTIVATED.
- Group key `report:<reportId>:REPORT_CLOSED`: closing again while the first is unread collapses into one row
  (existing `upsertGrouped`, `repo/NotificationRepository.java:35-56`).
- Click on the notification opens the Report page (nav key `report`).
- Failures never affect the webhook response: listener is async and catches everything (same as
  `service/notification/SuggestionNotificationListener.java:61-69`).

## Tasks

| ID | Task | ~LOC | Acceptance criteria |
|----|------|------|---------------------|
| RN-1 | `NotificationType.REPORT_CLOSED` (`enums/NotificationType.java:3-9`; column is `VARCHAR(30)` with no CHECK, `db/changelog/sql/202610030001-create-notifications.sql:4`). New record `event/ReportClosedEvent(int issueNumber, String reason)` (reason captured at publish time, so a quick reopen cannot change it). `GitHubWebhookServiceImpl.handle` (`service/impl/GitHubWebhookServiceImpl.java:60-82`) publishes it via `ApplicationEventPublisher` only when outcome = APPLIED and state = CLOSED; the listener finds the report with the new repo method `Optional<AppReport> findFirstWithReporterByStatusAndIssueNumber(...)` with `@EntityGraph("reporter")` (like `repo/AppReportRepository.java:26-28`). New `service/notification/ReportNotificationListener` with `@Async @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` (the webhook has no outer transaction), which loads the row, skips null/DEACTIVATED reporter, calls `upsertGrouped(userId, "REPORT_CLOSED", "report:<id>:REPORT_CLOSED", null, null, null, {"snippet": title, "reason": reason?})` and `publisher.unreadChanged(userId)` | ~55 | Unit tests: webhook publishes the event on APPLIED+CLOSED only (not on reopened, STALE, UNKNOWN_ISSUE, IGNORED); listener upserts with the right type, group key and payload and pings SSE; skips a deleted or deactivated reporter; never throws (repo exception swallowed and logged). Single-constructor beans (BeanConstructorTest passes) |
| RN-2 | Digest text: `NotificationDigestService.describe` (`service/notification/NotificationDigestService.java:126-143`) handles `REPORT_CLOSED` → `mail.digest.item.reportClosed` / `reportClosedReason` in `i18n/messages.properties` and `messages_vi.properties` (next to `:101-102`) | ~12 | `NotificationDigestServiceTest`: a REPORT_CLOSED row renders "Your report "T" was closed" and with reason "(completed)" in en and vi |
| RN-3 | FE: `utils/notificationText.js:5-22` returns `notifications:reportClosed` with title and a translated reason (`report:stateReason.*` from the D7 work, fallback raw value); new helper `notificationNav(n, isAdmin)` in `utils/notificationText.js` returns `'history'` for suggestion approved/rejected (moved from `AppShell.jsx:102-104`), `'report'` for `REPORT_CLOSED` when the user is admin, else `null` (a demoted reporter: the `report` nav is admin-only, `AppShell.jsx:158,379`); `openNotification` (`:101-117`) uses it; keys in `{en,vi}/notifications.json` (next to `:14`) | ~20 | `notificationText.test.js`: text with and without reason, unknown reason falls back; i18n parity test passes; `notificationNav` unit tests: REPORT_CLOSED → `report` for admin, `null` for non-admin; suggestion types → `history`; others → `null`; `npm run test` + `npm run lint` pass |

## Not in scope
- Notifying on reopen, on comments or on PR merge.
- Email right away (the digest job sends it, as for every other notification).
- Notifying anyone other than the reporter.
