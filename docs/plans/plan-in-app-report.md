# Plan: In-App Report → GitHub Issue

## Overview

**Status:** Planned
**Date:** 2026-10-20
**Spec:** [spec-in-app-report](../specs/spec-in-app-report.md) · **ADR:** [ADR-0026](../adr/0026-in-app-report.md) · **PRD:** [PRD-in-app-report](../prd/PRD-in-app-report.md)
**Size:** Medium. 6 implementation tasks (IR-1..IR-6) of about 150–400 LOC each, plus owner setup (IR-7).

## Objective

Build the spec as written: a Report page for ADMIN and SUPER_ADMIN that stores a report and creates a labelled GitHub issue; a list with GitHub state; retry for failed reports.

## Scope

### In Scope

Everything in the spec's FR-1..FR-7, BR-1..BR-9 and EC-1..EC-13.

### Out of Scope

As in the spec and PRD:
- CONTRIBUTOR role;
- webhook or sync back from GitHub;
- screenshots;
- editing or deleting a report.

## Technical Approach

### Architecture Changes

```
ReportPage (FE) ──► AdminReportController ──► ReportServiceImpl ──► AppReportRepository (app_report)
                                                      │
                                                      ├─► GitHubIssueClient (client, java.net.http) ──► api.github.com
                                                      └─► GitHubIssueStateCache (Guava, 5 min)
```

Dependency directions stay layered, so the `aic` check keeps reporting no cycle:
- `web → service → client, repo, cache`;
- `client → config` (`AppProperties`).

### Key Decisions

- **No new ADR.** ADR-0026 covers the token, the trust model, the caps and the storage. Everything else follows existing patterns.
- **Entity:** UUID key with `@GeneratedValue`, plus `@CreationTimestamp` and `@UpdateTimestamp`, like `entity/VideoComment.java:32-34, 51-57`.
- **Config:** `AppProperties.Github` nested class with `isConfigured()`, like `GoogleOauth` (`config/AppProperties.java:97-112`). In `application.yaml`, `app.github.token: ${GITHUB_REPORT_TOKEN:}` sits next to `google-oauth` (`application.yaml:140-143`).
- **HTTP client:** like `client/HttpRedirectProbe.java:33-38`, with a package-private constructor for tests, which use a stub `HttpServer` (`HttpRedirectProbeTest.java:26-52`).
- **Parallel state calls:** `TaskUtils.runAllIgnoreError` (`utils/TaskUtils.java:53-76`) on virtual threads. Do **not** copy `VideoStorageServiceImpl.java:129`, which uses platform threads.
- **Controller:** class-level `@PreAuthorize("hasRole('ADMIN')")` and `@AuditLog` / `@RateLimited` as in `web/AdminJobController.java:31-32, 62-64`. The list returns `ResultObjectInfo<Page<ReportDto>>` with `Pageable`, like `web/AdminController.java:75-79`.
- **Current user:** `AppUtils.getCurrentUser()` (`utils/AppUtils.java:21-27`).
- **Errors:** `CustomException.of(HttpStatus, subCode, "error.report.*")`, with the keys in `messages.properties` / `messages_vi.properties`, as for `error.job.*` (`:105-110`).
- **Token masking:** the field is named `token`, which is already on the masking list (`application.yaml:164-165`). It never enters a DTO.

## Implementation Steps

### Tasks

| ID | Task | Depends on | ~LOC (non-test) | Acceptance criteria |
|----|------|-----------|-----------------|---------------------|
| **IR-1** | DB + entity: migration `202610200001-create-app-report.sql` (spec DDL), `AppReport` entity, `AppReportRepository` with `countByStatusAndAutopilotTrueAndReporterRoleAndCreatedAtAfter` (or a `@Query`) and `findAllByOrderByCreatedAtDesc(Pageable)` | — | ~120 | Migration test (like `ScheduledJobMigrationTest`) shows the table, the two check constraints and both indexes; the repository cap count matches the BR-3 filter in a repo test; `mvn verify` green |
| **IR-2** | GitHub client: `AppProperties.Github` (`token`, `owner`, `repo`, `isConfigured()`) + yaml mapping; `GitHubIssueClient` + `JdkGitHubIssueClient` (`create`, `state`), headers and timeouts per spec FR-6; `GitHubIssueStateCache` | — | ~200 | Stub-server tests check: (a) create sends the method, path, headers and JSON body, and parses `number`/`html_url`; (b) non-201, timeout and IO errors raise a typed failure carrying `github.http.<status>` or `github.timeout`; (c) `state` maps 200 open/closed, 404, 410 and an exception to OPEN/CLOSED/UNKNOWN; (d) a fake token string appears in no log line or exception message; the cache stores OPEN/CLOSED and never UNKNOWN |
| **IR-3** | Service: `ReportService` + `ReportServiceImpl`, DTOs (`CreateReportRequest`, `ReportContext`, `ReportDto`), validation (spec Data Validation), labels (BR-2..BR-4), cap (BR-3), body builder (BR-7), store-then-call (FR-3), retry rules (BR-6), list with parallel state (BR-9); `error.report.*` keys in en + vi | IR-1, IR-2 | ~350 | Service unit tests (Mockito, like `ScheduledJobServiceImplTest`) cover: each BR; EC-1, EC-2, EC-4, EC-5, EC-7, EC-9, EC-10, EC-11; an ADMIN `autoLabel` stores nothing; the body has no email or user id; a SUPER_ADMIN is not capped; retry reapplies the cap |
| **IR-4** | API: `AdminReportController` (POST, GET paged, POST retry), `@AuditLog` + `@RateLimited` per spec BR-5 | IR-3 | ~90 | Permission tests (like `AdminJobControllerPermissionTest`) check: USER gets 403 on all three; ADMIN and SUPER_ADMIN get 200; the response JSON matches the spec contract; invalid page or size gives 400; a 6th create in the window gives 429 |
| **IR-5** | FE data layer: `webapp/src/api/reports.js` (`createReport`, `listReports`, `retryReport`), hooks `useReports` / `useCreateReport` / `useRetryReport` (key `['admin','reports',page]`, invalidate `['admin','reports']`), `__APP_BUILD__` define in `vite.config.js`, i18n `report.json` en + vi, `navReport` in `shell.json` en + vi | IR-4 (contract) | ~150 | API tests check the URLs and bodies; hook tests check invalidation, like `useAdminJobs.test.jsx`; the i18n parity test passes; lint clean |
| **IR-6** | FE UI: `components/report/ReportPage.jsx` (form + context preview + public warning + list + Retry); `AppShell` nav item above Settings (sidebar `:321`, mobile `:150-152`, `requiresAdmin`), main switch (`:358-377`), overflow list (`:352-353`), previous-nav state for `context.page` | IR-5 | ~350 | Component tests check: ADMIN sees no autopilot select; SUPER_ADMIN sees it; submit sends the context; CREATED shows the issue link; FAILED shows Retry; `capReached` notice; list badges OPEN/CLOSED/UNKNOWN and "Deleted user"; AppShell test: Report shown for ADMIN, hidden for USER, placed before Settings |
| IR-7 | **Owner setup + prod check** (not run by agents): `gh label create report`; fine-grained PAT (this repo, Issues r/w); add `GITHUB_REPORT_TOKEN=…` to `/opt/funnyapp/.env` on the VM, `chmod 600`, restart (`./stopServer.sh && ./startServer.sh`, per `docs/guides/video-import-setup-vi.md:122-137, 219`); send one report and confirm that issue N has `autopilot` and the plan job starts | IR-4 deployed | — | Issue created from prod; the token does not appear in prod logs |

### Merge order

- **Backend:** IR-1 and IR-2 are independent and can merge in either order. IR-3 comes after both, then IR-4.
- **Frontend:** IR-5 and IR-6 merge after IR-4 is deployed, so the menu never points at a missing API.
- **Every unit merges to `main` on its own:**
  - IR-1..IR-3 add no endpoint, so they are dark until IR-4.
  - IR-5 adds no UI.

### Phases

- **Phase 1, backend (IR-1..IR-4):** fits one `/autopilot docs/plans/plan-in-app-report.md` round (4 tasks ≤ 6), or `/swarm-execute`.
- **Phase 2, frontend (IR-5, IR-6):** a second round after Phase 1 is deployed.
- **Phase 3, owner (IR-7).**

## Files to Modify

| Path | Action | Task |
|------|--------|------|
| `api/src/main/resources/db/changelog/sql/202610200001-create-app-report.sql` | Create | IR-1 |
| `api/src/main/java/com/canhlabs/funnyapp/entity/AppReport.java`, `repo/AppReportRepository.java` | Create | IR-1 |
| `api/src/main/java/com/canhlabs/funnyapp/config/AppProperties.java`, `api/src/main/resources/application.yaml` | Modify | IR-2 |
| `api/src/main/java/com/canhlabs/funnyapp/client/GitHubIssueClient.java`, `client/JdkGitHubIssueClient.java`, `cache/GitHubIssueStateCache.java` | Create | IR-2 |
| `api/src/main/java/com/canhlabs/funnyapp/service/ReportService.java`, `service/impl/ReportServiceImpl.java`, `dto/admin/{CreateReportRequest,ReportContext,ReportDto}.java` | Create | IR-3 |
| `api/src/main/resources/i18n/messages.properties`, `messages_vi.properties` | Modify | IR-3 |
| `api/src/main/java/com/canhlabs/funnyapp/web/AdminReportController.java` | Create | IR-4 |
| `webapp/src/api/reports.js`, `webapp/src/hooks/useReports.js`, `webapp/src/i18n/locales/{en,vi}/report.json` | Create | IR-5 |
| `webapp/vite.config.js`, `webapp/src/i18n/locales/{en,vi}/shell.json` | Modify | IR-5 |
| `webapp/src/components/report/ReportPage.jsx` | Create | IR-6 |
| `webapp/src/components/layout/AppShell.jsx`, `webapp/src/styles/index.css` (only if a class is missing) | Modify | IR-6 |
| Tests next to each (api/src/test/…, webapp/src/**/__tests__/…) | Create | each |

## Dependencies

### Code Dependencies

- No new Maven or npm dependency.
- Uses Jackson, `java.net.http`, Guava and `StructuredTaskScope` (JDK 24 preview, already enabled), which are all already in use.

### Service Dependencies

- GitHub REST API (`api.github.com`), with the owner PAT. Tests never call it.

## Testing Strategy

### Unit Tests

- **Backend:** service rules (IR-3), client against a stub server (IR-2), cache.
- **Frontend:** API module, hooks, ReportPage, AppShell nav.

### Integration Tests

- Migration test (IR-1).
- `@WebMvcTest` permission and contract tests (IR-4).
- Phase QA: `bash scripts/qa-twice.sh` on the new tests.

### Manual Testing

IR-7, the prod check by the owner.

## Rollback Plan

- **Revert** the IR-4 commit (endpoint) and the IR-6 commit (menu). The table and dark classes can stay.
- **Turn the feature off** by clearing `GITHUB_REPORT_TOKEN`: reports are then stored as FAILED and no issue is created.

## Risks

| Risk | Mitigation |
|------|------------|
| IR-3 grows past 400 LOC | Split off the list and state part (BR-9) as IR-3b |
| Owner PAT missing at deploy | EC-1: FAILED + Retry; nothing else breaks |
| ADMIN-started autopilot runs cost money | Daily cap 10 (BR-3) + per-user limit (BR-5); ADR-0026 D2/D3 |

## Checklist

### Before Starting

- [x] PRD approved, ADR-0026 accepted, spec written
- [ ] Owner: label `report` created (can wait until IR-7)

### Before PR

- [ ] `mvn verify` (coverage ≥ `api/.coverage-threshold`), `npm run test`, `npm run lint`
- [ ] `aic` check shows no new package cycle

### Before Merge

- [ ] Code review `approve` (security focus on IR-2..IR-4: token handling, authZ, input)

## Notes

The spec was corrected at v1.1 during planning: the list returns `Page<ReportDto>`, and `navReport` lives in `shell.json`.

## Progress Log

| Date | Note |
|------|------|
| 2026-10-20 | Planned with 3 explorer workers (BE patterns, FE patterns, prod secret wiring) |
