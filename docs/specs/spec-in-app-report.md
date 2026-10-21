# Feature Specification: In-App Report

<!--
Feature Specification
Filename: docs/specs/spec-in-app-report.md
Owner: nguyenhuuca
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
-->

## Metadata

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-10-20
**Related PRD:** [PRD-in-app-report](../prd/PRD-in-app-report.md)
**Related ADR:** [ADR-0026](../adr/0026-in-app-report.md)

---

## Overview

ADMIN and SUPER_ADMIN users get a **Report** page, placed just above Settings. From there they send a bug, an enhancement or an issue. The server stores the report and creates a GitHub issue in `nguyenhuuca/assessment` through the GitHub REST API, labelled `autopilot` plus a type label. That issue starts headless autopilot (ADR-0022). The page also lists every stored report with its GitHub state and offers a retry for reports whose issue could not be created.

---

## Business Rules

### BR-1: Who can use it

Only `ADMIN` and `SUPER_ADMIN` can create, list or retry reports. `USER` gets 403 on every endpoint and does not see the menu.

### BR-2: Type and type label

The `type` is exactly one of three values, each with a fixed type label:

| `type` | Type label |
|--------|------------|
| `BUG` | `bug` |
| `ENHANCEMENT` | `enhancement` |
| `ISSUE` | `report` |

### BR-3: Autopilot label and the daily cap

- **Default:** every issue gets `autopilot`, unless the daily cap applies.
- **The cap:** when the reporter is an `ADMIN`, the server counts the stored reports with `status = CREATED`, `autopilot = true`, `reporter_role = ADMIN` and `created_at` in the last 24 hours.
  - If the count is **≥ 10**, the issue is created **without** `autopilot`, and the response has `capReached = true`.
  - Reports from a `SUPER_ADMIN` are neither limited nor counted.
- **When it is checked:** each time the server calls GitHub, so on create and on every retry. `FAILED` reports never count.

### BR-4: Auto labels

- `autoLabel` is one of `null`, `AUTO_MERGE` (adds `autopilot:auto-merge`) or `AUTO` (adds `autopilot:auto`). At most one can be sent; the field holds a single value.
- Only a `SUPER_ADMIN` may send a non-null `autoLabel`. An `ADMIN` who sends one gets 403 `error.report.autoLabelForbidden`, and nothing is stored.
- An auto label is only added together with `autopilot`. A SUPER_ADMIN is never capped (BR-3), so in practice it is always added.

### BR-5: Per-user rate limit

- Create is limited to **5 calls per 3,600 s per user**: `@RateLimited(permit = 5, windowInSeconds = 3600)`.
- Retry has the same annotation. Because the limiter keys by URI (`aop/RateLimitAspect.java:33-36`), the retry limit is **per user per report**.
- Over the limit, the response is 429 from the existing `RateLimitExceededException`.

### BR-6: Retry

- Ownership is checked first: only the original reporter or a `SUPER_ADMIN` may retry. Anyone else gets 403 `error.report.retryForbidden`, whatever the report status.
- Only a report with `status = FAILED` can be retried. Retrying a `CREATED` report returns 409 `error.report.alreadyCreated` (after the ownership check).
- A retry reuses the stored type, title, description and context. It re-applies BR-3 and BR-4, and the auto label and cap follow the caller's current role on retry (read from the database, not the stored `reporterRole`): the stored `autoLabel` is sent only when the caller is a `SUPER_ADMIN`; any other caller sends no auto label and is capped like an ADMIN.

### BR-7: Public issue content

The issue `title` is the report title as typed. The issue `body` is, in order:

1. the description as typed;
2. a `### Context` section with the following lines; a missing value is printed as `unknown`:
   - `Page: <page>`
   - `Path: <path>`
   - `App: <appVersion>`
   - `Browser: <userAgent>`
   - `Screen: <screenWidth>px`
3. a footer: `Reported in-app by an ADMIN · report <first 8 chars of id>` (or `the SUPER_ADMIN`);
4. a final line: `<!-- app-report:<id> -->`.

Rules for the body:
- **No email, user name or user id** appears in the issue.
- **No query string or fragment** in the path. The server removes everything from the first `?` or `#`, even if the client already did.

> **Assumption:** The PRD said "display name". `User` has no display-name field, only `id` and `userName` (`entity/User.java:33-36`), and `userName` may be an email, so the footer names the role instead.

### BR-8: Reporter deleted

`reporter_id` is set to `NULL` when the user is deleted (FK `ON DELETE SET NULL`). The report stays in the list as "Deleted user", and only a `SUPER_ADMIN` can retry it.

### BR-9: GitHub state: stored first, read-through as fallback

- The list shows `githubState`: `OPEN`, `CLOSED` or `UNKNOWN`, plus `githubStateReason` (`completed`, `not_planned`, `duplicate`, `reopened` or `null`).
- A `CREATED` row with a stored `github_state` (BR-10) uses it; no GitHub call.
- A `CREATED` row without one is fetched from GitHub and cached for 5 minutes, as before; `githubStateReason` is then `null`. The read-through result is not stored.
- `FAILED` rows have `githubState = null` and `githubStateReason = null`.
- No background refresh.

### BR-10: Webhook stores the state (ADR-0026 D7)

- `POST /v1/funny-app/webhooks/github` accepts a delivery only when `X-Hub-Signature-256` equals `sha256=` + hex HMAC-SHA256 of the raw body with `GITHUB_WEBHOOK_SECRET`, compared in constant time.
- Only `X-GitHub-Event: issues` with `action` `closed` or `reopened` changes anything. It sets `github_state` from `issue.state` (`open` → `OPEN`, `closed` → `CLOSED`), `github_state_reason` from `issue.state_reason` (only `completed`, `not_planned`, `duplicate` or `reopened`; any other value is stored as null), and `github_state_at` from `issue.updated_at`, on the `CREATED` report whose `issue_number` = `issue.number`.
- The update applies only when `issue.updated_at` is after the stored `github_state_at` (or none is stored), so an older delivery that arrives late cannot overwrite a newer state.
- The repository in the payload (`repository.full_name`) must equal the configured `owner/repo`; otherwise nothing changes.

---

## Functional Requirements

### FR-1: Navigation entry

- **Desktop:** a `report` nav item (icon `bug_report`, label `navReport`) placed just above the Settings button in the sidebar (`webapp/src/components/layout/AppShell.jsx:321-328`).
- **Mobile:** the same item placed just above `settings` in the mobile menu (`AppShell.jsx:150-152`).
- **Visibility:** shown only when the user is ADMIN or SUPER_ADMIN (`isAdminRole`, `webapp/src/utils/roles.js:2`).

### FR-2: Create a report

The form must have three inputs:
- type, as a segmented control: Bug / Enhancement / Issue;
- title, required, 1–120 characters after trimming;
- description, required, 1–5,000 characters after trimming.

A SUPER_ADMIN must also see an "Autopilot" select: None / Auto-merge / Full auto. An ADMIN must not see it.

Beside the form, the page must show the context it will send, read-only:
- `page`: the nav key the user was on before opening Report;
- `path`: `window.location.pathname`;
- `appVersion`: the build id, see FE;
- `userAgent`: `navigator.userAgent`;
- `screenWidth`: `window.innerWidth`.

The page must show the public-repo warning (PRD FR-10). On submit, the client calls `POST /admin/reports`, then:
- **`CREATED`:** shows "Issue #N created" with a link that opens in a new tab;
- **`capReached`:** adds "Autopilot quota reached for today; the owner can add the label";
- **`FAILED`:** shows the error and a **Retry** button.

### FR-3: Store, then call GitHub

The server must:
1. validate the request (Security → Data Validation);
2. insert the `app_report` row with `status = FAILED` and `error = 'pending'`;
3. compute the labels (BR-2 to BR-4);
4. call `POST https://api.github.com/repos/{owner}/{repo}/issues` with `{title, body, labels}`;
5. on a 201 response, update the row with `status = CREATED`, `issue_number`, `issue_url` (the `html_url` from the response), `labels` and `autopilot`, and clear `error`;
6. on any failure, keep `FAILED` with a short `error` code (EC-1, EC-2);
7. return the stored report.

### FR-4: List reports

`GET /admin/reports?page&size` must return the reports newest first, paged with a default size of 20 and a maximum of 50. Each row has the reporter's `userName`, which is shown in the app only and never sent to GitHub, plus the `githubState` from BR-9.

### FR-5: Retry

`POST /admin/reports/{id}/retry` must repeat FR-3 steps 3 to 7 for the stored row, after the checks in BR-6.

### FR-6: GitHub client

A `GitHubIssueClient` interface in `client`, implemented with `java.net.http.HttpClient`. It must:
- send `Authorization: Bearer <token>`, `Accept: application/vnd.github+json` and `X-GitHub-Api-Version: 2022-11-28`;
- use a 5 s connect timeout and a 10 s request timeout;
- never log the token or the response body.

It has two methods:
- `create(title, body, labels)` returns the issue number and `html_url`;
- `state(number)` returns `OPEN`, `CLOSED` or `UNKNOWN`. It maps any non-200 response or exception to `UNKNOWN`.

### FR-8: Webhook receiver

The webhook endpoint must:
- read the body as raw bytes and verify the signature **before** parsing JSON;
- return 503 when `GITHUB_WEBHOOK_SECRET` is blank, 401 on a missing or wrong signature, 400 on unparseable JSON from a valid signature, and 204 otherwise (applied or ignored);
- log the delivery id, event, action, issue number and outcome (`applied`, `ignored`, `stale`, `unknown-issue`) at INFO; never log the secret, the signature or the body;
- finish without any outbound call (GitHub expects an answer within 10 s).

### FR-7: Configuration

`AppProperties.github` has three fields:
- `token`, from env `GITHUB_REPORT_TOKEN`, no default;
- `owner`, default `nguyenhuuca`;
- `repo`, default `assessment`.

A blank token means "not configured" (EC-1). The app must start either way.

---

## API Changes

### New Endpoints

All endpoints are under `/v1/funny-app/admin/reports`, in a new `AdminReportController` with a class-level `@PreAuthorize("hasRole('ADMIN')")` (the same pattern as `web/AdminJobController.java:32`). Responses use the project wrappers.

#### POST /v1/funny-app/admin/reports

**Description:** Store a report and create its GitHub issue.

**Auth:** Required (JWT Bearer), role ADMIN or SUPER_ADMIN.
**Rate limit:** 5 per 3,600 s per user (BR-5). `@AuditLog`.

**Request**

```json
{
  "type": "BUG",
  "title": "Video Vault title wraps on mobile",
  "description": "Long titles break the row on a 390 px screen…",
  "autoLabel": null,
  "context": {
    "page": "admin",
    "path": "/",
    "appVersion": "a1b2c3d",
    "userAgent": "Mozilla/5.0 …",
    "screenWidth": 390
  }
}
```

**Response — Success** (HTTP 200, `ResultObjectInfo<ReportDto>`). The report was stored; check `status`.

```json
{
  "status": "SUCCESS",
  "data": {
    "id": "6f1c2b0e-…",
    "type": "BUG",
    "title": "Video Vault title wraps on mobile",
    "status": "CREATED",
    "issueNumber": 41,
    "issueUrl": "https://github.com/nguyenhuuca/assessment/issues/41",
    "labels": ["autopilot", "bug"],
    "autopilot": true,
    "autoLabel": null,
    "capReached": false,
    "error": null,
    "reporter": "admin@example.com",
    "githubState": "OPEN",
    "createdAt": "2026-10-20T12:00:00Z"
  }
}
```

When GitHub fails, the response is the same with `"status": "FAILED"`, `issueNumber` and `issueUrl` set to `null`, and `error` holding a short code such as `"github.notConfigured"`, `"github.timeout"` or `"github.http.502"`.

**Response — Error**

| HTTP Status | Code (i18n key) | Message (en) | Condition |
|-------------|-----------------|--------------|-----------|
| 400 | `error.report.invalidType` | Type must be BUG, ENHANCEMENT or ISSUE | `type` missing or not one of the three |
| 400 | `error.report.invalidTitle` | Title is required (at most 120 characters) | `title` blank after trim, or longer than 120 |
| 400 | `error.report.invalidDescription` | Description is required (at most 5,000 characters) | `description` blank after trim, or longer than 5,000 |
| 400 | `error.report.invalidContext` | Invalid page context | `path` not starting with `/` or longer than 300; `page` longer than 32; `appVersion` longer than 40; `userAgent` longer than 300; `screenWidth` outside 1–10,000 |
| 400 | `error.report.invalidAutoLabel` | Unknown autopilot option | `autoLabel` not null, `AUTO` or `AUTO_MERGE` |
| 401 | — | — | Missing or invalid JWT |
| 403 | — | Access denied | Role USER |
| 403 | `error.report.autoLabelForbidden` | Only the SUPER_ADMIN can choose an autopilot option | ADMIN sends a non-null `autoLabel` |
| 429 | (existing `RateLimitExceededException`) | Rate limit exceeded. Please try again later. | BR-5 |

#### GET /v1/funny-app/admin/reports

**Description:** List reports, newest first, with the GitHub state.

**Auth:** Required, role ADMIN or SUPER_ADMIN.
**Rate limit:** `@RateLimited(permit = 60)` (60 per 60 s per user).

**Request:** query `page` (default 0, at least 0) and `size` (default 20, from 1 to 50).

**Response — Success** (`ResultObjectInfo<Page<ReportDto>>` with `Pageable`, the same shape as the existing admin lists in `web/AdminController.java:75-79`): `data.content` is an array of `ReportDto`, and `data` carries the Spring `Page` fields (`totalElements`, `totalPages`, `number`, `size`). `githubState` is `OPEN`, `CLOSED` or `UNKNOWN` for `CREATED` rows, and `null` for `FAILED` rows. `reporter` is `null` when the user was deleted.

**Response — Error**

| HTTP Status | Code | Message | Condition |
|-------------|------|---------|-----------|
| 400 | `error.report.invalidPage` | Invalid page or size | `page` < 0, or `size` outside 1–50 |
| 401 | — | — | Missing or invalid JWT |
| 403 | — | Access denied | Role USER |

#### POST /v1/funny-app/admin/reports/{id}/retry

**Description:** Retry the GitHub call for a `FAILED` report.

**Auth:** Required, role ADMIN or SUPER_ADMIN, and BR-6 must hold.
**Rate limit:** 5 per 3,600 s per user per report (BR-5). `@AuditLog`.

**Response — Success:** `ResultObjectInfo<ReportDto>`, as for create.

**Response — Error**

| HTTP Status | Code | Message | Condition |
|-------------|------|---------|-----------|
| 401 | — | — | Missing or invalid JWT |
| 403 | — | Access denied | Role USER |
| 403 | `error.report.retryForbidden` | Only the reporter or the SUPER_ADMIN can retry this report | Caller is neither the reporter nor the SUPER_ADMIN |
| 404 | `error.report.notFound` | Report not found | Unknown `id`, or `id` not a UUID |
| 409 | `error.report.alreadyCreated` | This report already has a GitHub issue | `status = CREATED` |
| 429 | (existing) | Rate limit exceeded. Please try again later. | BR-5 |

#### POST /v1/funny-app/webhooks/github

**Description:** GitHub repo webhook for the "Issues" event (BR-10, FR-8).

**Auth:** No JWT (listed in `WHITE_LIST_PATH`, `utils/AppConstant.java:45`). HMAC signature with `GITHUB_WEBHOOK_SECRET`.
**Rate limit:** none (no user; the signature check comes first and is cheap).

**Request:** headers `X-GitHub-Event`, `X-GitHub-Delivery`, `X-Hub-Signature-256`; JSON body from GitHub. Fields read: `action`, `issue.number`, `issue.state`, `issue.state_reason`, `issue.updated_at`, `repository.full_name`.

**Response — Success:** 204, empty body, for applied and for ignored deliveries.

**Response — Error**

| Status | Condition |
|--------|-----------|
| 400 | Valid signature, body is not JSON or lacks `issue.number` for an `issues` event |
| 401 | `X-Hub-Signature-256` missing, not `sha256=<hex>`, or wrong |
| 413 | Body larger than 1 MiB (checked before the signature) |
| 503 | `GITHUB_WEBHOOK_SECRET` not configured |

### Modified Endpoints

- `GET /v1/funny-app/admin/reports`: `ReportDto` gains `githubStateReason` (string or `null`); `githubState` follows BR-9.

---

## Database Changes

### New Tables

```sql
-- In-app reports turned into GitHub issues (ADR-0026).
CREATE TABLE app_report
(
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id   BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    reporter_role VARCHAR(20)  NOT NULL,
    type          VARCHAR(16)  NOT NULL CHECK (type IN ('BUG', 'ENHANCEMENT', 'ISSUE')),
    title         VARCHAR(120) NOT NULL,
    description   TEXT         NOT NULL,
    page          VARCHAR(32),
    page_path     VARCHAR(300),
    app_version   VARCHAR(40),
    user_agent    VARCHAR(300),
    screen_width  INT,
    auto_label    VARCHAR(16)  CHECK (auto_label IN ('AUTO', 'AUTO_MERGE')),
    autopilot     BOOLEAN      NOT NULL DEFAULT FALSE,
    labels        VARCHAR(200) NOT NULL DEFAULT '',
    status        VARCHAR(10)  NOT NULL CHECK (status IN ('CREATED', 'FAILED')),
    issue_number  INT,
    issue_url     VARCHAR(300),
    error         VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_app_report_created_at ON app_report (created_at DESC);
-- BR-3 cap count
CREATE INDEX idx_app_report_autopilot_cap ON app_report (created_at)
    WHERE status = 'CREATED' AND autopilot AND reporter_role = 'ADMIN';
```

> Migration file: `api/src/main/resources/db/changelog/sql/202610200001-create-app-report.sql`. The latest file today is `202610190002-create-scheduled-job.sql`.

- `reporter_role` is stored when the report is created, so the cap and the footer do not change if the user's role changes later.
- `labels` is a comma-separated copy of what was sent to GitHub.

### Modified Tables

```sql
-- Issue state pushed by the GitHub webhook (ADR-0026 D7).
ALTER TABLE app_report
    ADD COLUMN github_state        VARCHAR(10) CHECK (github_state IN ('OPEN', 'CLOSED')),
    ADD COLUMN github_state_reason VARCHAR(16),
    ADD COLUMN github_state_at     TIMESTAMPTZ;
CREATE INDEX idx_app_report_issue_number ON app_report (issue_number) WHERE issue_number IS NOT NULL;
```

> Migration file: `api/src/main/resources/db/changelog/sql/202610210001-app-report-github-state.sql`. All columns are nullable, so existing rows keep working through the read-through path.

---

## Security Requirements

### Authentication

The three admin endpoints require a JWT. The webhook endpoint is public and is authenticated only by its HMAC signature (BR-10); `GITHUB_WEBHOOK_SECRET` is a separate secret from `GITHUB_REPORT_TOKEN`, set in the server's env file, never logged and never returned.

### Authorization

- **Class level:** `@PreAuthorize("hasRole('ADMIN')")`. The role hierarchy lets SUPER_ADMIN through (`filter/WebSecurityConfig.java:81-88`).
- **Service checks:**
  - an ADMIN sending a non-null `autoLabel` is rejected (BR-4);
  - a retry by someone other than the reporter or the SUPER_ADMIN is rejected (BR-6).
- **Future CONTRIBUTOR (ADR-0026 D6):** the class annotation becomes `hasRole('CONTRIBUTOR')`. The service checks stay as they are.

### Data Validation

| Field | Rule | Error |
|-------|------|-------|
| `type` | required; one of `BUG`, `ENHANCEMENT`, `ISSUE` | `error.report.invalidType` |
| `title` | trimmed; 1–120 chars | `error.report.invalidTitle` |
| `description` | trimmed; 1–5,000 chars | `error.report.invalidDescription` |
| `autoLabel` | null, `AUTO` or `AUTO_MERGE` | `error.report.invalidAutoLabel` |
| `context.page` | optional; ≤ 32 chars | `error.report.invalidContext` |
| `context.path` | optional; starts with `/`; ≤ 300 chars; query and fragment stripped by the server | `error.report.invalidContext` |
| `context.appVersion` | optional; ≤ 40 chars | `error.report.invalidContext` |
| `context.userAgent` | optional; ≤ 300 chars | `error.report.invalidContext` |
| `context.screenWidth` | optional; 1–10,000 | `error.report.invalidContext` |

All text goes to GitHub as JSON string values from Jackson, never through string concatenation into a command or URL. The issue number in `state(number)` is an `int`.

### Sensitive Data

- **The GitHub token** must never appear in logs, `@AuditLog` output, exceptions, `error` or any response.
- **Audit logging:** `@AuditLog` records the request DTO as the user sent it; it has no secret fields.
- **Reporter identity:** the reporter's `userName` is returned only by the admin API. It is never in the issue (BR-7).
- **Outbound data:** documented per `.claude/rules/security.md` "Data Routing". Only the issue title, the body of BR-7 and the labels leave the system, to `api.github.com`.

---

## Caching Impact

| Cache | Impact | Action |
|-------|--------|--------|
| New `GitHubIssueStateCache` (Guava, `expireAfterWrite` 5 min, `maximumSize` 500, like `cache/CredentialsVersionCache.java:24-25`) | Read and write | Key: issue number; value: `OPEN`/`CLOSED`. `UNKNOWN` is **not** cached, so the next list tries again. |

No existing cache is affected.

---

## Frontend Changes

### New Routes

None. The app navigates by nav key (`goNav`, `AppShell.jsx`), so this adds the nav key `report`.

| Nav key | Component | Auth Required |
|---------|-----------|---------------|
| `report` | `webapp/src/components/report/ReportPage.jsx` | Yes, ADMIN or SUPER_ADMIN |

### Modified Components

| Component | File | Change |
|-----------|------|--------|
| AppShell | `webapp/src/components/layout/AppShell.jsx` | Add the `report` item above Settings in the sidebar and the mobile menu (FR-1); render `ReportPage` when `activeNav === 'report'`; remember the previous nav key and pass it as `page`; add `report` to the scrollable views (`AppShell.jsx:352-353`) |
| New ReportPage | `webapp/src/components/report/ReportPage.jsx` | Form (FR-2) and list with state badges, issue links and Retry for `FAILED` rows owned by the user or for the SUPER_ADMIN |
| New API module | `webapp/src/api/reports.js` | `createReport(body)`, `listReports({page, size})`, `retryReport(id)` |
| i18n | `webapp/src/i18n/locales/{en,vi}/report.json` (auto-registered by `i18n/index.js:6`) + `navReport` in `{en,vi}/shell.json` | All labels, warnings, errors; the parity test (`i18n/__tests__/parity.test.js`) must pass |
| Vite config | `webapp/vite.config.js` | `define: { __APP_BUILD__: JSON.stringify(process.env.GITHUB_SHA?.slice(0, 7) ?? 'dev') }` for `appVersion`. Actions sets `GITHUB_SHA` in every step, so `deploy-web.yml` needs no change |

> **Assumption:** The webapp has no version today (`package.json` is `0.0.1`). The deploy commit's short SHA is the build id. Locally it is `dev`.

### State Management

```javascript
// Query keys
['admin', 'reports', page]

// useCreateReport / useRetryReport onSuccess
queryClient.invalidateQueries({ queryKey: ['admin', 'reports'] })
```

---

## Event / Job Changes

### Domain Events

| Event | Change | Schema |
|-------|--------|--------|
| — | No change | — |

### Scheduled Jobs

No `AppScheduler` or `scheduled_job` change.

---

## Non-Functional Requirements

### Performance

| Operation | Target | Notes |
|-----------|--------|-------|
| `POST /admin/reports` | < 3 s p95 | Dominated by the GitHub call (request timeout 10 s) |
| `GET /admin/reports` (warm cache) | < 1 s p95 | One indexed page query plus cache hits |
| `GET /admin/reports` (cold cache) | < 3 s p95 | At most `size` state calls, in parallel on virtual threads, each with the 10 s timeout |
| Cap count query | < 50 ms | Partial index `idx_app_report_autopilot_cap` |

### Availability

- **GitHub outage or missing token:** existing features are not affected. Reports are stored as `FAILED` (EC-1, EC-2), and the list shows `UNKNOWN`.
- **Rollback:**
  - remove the nav item and the controller;
  - the `app_report` table can stay, because nothing else reads it.

### Scalability

- One replica (ADR-0023 D5). The in-memory rate limiter and cache are fine at this scale.
- Admins are few and the cap is 10 per day, so the table grows by at most tens of rows a day.

---

## Edge Cases

### EC-1: Token not configured

**Condition:** `GITHUB_REPORT_TOKEN` is blank.
**Expected:**
- No HTTP call is made.
- The row stays `FAILED` with `error = 'github.notConfigured'`, and the response is 200 with that row.
- The UI shows "GitHub is not configured" plus Retry.
- The list shows `githubState = UNKNOWN` for older `CREATED` rows.

### EC-2: GitHub error or timeout

**Condition:** GitHub returns a non-201 response on create, or the call times out or throws.
**Expected:** the row is `FAILED` with `error = 'github.http.<status>'` or `'github.timeout'`, and the response is 200 with that row. Nothing from the response body is stored or logged.

### EC-3: Concurrent reports at the cap

**Condition:** 9 ADMIN autopilot reports exist in the last 24 h, and two ADMIN reports are created at the same moment.
**Expected:** both may get `autopilot`, giving 11 in the window. The count takes no lock (owner decision). The next report sees ≥ 10 and gets no `autopilot`.

### EC-4: Cap boundary

**Condition:** ADMIN reports with `autopilot` in the last 24 h: count = 9, then count = 10.
**Expected:** at 9, the new report gets `autopilot` and `capReached = false`. At 10, it gets only the type label and `capReached = true`. A SUPER_ADMIN report at either count gets `autopilot` plus its auto label.

### EC-5: Retry a CREATED report

**Condition:** `POST /admin/reports/{id}/retry` on a `CREATED` row.
**Expected:** 409 `error.report.alreadyCreated`. No GitHub call.

### EC-6: Response lost after GitHub created the issue

**Condition:** GitHub created the issue, but the response did not arrive (timeout), so the row is `FAILED`, and the user retries.
**Expected:** a second issue is created. Both carry `<!-- app-report:<id> -->`, so the owner can find and close the duplicate. Accepted risk (ADR-0026 D4).

### EC-7: Reporter deleted

**Condition:** the reporter's user row is deleted.
**Expected:** `reporter_id` becomes `NULL`, and the row stays in the list with `reporter = null` (UI: "Deleted user"). An ADMIN retry gets 403; a SUPER_ADMIN retry is allowed.

### EC-8: Issue deleted or transferred on GitHub

**Condition:** `GET issues/{n}` returns 404, 410 or a redirect.
**Expected:** `githubState = UNKNOWN`, not cached. The row keeps its stored link.

### EC-9: Partial state failure

**Condition:** some state calls fail while others succeed.
**Expected:** the failing rows show `UNKNOWN`, the others their state. The endpoint still returns 200.

### EC-10: Text boundaries

**Condition:** title of 120 characters, title of 121 characters, a whitespace-only title, a description of 5,000 characters, a description of 5,001 characters.
**Expected:** 120 and 5,000 succeed. 121, whitespace-only and 5,001 return 400 with the field's code.

### EC-11: Path with a query string or fragment

**Condition:** `context.path = "/?token=abc#x"`.
**Expected:** the stored `page_path` and the issue body say `Path: /`.

### EC-12: Markdown and mentions in the description

**Condition:** the description contains Markdown, HTML or `@user`.
**Expected:** it is sent as-is (GitHub renders Markdown and sanitises HTML).

> **Assumption:** pinging a GitHub user with `@` is acceptable from an admin-only form.

### EC-13: Same user, two quick submits

**Condition:** the user double-clicks Submit.
**Expected:**
- The UI disables Submit while the request is pending, so normally only one request is sent.
- If two requests still arrive, both are processed: two rows and two issues, each counted by BR-5.

---

### EC-14: Webhook deliveries out of order

A `reopened` event with `updated_at` T1 arrives after a `closed` event with T2 > T1. The stored state stays `CLOSED` (BR-10).

### EC-15: Webhook for an issue the app did not create

No `CREATED` report has that `issue_number` → 204, nothing stored, outcome `unknown-issue`.

### EC-16: Webhook redelivered

The same delivery sent twice: the second has the same `updated_at`, which is not after the stored value, so nothing changes (outcome `stale`); the response is still 204.

> **Accepted limit:** GitHub's `updated_at` has second resolution, so a close and a reopen in the same second keep the first state stored. The next event or a manual redeliver fixes it. The endpoint has no per-IP rate limit; the 1 MiB cap and the HMAC check keep the cost per request small (edge limits apply).

### EC-17: `ping` and other events

`X-GitHub-Event: ping`, or `issues` with an action other than `closed`/`reopened` → 204, nothing stored, outcome `ignored`.

## Acceptance Criteria

- [ ] An ADMIN and the SUPER_ADMIN see **Report** above Settings on desktop and mobile; a USER does not (FR-1).
- [ ] A USER gets 403 on all three endpoints (BR-1).
- [ ] Creating a BUG as ADMIN produces an issue labelled `autopilot`, `bug`. The body has the description, the Context section, the role footer and the `app-report` marker, and no email or user id (BR-2, BR-3, BR-7).
- [ ] ENHANCEMENT maps to `enhancement` and ISSUE maps to `report` (BR-2).
- [ ] A SUPER_ADMIN with `AUTO` gets `autopilot`, `autopilot:auto` and the type label; with `AUTO_MERGE`, `autopilot:auto-merge` (BR-4).
- [ ] An ADMIN sending `autoLabel` gets 403 `error.report.autoLabelForbidden`, and no row is stored (BR-4).
- [ ] At 10 ADMIN autopilot reports in 24 h, the next ADMIN report has no `autopilot` and returns `capReached = true`. A SUPER_ADMIN report is unaffected (BR-3, EC-4).
- [ ] The 6th create within an hour by the same user returns 429 (BR-5).
- [ ] With no token, the report is stored as `FAILED` with `github.notConfigured`, and the UI shows Retry (EC-1).
- [ ] A GitHub 5xx or a timeout stores `FAILED` with `github.http.<status>` or `github.timeout` (EC-2).
- [ ] Retry turns a `FAILED` row into `CREATED`. Retry on a `CREATED` row returns 409. A retry by another ADMIN returns 403 (BR-6, EC-5).
- [ ] The list returns the reports newest first with `githubState` `OPEN`, `CLOSED` or `UNKNOWN`. `FAILED` rows have `null`. A failing state call gives `UNKNOWN` for that row only (FR-4, BR-9, EC-8, EC-9).
- [ ] The path is stored and sent without its query string or fragment (EC-11).
- [ ] The text boundaries in EC-10 hold.
- [ ] The token never appears in logs, audit output or responses (verified by a test with a fake token string).
- [ ] The GitHub client is tested against a local stub (`HttpServer`, like `HttpRedirectProbeTest`). No test calls the real GitHub.
- [ ] Webhook: a signed `closed` event with `state_reason: completed` stores `CLOSED`/`completed`, and the list shows it without calling GitHub (BR-9, BR-10).
- [ ] Webhook: a missing or wrong signature → 401 and nothing stored; no secret → 503 (FR-8).
- [ ] Webhook: EC-14 to EC-17 hold; a payload for another repository changes nothing.
- [ ] Webhook: the secret, signature and body never appear in logs.
- [ ] FE: a `CLOSED` badge shows the reason when one is stored ("Closed · completed" / "Closed · not planned").
- [ ] Existing flows are unaffected (regression).
- [ ] `mvn verify` passes with the coverage gate met; `npm run test` and `npm run lint` pass.

---

## Open Questions

- [x] **Owner setup:** create the missing label `report` (`gh label create report`). `bug`, `enhancement` and the three `autopilot*` labels already exist; GitHub would also create `report` on first use, with a default color.
- [ ] **Owner setup (D7):** add the repo webhook (Settings → Webhooks: payload URL `https://canh-labs.com/api/v1/funny-app/webhooks/github`, content type `application/json`, a random secret, "Issues" events only) and set `GITHUB_WEBHOOK_SECRET` to the same secret in the server's env file, then restart.
- [x] **Owner setup:** create the fine-grained PAT (this repo, Issues read/write) and set `GITHUB_REPORT_TOKEN` on the server, through the deploy environment and secrets.

---

## Version History

| Version | Date | Author | Change |
|---------|------|--------|--------|
| 1.0 | 2026-10-20 | nguyenhuuca | Initial draft from `/spec` |
| 1.1 | 2026-10-20 | nguyenhuuca | Planning fixes: list returns `Page<ReportDto>` like other admin lists; `navReport` lives in `shell.json` |
| 1.2 | 2026-10-20 | nguyenhuuca | Review fixes: context and title reject control characters; retry checks ownership before 409; retry auto label and cap follow the caller's current role |
| 1.3 | 2026-10-21 | nguyenhuuca | ADR-0026 D7: HMAC-verified GitHub webhook stores the issue state and reason (BR-9 changed, BR-10, FR-8, EC-14..EC-17, new columns) |
