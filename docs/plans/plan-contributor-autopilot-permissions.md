# Plan: Contributor Role and Autopilot Permissions for Reports

<!--
Implementation Plan
Filename: docs/plans/plan-contributor-autopilot-permissions.md
Owner: Builder (/builder)
-->

## Overview

**Status:** Done (2026-10-23, autopilot run `2026-10-23-contributor-autopilot-permissions`)
**Date:** 2026-10-23
**PRD:** [PRD-contributor-autopilot-permissions](../prd/PRD-contributor-autopilot-permissions.md)
**ADR:** [ADR-0030](../adr/0030-contributor-autopilot-permissions.md)
**Spec:** [spec-contributor-autopilot-permissions](../specs/spec-contributor-autopilot-permissions.md) (rules are cited as BR-n / FR-n / EC-n)
**Sequence:** [sequence-contributor-autopilot-permissions](../sequence/sequence-contributor-autopilot-permissions.md)
**Size:** Medium. 6 tasks: 3 backend, 3 frontend, about 1,300 LOC including tests.

## Objective

Add the `CONTRIBUTOR` role (Report only) and two SUPER_ADMIN-granted bits, so that the holder can put `autopilot:auto-merge` or `autopilot:auto` on a report. Every check is in the server and reads the DB.

## Scope

### In Scope

- Backend: role, bits, security matchers, account rules, report label and cap rules, one Liquibase changeset
- Frontend: role helpers, auth state, navigation and notifications, report form, account table
- Tests for every BR, EC and acceptance criterion in the spec that can be automated

### Out of Scope

- Everything listed as out of scope in the PRD (grant history UI, self sign-up, notifications on grant, auto-revoke)
- Any change to `.github/`, the autopilot scripts or ADR-0025

## Technical Approach

### Architecture Changes

See the spec's API and Frontend sections and the sequence diagrams (flows 1–7). There is no new table, endpoint or cache. The changes are:
- one enum value (`UserRole.CONTRIBUTOR`);
- two enum bits (`Permission.AUTOPILOT_*`);
- one security matcher placed before `/admin/**`;
- service rules in `AdminAccountServiceImpl` and `ReportServiceImpl`;
- one partial-index changeset.

### Key Decisions

All recorded in ADR-0030 (D1–D6) and the spec's Open Questions (owner decisions of 2026-10-23):
- `@HasPermission(ADMIN)` is dropped from the report endpoints;
- `ALL_BITS` stays 31;
- a retry downgrades the label instead of refusing.

**Explorer findings that shape the tasks:**
- Liquibase uses `includeAll: sql/` (`db.changelog-master.yaml:1-4`), so a new `.sql` file is picked up without a master entry.
- `@WebMvcTest` permission tests import `AdminControllerPermissionTest.TestSecurity`, not `WebSecurityConfig`. So the real `/admin/reports/**` vs `/admin/**` matcher order needs its own test (CA-1).
- `AdminAccountTable.jsx:11-12,68,81` would rank CONTRIBUTOR as 0 (like USER), promote it straight to ADMIN and badge it as a user. CA-6 must fix all three.
- `AuthContext.jsx:51` copies only `role` from `/user/me`, and `permissions` is not stored anywhere. CA-4 must add it, or the report form cannot know the caller's bits after a reload.
- The backend does not filter the notification ADMIN category by role (`NotificationServiceImpl.java:37-42`), so BR-14 is frontend only.

## Implementation Steps

Every task lands on `main` on its own. The order in the dependency graph is the merge order.

| ID | Task | Files | Acceptance criteria | Est. LOC (main + tests) |
|----|------|-------|---------------------|-------------------------|
| **CA-1** | BE: role, bits, security matchers, report controller | `enums/UserRole.java`, `enums/Permission.java`, `filter/WebSecurityConfig.java:54`, `web/AdminReportController.java:32,43,51,59`; tests `WebSecurityConfigRoleHierarchyTest`, `AdminReportControllerPermissionTest`, new `WebSecurityConfigAdminMatcherTest` | (1) `UserRole` = USER, CONTRIBUTOR, ADMIN, SUPER_ADMIN; `CONTRIBUTOR.defaultPermissions()==0`; ADMIN/SUPER_ADMIN still 31 (BR-1). (2) `Permission` adds 32/64, `ALL_BITS==31`, `AUTOPILOT_BITS==96`, `allowsAutoLabel(mask,label)` per BR-8 (FR-4). (3) `roleHierarchy()` reaches CONTRIBUTOR from ADMIN; `adminImpliesUserOnly` updated. (4) Matcher `/v1/funny-app/admin/reports/**` → CONTRIBUTOR/ADMIN/SUPER_ADMIN, before `/admin/**` (FR-2). The new test boots the real filter chain and checks that a CONTRIBUTOR gets 200 on `/admin/reports` and 403 on each EC-8 path. (5) Controller `hasRole('CONTRIBUTOR')`, no `@HasPermission` on the 3 methods (FR-3). A CONTRIBUTOR with mask 0 gets 200 with the flag on and off; USER still 403 | ~90 + ~180 |
| **CA-2** | BE: account rules for CONTRIBUTOR and autopilot bits | `service/impl/AdminAccountServiceImpl.java:41-58,73-89`; `AdminAccountServiceImplTest` | (1) Mask outside 127 → 400 (literal 32 test moves to 128). (2) Non-SA changing the autopilot bits → 403 "Only the SUPER_ADMIN can change autopilot permissions"; an ADMIN changing only the other bits of a CONTRIBUTOR is accepted (BR-5, EC-4). (3) Autopilot bit on a USER target → 400 (BR-6, EC-5). (4) `updateRole`: keep the autopilot bits for CONTRIBUTOR/ADMIN, clear them for USER (BR-7, EC-5, EC-6). (5) ADMIN may set USER ⇄ CONTRIBUTOR, cannot set ADMIN (BR-3). Sessions are revoked as before | ~40 + ~160 |
| **CA-3** | BE: report label, retry and cap rules + migration | `service/impl/ReportServiceImpl.java:103,162-199`, `service/impl/ReportIssueContent.java:45`, `repo/AppReportRepository.java:31`, new `db/changelog/sql/202610230002-app-report-cap-contributor.sql`, `messages*.properties:122`; tests `ReportServiceImplTest`, `AppReportMigrationTest` | (1) Create: BR-8 table, refused label → 403 `error.report.autoLabelForbidden` and nothing saved (BR-9). (2) Retry: label capped to the current caller (BR-10, EC-1, EC-2); SA retry keeps the full label. (3) Cap counts `reporter_role IN (ADMIN, CONTRIBUTOR)` (BR-11): repository method `…ReporterRoleIn…`; 10 split across both roles → 11th gets the type label only. (4) Body says "a CONTRIBUTOR" (BR-12). (5) Checks hold with the Harness flag off (EC-7; the service never calls `FeatureFlagService`). (6) Migration replaces `idx_app_report_autopilot_cap` with the new predicate; the migration test asserts it. (7) Message text EN/VI updated | ~80 + ~220 |
| **CA-4** | FE: role helpers, auth permissions, nav and notifications | `utils/roles.js`, `utils/permissions.js`, `contexts/AuthContext.jsx:45-93`, `components/layout/AppShell.jsx:46,103,139,160-161,314,331,343,380-383`, `components/notifications/NotificationDropdown.jsx:18-22`, `utils/notificationText.js:31-35`; tests `AuthContext.test.jsx`, `AppShell.report.test.jsx`, `notificationText.test.js`, `NotificationBell.test.jsx`, new `roles.test.js`, `permissions.test.js` | (1) `canReport(role)` true for CONTRIBUTOR/ADMIN/SUPER_ADMIN. (2) `AUTOPILOT_PERMISSIONS` (32, 64) and `allowedAutoLabels(role, mask)` per BR-8. (3) AuthContext keeps `permissions` from login and `/user/me` and exposes `canReport`. (4) CONTRIBUTOR sees Report (desktop + mobile) and no Admin; USER sees neither. (5) CONTRIBUTOR's dropdown has the Admin tab; `REPORT_CLOSED` opens Report (BR-14) | ~60 + ~150 |
| **CA-5** | FE: report form autopilot options by bits | `components/report/ReportPage.jsx:10-12,52-56,88,113,173-180`, `i18n/locales/{en,vi}/report.json`; test `ReportPage.test.jsx` | (1) The select lists exactly `allowedAutoLabels(user.role, user.permissions)` plus "No auto"; hidden when empty (FR-10). (2) The payload sends only an allowed value. (3) Existing tests "ADMIN has no autopilot select" and "SUPER_ADMIN sees the autopilot select" still pass; new: CONTRIBUTOR mask 32 sees [No auto, Auto-merge], mask 64 sees both. (4) CONTRIBUTOR can retry own FAILED row (reporter rule unchanged) | ~25 + ~80 |
| **CA-6** | FE: account table CONTRIBUTOR + autopilot toggles | `components/admin/AdminAccountTable.jsx:11-12,67-75,81-122`, `src/index.css:1658-1691`, `i18n/locales/{en,vi}/admin.json`; test `AdminAccountTable.test.jsx`, `badgeContrast.test.js` if the new badge is covered | (1) `ROLE_RANK` USER 0, CONTRIBUTOR 1, ADMIN 2, SUPER_ADMIN 3. (2) Role control: SUPER_ADMIN gets a select USER/CONTRIBUTOR/ADMIN (ADMIN keeps the grant-admin confirm); an ADMIN viewer gets USER ⇄ CONTRIBUTOR only (spec assumption). (3) `admin-badge-contributor` style + `roleContributor` label. (4) Two "Autopilot" toggles (bits 32/64) shown only to the SUPER_ADMIN on CONTRIBUTOR/ADMIN rows; toggling sends the new mask; the existing 5 chips are unchanged. (5) en/vi keys added | ~90 + ~140 |

### Dependency graph

```
CA-1 ──┬──> CA-2
       └──> CA-3
CA-4 ──┬──> CA-5
       └──> CA-6   (also after CA-2)
```

- CA-1 and CA-4 can start in parallel (separate backend and frontend files).
- CA-2 and CA-3 touch different services. They depend on CA-1's enums, so they run in parallel after CA-1 lands.
- CA-5 and CA-6 depend on CA-4's helpers, and run in parallel after it. CA-6 also lands after CA-2: its role select sends `CONTRIBUTOR` and its toggles send masks 32/64, which the backend rejects with 400 before CA-1/CA-2.
- CA-4 and CA-5 can land before the backend: they only read a role and bits that no account has yet.
- **Rollout rule:** no account is given CONTRIBUTOR until CA-3 is merged and deployed. Between CA-1 and CA-3, a contributor's reports would not count toward the cap (`ReportServiceImpl.java:197` counts ADMIN only), and the issue body would say "an ADMIN" (`ReportIssueContent.java:45`).

## Files to Modify

See the Files column above. New files:
- `api/src/main/resources/db/changelog/sql/202610230002-app-report-cap-contributor.sql`
- `api/src/test/java/com/canhlabs/funnyapp/filter/WebSecurityConfigAdminMatcherTest.java`
- `webapp/src/utils/__tests__/roles.test.js`

## Dependencies

### Code Dependencies

- ADR-0015 bitwise permissions (`Permission`, `PermissionServiceImpl`)
- ADR-0026 report service and controller
- Spring Security `RoleHierarchyImpl.withDefaultRolePrefix()` (`WebSecurityConfig.java:81-83`)

### Service Dependencies

- GitHub REST API (unchanged usage)
- Harness flag `permission_enforcement_enabled`, which is on in prod. The new checks do not depend on it.

## Testing Strategy

### Unit Tests

- `AdminAccountServiceImplTest`: BR-3, BR-5, BR-6, BR-7, EC-4, EC-5, EC-6
- `ReportServiceImplTest`: BR-8 table (4 callers × labels), BR-9, BR-10/EC-1/EC-2, BR-11, BR-12, EC-7
- `WebSecurityConfigRoleHierarchyTest`, `AppReportMigrationTest`
- Vitest: roles, permissions, AuthContext, ReportPage, AdminAccountTable, AppShell, notificationText, NotificationBell

### Integration Tests

- `WebSecurityConfigAdminMatcherTest` (real filter chain): BR-2 / EC-8
- `AdminReportControllerPermissionTest` (`@WebMvcTest`): CONTRIBUTOR 200 on all three endpoints with mask 0, flag on and off

### Manual Testing

Owner, after deploy:
1. Set a test account to CONTRIBUTOR.
2. Grant `AUTOPILOT_AUTO_MERGE` and send a report with "Auto-merge". Check that the issue has `autopilot:auto-merge`.
3. Revoke the grant and send again. The form shows no option, and a crafted request gets 403.
4. Run `./api/mock-test.sh`. It is read-only and the ADMIN test account is unchanged.

## Rollback Plan

- `git revert` the task commits in reverse order (CA-6 … CA-1).
- Before reverting CA-1, set every CONTRIBUTOR back to USER: `UserRole.valueOf` would fail on a stored `CONTRIBUTOR`.
- The CA-3 index changeset may stay; the wider predicate is harmless.

## Risks

| Risk | Mitigation |
|------|------------|
| Matcher order wrong, so contributors reach admin endpoints or get 403 on reports | CA-1 real-filter-chain test |
| New bits leak to admins | `ALL_BITS==31` asserted; CA-2 role-change tests |
| Stored `CONTRIBUTOR` row on a rollback | Rollback step above |
| CA-3 changes a Liquibase file, which the headless auto-merge refuses | Land CA-3 through a normal run; the owner merges or pushes |
| Account table promotes CONTRIBUTOR to ADMIN by mistake | CA-6 (1)–(2) + tests |

## Checklist

### Before Starting

- [x] PRD, ADR-0030, spec, sequence diagrams written
- [x] Explorer findings recorded (Key Decisions)
- [ ] TASKS.md section added

### Before PR

- [ ] `mvn -f api/pom.xml verify` (coverage ≥ `api/.coverage-threshold`)
- [ ] `npm --prefix webapp run test` and `npm --prefix webapp run lint`
- [ ] `BeanConstructorTest` green (no new beans expected)

### Before Merge

- [ ] Security review (focus `security`: authZ matcher order, bit checks)
- [ ] `docs/tracking.md` updated

## Notes

- Six tasks, so `/autopilot docs/plans/plan-contributor-autopilot-permissions.md` can run it in one round. `/swarm-execute` can parallelise CA-1 ∥ CA-4, then CA-2 ∥ CA-3 ∥ CA-5 ∥ CA-6.
- Keep the sequence diagrams in sync if the as-built flow differs.

## Progress Log

| Date | Task | Commit | Note |
|------|------|--------|------|
| 2026-10-23 | — | — | Plan written from spec + 3 explorer reports |
| 2026-10-23 | CA-1..CA-6 | e1eeb892..102fced0 | Built by autopilot (2 worker-builders); plan + code review approve; QA ×2 290/290; mvn verify 1640 tests, line coverage 96.70% |
