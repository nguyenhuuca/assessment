# Plan: Re-review Modal Closes on Escape and Focuses the Message Field

## Overview

**Status:** Planned 2026-10-19 (autopilot, headless, issue #30)
**Author:** nguyenhuuca
**Date:** 2026-10-19
**Related plan:** [plan-suggestion-rereview](plan-suggestion-rereview.md) (added the modal)
**ADR:** none. Frontend-only behavior of one component; no API, schema or dependency change.
**Size:** Small. This plan is the only artifact.

## Objective

`RereviewModal` (the "Request re-review" dialog in "My suggestions") closes only on a backdrop click or the Cancel button, and keyboard focus stays on the button behind it. Make Escape close it (unless the request is pending) and focus the message textarea when it opens.

## Verified Facts (read 2026-10-19)

| Fact | Where |
|------|-------|
| `RereviewModal` renders a backdrop whose `onClick` is `rereview.isPending ? undefined : onClose`, and a Cancel button `disabled={rereview.isPending}` | `webapp/src/components/history/SuggestionCard.jsx:32,51` |
| The component has no `useEffect`, no `useRef` and no keydown handling; the file imports only `useState` | `SuggestionCard.jsx:1,17-59` |
| The textarea has `id="rereview-msg-{id}"` and no `ref` | `SuggestionCard.jsx:40-47` |
| The parent passes an inline `onClose={() => setRereviewOpen(false)}`, a new function on every parent render | `SuggestionCard.jsx:152` |
| Reference pattern: a `window` `keydown` listener in `useEffect`, ignored while `pending`, removed on cleanup, deps `[onCancel, pending]` | `webapp/src/components/common/ConfirmModal.jsx:21-26` |
| `ConfirmModal` focuses its confirm button inside that same effect | `ConfirmModal.jsx:22` |
| Tests use `renderCard`, `openDialog()` (clicks "Request re-review", returns the dialog) and a mocked `suggestionsApi.requestRereview` | `webapp/src/components/history/__tests__/SuggestionCard.test.jsx:9-26,31` |
| The existing cancel test closes via the Cancel button and asserts no API call | `SuggestionCard.test.jsx:99-104` |
| `jest-dom` matchers (`toHaveAttribute`, `toHaveValue`) are already available in this test file | `SuggestionCard.test.jsx:59,95` |

## Design

- **D1 (Escape):** add the `ConfirmModal` effect to `RereviewModal`: `window.addEventListener('keydown', onKey)` with `if (e.key === 'Escape' && !rereview.isPending) onClose()`, removed on cleanup, deps `[onClose, rereview.isPending]`. This is the same rule as the backdrop click and the disabled Cancel button.
- **D2 (focus):** a `useRef` on the textarea and a **separate** `useEffect(() => { messageRef.current?.focus() }, [])`. It is not merged into the Escape effect as `ConfirmModal` does, because `onClose` is a new function on every parent render: the Escape effect re-runs on each render, and a focus call inside it would pull focus back to the textarea whenever the parent re-renders (for example when the `copied` timeout state changes, or a query refetch re-renders the card).
- **D3:** `ConfirmModal` and the other modals are not touched (out of scope in the issue). No new dependency, no CSS, no i18n key.
- **D4:** no focus return to the opening button on close. The issue does not ask for it.

## Tasks

| ID | Task | Acceptance criteria | Depends on |
|----|------|---------------------|------------|
| **RE-1** | `SuggestionCard.jsx`: in `RereviewModal`, add the Escape listener (D1) and focus the textarea on mount via a ref (D2) | Escape closes the modal; Escape while `rereview.isPending` leaves it open; the textarea has focus right after opening; the listener is removed when the modal closes or unmounts; only `SuggestionCard.jsx` and its test change | — |
| **RE-2** | Tests in `SuggestionCard.test.jsx`, `describe('SuggestionCard re-review')` | (a) `fireEvent.keyDown(window, { key: 'Escape' })` removes the dialog and does not call the API; a second Escape afterwards throws nothing and still does not call the API; (b) with `requestRereview` mocked as a never-resolving promise, click Send, then Escape: the dialog is still present (a regression guard: it passes before RE-1, and fails if the pending check is dropped from RE-1; (a) is its positive control); (c) after `openDialog()` the `Message (optional)` textarea `toHaveFocus()`. (a) and (c) fail without RE-1 | RE-1 |

Gates: `npm run test` and `npm run lint` in `webapp/`. No backend change, so no `mvn verify`.

## Risks

| Risk | Mitigation |
|------|------------|
| Escape also fires other listeners (for example a video swiper) while the modal is open | Same behavior as `ConfirmModal`; the modal is a `position: fixed` overlay and the issue asks for this exact pattern |
| `window` listener from a closed modal leaks | The cleanup removes it; RE-2(a) closes the modal and a second Escape must not throw and the dialog stays absent |
| Focus pulled back to the textarea on re-render | D2 keeps focus in an empty-deps effect |
