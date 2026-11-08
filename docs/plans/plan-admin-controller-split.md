# Plan: Split `AdminController` by admin area

<!--
Implementation Plan
Filename: docs/plans/plan-admin-controller-split.md
Owner: Builder (/builder)
Handoff to: Builder (/builder), QA Engineer (/qa-engineer)
-->

## Overview

**Status:** Done
**Author:** nguyenhuuca
**Date:** 2026-11-08
**Source:** [architecture-quality-2026-11-07-en.md](../reports/architecture-quality-2026-11-07-en.md), finding 3
**Decision class:** Two-way door. Pure refactor: same URLs, same security rules, same responses. Plan only, no ADR, no PRD.

## Problem

`web.AdminController` (344 lines, 19 endpoints) serves three admin areas at once: videos, accounts and comments. Its direct fan-out is 31 classes, the highest in the codebase. Any change to one area touches the shared class and its two test files (`AdminControllerTest`, 26 tests; `AdminControllerPermissionTest`, 48 tests).

The other admin areas already have their own controllers: `AdminJobController`, `AdminReportController`, `AdminDriveSyncController`, `AdminVideoImportController` and `AdminVideoSuggestionController`. This plan applies the same pattern to the three areas left in `AdminController`.

## Target

| New controller | Endpoints (paths unchanged, all under `/admin`) | Services |
|----------------|-----------------------------------------------|----------|
| `AdminVideoController` | `GET /videos`, `PATCH /videos/{id}/status`, `/priority`, `/details`, `/visibility`, `DELETE /videos/{id}`, `PUT` + `DELETE /videos/{id}/hls`, `POST /videos/bulk`, `GET /stats` | `AdminVideoService`, `AdminVideoBulkService` |
| `AdminAccountController` | `GET /accounts`, `PATCH /accounts/{id}/role`, `/permissions`, `POST /accounts/bulk`, `DELETE /accounts/{id}` | `AdminAccountService`, `AdminAccountBulkService` |
| `AdminCommentController` | `GET /comments`, `PATCH /comments/moderation`, `POST /comments/hard-delete`, `PATCH /comments/{id}/moderation` | `AdminCommentService` |

`AdminController` is deleted once it is empty.

`GET /stats` goes with videos because it calls `adminVideoService.getStats()`.

Rules for the move:
- Each new class keeps `@RequestMapping(AppConstant.API.BASE_URL + "/admin")` and the class-level `@PreAuthorize("hasRole('ADMIN')")`. Method paths and method-level annotations (`@HasPermission`, the inlined SUPER_ADMIN `@PreAuthorize`, `@AuditLog`, `@RateLimited`, `@PageableDefault`) are copied verbatim. Nothing in the frontend changes.
- Injection stays as `@Autowired` setter methods (`injectXxx`), like `AdminJobController`. `BeanConstructorTest` keeps passing.
- Method bodies are moved, not rewritten (Two Hats rule). Behavior-changing fixes found along the way, such as Sonar S2259 in `bulkAccounts`, go into a separate commit or ticket.
- The inline fully-qualified `com.canhlabs.funnyapp.service.AdminAccountBulkService` becomes a normal import.

**Success criteria:**
- Direct fan-out of every new controller is ≤ 22, and well below the current 31. It is measured with the same `jdeps -verbose:class` method as the report. Estimate: videos ≈ 20, accounts ≈ 15, comments ≈ 14. If videos ends above 22, AC-6 reports the figure and moves `GET /stats` together with the HLS pair into its own follow-up. The fan-out is not forced down inside this refactor.
- The set of 19 admin routes, HTTP methods and security expressions is identical before and after (AC-1).
- All existing tests pass with only wiring changes. No assertion is removed.

## Tasks

| ID | Task | Files | Notes |
|----|------|-------|-------|
| **AC-1** | Route-contract test, written **first** against the current code | `web/AdminRouteContractTest.java` (new) | Reflect over `@RestController` classes in `web` whose base path starts with `/admin`. Collect one row per handler with these fields:
- HTTP method and full path;
- effective security: the method-level `@PreAuthorize` if present, otherwise the class-level one, plus the `@HasPermission(perm)` value;
- the `@AuditLog` and `@RateLimited` values.

Compare the rows with a checked-in list of the 19 current routes: 10 video including `/stats`, 5 account and 4 comment. The test also fails on duplicate (method, path) pairs. It passes before the split, so it protects the move. Commit it on its own |
| **AC-2** | `AdminCommentController` (smallest area first) | new controller; remove 4 methods from `AdminController` | Move the comment tests from `AdminControllerTest` into `AdminCommentControllerTest`. Add the new class to the permission test's `@WebMvcTest` list (AC-5 wiring) |
| **AC-3** | `AdminAccountController` | new controller; remove 5 methods | Move the account tests into `AdminAccountControllerTest`. The `getAccounts` `@PageableDefault` reflection test (`AdminControllerTest.java:253`) follows its method. Add the class to `@WebMvcTest` |
| **AC-4** | `AdminVideoController` (10 endpoints) + delete `AdminController` | new controller; delete old class | Move the remaining tests into `AdminVideoControllerTest`. `HasPermissionAnnotationTest.java:10,85` switches to `AdminVideoController.getStats` in this commit. It compiles against `AdminController` until then. `@WebMvcTest` lists the three new controllers |
| **AC-5** | Permission test wiring (no own commit) | `AdminControllerPermissionTest` | Done inside AC-2 to AC-4, so every commit is green. The 48 tests stay unchanged, since they call URLs, not classes. **The class keeps its name.** `AdminReportControllerPermissionTest.java:57` and `AdminJobControllerPermissionTest.java:47` import its nested `TestSecurity`, and a rename would break them |
| **AC-6** | Verify and report | `docs/reports/architecture-quality-2026-11-07-en.md` | `mvn verify` (coverage ≥ 90%, `PackageCycleTest`, `AdminRouteContractTest`). Recompute fan-out with jdeps. Update finding 3 and the follow-ups table with the commits |

Each of AC-2 to AC-4 is one atomic commit with green tests, so any step can be pushed or reverted on its own.

## Testing

- **Safety net:** AC-1 fails if any route, HTTP method or security expression is lost, duplicated or changed during the move.
- **Security:** the 48 MockMvc tests in the permission test run the real `PermissionServiceImpl` and cover ADMIN, SUPER_ADMIN and the permission gates per endpoint. They must pass unchanged.
- **Unit:** the 26 controller tests are moved by area. Their count stays at 26.
- **Boot:** the app has no `@SpringBootTest`, so start it locally once (`./startLocal.sh`) and call `GET /admin/stats` and `GET /admin/videos` with an admin token. This catches ambiguous-mapping or bean errors that unit tests miss.
- **Prod smoke after deploy:** open the admin Vault, Accounts and Comments tabs. Expect 200s and no 404 or 405.

## Risks

| Risk | Mitigation |
|------|------------|
| A security annotation is dropped in the move, so an endpoint becomes looser | AC-1 compares security expressions per route. The permission test covers the same routes over HTTP |
| Two controllers map the same route (`IllegalStateException: Ambiguous mapping` at startup) | AC-1 checks for duplicates. Local boot check |
| `@AuditLog` / `@RateLimited` stop applying | Both are AOP on annotations, independent of the class. Annotations are copied verbatim, and the existing audit and rate-limit tests cover them |
| Frontend breaks | No URL changes. AC-1 enforces this |

## Out of scope

- Changing URLs, DTOs or service interfaces.
- Splitting the services. `SuggestionBulkServiceImpl`, `UserServiceImpl` and `VideoSuggestionServiceImpl` are the other coupling hotspots, and they need their own analysis.
- Sonar S2259 in `bulkAccounts`.
- Historical docs that name `AdminController` stay as written, because they record the design at the time. These are `docs/sequence/*`, `docs/specs/spec-collection-only-videos.md:328` and `docs/adr/0036-admin-bulk-actions.md:14`.

## Estimate

About 350 lines moved and 1 new test class of about 120 lines. 5 commits: AC-1, AC-2, AC-3, AC-4 (each including its AC-5 wiring), and AC-6. Roughly 1 to 2 hours.
