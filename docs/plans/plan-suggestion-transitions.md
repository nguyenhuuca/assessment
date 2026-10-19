# Plan: One Place for Suggestion Status Transitions (Refactor)

<!--
Implementation Plan
Filename: docs/plans/plan-suggestion-transitions.md
Owner: Builder (/swarm-execute) — run by /autopilot
Handoff to: Builder
-->

## Overview

**Status:** Planned 2026-10-19 (autopilot run)
**Author:** nguyenhuuca
**Date:** 2026-10-19
**Related plan:** [plan-suggestion-rereview](plan-suggestion-rereview.md) (added reopen + rereview, which spread the rules out)
**ADR:** none. This is an internal refactor inside one service: no API, schema or behavior change, and it is easy to revert.
**Size:** Small (a few hours). This plan is the only artifact.
**Hat:** Refactoring only (`.claude/rules/code-quality.md` Two Hats Rule). Behavior stays the same, and the existing tests must pass **without edits**.

## Objective

Today the rule "which suggestion may move from which status to which" lives in five places:
- `lockPending`;
- `reopen`'s inline checks;
- `requireRereviewable`;
- `canReopen`;
- the `canRequestRereview` expression in the DTO mapping.

Move these rules into **one** type, so the service, the DTO flags and the tests all read the same table. The concurrency guards stay where they are: the row locks, the conditional UPDATE and the partial unique index handle races, not business rules.

## Verified Facts (read 2026-10-19, `service/impl/VideoSuggestionServiceImpl.java` after `6c16388e`)

| Transition | Current rule location | Rule | Error when refused |
|------------|----------------------|------|--------------------|
| approve, reject | `lockPending` `:279-286` | status == PENDING | 409 4093 `error.suggestion.notPending` |
| reopen (admin) | `reopen` `:228-238` | PENDING → refuse; APPROVED → job status (read via `findLockedById`) ∈ `REOPENABLE_JOB_STATUSES`; REJECTED → OK | 409 4095 `alreadyPending`; 409 4096 `cannotReopen` |
| rereview (user) | `requireRereviewable` `:288-295` | status == REJECTED and `rereviewCount < MAX_REREVIEWS` | 409 4097 `notRejected`; 409 4098 `rereviewLimit` |
| DTO `canReopen` | `canReopen` `:314-318` | same as the reopen rule, as a boolean | — |
| DTO `canRequestRereview` | `toDto` `:419` | same as the rereview rule, as a boolean | — |
| DB guard (stays) | `VideoSuggestionRepository.requestRereview` `:37-48` | `WHERE status = REJECTED AND rereview_count < :maxCount` | 0 rows → re-read at `VideoSuggestionServiceImpl.java:268` runs the rereview rule again (third caller) |
| DTO `canReopen` argument | `submit` `:132`, `mine` `:152`, `approve` `:197`, `reopen` `:247` pass `false`; `reject` `:219` passes `true`; `list` computes `canReopen(...)` | after approve the job is fresh (never reopenable); after reject REOPEN is always allowed; `mine` is the user view | — |

Duplicate and not-found checks (`requireNoDuplicate`, 404 4042) are **not** transition rules and stay in the service. These also **stay unchanged**, because they select data rather than allow a transition:
- the `APPROVED && importJobId != null` filters in `liveRefs` (`:324`) and `jobStatuses` (`:373`);
- the APPROVED filter in `SuggestionNotificationListener.java:78`.

## Design

- **D1 — `SuggestionTransition` enum** in `enums/`, next to `SuggestionStatus`. Values: `APPROVE`, `REJECT`, `REOPEN`, `REREVIEW`. Each value implements:
  - `Optional<Refusal> check(SuggestionStatus status, ImportStatus jobStatus, int rereviewCount)`. It is a pure function with no repository and no entity, so it can be unit-tested as a table.
  - `boolean allowed(...)`, which returns `check(...).isEmpty()`. The DTO flags use it.
- **D2 — `Refusal`** is a nested record `SuggestionTransition.Refusal(HttpStatus status, int subCode, String messageKey)` in `enums/`. It is nested so that it does not need its own file. It carries today's exact codes and keys, The service turns it into an exception with one private helper, `refuse(Optional<Refusal>)`. The helper throws `error(r.status(), r.subCode(), r.messageKey())`, the same `error(...)` factory that builds today's `CustomException`.
- **D3 — Constants move with the rules.** `MAX_REREVIEWS` and `REOPENABLE_JOB_STATUSES` become constants of `SuggestionTransition`. The service passes `SuggestionTransition.MAX_REREVIEWS` to the repository's conditional UPDATE. The DB guard and the rule then share one number.
- **D4 — The service keeps the order of operations.** Lock the suggestion first. The service locks and reads the job row with `findLockedById` **only when the status is APPROVED and `importJobId != null`**, exactly as at `:231-234`. PENDING, REJECTED and the non-owner paths never touch the job row. Then it runs `check`, then the duplicate check, then saves. Inside `REOPEN.check` the PENDING refusal (4095) comes before the job-status refusal (4096). The `canReopen` flag arguments at `:132/152/197/219/247` keep their current values. `list` uses `REOPEN.allowed`. Only the `if` blocks are replaced. Lock order, transactions, events and error codes stay the same.
- **D5 — The target end state is not a GoF State pattern or Spring State Machine.** Three statuses do not justify either.

## Tasks

| ID | Task | Acceptance criteria | Depends on |
|----|------|---------------------|------------|
| **ST-1** | Add `enums/SuggestionTransition.java` + `Refusal`, and a table test `SuggestionTransitionTest` | Every (transition × status) cell is covered, plus REOPEN × each `ImportStatus` incl. null, and REREVIEW at count 0/1/2. Each refusal returns today's status, subcode and key exactly | — |
| **ST-2** | Use it in `VideoSuggestionServiceImpl`: `lockPending`, `reopen`, `requireRereviewable`, `canReopen`, `toDto` flag; remove the now-unused constants and helpers | `VideoSuggestionServiceImplTest` (incl. its `findLockedById` verify/never checks), `VideoSuggestionControllerTest` and `AdminVideoSuggestionControllerTest` pass **unchanged** (`git diff` shows no edits under those test files); `mvn verify` green with coverage ≥ threshold; no API, DTO or migration change | ST-1 |

Both tasks fit in one commit (~150 changed LOC).

## Risks

| Risk | Mitigation |
|------|------------|
| A subtle behavior change, e.g. a different error code for one combination | The existing tests stay unedited and cover every error path. The table test pins each code |
| The rule and the SQL guard drift apart | They share the `MAX_REREVIEWS` constant (D3) |
