# Plan: Vietnamese + English (i18n)

<!--
Implementation Plan
Filename: docs/plans/plan-i18n.md
Owner: Builder (/swarm-execute)
Handoff to: Builder, QA Engineer
-->

## Overview

**Status:** Done (QA-1 verified on prod by owner; recorded 2026-10-18). Approved 2026-10-14.
**Author:** nguyenhuuca
**Date:** 2026-10-14
**Related PRD:** [PRD-i18n](../prd/PRD-i18n.md)
**Related ADR:** [ADR-0020](../adr/0020-i18n-vietnamese-english.md)

Owner decisions (2026-10-14):
- Use `react-i18next`.
- Default language comes from the browser.
- Scope covers user UI, API errors, emails and Admin.

## Objective

Every user-facing string on the frontend and backend comes from per-language resources (vi, en), with one switcher, a persisted preference, and localized errors and emails.

## Technical Approach

### Frontend
```
webapp/src/i18n/
  index.js                     # i18next init: resources, detector (localStorage 'lang' → navigator), supportedLngs [vi,en], fallback en
  format.js                    # relativeTime(date), compactNumber(n), formatDate(d): Intl.* with i18n.language
  locales/vi/*.json            # common, feed, library, auth, comments, notifications, settings, admin, errors
  locales/en/*.json
  __tests__/parity.test.js     # identical key sets per namespace, no empty values
components/common/LanguageSwitcher.jsx   # "VI | EN", aria-pressed; in header + Settings
```
- `main.jsx` imports `./i18n` before rendering.
- A `languageChanged` handler does three things:
  - Sets `document.documentElement.lang`.
  - Writes `localStorage.lang`.
  - When logged in, PATCHes `/user/settings { locale }`.
- `api/client.js` adds `Accept-Language: i18n.language` to every request.
- After login (`AuthContext`), if `settings.locale` is set and differs from the current language, the app calls `i18n.changeLanguage(settings.locale)`.
- Components call `const { t } = useTranslation('<ns>')`. Interpolation uses `t('library.removeFrom', { title })`, and plurals use `t('library.videoCount', { count })`.
- The default collection shows `t('library.watchLater')` when `isDefault`.
- `notificationText.js` and `commentUtils.js` call the i18n instance, using `i18n.t` outside components.
- `test-setup.js` runs `i18n.init` synchronously with `lng: 'vi'`.

### Backend
```
src/main/resources/i18n/messages.properties      # en (default)
src/main/resources/i18n/messages_vi.properties
config/I18nConfig.java                           # MessageSource (UTF-8, basename i18n/messages, fallbackToSystemLocale=false), AcceptHeaderLocaleResolver [vi,en] default en
exception/CustomException                         # + messageKey, args (optional; message kept for un-migrated callers)
exception/RestExceptionHandler                    # resolve messageKey with LocaleContextHolder.getLocale(); ResultErrorInfo + code
db/changelog/sql/2026101500xx-user-settings-locale.sql  # ALTER TABLE user_settings ADD COLUMN locale VARCHAR(5) NULL CHECK (locale IN ('vi','en'))
templates/email/invite_vi.html, invite_en.html    # split from invite.html
MailService.send...(…, Locale locale)             # locale passed explicitly (async-safe)
```

## Tasks

| ID | Task | Est. | Depends on | Acceptance criteria |
|----|------|------|-----------|---------------------|
| FE-1 | i18n foundation: dependencies, `i18n/index.js`, `format.js`, `LanguageSwitcher` in the header, `<html lang>`, `Accept-Language` in `client.js`, test setup, parity test, tech-strategy.md update | 1d | — | A `vi-VN` browser loads Vietnamese and `en-US` loads English. The switcher changes the language without a reload and persists it in `localStorage`. Every API request carries `Accept-Language`. The parity test fails when a key is removed from one language. |
| FE-2 | Core UI: `AppShell` (side and mobile nav, header), `video/*`, `explore/*`, `modals/*`, `common/*` | 1.5d | FE-1 | No hardcoded user text remains in these folders (grep check). Compact view counts and relative time follow the language. Existing tests pass with `vi`, and one `en` render test per component. |
| FE-3 | Library, comments, notifications: `library/*`, `comments/*`, `notifications/*`, `notificationText.js`, `commentUtils.js` | 1.5d | FE-1 | "Xem sau" / "Watch later" are shown for the default collection. The `{count} video(s)` plural is correct in `en`. Notification titles and removal reasons are translated. Relative time reads "2 phút trước" / "2 minutes ago". |
| FE-4 | Auth and Settings: `auth/*`, `settings/*`, plus a language row in Settings | 1d | FE-1, BE-2 | Login, MFA, password and magic-link screens are translated. The Settings language choice is saved to the server and restored after login on another device. |
| FE-5 | Admin: `admin/*` | 1.5d | FE-1 | The admin tables, filters, dialogs and import panel are translated. Admin tests pass. |
| BE-1 | `I18nConfig` (`MessageSource` and `LocaleResolver`), `CustomException.messageKey`, `RestExceptionHandler` + `ResultErrorInfo.code`, `messages*.properties`, and migration of user-facing errors (collections, comments, auth/password, rate limit, validation) | 1.5d | — | `Accept-Language: vi` returns a Vietnamese `message` and `en` (or no header) returns English; `code` is stable. A JUnit parity test checks that the two properties files share the same keys. Exceptions without a key behave as before. Coverage ≥ 90%. |
| BE-2 | `user_settings.locale`: migration, entity, DTO, and `PATCH /user/settings` validation (vi, en, null) | 0.5d | — | PATCH accepts `vi`, `en` and null and rejects anything else with 400. GET returns the value. The migration applies on prod data, where existing rows get NULL. |
| BE-3 | Localized emails: `invite_{vi,en}.html`, subjects and password emails through `MessageSource`, and the locale resolved as settings → request → `en` and passed explicitly | 1d | BE-1, BE-2 | A magic link for a `vi` user arrives in Vietnamese and for an `en` user in English. A brand-new sign-up uses the request language. The async password notice uses the stored locale, with no `LocaleContextHolder` in the mail path (unit test). |
| QA-1 | E2E and prod smoke | 1d | all | Desktop and mobile in both languages show no mixed chrome. Errors are localized. Emails are checked in both languages. Cloudflare does not edge-cache `/api` JSON, or `Vary: Accept-Language` is present. |

The total is about 10.5 days, which makes this a Medium feature (PRD, ADR and plan).

## Dependency Graph

```
FE-1 ─┬→ FE-2 ─────────────┐
      ├→ FE-3 ─────────────┤
      ├→ FE-5 ─────────────┤
      └→ FE-4 ←── BE-2 ────┼→ QA-1
BE-1 ─┬────────────────────┤
BE-2 ─┴→ BE-3 ─────────────┘
```

Two tracks run in parallel from the start: FE-1 and BE-1/BE-2. FE-2, FE-3 and FE-5 run in parallel after FE-1. Suggested PR order is FE-1 + BE-1 + BE-2, then FE-2/FE-3/FE-4, then BE-3, then FE-5, then QA-1.

## Beads

`bd` is not installed in this repo, so tasks are tracked in this table and in `docs/tracking.md`. When `bd` is available:

```bash
bd create --title="i18n: Vietnamese + English" --type=feature --priority=2
bd create --title="[FE-1] i18n foundation + switcher + Accept-Language" --type=task
bd create --title="[FE-2] Translate core UI (shell, video, explore, modals)" --type=task
bd create --title="[FE-3] Translate library, comments, notifications + Intl formatting" --type=task
bd create --title="[FE-4] Translate auth + settings, language preference sync" --type=task
bd create --title="[FE-5] Translate admin" --type=task
bd create --title="[BE-1] MessageSource + localized API errors" --type=task
bd create --title="[BE-2] user_settings.locale" --type=task
bd create --title="[BE-3] Localized emails" --type=task
bd create --title="[QA-1] i18n E2E + prod smoke" --type=task
# deps: FE-2/3/5←FE-1, FE-4←FE-1+BE-2, BE-3←BE-1+BE-2, QA-1←all
```

## Risks

| Risk | Mitigation |
|------|------------|
| A large diff across about 40 components breaks tests | Phase the work by folder (FE-2..5). Tests run with `vi` so most assertions are unchanged. Use key/role queries where English chrome changes. |
| Missing translation at runtime | The parity tests in CI (FE and BE) and `fallbackLng: 'en'` |
| Edge cache serves the wrong language | Verify Cloudflare cache rules for `/api`, and add `Vary: Accept-Language` on localized responses |
| Async email loses the locale | Pass the locale explicitly. A unit test asserts that the mail path does not depend on the thread locale. |
| Flash of the wrong language | Resources are bundled and the detector runs synchronously before `createRoot().render` |

## Rollback

- **FE:** revert the PRs. Resources and the switcher are additive, and components fall back to the previous text.
- **BE:** `messageKey` is optional, and without it the old `message` is returned. The `locale` column is nullable and unused when rolled back. Leave it, or run `ALTER TABLE user_settings DROP COLUMN locale`.

## Progress Log

| Date | Note |
|------|------|
| 2026-10-14 | Owner approved the PRD, ADR-0020 and this plan. The switcher is a "VI | EN" toggle and the English name is "Watch later". |
| 2026-10-14 | Plan created from a parallel exploration of FE strings and BE text, errors and emails. Owner chose react-i18next, browser default, and full scope including Admin. |
| 2026-10-15 | Implemented FE-1..5 and BE-1..3 with parallel workers. FE: 300 tests, ~20 KB gzip added. BE: 1145 tests, coverage 96.29%. QA: `/api` responses are `cache-control: no-store` and `cf-cache-status: DYNAMIC`, so no edge-cache leak and no `Vary` needed. useLocaleSync saves the UI language when `user_settings.locale` is null, so emails match. Digest emails for users who never log in again fall back to English (ADR D6). |
| 2026-10-18 | QA-1 passed: owner tested both languages on prod. Plan marked Done. |
