# Autopilot run: Notify the reporter when the report's issue is closed

- **Request:** notify user tao report khi issue duoc close
- **Mode:** local (started lite, promoted to full when the diff passed ~100 LOC)
- **Plan:** [plan-report-closed-notify](../plans/plan-report-closed-notify.md)
- **Started:** 2026-10-21 10:43 · **Landed:** 2026-10-21 11:10
- **Result:** ✅ pushed `6d5fe722` (BE `801dcb9f`, FE `b3030e52`); CI green, smoke 22/22

## Pipeline

| Stage | Result | Rounds |
|-------|--------|--------|
| Start | owner request in chat (`/autopilot`) | — |
| Plan | written inline by the orchestrator; lite at first, plan file written at promotion | 1 of 3 |
| Plan review | approve, 0 blocking, 3 advisory (all folded in: reason in the event, admin-only click, testable nav helper) | 1 of 3 |
| Gate 1 (owner) | approved | — |
| Implement | orchestrator inline; 2 commits; non-test ≈145 LOC (listener, event, digest, FE text/nav, i18n) | 1 of 3 |
| Code review | approve, 0 blocking, 2 advisory (plan text fixed; re-close grouping → manual check) | 1 of 3 |
| Local QA ×2 | `qa-twice.sh`: 64 tests, pass 64 · flaky 0 · fail 0 | 1 of 3 |
| Fix | one test fix during implement (re-stubbing a throwing mock), split into two tests | — |
| Land: full gates | `mvn verify`: 1,560 tests, 0 fail, LINE 96.68 % (≥ 90 %); `npm run test`: 532 pass; `npm run lint`: 0 errors (3 old warnings) | — |
| Gate 2 (owner) | push | — |
| CI + smoke | Build and Deploy, Deploy Frontend, Publish Docs green; `mock-test.sh` 22/22; webhook still answers 401 to an unsigned POST | — |

## What changed

- `service/impl/GitHubWebhookServiceImpl.java`: on outcome APPLIED with state CLOSED, publishes `ReportClosedEvent(issueNumber, reason)`; reopen, STALE, UNKNOWN_ISSUE, IGNORED and redeliveries publish nothing.
- `service/notification/ReportNotificationListener.java`: `@Async @TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`; loads the report with its reporter (`AppReportRepository.findFirstWithReporterByStatusAndIssueNumber`, `@EntityGraph`), skips a deleted or deactivated reporter, upserts `REPORT_CLOSED` (group `report:<id>:REPORT_CLOSED`, payload title + reason) and pings SSE; never throws.
- `NotificationDigestService`: digest line "Your report "T" was closed (completed)" in en/vi, raw reason as fallback.
- FE `utils/notificationText.js`: notification text with translated reason; `notificationNav(n, isAdmin)` sends REPORT_CLOSED to the Report page for admins and does nothing otherwise; `AppShell.openNotification` uses it (other types unchanged).

## Runs

| # | cycle | stage | round | path | result | blocking | advisory | turns | min | tokens | model |
|---|-------|-------|-------|------|--------|----------|----------|-------|-----|--------|-------|
| 1 | 1 | plan | 1 | inline (orchestrator) | plan (lite) | — | — | — | 4 | — | opus |
| 2 | 1 | plan review | 1 | fallback (worker-reviewer) | approve | 0 | 3 | 10 | 1 | 43,061 | sonnet |
| 3 | 1 | gate 1 | — | — | approved | — | — | — | — | — | — |
| 4 | 1 | implement RN-1..RN-3 | 1 | inline (orchestrator) | tests green | — | — | — | 10 | — | opus |
| 5 | 1 | code review | 1 | fallback (worker-reviewer) | approve | 0 | 2 | 7 | 1 | 43,041 | sonnet |
| 6 | 1 | QA ×2 | 1 | `qa-twice.sh` | pass 64 | — | — | — | 2 | — | — |
| 7 | 1 | land: full gates | — | orchestrator | green | — | — | — | 3 | — | — |

## Open advisories

- none open (the re-close grouping advisory is a manual check below; it needs a real database)

## Manual checks for the owner

- Close an issue that was created from the app: the reporter's bell shows "Your report was closed: <title> (Completed)" without a page reload; clicking it opens the Report page.
- Reopen and close the same issue again while the first notification is unread: still one notification (grouped). After reading it, a new close gives a new one.
- The next email digest lists the closed report.

## Ignored instructions

- none

## Cost

API list-price equivalent, not a bill.
- **Local:** `node scripts/claude-cost.mjs --from 2026-10-21T03:43:37Z --to 2026-10-21T03:58:00Z --md`

| who | model | requests | input | cache write | cache read | output | cost |
|---|---|---|---|---|---|---|---|
| main session (orchestrator) | claude-opus-5-5 | 29 | 62 | 50,195 | 9,147,161 | 26,807 | $2.77 |
| worker-reviewer: Plan review report notify | claude-sonnet-5-5 | 9 | 20 | 41,459 | 273,268 | 2,765 | $0.16 |
| worker-reviewer: Code review report notify | claude-sonnet-5-5 | 5 | 10 | 41,898 | 140,629 | 59 | $0.12 |
| **TOTAL** |  | **43** | **92** | **133,552** | **9,561,058** | **29,631** | **$3.05** |
