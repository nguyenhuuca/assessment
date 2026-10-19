# Plan: Disable the Reopen Button While the Request Is Pending

## Overview

**Status:** Done 2026-10-19 (autopilot, headless, issue #26)
**Author:** nguyenhuuca
**Date:** 2026-10-19
**Related plan:** [plan-suggestion-rereview](plan-suggestion-rereview.md) (added the Reopen button)
**ADR:** none. One attribute on one button; no API, schema or behavior change on the server.
**Size:** Small. This plan is the only artifact.

## Objective

In the admin Suggestions tab the Reopen button only sets `aria-busy` while its mutation runs, so it can still be clicked. A second click opens the confirm dialog again, and confirming sends a second `POST /admin/video-suggestions/{id}/reopen`. Disable the button while that row's request is pending, and add a test.

## Verified Facts (read 2026-10-19)

| Fact | Where |
|------|-------|
| The Reopen button has `aria-busy={isBusyFor(reopen, s.id, v => v?.id)}` and no `disabled` | `webapp/src/components/admin/AdminSuggestionTable.jsx:260-270` |
| `reopenRow` awaits the confirm dialog, then calls `reopen.mutate({ id: s.id }, ...)` | `AdminSuggestionTable.jsx:179-188` |
| `isBusyFor` is true only while `mutation.isPending` and the variables match the row id | `webapp/src/utils/mutationBusy.js:5-10` |
| The Approve / Reject modal submit buttons use `disabled={saving} aria-busy={saving}` (`saving` = `mutation.isPending`) | `AdminSuggestionTable.jsx:65,101,294,303` |
| The Approve / Reject **row** buttons only set `aria-busy`; they open a modal rather than send a request, so they cannot double-send | `AdminSuggestionTable.jsx:240-257` |
| Existing reopen tests live in `describe('reopen')`; the API is mocked with `suggestionsApi.reopen` | `webapp/src/components/admin/__tests__/AdminSuggestionTable.test.jsx:133-175` |

## Design

- **D1:** compute `const reopening = isBusyFor(reopen, s.id, v => v?.id)` for the row and set `disabled={reopening}` next to the existing `aria-busy={reopening}`. The disabled state is per row, the same scope as `aria-busy`, so reopening one suggestion does not block another.
- **D1b:** one `useReopenSuggestion` instance serves the whole table and keeps only the latest `variables`, so the per-row flag is exact for one request at a time. `reopenRow` therefore also returns early when `reopen.isPending`, so a second request cannot start while one is running.
- **D2:** Approve / Reject row buttons are left alone. The issue asks for Reopen, and those open a modal whose submit button is already disabled while pending.
- Because `reopenRow` awaits the confirm dialog, the dialog's backdrop covers the table until the user answers, so the only gap that matters is "mutation pending", which D1 closes.

## Tasks

| ID | Task | Acceptance criteria | Depends on |
|----|------|---------------------|------------|
| **RB-1** | `AdminSuggestionTable.jsx`: `disabled` on the Reopen row button while `isBusyFor(reopen, s.id, ...)`, plus an early return in `reopenRow` when `reopen.isPending` | While `suggestionsApi.reopen` is unresolved the button has `disabled` and `aria-busy="true"`; after it resolves the button is enabled again; no other file changes | — |
| **RB-2** | Test in `describe('reopen')` of `AdminSuggestionTable.test.jsx` | With `reopen` mocked as a never-resolving promise: click Reopen, confirm, assert the button is disabled and `aria-busy`; click it again and assert no second dialog opens (`queryByRole('dialog')` is null) and `suggestionsApi.reopen` was called exactly once with `2`. The test fails without RB-1 | RB-1 |

Gates: `npm run test` and `npm run lint` in `webapp/`. No backend change, so no `mvn verify`.

## Risks

| Risk | Mitigation |
|------|------------|
| `disabled` removes the button from the tab order, so focus is lost after the click | Same as the modal buttons; the button comes back when the list refetches |
| A second click between `mutate()` and the re-render that applies `disabled` (TanStack batches notifications, so the DOM updates a tick later) | The window is tiny, and the confirm dialog must be answered again before a second `mutate`; D1b's `isPending` guard in `reopenRow` closes it once the re-render lands |
