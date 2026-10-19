# Plan: Admin Suggestions: Mark Re-review Requests

## Overview

**Status:** Planned 2026-10-19 (autopilot, headless, issue #32)
**Author:** nguyenhuuca
**Date:** 2026-10-19
**Related plan:** [plan-suggestion-rereview](plan-suggestion-rereview.md) (added re-review), [plan-rereview-max-from-api](plan-rereview-max-from-api.md) (added `maxRereviews` to the DTO)
**ADR:** none. Frontend-only, one component, no API, schema or dependency change.
**Size:** Small. This plan is the only artifact.

## Objective

In the admin "Suggestions" tab, a suggestion the submitter sent back for re-review looks like a new one. Show a small badge under the status, "Re-review 1/2" (EN) / "Yêu cầu review lại 1/2" (VI), on every row with `rereviewCount > 0`.

## Verified Facts (read 2026-10-19)

| Fact | Where |
|------|-------|
| `SuggestionDto` already has `rereviewCount` and `maxRereviews` (plain `int`, so never missing from the API) | `api/src/main/java/com/canhlabs/funnyapp/dto/suggestion/SuggestionDto.java:41,43` |
| `toDto` always sets both fields, and the admin list path uses the same `toDto` | `VideoSuggestionServiceImpl.java:165,400-401` |
| The Status column renders `<StatusCell s={s} />` | `webapp/src/components/admin/AdminSuggestionTable.jsx:236` |
| `StatusCell` renders the status badge, then a muted `div` for the reject reason (REJECTED) and one for live/import job (APPROVED) | `AdminSuggestionTable.jsx:127-147` |
| The Note column already shows `s.note`; no change needed there | `AdminSuggestionTable.jsx:233` |
| Existing badge styles: `.admin-badge` and the `.pending` variant (yellow); `.admin-cell-muted` for the small line | `webapp/src/styles/index.css:1645,2426,1640` |
| The user-side card already uses a `rereviewCount` key with `{{count}}`/`{{max}}`, in `history.json` (not reusable here: different namespace and wording) | `webapp/src/i18n/locales/en/history.json:65`, `vi/history.json:63`, `SuggestionCard.jsx:142-143` |
| The admin namespace keeps suggestion strings under `sugg.*` in both locales | `en/admin.json:222-257`, `vi/admin.json:213-248` |
| Test file: `renderTable()`, language set to `vi` in `beforeEach`, fixtures `pending`/`rejected`/`approved`, `adminList` mocked per test | `webapp/src/components/admin/__tests__/AdminSuggestionTable.test.jsx:8-45` |
| Fixtures have no `rereviewCount`, so existing rows must show no badge (`undefined > 0` is false) | `AdminSuggestionTable.test.jsx:14-28` |

## Design

- **D1:** in `StatusCell`, after the status badge, render when `s.rereviewCount > 0`: `<div className="admin-cell-muted"><span className="admin-badge pending">{t('sugg.rereviewBadge', { count: s.rereviewCount, max: s.maxRereviews })}</span></div>`. Reuses `admin-badge pending` and `admin-cell-muted`; no new CSS, no inline styles beyond what exists.
- **D2:** shown for any status, not only PENDING. A re-reviewed row that was approved again still tells the admin the video was rejected before. The issue says "a row with `rereviewCount > 0`".
- **D3:** new key `sugg.rereviewBadge` in `en/admin.json` ("Re-review {{count}}/{{max}}") and `vi/admin.json` ("Yêu cầu review lại {{count}}/{{max}}"), added next to the `reopen*` keys inside `sugg`.
- **D4:** no backend change, no sorting or filtering by count (out of scope in the issue).

## Tasks

| ID | Task | Acceptance criteria | Depends on |
|----|------|---------------------|------------|
| **RR-1** | `AdminSuggestionTable.jsx` `StatusCell` renders the badge (D1) and `sugg.rereviewBadge` is added to `en/admin.json` and `vi/admin.json` with `{{count}}` and `{{max}}` (D3) | A row with `rereviewCount > 0` shows "Re-review n/max" (EN) / "Yêu cầu review lại n/max" (VI) under the status; `rereviewCount` 0 or missing shows nothing extra; the Note column is unchanged; no new CSS file, no new dependency; only the component and the two locale files change | — |
| **RR-2** | Tests in `AdminSuggestionTable.test.jsx` | (a) a row `{ ...pending, rereviewCount: 1, maxRereviews: 2 }` shows `Yêu cầu review lại 1/2` (the suite runs in `vi`); (b) the same row after `i18n.changeLanguage('en')` shows `Re-review 1/2`; (c) a row with `rereviewCount: 0` shows no badge, and so do the existing fixtures with the field missing (`queryByText(/review lại/)` is null). (a) and (b) fail without RR-1; (c) is the guard | RR-1 |

Gates: `npm run test` and `npm run lint` in `webapp/`. No backend change, so no `mvn verify`.

## Risks

| Risk | Mitigation |
|------|------------|
| `maxRereviews` missing in an old API response would print a raw `{{max}}` | The field is a primitive `int` on the DTO, so it is always present; the admin and user lists share `toDto` |
| Test (b) switches the language and could leak into later tests | `beforeEach` already resets the language to `vi` (`AdminSuggestionTable.test.jsx:31-33`) |
