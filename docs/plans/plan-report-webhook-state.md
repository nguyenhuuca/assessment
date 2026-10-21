# Plan: Report Issue State by GitHub Webhook

## Overview

**Status:** Built (WH-1..WH-4, `5582b2fc`, `90dd0a78`); WH-5 owner setup pending
**Date:** 2026-10-21
**ADR:** [ADR-0026 D7](../adr/0026-in-app-report.md) · **Spec:** [spec-in-app-report v1.3](../specs/spec-in-app-report.md) (BR-9, BR-10, FR-8, EC-14..EC-17) · **Parent plan:** [plan-in-app-report](plan-in-app-report.md)
**Size:** Small feature on top of In-App Report. 4 implementation tasks (~230 LOC non-test), plus owner setup (WH-5).

## Objective

GitHub pushes `closed` / `reopened` issue events to the app; the app verifies the HMAC signature and stores the state and its reason on `app_report`. The report list uses the stored state and calls GitHub only for rows that have none. The badge shows the reason ("Closed · completed" / "Closed · not planned").

## Scope

### In Scope

Spec v1.3: BR-9 (changed), BR-10, FR-8, the webhook endpoint, the modified `app_report` table, `ReportDto.githubStateReason`, EC-14..EC-17 and the webhook acceptance criteria.

### Out of Scope

- Other issue events (comments, labels, PR links) and in-app notifications.
- Back-filling the state of existing reports (they keep the read-through path until their next event).
- Storing the read-through result.
- Automatic redelivery of missed events (GitHub has none; the owner can redeliver by hand).

## Technical Approach

### Architecture Changes

```
api.github.com ──POST (HMAC)──► GitHubWebhookController ──► GitHubWebhookService ──► AppReportRepository.applyGithubState (conditional UPDATE)
ReportPage (FE) ──► AdminReportController ──► ReportServiceImpl.list ──► stored state, else GitHubIssueClient + GitHubIssueStateCache (as today)
```

Layers stay `web → service → repo`; `service → config` for the secret. No new library.

### Key Decisions

- **No new ADR.** ADR-0026 D7 (2026-10-21) covers the endpoint, the secret, the columns and the ordering rule.
- **Public path:** add `POST /webhooks/github` to `WHITE_LIST_PATH` (`api/src/main/java/com/canhlabs/funnyapp/utils/AppConstant.java:45-54`). That list is used both by `WebSecurityConfig` (`filter/WebSecurityConfig.java:55`) and by `JWTAuthenticationFilter.shouldNotFilter` (`filter/JWTAuthenticationFilter.java:104-110`), so no JWT is needed and none is parsed.
- **Raw body, capped:** the controller reads `HttpServletRequest.getInputStream().readNBytes(MAX_BODY + 1)` with `MAX_BODY` = 1 MiB and returns 413 above it, before the HMAC check. Check order: 503 (no secret, body not read) → 413 → 401 → 400 → 204, pinned by a controller test that posts a raw body (CWE-770; `issues` payloads are tens of KB). No filter in `api/` reads the request body first (no `ContentCachingRequestWrapper`; `filter/` holds only the JWT filter, provider and security config).
- **Signature:** `HmacSHA256` (JDK `javax.crypto.Mac`) over the raw bytes; compare `sha256=<hex>` with `MessageDigest.isEqual`. Verify before any JSON parsing. Header format checked against GitHub's "Validating webhook deliveries" page.
- **Secret config:** add `webhookSecret` to `AppProperties.Github` (`config/AppProperties.java:118-126`) with `isWebhookConfigured()`, and `webhook-secret: ${GITHUB_WEBHOOK_SECRET:}` under `app.github` (`api/src/main/resources/application.yaml:144-147`). The name contains `secret`, which is on the masking list (`application.yaml:168-169`), and it never enters a DTO.
- **Transaction:** the repo method is `@Modifying @Transactional @Query(...)`; the service is not transactional otherwise (Spring Data needs a transaction for a modifying query, else `TransactionRequiredException`).
- **Ordering and races:** one JPQL `@Modifying` update, `WHERE status = CREATED AND issueNumber = :n AND (githubStateAt IS NULL OR githubStateAt < :at)`. Zero rows → `existsByStatusAndIssueNumber` tells `stale` from `unknown-issue`. This makes EC-14 and EC-16 hold under concurrent deliveries without locks.
- **Reason whitelist:** `state_reason` is kept only when it is one of `completed`, `not_planned`, `duplicate`, `reopened`; anything else is stored as null (fits `VARCHAR(16)`, no 500 after a valid signature).
- **State mapping:** `GitHubIssueState` (entity enum) maps to `GitHubIssueClient.IssueState` by name (`OPEN`→`OPEN`, `CLOSED`→`CLOSED`) in `resolveStates`; `toDto` keeps taking the state as a string (`:291`).
- **List:** `ReportServiceImpl.resolveStates` (`service/impl/ReportServiceImpl.java:197-229`) skips the GitHub call when `row.getGithubState() != null`. `toDto` (`:291-310`) adds `githubStateReason` from the row (null for read-through rows).
- **Migration:** new file `api/src/main/resources/db/changelog/sql/202610210001-app-report-github-state.sql`, picked up by `includeAll: sql/` (`db/changelog/db.changelog-master.yaml:2-3`). Additive, nullable columns only, so a rolling deploy is safe. Latest file today: `202610200001-create-app-report.sql`.
- **Controller style:** like `web/AdminReportController.java:30-40` (`@RestController`, `AppConstant.API.BASE_URL`, setter injection), but without `@PreAuthorize`, `@HasPermission` or `@RateLimited`, since there is no user.
- **FE badge:** `StateBadge` (`webapp/src/components/report/ReportPage.jsx:34-40`) appends ` · <reason>` when `githubStateReason` is set, from new i18n keys `stateReason.*` next to `state` (`webapp/src/i18n/locales/en/report.json:52`, `vi/report.json:52`).

## Implementation Steps

### Tasks

| ID | Task | Depends on | ~LOC (non-test) | Acceptance criteria |
|----|------|-----------|-----------------|---------------------|
| WH-1 | Migration `202610210001-app-report-github-state.sql` (spec "Modified Tables"); new enum `enums/GitHubIssueState { OPEN, CLOSED }` (kept out of `client` so `entity` does not depend on `client`); `AppReport` fields `githubState` (`@Enumerated(STRING)`), `githubStateReason`, `githubStateAt` (`entity/AppReport.java:90-97` area); repo methods `applyGithubState(...)` (conditional update, returns int) and `existsByStatusAndIssueNumber` (`repo/AppReportRepository.java:15-28`); `applyGithubState` is `@Modifying @Transactional` | — | ~40 | Migration SQL matches the spec; columns nullable; `applyGithubState` carries `@Modifying` and `@Transactional` (checked by a reflection test, since the repo is mocked elsewhere); `mvn verify` passes; entity maps the new columns (checked by the WH-2 service test with a mocked repo and by `BeanConstructorTest` still passing) |
| WH-2 | `AppProperties.Github.webhookSecret` + yaml; `GitHubWebhookService` (`verify(signatureHeader, body)`, `handle(event, deliveryId, body)` → outcome enum `APPLIED/IGNORED/STALE/UNKNOWN_ISSUE`); `GitHubWebhookController` `POST /v1/funny-app/webhooks/github` returning 204/400/401/503; `WHITE_LIST_PATH` entry | WH-1 | ~130 | Unit tests: valid signature + `closed`/`completed` → repo update called with `CLOSED`, `completed`, payload `updated_at`; wrong, missing or malformed signature → 401 and repo untouched; blank secret → 503; `ping` and `issues`/`labeled` → 204 IGNORED; other `repository.full_name` → IGNORED; zero rows updated + exists → STALE, + not exists → UNKNOWN_ISSUE; bad JSON with valid signature → 400; `issues` event without `issue.number` → 400; body over 1 MiB → 413 and no HMAC/parse; unknown `state_reason` (e.g. `something_new_and_long`) → stored as null. `@WebMvcTest` (or the `AdminReportControllerPermissionTest` style) proves the path needs no JWT. Logs carry delivery id, event, action, issue number, outcome; a test with a fake secret and body asserts neither appears in the captured log |
| WH-3 | `ReportServiceImpl.resolveStates` uses the stored state (mapped by name to `IssueState`); `ReportDto.githubStateReason` (`dto/admin/ReportDto.java`); `toDto` fills it | WH-1 | ~25 | `ReportServiceImplTest`: a CREATED row with stored `CLOSED`/`not_planned` → DTO has both and `client.state` is never called; a row without a stored state → read-through as before (existing tests stay green); FAILED rows → both null |
| WH-4 | FE `StateBadge` shows the reason; i18n `stateReason.{completed,not_planned,duplicate,reopened}` in `en` and `vi` `report.json` | WH-3 | ~20 | `ReportPage.test.jsx`: a CLOSED row with `githubStateReason: 'not_planned'` renders "Closed · Not planned"; a row without a reason renders "Closed" only; unknown reason falls back to the raw value; i18n parity test passes; `npm run test` and `npm run lint` pass |
| WH-5 | **Owner setup + prod check** (not run by agents): repo Settings → Webhooks (URL `https://canh-labs.com/api/v1/funny-app/webhooks/github`, `application/json`, random secret, Issues only); `GITHUB_WEBHOOK_SECRET` in `/opt/funnyapp/.env`, restart; close and reopen a test issue created from the app | WH-2..WH-4 deployed | — | GitHub "Recent Deliveries" shows 204 for `ping` and for the close; the Report list shows "Closed · completed" without waiting for the 5-min cache |

### Merge order

- WH-1 → WH-2 and WH-3 (independent of each other) → WH-4. Every unit merges to `main` on its own: WH-1 is dark (unused columns); WH-2 adds the endpoint, which returns 503 until the secret is set; WH-3 reads the columns, which stay null until events arrive.
- Backend before frontend: WH-4 reads `githubStateReason`, which is absent (undefined) until WH-3 is deployed, and the badge then shows "Closed" as today.

## Files to Modify

| Path | Action | Task |
|------|--------|------|
| `api/src/main/resources/db/changelog/sql/202610210001-app-report-github-state.sql` | Create | WH-1 |
| `api/src/main/java/com/canhlabs/funnyapp/entity/AppReport.java` | Modify | WH-1 |
| `api/src/main/java/com/canhlabs/funnyapp/repo/AppReportRepository.java` | Modify | WH-1 |
| `api/src/main/java/com/canhlabs/funnyapp/config/AppProperties.java`, `api/src/main/resources/application.yaml` | Modify | WH-2 |
| `api/src/main/java/com/canhlabs/funnyapp/service/GitHubWebhookService.java` (+ `impl/`), `web/GitHubWebhookController.java` | Create | WH-2 |
| `api/src/main/java/com/canhlabs/funnyapp/utils/AppConstant.java` | Modify | WH-2 |
| `api/src/main/java/com/canhlabs/funnyapp/service/impl/ReportServiceImpl.java`, `dto/admin/ReportDto.java` | Modify | WH-3 |
| `webapp/src/components/report/ReportPage.jsx`, `webapp/src/i18n/locales/{en,vi}/report.json` | Modify | WH-4 |
| Tests: `service/impl/GitHubWebhookServiceImplTest.java`, `web/GitHubWebhookControllerTest.java`, `ReportServiceImplTest.java`, `ReportPage.test.jsx` | Create / Modify | WH-2..WH-4 |

## Testing Strategy

- **Unit:** HMAC verify with a known vector (secret + body → expected hex computed in the test with `Mac`), every outcome of `handle`, `resolveStates` with stored vs missing state.
- **Web slice:** the webhook path answers without an `Authorization` header; the admin paths still need one (regression).
- **Not covered by automated tests:** the conditional UPDATE against PostgreSQL (no DB test infra in `api/`, same accepted gap as the parent plan); it is checked in WH-5 by closing an issue twice (redelivery → `stale` in the log).
- **Gates:** `mvn -f api/pom.xml verify` (coverage ≥ `api/.coverage-threshold`), `npm --prefix webapp run test`, `npm --prefix webapp run lint`.

## Rollback Plan

- Unset `GITHUB_WEBHOOK_SECRET` (endpoint → 503) or delete the webhook on GitHub: the list falls back to stored-or-read-through, and stored states simply stop updating.
- Revert WH-3 to return to read-through only. The columns can stay (nullable, unused).

## Risks

| Risk | Mitigation |
|------|-----------|
| Secret leak lets anyone set display states | Separate secret from the PAT; rotate in GitHub and the env file; effect is display only (ADR-0026 D7 consequences) |
| Missed delivery leaves a stale state | Owner redelivers from "Recent Deliveries"; accepted in ADR-0026 D7 |
| A proxy changes the body and breaks the signature | Nginx passes the body unchanged by default; WH-5 checks the `ping` delivery returns 204 |
