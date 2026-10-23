# Feature Specification: Contributor Role and Autopilot Permissions for Reports

<!--
Feature Specification
Filename: docs/specs/spec-contributor-autopilot-permissions.md
Owner: Builder (/builder) or Architect (/architect)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
-->

## Metadata

**Status:** Draft
**Author:** nguyenhuuca
**Date:** 2026-10-23
**Related PRD:** [PRD-contributor-autopilot-permissions](../prd/PRD-contributor-autopilot-permissions.md)
**Related ADR:** [ADR-0030](../adr/0030-contributor-autopilot-permissions.md) (amends [ADR-0026](../adr/0026-in-app-report.md))
**Sequence diagrams:** [sequence-contributor-autopilot-permissions](../sequence/sequence-contributor-autopilot-permissions.md)

---

## Overview

The system gets a new role, `CONTRIBUTOR`, that can use only the in-app Report. It also gets two permission bits that the SUPER_ADMIN grants per account (CONTRIBUTOR or ADMIN). These bits let the holder put `autopilot:auto-merge` or `autopilot:auto` on the GitHub issue a report creates. Every rule is enforced in the server against the user row in the DB. The headless autopilot workflow is unchanged.

---

## Business Rules

### BR-1: Role rank

`UserRole` order becomes `USER < CONTRIBUTOR < ADMIN < SUPER_ADMIN`. `CONTRIBUTOR.defaultPermissions()` is `0`.

### BR-2: Contributor access

A CONTRIBUTOR may call only the three report endpoints (create, list, retry) under `/v1/funny-app/admin/reports`. Every other `/v1/funny-app/admin/**` endpoint returns 403 to a CONTRIBUTOR.

### BR-3: Who may assign CONTRIBUTOR

The SUPER_ADMIN and ADMINs may set a USER to CONTRIBUTOR and a CONTRIBUTOR to USER. This follows the existing rank rule: the actor must outrank both the target's current role and the new role (`AdminAccountServiceImpl.java:46,105`).

### BR-4: Autopilot bits

`AUTOPILOT_AUTO_MERGE = 1 << 5` (32) and `AUTOPILOT_AUTO = 1 << 6` (64). They are not part of `ALL_BITS` (stays `0b11111` = 31). `AUTOPILOT_BITS = 32 | 64 = 96`. A valid mask is a subset of `ALL_BITS | AUTOPILOT_BITS` (127).

### BR-5: Who may change autopilot bits

Only the SUPER_ADMIN. When a non-SUPER_ADMIN calls `PATCH /admin/accounts/{id}/permissions` with a mask whose `AUTOPILOT_BITS` differ from the target's current ones, the call gets 403. Changes to the other five bits follow today's rules.

### BR-6: Which accounts may hold autopilot bits

Only CONTRIBUTOR and ADMIN accounts. Setting any autopilot bit on a USER returns 400. The bits on the SUPER_ADMIN account are never read (BR-8). No migration and no default grants them.

### BR-7: Role change

`updateRole` sets `role.defaultPermissions()`, then:
- if the new role is CONTRIBUTOR or ADMIN, ORs back the target's previous `AUTOPILOT_BITS`;
- if the new role is USER, the autopilot bits are cleared.

Session revocation (`credentials_version + 1`) is unchanged.

### BR-8: Allowed auto labels

| Caller | `AUTO_MERGE` | `AUTO` |
|--------|--------------|--------|
| SUPER_ADMIN | yes | yes |
| has `AUTOPILOT_AUTO` | yes | yes |
| has only `AUTOPILOT_AUTO_MERGE` | yes | no |
| no autopilot bit | no | no |

The caller's bits are read from the user row loaded by `currentUser()`, never from the JWT and never through `@HasPermission`.

### BR-9: Create with a label the caller may not use

`POST /admin/reports` with an `autoLabel` not allowed by BR-8 returns 403 `error.report.autoLabelForbidden`, and no row is saved.

### BR-10: Retry downgrades, never refuses, on labels

A retry applies the stored `auto_label` capped to what the **current** caller may use (BR-8):
- `AUTO` falls to `AUTO_MERGE` when the caller holds only `AUTOPILOT_AUTO_MERGE`;
- any label falls to none when the caller holds no autopilot bit.

A retry is never refused because of the label. The stored `auto_label` is not changed; only the issue labels reflect the downgrade.

### BR-11: Shared daily cap

At most **10** reports with `autopilot` from non-SUPER_ADMIN reporters (ADMIN and CONTRIBUTOR together) in a rolling 24 h. The count is of rows with `status = 'CREATED' AND autopilot AND reporter_role IN ('ADMIN','CONTRIBUTOR')` and `created_at` within the window. Over the cap, the issue is created with the type label only: no `autopilot`, no auto label. SUPER_ADMIN reports are never capped.

### BR-12: Issue body role line

"Reported in-app by" is followed by `the SUPER_ADMIN`, `an ADMIN` or `a CONTRIBUTOR`, from the stored `reporter_role`. Still no email, user name or id.

### BR-13: Report list

Every caller allowed on the endpoint (CONTRIBUTOR, ADMIN, SUPER_ADMIN) sees every report, as today.

### BR-14: Report-closed notification

A CONTRIBUTOR gets `REPORT_CLOSED` for their own reports, as an ADMIN does today, and sees it under the Admin tab of the notification dropdown. A click opens the Report page.

---

## Functional Requirements

### FR-1: Role enum

`UserRole` must declare `USER, CONTRIBUTOR, ADMIN, SUPER_ADMIN` in that order. `roleHierarchy()` must derive `ROLE_ADMIN > ROLE_CONTRIBUTOR > ROLE_USER` from it with no hand-written change. Nothing may persist or compare `ordinal()`.

### FR-2: Security matchers

`WebSecurityConfig.securityFilterChain` must register `/v1/funny-app/admin/reports/**` with `hasAnyRole("CONTRIBUTOR","ADMIN","SUPER_ADMIN")` **before** `/v1/funny-app/admin/**` with `hasAnyRole("ADMIN","SUPER_ADMIN")`. The bare path `/v1/funny-app/admin/reports` must be covered too, because `/**` already matches it in Spring's `PathPattern`.

### FR-3: Report controller

`AdminReportController` must use `@PreAuthorize("hasRole('CONTRIBUTOR')")` at class level and must **drop** `@HasPermission(perm = Permission.ADMIN)` from `create`, `list` and `retry` (owner decision, 2026-10-23). `@AuditLog` and `@RateLimited` stay as they are: 5/h for create and retry, 60/min for list.

### FR-4: Permission enum

`Permission` must add `AUTOPILOT_AUTO_MERGE(1 << 5)` and `AUTOPILOT_AUTO(1 << 6)`, keep `ALL_BITS = 0b11111`, and add `AUTOPILOT_BITS = 0b1100000`. A helper `Permission.allowsAutoLabel(int mask, ReportAutoLabel label)` must implement BR-8 for non-SA callers.

### FR-5: Account service

`AdminAccountServiceImpl` must:
- `updatePermissions`: reject bits outside `ALL_BITS | AUTOPILOT_BITS` (400, as today), reject an autopilot change by a non-SUPER_ADMIN (403, BR-5), and reject any autopilot bit on a USER target (400, BR-6);
- `updateRole`: apply BR-7.

### FR-6: Report service

`ReportServiceImpl` must:
- `create`: replace the SUPER_ADMIN-only check (`ReportServiceImpl.java:103`) with BR-8/BR-9;
- `callGitHub`: replace `callerRole == SUPER_ADMIN ? row.getAutoLabel() : null` (`:163`) with the BR-10 cap, passing the caller's `User` (role + permissions) instead of only the role;
- `isCapped`: count with BR-11 (repository method takes a collection of roles).

### FR-7: Repository

`AppReportRepository` must replace `countByStatusAndAutopilotTrueAndReporterRoleAndCreatedAtAfter(status, role, since)` with `countByStatusAndAutopilotTrueAndReporterRoleInAndCreatedAtAfter(status, Collection<UserRole> roles, since)`.

### FR-8: Issue content

`ReportIssueContent.body` must render BR-12.

### FR-9: Notifications

No backend change: `REPORT_CLOSED` already goes to the reporter by id. Frontend: the Admin tab and `notificationNav(n, …)` must use "can report" (CONTRIBUTOR or above) instead of `isAdmin` for `REPORT_CLOSED` (BR-14).

### FR-10: Messages

`error.report.autoLabelForbidden` text changes in `messages.properties` and `messages_vi.properties` (see API Changes).

---

## API Changes

### New Endpoints

None.

### Modified Endpoints

#### POST /v1/funny-app/admin/reports

**Change:** CONTRIBUTOR may call it. `autoLabel` is checked against the caller's bits (BR-8) instead of the SUPER_ADMIN role. The request and response bodies are unchanged.

**Auth:** Required (JWT). Role CONTRIBUTOR or above. No permission bit needed.
**Rate limit:** 5 per hour per user (unchanged)

**Request** (unchanged)

```json
{
  "type": "BUG",
  "title": "Player stalls on seek",
  "description": "…",
  "autoLabel": "AUTO_MERGE",
  "context": { "page": "explore", "path": "/explore", "appVersion": "1.4.2", "userAgent": "…", "screenWidth": 390 }
}
```

**Response — Success** (unchanged shape)

```json
{
  "status": "SUCCESS",
  "data": {
    "id": "6f1c…",
    "type": "BUG",
    "title": "Player stalls on seek",
    "status": "CREATED",
    "issueNumber": 71,
    "issueUrl": "https://github.com/…/issues/71",
    "labels": ["autopilot", "autopilot:auto-merge", "bug"],
    "autoLabel": "AUTO_MERGE",
    "autopilot": true,
    "capReached": false,
    "reporter": "…",
    "githubState": "OPEN"
  }
}
```

**Response — Error**

| HTTP Status | Code | Message | Condition |
|-------------|------|---------|-----------|
| 400 | `error.report.invalidAutoLabel` | Unknown autopilot option | `autoLabel` not `AUTO` / `AUTO_MERGE` / null (unchanged) |
| 400 | `error.report.invalid*` | (unchanged) | Title, description, type or context invalid (unchanged) |
| 401 | — | — | Missing or invalid JWT |
| 403 | — | — | Role USER (Spring Security, FR-2) |
| 403 | `error.report.autoLabelForbidden` | EN: "Your account may not choose this autopilot option" · VI: "Tài khoản của bạn không được chọn tùy chọn autopilot này" | BR-9 |
| 429 | — | — | Rate limit exceeded (unchanged) |

#### POST /v1/funny-app/admin/reports/{id}/retry

**Change:** CONTRIBUTOR may call it, for their own reports (the reporter-or-SA rule at `ReportServiceImpl.java:148` is unchanged). Labels follow BR-10. No new error.

#### GET /v1/funny-app/admin/reports

**Change:** CONTRIBUTOR may call it and sees every report (BR-13). No body change.

#### PATCH /v1/funny-app/admin/accounts/{id}/permissions

**Change:** accepts bits 32 and 64. Request and response shapes are unchanged (`{ "permissions": 96 }` → `AdminAccountDto`).

| HTTP Status | Message | Condition |
|-------------|---------|-----------|
| 400 | `Unknown permission bits: <n>` | Any bit outside 127 (was: outside 31) |
| 400 | `Autopilot permissions need the CONTRIBUTOR or ADMIN role` | Any autopilot bit set on a USER target (BR-6) |
| 403 | `Only the SUPER_ADMIN can change autopilot permissions` | Caller is not SUPER_ADMIN and the autopilot bits differ from the target's current bits (BR-5) |
| 403 | (unchanged) | Caller does not outrank the target |

#### PATCH /v1/funny-app/admin/accounts/{id}/role

**Change:** `role` accepts `CONTRIBUTOR`. Bits follow BR-7. Errors are unchanged. `UserRole.valueOf` of an unknown name stays a 400.

#### GET /v1/funny-app/user/me and login responses

No shape change: `user.permissions` is already sent (`Converter.java:53`). The frontend starts keeping it (see Frontend Changes).

---

## Database Changes

### New Tables

None.

### Modified Tables

| Table | Change | Column | Type | Notes |
|-------|--------|--------|------|-------|
| `app_report` | Replace partial index | `created_at` | — | Cap predicate now covers CONTRIBUTOR (BR-11) |
| `users` | None | `role` | `VARCHAR(20)` | `CONTRIBUTOR` (11 chars) fits; no check constraint on `role` exists |
| `users` | None | `permissions` | `INT` | Bits 32 and 64 fit; no backfill |

```sql
-- Cap count covers CONTRIBUTOR reports too (ADR-0030 D6, spec BR-11).
DROP INDEX IF EXISTS idx_app_report_autopilot_cap;
CREATE INDEX idx_app_report_autopilot_cap ON app_report (created_at)
    WHERE status = 'CREATED' AND autopilot AND reporter_role IN ('ADMIN', 'CONTRIBUTOR');
```

> Migration file: `api/src/main/resources/db/changelog/sql/202610230002-app-report-cap-contributor.sql`, plus its changeset entry in the master changelog. `AppReportMigrationTest` must assert the new predicate.

---

## Security Requirements

### Authentication

JWT as today. `updateRole` and `updatePermissions` already bump `credentials_version` (`AdminAccountServiceImpl.java:54,85,93`), so the target's existing tokens stop working on every grant, revoke or role change.

### Authorization

| Action | Rule | Where enforced |
|--------|------|----------------|
| Report endpoints | CONTRIBUTOR or above | `WebSecurityConfig` matcher (FR-2) + class `@PreAuthorize` (FR-3) |
| Other admin endpoints | ADMIN or above | unchanged `/admin/**` matcher + controller annotations |
| Assign or remove CONTRIBUTOR | actor outranks old and new role | `AdminAccountServiceImpl.updateRole` |
| Change autopilot bits | SUPER_ADMIN only | `AdminAccountServiceImpl.updatePermissions` (BR-5) |
| Use an auto label | BR-8, read from DB | `ReportServiceImpl.create` / `callGitHub` |

None of these depend on the Harness flag `permission_enforcement_enabled`.

### Data Validation

- Permission mask: integer, subset of 127, autopilot bits only on CONTRIBUTOR or ADMIN.
- `role`: one of the four enum names.
- Report fields: unchanged (ADR-0026, `ReportServiceImpl` limits).

### Sensitive Data

- The issue body keeps BR-7 of the in-app report spec: no email, user name or id. Only the role word changes (BR-12).
- Audit: `@AuditLog` on create and retry is unchanged. Role and permission changes are logged by the existing `log.info` lines in `AdminAccountServiceImpl` (`:57`, `:88`), which carry the ids and the new mask. No new audit screen.

---

## Caching Impact

| Cache | Impact |
|-------|--------|
| `credentialsVersionCache` | Already evicted on role and permission change (`AdminAccountServiceImpl.java:56,87`); no change |
| `GitHubIssueStateCache` | None |
| `VideoCacheImpl`, `ChunkIndexCacheImpl`, `StatsCacheImpl` | None |

---

## Frontend Changes

### New Routes

None. The Report page stays at the `report` nav key.

### Modified Components

| File | Change |
|------|--------|
| `webapp/src/utils/roles.js` | Add `canReport(role)` = CONTRIBUTOR, ADMIN or SUPER_ADMIN; `isAdminRole` unchanged |
| `webapp/src/utils/permissions.js` | Add `AUTOPILOT_PERMISSIONS = [{ key: 'AUTOPILOT_AUTO_MERGE', bit: 32 }, { key: 'AUTOPILOT_AUTO', bit: 64 }]` and `allowedAutoLabels(role, mask)` → `[]`, `['AUTO_MERGE']` or `['AUTO_MERGE','AUTO']` (SUPER_ADMIN: both) |
| `webapp/src/contexts/AuthContext.jsx` | Keep `permissions` from login and from `authApi.me()` next to `role` (`:51,72,86`); add `canReport` next to `isAdmin` |
| `webapp/src/components/layout/AppShell.jsx` | Report nav item and page (`:161,382`) use `canReport`; Admin item stays `isAdmin` |
| `webapp/src/components/report/ReportPage.jsx` | The autopilot select (`:52,56,88`) shows `allowedAutoLabels(user.role, user.permissions)`; it is hidden when the list is empty; default "No auto"; only an allowed value is sent |
| `webapp/src/components/admin/AdminAccountTable.jsx` | `ROLE_RANK` gets `CONTRIBUTOR: 1`, ADMIN → 2, SUPER_ADMIN → 3; badge class `admin-badge-contributor`; role control offers USER ⇄ CONTRIBUTOR (any admin), ADMIN ⇄ … unchanged; two autopilot toggles shown only when the viewer is SUPER_ADMIN and the row is CONTRIBUTOR or ADMIN |
| `webapp/src/components/notifications/NotificationDropdown.jsx`, `webapp/src/utils/notificationText.js` | Admin tab and `REPORT_CLOSED` navigation use `canReport` (BR-14) |
| `webapp/src/i18n/locales/{en,vi}/admin.json`, `report.json` | `roleContributor`, `promoteContributor`, `demoteContributor`, `permAutopilotAutoMerge`, `permAutopilotAuto`, `autoLabelNone` (both languages, ADR-0020) |

> **Assumption:** the role control for an ADMIN viewer offers USER ⇄ CONTRIBUTOR only. A SUPER_ADMIN gets a small select (USER / CONTRIBUTOR / ADMIN) in place of today's single promote/demote toggle (`AdminAccountTable.jsx:68`), since a toggle cannot express three targets.

### State Management

| Query key | Change |
|-----------|--------|
| `['admin', 'accounts']` | Invalidated after role and permission mutations (as today) |
| `['reports', …]` (`useReports.js`) | Unchanged |
| Notification queries | `enabled` for the ADMIN category uses `canReport` |

---

## Event / Job Changes

### Domain Events

None new. `ReportClosedEvent` already notifies the reporter whatever the role.

### Scheduled Jobs

None.

---

## Non-Functional Requirements

### Performance

| Operation | Target |
|-----------|--------|
| `POST /admin/reports` | p95 < 3 s including the GitHub call (unchanged, PRD-in-app-report NFR-1). The bit check is in memory on the already loaded user |
| Cap count | one indexed count, < 10 ms (partial index) |
| `PATCH /admin/accounts/{id}/permissions` and `/role` | p95 < 300 ms |

### Availability

Unchanged. A GitHub failure still saves the report as FAILED with Retry (ADR-0026).

### Scalability

N/A: one more `IN` value in an indexed count; no new tables or caches.

---

## Edge Cases

### EC-1: Revoke between create and retry

A contributor creates a report with `AUTO`, GitHub fails (FAILED). The SUPER_ADMIN removes `AUTOPILOT_AUTO` and leaves `AUTOPILOT_AUTO_MERGE`. The contributor logs in again (old JWT revoked) and retries. The issue is created with `autopilot`, `autopilot:auto-merge` and the type label. The stored `auto_label` stays `AUTO` (BR-10).

### EC-2: Retry of a contributor's report by the SUPER_ADMIN

The stored label is applied in full, because the SUPER_ADMIN may use every label.

### EC-3: Concurrent creates at the cap

Two creates at 9 used can both pass and make 11. This is accepted, as in ADR-0026 (EC-3 of the in-app report spec: the count takes no lock).

### EC-4: ADMIN edits a granted contributor's other bits

An ADMIN sends a mask that keeps the target's current autopilot bits unchanged and flips `READ`. Accepted (BR-5 compares only the autopilot bits). The same call with an autopilot bit flipped → 403 and nothing changes.

### EC-5: Autopilot bit on a USER

The SUPER_ADMIN sends `{ "permissions": 32 }` for a USER → 400, nothing saved (BR-6). A CONTRIBUTOR with bit 64 demoted to USER → stored mask `0` (BR-7).

### EC-6: Promotion keeps the grant

A CONTRIBUTOR with mask `32` is promoted to ADMIN by the SUPER_ADMIN. The stored mask becomes `31 | 32 = 63`.

### EC-7: Harness flag off

With `permission_enforcement_enabled` off, `@HasPermission` checks are skipped, but BR-5, BR-8 and BR-9 still apply (plain service checks). A test runs them with the flag stubbed off.

### EC-8: Contributor on another admin endpoint

`GET /admin/accounts`, `/admin/videos`, `/admin/jobs`, `/admin/drive-sync/files`, `/admin/comments`, `/admin/video-imports` and `/admin/video-suggestions/pending-count` with a CONTRIBUTOR JWT → 403 each.

### EC-9: Old JWT with role ADMIN after demotion to CONTRIBUTOR

Rejected by the `credentials_version` check before any role is read (existing behaviour).

---

## Acceptance Criteria

- [ ] `UserRole` order is USER, CONTRIBUTOR, ADMIN, SUPER_ADMIN; `ADMIN` outranks `CONTRIBUTOR`; `CONTRIBUTOR.defaultPermissions() == 0`
- [ ] A CONTRIBUTOR can create, list and retry reports (200) and gets 403 on every endpoint in EC-8
- [ ] A USER gets 403 on the report endpoints
- [ ] SUPER_ADMIN and ADMIN can set USER ⇄ CONTRIBUTOR; an ADMIN cannot set ADMIN
- [ ] Only the SUPER_ADMIN can change bits 32/64 (403 for an ADMIN, EC-4); bits on a USER → 400 (EC-5)
- [ ] `ALL_BITS` is still 31; role change to ADMIN gives `31 | previous autopilot bits` (EC-6); role change to USER gives 0
- [ ] Create: BR-8 table holds for each row (4 caller kinds × 2 labels + none); a refused label → 403 `error.report.autoLabelForbidden`, no row saved
- [ ] Retry: EC-1 and EC-2 produce the labels stated
- [ ] Cap: 10 CREATED autopilot rows split across ADMIN and CONTRIBUTOR → the 11th non-SA report gets the type label only and `capReached: true`; a SUPER_ADMIN report still gets `autopilot`
- [ ] Issue body says "a CONTRIBUTOR" for a contributor report
- [ ] BR-8/BR-9 checks hold with the Harness flag off (EC-7)
- [ ] Migration test asserts the new index predicate
- [ ] Frontend: a CONTRIBUTOR sees the Report menu and no Admin menu; the autopilot select lists exactly `allowedAutoLabels`; the account table shows the two toggles only to the SUPER_ADMIN on CONTRIBUTOR/ADMIN rows; the CONTRIBUTOR badge and i18n strings exist in `en` and `vi`
- [ ] A CONTRIBUTOR's `REPORT_CLOSED` notification shows under the Admin tab and opens the Report page
- [ ] Coverage ≥ `api/.coverage-threshold`; `npm --prefix webapp run test` and `lint` green
- [ ] Prod check (owner): grant `AUTOPILOT_AUTO_MERGE` to a contributor, send a report with "Auto-merge", see the issue get `autopilot:auto-merge`

---

## Open Questions

None. Owner decisions of 2026-10-23: ADMIN may assign CONTRIBUTOR; the endpoints stay at `/admin/reports`; contributors see all reports; bits are kept on CONTRIBUTOR ⇄ ADMIN and cleared on USER; `@HasPermission(ADMIN)` is dropped from the report endpoints; the four edge cases in BR-6, BR-10 and BR-14 are settled.

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-10-23 | nguyenhuuca | Initial spec from PRD v1.1 + ADR-0030 via `/spec` |
