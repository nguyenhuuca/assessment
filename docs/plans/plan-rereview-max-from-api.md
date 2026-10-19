# Plan: Max Re-review Count Comes From the API (Issue #28)

## Overview

**Status:** Planned 2026-10-19 (autopilot, headless, issue #28)
**Author:** nguyenhuuca
**Date:** 2026-10-19
**Related plan:** [plan-suggestion-rereview](plan-suggestion-rereview.md), [plan-suggestion-transitions](plan-suggestion-transitions.md)
**ADR:** none. One additive DTO field, no schema change, easy to revert.
**Size:** Small (under 1 hour, ~40 LOC). This plan is the only artifact.

## Objective

The limit on re-review requests lives in two places: `SuggestionTransition.MAX_REREVIEWS = 2` (BE) and `REREVIEW_MAX = 2` (FE). Changing one makes the "Re-review requested n/2" text wrong. Make the BE the only source: `SuggestionDto` gets `maxRereviews`, and the card reads it. The value stays 2, and no behavior or DB change.

## Verified Facts (read 2026-10-19)

| Fact | Where |
|------|-------|
| BE constant | `api/src/main/java/com/canhlabs/funnyapp/enums/SuggestionTransition.java:56` (`MAX_REREVIEWS = 2`) |
| DTO already carries the re-review state (`rereviewCount`, `canRequestRereview`) | `dto/suggestion/SuggestionDto.java:40-43` |
| One mapping point for every endpoint that returns a suggestion (`/suggestions/mine`, `/rereview`, admin list/approve/reject/reopen) | `service/impl/VideoSuggestionServiceImpl.java:382-404` (`toDto`, builder at `:400-401`) |
| FE hardcode | `webapp/src/components/history/SuggestionCard.jsx:10` (`const REREVIEW_MAX = 2`), used at `:131` in `t('rereviewCount', { count, max })` |
| i18n text already takes `{{max}}`, so no locale change | `webapp/src/i18n/locales/en/history.json:65`, `vi/history.json:63` |
| FE test fixture and the counter test (`'Re-review requested 1/2'`) | `webapp/src/components/history/__tests__/SuggestionCard.test.jsx:13`, `:41-42` |
| Controller tests mock the service, so they cannot prove a field is built from the constant | `web/VideoSuggestionControllerTest.java:255-268`, `web/AdminVideoSuggestionControllerTest.java:334` |
| Service tests call `mine` / `toDto` for real | `service/impl/VideoSuggestionServiceImplTest.java` (`rejected(id, count)` helper at `:579`) |

## Design

- **D1 — Add `private int maxRereviews;` to `SuggestionDto`** next to `rereviewCount`, with a one-line Javadoc "Re-review requests allowed per suggestion."
- **D2 — `toDto` sets `.maxRereviews(SuggestionTransition.MAX_REREVIEWS)`** on every suggestion it maps, admin and user alike. It is the constant itself, not a copy. `/suggestions/mine` already goes through `toDto`.
- **D3 — FE:** delete `REREVIEW_MAX` and pass `max: s.maxRereviews` at `SuggestionCard.jsx:131`. No fallback to a literal: a fallback would be the same hardcode again.
- **D4 — The counter keeps its `rereviewCount > 0` condition.** The text and locale files stay the same.

## Tasks

| ID | Task | Acceptance criteria | Depends on |
|----|------|---------------------|------------|
| **RM-1** | BE: add `maxRereviews` to `SuggestionDto`, set it in `toDto` from `SuggestionTransition.MAX_REREVIEWS`; add service test(s) | `mine(...)` for a rejected suggestion returns a DTO whose `getMaxRereviews()` equals `SuggestionTransition.MAX_REREVIEWS` (and is 2), with a count other than 0 so it is not a default; `VideoSuggestionServiceImplTest` + both controller tests otherwise pass unchanged; no migration | — |
| **RM-2** | FE: `SuggestionCard` uses `s.maxRereviews`, `REREVIEW_MAX` removed; update the test | `grep REREVIEW_MAX webapp/src` finds nothing. Fixture gets `maxRereviews`; a test renders `maxRereviews: 3, rereviewCount: 1` and expects `Re-review requested 1/3` (proves the text uses the API value, not 2); the existing `1/2` test passes with `maxRereviews: 2` in the fixture; `npm run test` and `npm run lint` green | RM-1 (API shape only; can be built in parallel) |

One commit. The API field is additive, so a FE that deploys before the BE shows `n/undefined` for a moment: the deploy order is BE first, which is the normal order in this repo's pipeline.

## Risks

| Risk | Mitigation |
|------|------------|
| FE shows `n/undefined` if the BE is not yet deployed | Additive field and BE deploys first; the counter only shows after a re-review was used |
| Another FE reader of the limit | `grep -rn "REREVIEW_MAX\|rereviewCount" webapp/src` found only `SuggestionCard.jsx` and its test |
